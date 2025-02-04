//
// Copyright (c) 2020 Couchbase, Inc All rights reserved.
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
import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * Objects with native peers subclass this type.
 * This object's log is part of its API, so that subclasses can create actions that are
 * atomic WRT the peer.
 * Ideally, it would never expose the peer handle at all...
 */
public abstract class C4NativePeer implements AutoCloseable {
    private static final String HANDLE_NAME = "peer handle";

    private final Object lock = new Object();

    @GuardedBy("lock")
    private volatile long peer;

    // Instrumentation
    private volatile Exception closedAt;

    // ??? questionable design
    protected C4NativePeer() { }

    protected C4NativePeer(long peer) { setPeerInternal(peer); }

    @NonNull
    @Override
    public String toString() { return "@x" + Long.toHexString(peer); }

    protected final void withPeer(@NonNull Fn.Consumer<Long> fn) {
        synchronized (getPeerLock()) {
            final long peer = get();
            if (peer != 0L) {
                fn.accept(peer);
                return;
            }
        }

        logBadCall();
    }

    @NonNull
    protected final <T, E extends Exception> T withPeer(
        @NonNull T def,
        @NonNull Fn.FunctionThrows<Long, T, E> fn)
        throws E {
        synchronized (getPeerLock()) {
            final long peer = get();
            if (peer != 0L) {
                final T val = fn.apply(peer);
                return (val == null) ? def : val;
            }
        }

        logBadCall();
        return def;
    }

    @Nullable
    protected final <T, E extends Exception> T withPeerOrNull(
        @NonNull Fn.FunctionThrows<Long, T, E> fn)
        throws E {
        synchronized (getPeerLock()) {
            final long peer = get();
            if (peer != 0L) { return fn.apply(peer); }
        }

        logBadCall();
        return null;
    }

    /**
     * Mark the peer as released.
     */
    protected final void releasePeer() {
        synchronized (lock) { releasePeerLocked(); }
    }

    /**
     * Release the native peer, giving it the passed goodbye-kiss.
     * When this method is used to release a peer that should already have been released
     * (say, in a finalizer for an [Auto]Closable object) pass a non-null domain to produce
     * an error message if the peer has not already been freed.
     * If this method is expecting to free the object (e.g., from the close() method)
     * passing a null domain will prevent it from logging an error.
     * <p>
     * Be careful about passing functions that seize locks: it would be easy to cause deadlocks.
     *
     * @param domain Domain for the error message if this call frees the peer.  No error message if null.
     * @param fn     The goodbye-kiss.  Be careful if this function seizes locks
     * @param <E>    The type of exception (typically none) thrown by the goodbye-kiss
     * @throws E the goodbye kiss failed.
     */
    protected final <E extends Exception> void releasePeer(
        @Nullable LogDomain domain,
        @NonNull Fn.ConsumerThrows<Long, E> fn)
        throws E {
        final long peer;

        // !!! This code occasionally causes the error message:
        //     java.lang.NullPointerException: Null reference used for synchronization (monitor-enter)
        synchronized (lock) {
            peer = releasePeerLocked();
            if (peer != 0L) { fn.accept(peer); }
        }

        if (!CouchbaseLiteInternal.debugging()) { return; }

        if (domain == null) {
            // here if we don't expect the peer to have been closed
            if (peer == 0L) { logBadCall(); }
            return;
        }

        // here if we expected the peer to have been closed
        if (peer != 0L) {
            Log.d(domain, "Peer %x for %s was not closed", peer, getClass().getSimpleName());
        }
    }

    // ??? questionable design
    // The next three methods should go away.
    // They invite race conditions and are accidents waiting to happen.
    // Ideally, this class should never expose the peer handle

    protected final void setPeer(long peer) { setPeerInternal(peer); }

    protected final long getPeerUnchecked() {
        final long peer = get();
        if (peer == 0L) { Log.v(LogDomain.DATABASE, "Unchecked peer is 0", new Exception("peer is 0")); }
        return peer;
    }

    protected final long getPeer() {
        final long peer = get();
        if (peer != 0L) { return peer; }

        logBadCall();
        throw new IllegalStateException("Operation on closed native peer");
    }

    /**
     * Get this object's lock.
     * Enables subclasses to assure atomicity with this class' internal behavior.
     * You *know* you gotta be careful with this, right?
     *
     * @return the lock used by this object
     */
    @NonNull
    protected final Object getPeerLock() { return lock; }

    //-------------------------------------------------------------------------
    // private methods
    //-------------------------------------------------------------------------

    private long get() {
        synchronized (getPeerLock()) { return peer; }
    }

    private void setPeerInternal(long peer) {
        Preconditions.assertNotZero(peer, HANDLE_NAME);
        synchronized (getPeerLock()) {
            Preconditions.assertZero(this.peer, HANDLE_NAME);
            this.peer = peer;
        }
    }

    @GuardedBy("lock")
    private long releasePeerLocked() {
        final long peer = this.peer;
        this.peer = 0L;
        if ((peer != 0L) && CouchbaseLiteInternal.debugging()) { closedAt = new Exception(); }
        return peer;
    }

    private void logBadCall() {
        if (CouchbaseLiteInternal.debugging()) { return; }

        Log.i(LogDomain.DATABASE, "Operation on closed native peer", new Exception());
        final Exception closedLoc = closedAt;
        if (closedLoc != null) { Log.e(LogDomain.DATABASE, "Closed at", closedLoc); }
    }
}
