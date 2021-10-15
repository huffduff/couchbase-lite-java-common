//
// Copyright (c) 2020, 2017 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite.internal.core;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.SocketFactory;
import com.couchbase.lite.internal.core.peers.NativeRefPeerBinding;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * The process for closing one of these is complicated.  No matter what happens, though, it always ends like this:
 * Java calls C4Socket.closed (in the JNI, this turns into a call to c4socket_closed, which actually frees
 * the native object). Presuming that the C has a non-null C4SocketFactory reference and that it contains a
 * non-null socket_dispose reference, the C invokes it, producing the call to C4Socket.dispose
 * <p>
 * I think that this entire class should be re-architected to use a single-threaded executor.
 * Incoming messages should be enqueued as tasks on the executor. That would allow the removal
 * of all of the synchronization and assure that tasks were processed in order.
 * <p>
 * Note that state transitions come from 3 places.  Neither of the two subclasses, MessageSocket nor
 * AbstractCBLWebSocket, allow inbound connections.  For both, though shutdown is multiphase.
 * <nl>
 * <li>Core: core request open and can request close</li>
 * <li>Remote: this is a connection to a remote service.  It can request shutdown</li>
 * <li>Client: the client code can close the connection.  It expects never to hear from it again</li>
 * </nl>
 */
@SuppressWarnings({"LineLength", "PMD.TooManyMethods", "PMD.GodClass"})
public abstract class C4Socket extends C4NativePeer {
    //-------------------------------------------------------------------------
    // Constants
    //-------------------------------------------------------------------------
    private static final LogDomain LOG_DOMAIN = LogDomain.NETWORK;

    // C4SocketFraming (c4Socket.h)
    public static final int WEB_SOCKET_CLIENT_FRAMING = 0; ///< Frame as WebSocket client messages (masked)
    public static final int NO_FRAMING = 1;                ///< No framing; use messages as-is
    public static final int WEB_SOCKET_SERVER_FRAMING = 2; ///< Frame as WebSocket server messages (not masked)

    //-------------------------------------------------------------------------
    // Static Variables
    //-------------------------------------------------------------------------

    // Lookup table: maps a handle to a peer native socket to its Java companion
    private static final NativeRefPeerBinding<C4Socket> BOUND_SOCKETS = new NativeRefPeerBinding<>();

    //-------------------------------------------------------------------------
    // JNI callback methods
    //-------------------------------------------------------------------------

    // This method is called by reflection.  Don't change its signature.
    static void open(
        long peer,
        @Nullable Object factory,
        @Nullable String scheme,
        @Nullable String hostname,
        int port,
        @Nullable String path,
        @NonNull byte[] options) {
        C4Socket socket = BOUND_SOCKETS.getBinding(peer);
        Log.d(LOG_DOMAIN, "C4Socket.open @%x: %s, %s", peer, socket, factory);

        // !!! What happens when a C thread gets an exception???

        // This socket will be bound in C4Socket.<init>
        if (socket == null) {
            if (!(factory instanceof SocketFactory)) {
                throw new IllegalArgumentException("Context is not a socket factory: " + factory);
            }

            socket = ((SocketFactory) factory).createSocket(
                peer,
                Preconditions.assertNotNull(scheme, "scheme"),
                Preconditions.assertNotNull(hostname, "hostname"),
                port,
                Preconditions.assertNotNull(path, "path"),
                options);
        }

        Preconditions.assertNotNull(socket, "socket").openSocket();
    }

    // This method is called by reflection.  Don't change its signature.
    static void write(long peer, @Nullable byte[] allocatedData) {
        if (allocatedData == null) {
            Log.d(LOG_DOMAIN, "C4Socket.write: allocatedData is null");
            return;
        }

        final C4Socket socket = BOUND_SOCKETS.getBinding(peer);
        Log.d(LOG_DOMAIN, "C4Socket.write(%d) @%x: %s", allocatedData.length, peer, socket);

        if (socket == null) {
            Log.w(LogDomain.NETWORK, "No socket for peer @%x! Packet(%d) dropped!", peer, allocatedData.length);
            return;
        }

        socket.send(allocatedData);
    }

    // This method is called by reflection.  Don't change its signature.
    static void completedReceive(long peer, long byteCount) {
        final C4Socket socket = BOUND_SOCKETS.getBinding(peer);
        Log.d(LOG_DOMAIN, "C4Socket.completedReceive(%d) @%x: %s", byteCount, peer, socket);

        if (socket == null) {
            Log.w(LogDomain.NETWORK, "No socket for peer @%x! Receipt dropped!", peer);
            return;
        }

        socket.completedReceive(byteCount);
    }

    // This method is called by reflection.  Don't change its signature.
    static void requestClose(long peer, int status, @Nullable String message) {
        final C4Socket socket = BOUND_SOCKETS.getBinding(peer);
        Log.d(LOG_DOMAIN, "C4Socket.requestClose(%d) @%x: %s, '%s'", status, peer, socket, message);

        if (socket == null) {
            Log.w(LogDomain.NETWORK, "No socket for peer @%x! Close request dropped!", peer);
            return;
        }

        socket.requestClose(status, message);
    }

    // This method is called by reflection.  Don't change its signature.
    // NOTE: close(long) method should not be called.
    static void close(long peer) {
        final C4Socket socket = BOUND_SOCKETS.getBinding(peer);
        Log.d(LOG_DOMAIN, "C4Socket.close @%x: %s", peer, socket);

        if (socket == null) {
            Log.w(LogDomain.NETWORK, "No socket for peer @%x! Close dropped!", peer);
            return;
        }

        socket.closeSocket();
    }

    // This method is called by reflection.  Don't change its signature.
    // NOTE: close(long) method should not be called.
    //
    // It is the second half of the asynchronous close process.
    // We are guaranteed this callback once we call `C4Socket.closed`
    // This is where we actually free the Java object.
    static void dispose(long peer) { BOUND_SOCKETS.unbind(peer); }


    //-------------------------------------------------------------------------
    // Fields
    //-------------------------------------------------------------------------

    @GuardedBy("getPeerLock()")
    private boolean closing;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    protected C4Socket(long peer) {
        super(peer);
        BOUND_SOCKETS.bind(peer, this);
    }

    protected C4Socket(@NonNull String schema, @NonNull String host, int port, @NonNull String path, int framing) {
        this(fromNative(0L, schema, host, port, path, framing));
    }

    //-------------------------------------------------------------------------
    // Abstract methods (Core to Remote)
    //-------------------------------------------------------------------------

    protected abstract void openSocket();

    protected abstract void send(@NonNull byte[] allocatedData);

    protected abstract void completedReceive(long byteCount);

    protected abstract void requestClose(int status, @Nullable String message);

    // NOTE!! The implementation of this method *MUST* call closed(int, int, String)
    protected abstract void closeSocket();

    //-------------------------------------------------------------------------
    // Protected methods (Remote to Core)
    //-------------------------------------------------------------------------

    protected final void opened() {
        final long peer;
        synchronized (getPeerLock()) {
            peer = getPeerUnchecked();
            if (peer != 0) { opened(peer); }
        }
        Log.d(LOG_DOMAIN, "C4Socket.opened @%x: %s", peer, this);
    }

    protected final void gotHTTPResponse(int httpStatus, @Nullable byte[] responseHeadersFleece) {
        final long peer;
        synchronized (getPeerLock()) {
            peer = getPeerUnchecked();
            if (peer != 0) { gotHTTPResponse(peer, httpStatus, responseHeadersFleece); }
        }
        Log.d(LOG_DOMAIN, "C4Socket.gotHTTPResponse(%d) @%x: %s", httpStatus, peer, this);
    }

    protected final void completedWrite(long byteCount) {
        final long peer;
        synchronized (getPeerLock()) {
            peer = getPeerUnchecked();
            if (peer != 0) { completedWrite(peer, byteCount); }
        }
        Log.d(LOG_DOMAIN, "C4Socket.completedWrite(%d) @%x: %s", byteCount, peer, this);
    }

    protected final void received(@NonNull byte[] data) {
        final long peer;
        synchronized (getPeerLock()) {
            peer = getPeerUnchecked();
            if (peer != 0) { received(peer, data); }
        }
        Log.d(LOG_DOMAIN, "C4Socket.received(%d) @%x: %s", data.length, peer, this);
    }

    protected final void closeRequested(int status, String message) {
        final long peer;
        synchronized (getPeerLock()) {
            peer = getPeerUnchecked();
            if (peer != 0) { closeRequested(peer, status, message); }
        }
        Log.d(LOG_DOMAIN, "C4Socket.closeRequested(%d) @%x: %s, '%s'", status, peer, this, message);
    }

    protected final void closed(int errorDomain, int errorCode, String message) {
        closeInternal(errorDomain, errorCode, message);
    }

    @GuardedBy("getPeerLock()")
    protected final boolean isC4SocketClosing() { return closing || (getPeerUnchecked() == 0L); }

    // there's really no point in having a finalizer...
    // there's a hard reference to this object in HANDLES_TO_SOCKETS

    //-------------------------------------------------------------------------
    // package protected methods
    //-------------------------------------------------------------------------

    // !!! Wildly unsafe...
    final long getPeerHandle() { return getPeer(); }

    //-------------------------------------------------------------------------
    // private methods
    //-------------------------------------------------------------------------

    private void closeInternal(int domain, int code, String msg) {
        final long peer;
        synchronized (getPeerLock()) {
            peer = getPeerUnchecked();
            if (!closing && (peer != 0)) { closed(peer, domain, code, msg); }
            closing = true;
        }
        Log.d(LOG_DOMAIN, "C4Socket.closed(%d,%d) @%x: %s, '%s'", domain, code, peer, this, msg);
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    // wrap an existing Java C4Socket in a C-native C4Socket
    private static native long fromNative(
        long token,
        String schema,
        String host,
        int port,
        String path,
        int framing);

    private static native void opened(long peer);

    private static native void gotHTTPResponse(long peer, int httpStatus, @Nullable byte[] responseHeadersFleece);

    private static native void completedWrite(long peer, long byteCount);

    private static native void received(long peer, byte[] data);

    private static native void closeRequested(long peer, int status, String message);

    private static native void closed(long peer, int errorDomain, int errorCode, String message);
}
