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
package com.couchbase.lite.internal.replicator;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.EOFException;
import java.net.NoRouteToHostException;
import java.net.PortUnreachableException;
import java.net.SocketException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.concurrent.ThreadSafe;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.TrustManager;

import okhttp3.Authenticator;
import okhttp3.Challenge;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.Credentials;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.core.C4Replicator;
import com.couchbase.lite.internal.core.peers.TaggedWeakPeerBinding;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.sockets.CoreSocketDelegate;
import com.couchbase.lite.internal.sockets.CoreSocketListener;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.StateMachine;
import com.couchbase.lite.internal.utils.StringUtils;


/**
 * First of all, you need to know about ProtocolTypes.
 * Core knows all about the WebSockets protocol.  It would be glad to be pretty much completely responsible
 * for a WS connection, if we could just send the bytes across some raw byte stream.  For better or worse, though
 * we hired OkHTTP for this job. It is also very smart and *it* wants to handle the WS connection.  The solution,
 * dating back to the dawn of time, is that we *always* use what Core, quite oddly, calls MESSAGE_STREAM protocol.
 * In this mode Core hands us only minimal state transition information and the basic payload data that
 * must be transferred.
 * The comments in c4Socket.h are incredibly valuable.
 * <p>
 * So, some assumptions.  If you are here:
 * <ul>
 * <li> you are talking MESSAGE_STREAM.
 * <li> there are no inbound connections
 * </ul>
 * This class is just a switch that routes things between OkHttp and Core.  Core does its callbacks via the
 * abstract methods defined in C4Socket and implemented here.  OkHttp does its callbacks to the CBLWebSocketListener
 * which proxies them directly to Core, via C4Socket.
 * The peculiar factory method returns an instance of the concrete subclass, CBLWebSocket.  There are different
 * sources for that class, one for each of the (CE/EE x platform) variants of the product.
 * <p>
 * State transition:
 * Things kick off when Core calls openSocket.  We are now in the state CONNECTING.  In response, we ask OkHttp
 * to open a connection to the remote. In the happy case, OkHttp successfully makes the connection to the remote.
 * That causes a callback to CBLWebSocketListener.onOpen.  We proxy that call to Core.  The connection is now OPEN.
 * After that, the two sides chat.  If Core has something to say, we get a call to send().  We proxy that call to
 * OkHttp, and, when the data has been sent, call back to Core with completedWrite().  If the remote has something
 * to say, we get a call to one of the two CBLWebSocketListener.onMessage() methods and proxy the content to
 * Core by calling received()
 * Eventually, someone decides to close the connection.  If it is the remote, we get a call to
 * CBLWebSocketListener.onClosing().  We proxy that to Core, which, surprisingly, turns right around and proxies
 * it back to us, by calling close().
 * If it is Core that decides to close the connection, we get a call to requestClose().  That should result in a
 * call to CBLWebSocketListener.onClosed().
 * <p>
 * This class is going to cause deadlocks.  While C4Socket does not synchronise outbound messages (Core to remote)
 * this class does.  Messages headed in either direction (to or from core) seize the object lock (from C4NativePeer).
 * If message processing seizes any other locks, the two lock may be seized in the opposite order depending on which
 * way the message is going.  This invites deadlock.
 */
@SuppressWarnings({"PMD.GodClass", "PMD.CyclomaticComplexity", "PMD.ExcessiveImports"})
public abstract class AbstractCBLWebSocket implements CoreSocketListener, AutoCloseable {
    private static final LogDomain TAG = LogDomain.NETWORK;

    private static final int MAX_AUTH_RETRIES = 3;
    public static final int DEFAULT_HEARTBEAT_SEC = 300;

    private static final String CHALLENGE_BASIC = "Basic";
    private static final String HEADER_AUTH = "Authorization";

    private enum State {INIT, CONNECTING, OPEN, CLOSE_REQUESTED, CLOSING, CLOSED, FAILED}

    //-------------------------------------------------------------------------
    // Internal types
    //-------------------------------------------------------------------------

    @ThreadSafe
    final class OkHttpRemote extends WebSocketListener {

        // OkHTTP callback, proxied to C4Socket
        // We have an open connection to the remote
        @Override
        public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
            onRemoteOpen(webSocket, response);
        }

        // OkHTTP callback, proxied to C4Socket
        // Remote sent data
        @Override
        public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
            onRemoteMessage(text.getBytes(StandardCharsets.UTF_8));
        }

        // OkHTTP callback, proxied to C4Socket
        // Remote sent data
        @Override
        public void onMessage(@NonNull WebSocket webSocket, @NonNull ByteString bytes) {
            onRemoteMessage(bytes.toByteArray());
        }

        // OkHTTP callback, proxied to C4Socket
        // Remote wants to close the connection
        @Override
        public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
            onRemoteClosing(code, reason);
        }

        // OkHTTP callback, proxied to C4Socket
        // Remote connection has been closed
        @Override
        public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
            onRemoteClosed(code, reason);
        }

        // OkHTTP callback, proxied to C4Socket
        // Remote connection failed
        // Invoked when a web socket has been closed due to an error reading from or writing to the network.
        // Outgoing and incoming messages may have been lost. OkHTTP will not make any more calls to this listener
        @Override
        public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable err, @Nullable Response response) {
            onRemoteFailure(err, response);
        }
    }

    // Using the C4NativePeer lock to protect cookieStore may seem like overkill.  We have to use it anyway,
    // for the (very necessary) call to assertState.  Might as well use it everywhere...
    private class WebSocketCookieJar implements CookieJar {
        @Override
        public void saveFromResponse(@NonNull HttpUrl httpUrl, @NonNull List<Cookie> cookies) {
            synchronized (delegate.getLock()) {
                for (Cookie cookie: cookies) { cookieStore.setCookie(httpUrl.uri(), cookie.toString()); }
            }
        }

        @NonNull
        @Override
        public List<Cookie> loadForRequest(@NonNull HttpUrl url) {
            final List<Cookie> cookies = new ArrayList<>();
            synchronized (delegate.getLock()) {
                if (!state.assertState(State.INIT, State.CONNECTING)) { return cookies; }

                // Cookies from config
                if (options != null) {
                    final String confCookies = (String) options.get(C4Replicator.REPLICATOR_OPTION_COOKIES);
                    if (confCookies != null) { cookies.addAll(CBLCookieStore.parseCookies(url, confCookies)); }
                }

                // Set cookies in the CookieStore
                final String setCookies = cookieStore.getCookies(url.uri());
                if (setCookies != null) { cookies.addAll(CBLCookieStore.parseCookies(url, setCookies)); }

                return cookies;
            }
        }
    }

    //-------------------------------------------------------------------------
    // Static members
    //-------------------------------------------------------------------------

    @NonNull
    private static final TaggedWeakPeerBinding<KeyManager> KEY_MANAGERS = new TaggedWeakPeerBinding<>();

    private static final StateMachine.Builder<State> WS_STATE_BUILDER
        = new StateMachine.Builder<>(State.class, State.INIT, State.FAILED)
        .addTransition(State.INIT, State.CONNECTING)
        .addTransition(State.CONNECTING, State.OPEN, State.CLOSE_REQUESTED, State.CLOSING, State.CLOSED)
        .addTransition(State.OPEN, State.CLOSE_REQUESTED, State.CLOSING, State.CLOSED)
        .addTransition(State.CLOSE_REQUESTED, State.CLOSING, State.CLOSED)
        .addTransition(State.CLOSING, State.CLOSED);

    @NonNull
    private static final OkHttpClient BASE_HTTP_CLIENT = new OkHttpClient.Builder()
        // timeouts: Core manages this: set no timeout, here.
        .connectTimeout(0, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(0, TimeUnit.SECONDS)

        // redirection
        .followRedirects(true)
        .followSslRedirects(true)

        // ??? .retryOnConnectionFailure(false)
        .build();

    //-------------------------------------------------------------------------
    // Static methods
    //-------------------------------------------------------------------------

    public static long addKeyManager(@NonNull KeyManager keyManager) {
        final long token = KEY_MANAGERS.reserveKey();
        KEY_MANAGERS.bind(token, keyManager);
        return token;
    }


    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------

    // assert these are thread-safe
    @NonNull
    private final URI uri;
    @NonNull
    private final OkHttpRemote okHttpRemote;
    @NonNull
    private final OkHttpClient okHttpSocketFactory;
    @Nullable
    private final Map<String, Object> options;
    @NonNull
    private final Fn.Consumer<List<Certificate>> serverCertsListener;

    @GuardedBy("getPeerLock()")
    @NonNull
    private final StateMachine<State> state = WS_STATE_BUILDER.build();
    @GuardedBy("getPeerLock()")
    @NonNull
    private final CBLCookieStore cookieStore;
    @NonNull
    protected final CoreSocketDelegate delegate;

    @GuardedBy("getPeerLock()")
    private WebSocket webSocket;

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    protected AbstractCBLWebSocket(
        @NonNull CoreSocketDelegate delegate,
        @NonNull URI uri,
        @Nullable byte[] opts,
        @NonNull CBLCookieStore cookieStore,
        @NonNull Fn.Consumer<List<Certificate>> serverCertsListener)
        throws GeneralSecurityException {
        this.delegate = delegate;
        this.uri = uri;
        this.options = (opts == null) ? null : Collections.unmodifiableMap(FLValue.fromData(opts).asDict());
        this.cookieStore = cookieStore;
        this.serverCertsListener = serverCertsListener;
        this.okHttpSocketFactory = setupOkHttpFactory();
        this.okHttpRemote = new OkHttpRemote();
    }

    @Override
    @NonNull
    public String toString() { return "CBLWebSocket{@" + super.toString() + ": " + uri + "}"; }

    // Implementation of AutoClosable.close()
    @Override
    public void close() {
        Log.d(TAG, "%s#External told to close: %s", this, uri);
        synchronized (delegate.getLock()) {
            if (state.setState(State.CLOSE_REQUESTED)) {
                delegate.closeRequested(C4Constants.WebSocketError.GOING_AWAY, "Closed by client");
                return;
            }
            if (state.setState(State.CLOSING)) {
                closeWebSocket(C4Constants.WebSocketError.GOING_AWAY, "Closed by client");
                return;
            }
            state.setState(State.CLOSED);
        }
    }

    @NonNull
    @VisibleForTesting
    public final OkHttpClient getOkHttpSocketFactory() { return okHttpSocketFactory; }

    @Nullable
    @VisibleForTesting
    public Map<String, Object> getOptions() { return options; }

    //-------------------------------------------------------------------------
    // Abstract methods
    //-------------------------------------------------------------------------

    // Allow subclass to handle errors.
    protected abstract boolean handleClose(Throwable error);

    protected abstract int handleCloseCause(Throwable error);


    //-------------------------------------------------------------------------
    // Implementation of CoreSocketListener (Core to Remote)
    //
    // This is the synchronization that is going to get us into trouble
    //-------------------------------------------------------------------------

    // Core callback, proxied to OkHTTP
    // Core needs a connection to the remote
    @Override
    public final void onCoreRequestOpen() {
        Log.d(TAG, "%s#Core connect: %s", this, uri);
        synchronized (delegate.getLock()) {
            if (!state.setState(State.CONNECTING)) { return; }
            okHttpSocketFactory.newWebSocket(newRequest(), okHttpRemote);
        }
    }

    // Core callback, proxied to OkHTTP
    // Core wants to transfer data to the remote
    @Override
    public final void onCoreSend(@NonNull byte[] data) {
        final int dLen = data.length;
        Log.d(TAG, "%s#Core send: %d", this, dLen);
        synchronized (delegate.getLock()) {
            if (!state.assertState(State.OPEN)) { return; }

            if (!webSocket.send(ByteString.of(data, 0, dLen))) {
                Log.i(TAG, "CBLWebSocket failed to send data of length = " + dLen);
                return;
            }

            delegate.completedWrite(data.length);
        }
    }

    // Core callback, ignored
    // Core confirms the reception of n bytes
    @Override
    public final void onCoreCompletedReceive(long n) { Log.d(TAG, "%s#Core complete receive: %d", this, n); }

    // Core callback, proxied to OkHttp
    // Core wants to break the connection
    @Override
    public final void onCoreRequestClose(int code, String message) {
        Log.d(TAG, "%s#Core request close: %d", this, code);
        synchronized (delegate.getLock()) {
            if (!state.setState(State.CLOSE_REQUESTED)) { return; }
            closeWebSocket(code, message);
        }
    }

    // Core callback.
    // Used in byte stream mode: irrelevant here
    @Override
    public final void onCoreClosed() { Log.d(TAG, "%s#Core closed", this); }


    //-------------------------------------------------------------------------
    // Implementation of CoreSocketListener (Remote to Core)
    //
    // This is the synchronization that is going to get us into trouble
    //-------------------------------------------------------------------------

    private void onRemoteFailure(@NonNull Throwable err, @Nullable Response response) {
        Log.d(TAG, "%s#OkHTTP failed: %s, %s", this, err, response);
        synchronized (delegate.getLock()) {
            state.setState(State.FAILED);

            if (response == null) {
                closeWithError(err);
                return;
            }

            closeWithCode(response.code(), response.message());
        }
    }

    private void onRemoteClosed(int code, @NonNull String reason) {
        Log.d(TAG, "%s#OkHTTP closed: (%d) %s", this, code, reason);
        synchronized (delegate.getLock()) {
            if (!state.setState(State.CLOSED)) { return; }
            closeWithCode(code, reason);
        }
    }

    private void onRemoteClosing(int code, @NonNull String reason) {
        Log.d(TAG, "%s#OkHTTP closing: %s", this, reason);
        synchronized (delegate.getLock()) {
            if (!state.setState(State.CLOSE_REQUESTED)) { return; }
            delegate.closeRequested(code, reason);
        }
    }

    private void onRemoteMessage(@NonNull byte[] data) {
        Log.d(TAG, "%s#OkHTTP text data: %d", this, data.length);
        synchronized (delegate.getLock()) {
            if (!state.assertState(State.OPEN)) { return; }
            delegate.received(data);
        }
    }

    private void onRemoteOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
        Log.d(TAG, "%s#OkHTTP open: %s", this, response);
        synchronized (delegate.getLock()) {
            if (!state.setState(State.OPEN)) { return; }
            this.webSocket = webSocket;
            receivedHTTPResponse(response);
            delegate.opened();
        }
        Log.i(TAG, "WebSocket OPEN");
    }

    //-------------------------------------------------------------------------
    // private methods
    //-------------------------------------------------------------------------

    @GuardedBy("getPeerLock()")
    private void receivedHTTPResponse(@NonNull Response response) {
        Log.d(TAG, "CBLWebSocket received HTTP response %s", response);

        // Post the response headers to LiteCore:
        final Headers hs = response.headers();
        if ((hs == null) || (hs.size() <= 0)) { return; }

        byte[] headersFleece = null;
        final Map<String, Object> headers = new HashMap<>();
        for (int i = 0; i < hs.size(); i++) { headers.put(hs.name(i), hs.value(i)); }

        try (FLEncoder enc = FLEncoder.getManagedEncoder()) {
            enc.write(headers);
            headersFleece = enc.finish();
        }
        catch (LiteCoreException e) {
            Log.w(TAG, "CBLWebSocket failed to encode response headers", e);
            Log.d(TAG, StringUtils.toString(headers));
        }

        delegate.gotHTTPResponse(response.code(), headersFleece);
    }

    // Close the OkHTTP connection to the remote
    @GuardedBy("getPeerLock()")
    private void closeWebSocket(int code, String message) {
        // never got opened...
        if (webSocket == null) { return; }

        // We've told Core to leave the connection to us, so it might pass us the HTTP status
        // If it does, we need to convert it to a WS status for the other side.
        if ((code > C4Constants.HttpError.STATUS_MIN) && (code < C4Constants.HttpError.STATUS_MAX)) {
            code = C4Constants.WebSocketError.POLICY_ERROR;
        }

        if (!webSocket.close(code, message)) {
            Log.i(TAG, "CBLWebSocket failed to initiate a graceful shutdown of this web socket.");
        }
    }

    @GuardedBy("getPeerLock()")
    private void closeWithCode(int code, String reason) {
        Log.v(TAG, "WebSocket CLOSED with code: " + code + "(" + reason + ")");

        // success
        if (code == C4Constants.WebSocketError.NORMAL) {
            delegate.closed(C4Constants.ErrorDomain.LITE_CORE, C4Constants.LiteCoreError.SUCCESS, null);
            return;
        }

        delegate.closed(C4Constants.ErrorDomain.WEB_SOCKET, code, reason);
    }

    @GuardedBy("getPeerLock()")
    private void closeWithError(@Nullable Throwable error) {
        Log.i(TAG, "WebSocket CLOSED with error", error);

        // this probably doesn't happen
        if (error == null) {
            delegate.closed(C4Constants.ErrorDomain.WEB_SOCKET, 0, null);
            return;
        }

        if (handleClose(error)) { return; }

        // ??? this is a kludge to get this test to handle Android versioning,
        //  and still fit into this if-then-else chain.
        // ... which, in itself is a kludge: this implementation is incredibly fragile,
        // being all order dependent and ad-hoc
        final int causeCode = getCodeForCause(error);

        int domain = C4Constants.ErrorDomain.NETWORK;
        final int code;

        if ((error instanceof NoRouteToHostException) || (error instanceof PortUnreachableException)) {
            code = C4Constants.NetworkError.HOST_UNREACHABLE;
        }

        else if ((error instanceof SocketException) || (error instanceof EOFException)) {
            code = C4Constants.NetworkError.NOT_CONNECTED;
        }

        else if (causeCode > 0) { code = causeCode; }

        // UnknownHostException - this is also thrown when in Airplane mode or offline
        else if (error instanceof UnknownHostException) {
            code = C4Constants.NetworkError.UNKNOWN_HOST;
        }

        else if (error instanceof SSLHandshakeException) {
            code = C4Constants.NetworkError.TLS_HANDSHAKE_FAILED;
        }

        else if (error instanceof SSLPeerUnverifiedException) {
            code = C4Constants.NetworkError.TLS_CERT_UNTRUSTED;
        }

        else if (error instanceof SSLException) {
            domain = C4Constants.ErrorDomain.WEB_SOCKET;
            code = C4Constants.WebSocketError.TLS_FAILURE;
        }

        // default: no idea what happened.
        else {
            domain = C4Constants.ErrorDomain.WEB_SOCKET;
            code = C4Constants.WebSocketError.POLICY_ERROR;
        }

        delegate.closed(domain, code, error.toString());
    }

    private int getCodeForCause(Throwable error) {
        final Throwable cause = error.getCause();
        if (cause == null) { return -1; }

        final int code = handleCloseCause(cause);
        if (code > 0) { return code; }

        if (cause instanceof CertificateExpiredException) {
            return C4Constants.NetworkError.TLS_CERT_REVOKED;
        }

        if (cause instanceof CertificateException) {
            return C4Constants.NetworkError.TLS_CERT_UNTRUSTED;
        }

        return 0;
    }

    @NonNull
    private OkHttpClient setupOkHttpFactory() throws GeneralSecurityException {
        final OkHttpClient.Builder builder = BASE_HTTP_CLIENT.newBuilder();

        // Heartbeat
        if (options != null) {
            final Object heartbeat = options.get(C4Replicator.REPLICATOR_HEARTBEAT_INTERVAL);
            builder.pingInterval(
                (heartbeat instanceof Number) ? ((long) heartbeat) : DEFAULT_HEARTBEAT_SEC,
                TimeUnit.SECONDS);
        }

        // Authenticator
        final Authenticator authenticator = getBasicAuthenticator();
        if (authenticator != null) { builder.authenticator(authenticator); }

        // Cookies
        builder.cookieJar(new WebSocketCookieJar());

        // Setup SSLFactory and trusted certificate (pinned certificate)
        setupSSLSocketFactory(builder);

        return builder.build();
    }

    @NonNull
    private Request newRequest() {
        final Request.Builder builder = new Request.Builder();

        // Sets the URL target of this request.
        builder.url(uri.toString());

        // Set/update the "Host" header:
        String host = uri.getHost();
        if (uri.getPort() >= 0) { host = host + ":" + uri.getPort(); }
        builder.header("Host", host);

        // Add any additional headers
        if (options != null) {
            final Object extraHeaders = options.get(C4Replicator.REPLICATOR_OPTION_EXTRA_HEADERS);
            if (extraHeaders instanceof Map<?, ?>) {
                for (Map.Entry<?, ?> header: ((Map<?, ?>) extraHeaders).entrySet()) {
                    builder.header(header.getKey().toString(), header.getValue().toString());
                }
            }

            // Configure WebSocket related headers:
            final Object protocols = options.get(C4Replicator.SOCKET_OPTION_WS_PROTOCOLS);
            if (protocols instanceof String) { builder.header("Sec-WebSocket-Protocol", (String) protocols); }
        }

        // Construct the HTTP request
        return builder.build();
    }

    @Nullable
    private Authenticator getBasicAuthenticator() {
        if (options == null) { return null; }

        final Object obj = options.get(C4Replicator.REPLICATOR_OPTION_AUTHENTICATION);
        if (!(obj instanceof Map)) { return null; }
        final Map<?, ?> auth = (Map<?, ?>) obj;

        final Object authType = auth.get(C4Replicator.REPLICATOR_AUTH_TYPE);
        if (!C4Replicator.AUTH_TYPE_BASIC.equals(authType)) { return null; }

        final Object username = auth.get(C4Replicator.REPLICATOR_AUTH_USER_NAME);
        if (!(username instanceof String)) { return null; }
        final Object password = auth.get(C4Replicator.REPLICATOR_AUTH_PASSWORD);
        if (!(password instanceof String)) { return null; }

        return (route, response) -> authenticate(response, (String) username, (String) password);
    }

    private void setupSSLSocketFactory(@NonNull OkHttpClient.Builder builder) throws GeneralSecurityException {
        byte[] pinnedServerCert = null;
        boolean acceptOnlySelfSignedServerCert = false;
        KeyManager[] keyManagers = null;
        if (options != null) {
            // Pinned Certificate:
            Object opt = options.get(C4Replicator.REPLICATOR_OPTION_PINNED_SERVER_CERT);
            if (opt instanceof byte[]) { pinnedServerCert = (byte[]) opt; }

            // Accept only self-signed server cert mode:
            opt = options.get(C4Replicator.REPLICATOR_OPTION_SELF_SIGNED_SERVER_CERT);
            if (opt instanceof Boolean) { acceptOnlySelfSignedServerCert = (boolean) opt; }

            // KeyManager for client cert authentication:
            final KeyManager clientCertAuthKeyManager = getAuthenticator();
            if (clientCertAuthKeyManager != null) { keyManagers = new KeyManager[] {clientCertAuthKeyManager}; }
        }

        // TrustManager for server cert verification:
        final CBLTrustManager trustManager
            = new CBLTrustManager(pinnedServerCert, acceptOnlySelfSignedServerCert, serverCertsListener);

        final SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagers, new TrustManager[] {trustManager}, null);
        builder.sslSocketFactory(sslContext.getSocketFactory(), trustManager);

        // HostnameVerifier:
        if (pinnedServerCert != null || acceptOnlySelfSignedServerCert) {
            // As the certificate will need to be matched with the pinned certificate,
            // accepts any host name specified in the certificate.
            builder.hostnameVerifier((s, sslSession) -> true);
        }
    }

    @Nullable
    private KeyManager getAuthenticator() {
        if (options == null) { return null; }

        final Object opt = options.get(C4Replicator.REPLICATOR_OPTION_AUTHENTICATION);
        if (!(opt instanceof Map)) { return null; }
        final Map<?, ?> auth = (Map<?, ?>) opt;

        if (!C4Replicator.AUTH_TYPE_CLIENT_CERT.equals(auth.get(C4Replicator.REPLICATOR_AUTH_TYPE))) { return null; }

        KeyManager keyManager = null;
        final Object certKey = auth.get(C4Replicator.REPLICATOR_AUTH_CLIENT_CERT_KEY);
        if (certKey instanceof Long) { keyManager = KEY_MANAGERS.getBinding((long) certKey); }
        if (keyManager == null) { Log.i(TAG, "No key manager configured for client certificate authentication"); }

        return keyManager;
    }

    // http://www.ietf.org/rfc/rfc2617.txt
    @Nullable
    private Request authenticate(@NonNull Response resp, @NonNull String user, @NonNull String pwd) {
        Log.d(TAG, "CBLWebSocket.authenticate: %s", resp);

        // If failed 3 times, give up.
        if (responseCount(resp) >= MAX_AUTH_RETRIES) { return null; }

        final List<Challenge> challenges = resp.challenges();
        Log.d(TAG, "CBLWebSocket challenges: %s", challenges);
        if (challenges == null) { return null; }

        for (Challenge challenge: challenges) {
            if (CHALLENGE_BASIC.equals(challenge.scheme())) {
                return resp.request()
                    .newBuilder()
                    .header(HEADER_AUTH, Credentials.basic(user, pwd))
                    .build();
            }
        }

        return null;
    }

    private int responseCount(Response response) {
        int result = 1;
        while ((response = response.priorResponse()) != null) { result++; }
        return result;
    }
}

