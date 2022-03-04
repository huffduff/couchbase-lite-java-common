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

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.net.NoRouteToHostException;
import java.net.PortUnreachableException;
import java.net.SocketException;
import java.net.URI;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.TrustManager;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.core.C4Replicator;
import com.couchbase.lite.internal.core.peers.TaggedWeakPeerBinding;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.sockets.SocketFromCore;
import com.couchbase.lite.internal.sockets.SocketFromRemote;
import com.couchbase.lite.internal.sockets.SocketToCore;
import com.couchbase.lite.internal.sockets.SocketToRemote;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.ClassUtils;
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
public abstract class AbstractCBLWebSocket implements SocketFromCore, SocketFromRemote, AutoCloseable {
    //-------------------------------------------------------------------------
    // Constants
    //-------------------------------------------------------------------------

    private static final LogDomain LOG_DOMAIN = LogDomain.NETWORK;

    private static final int MAX_AUTH_RETRIES = 3;
    public static final int DEFAULT_HEARTBEAT_SEC = 300;

    private static final String CHALLENGE_BASIC = "Basic";
    private static final String HEADER_AUTH = "Authorization";
    public static final String HEADER_COOKIES = "Cookies"; // client customized cookies

    private enum State {INIT, CONNECTING, OPEN, CLOSE_REQUESTED, CLOSING, CLOSED, FAILED}

    //-------------------------------------------------------------------------
    // Types
    //-------------------------------------------------------------------------

    // Using the C4NativePeer lock to protect cookieStore may seem like overkill.  We have to use it anyway,
    // for the (very necessary) call to assertState.  Might as well use it everywhere...
    private class WebSocketCookieJar implements CookieJar {
        @Override
        public void saveFromResponse(@NonNull HttpUrl httpUrl, @NonNull List<Cookie> cookies) {
            synchronized (getLock()) {
                for (Cookie cookie: cookies) { cookieStore.setCookie(httpUrl.uri(), cookie.toString()); }
            }
        }

        /**
         * This function sends all 3 types of cookies: Sync Gateway authentication cookie, client-specified cookies
         * and set cookies to remote OkHttp. These cookies are saved in 2 places in CBL JAK, replicator config option
         * and CookieStore.
         * <p>
         * 1. Replicator config options:
         * - Replicator options is a key-value pairs map with many replicator options, including cookies option.
         * Cookies option has the key “cookies,” and the value is a string representation of all the cookies provided
         * by Sync Gateway authentication and included in client-specified headers.
         * Users create and save these two types of cookies by creating a ReplicatorConfiguration,
         * setting the SessionAuthenticator and headers map and then running the replicator with this config.
         * After all existing cookies from SessionAuthenticator and headers are added into cookies option,
         * FLEncoder encodes them into Fleece and CBL gives this Fleece options to LiteCore until needed.
         * - Whenever there's a request, LiteCore calls back to CBL, CBL decodes Fleece options back to a map, from
         * which loadForRequest gets all Sync Gateway and client-specified cookies.
         * <p>
         * 2. Database Cookie Store
         * - After receiving an HTTP request, a server can send one or more Set-Cookie headers with the response.
         * OkHttp saves the cookies from the response in the CookieJar. The ReplicatorCookieStore then passes these
         * cookies to C4Database, which then sends them to LiteCore. LiteCore saves these cookies in the database's
         * cookie store until needed.
         * - Whenever there's a request, we load these cookies from the CookieJar by calling cookieStore.getCookies.
         * The actual implementation of this method is LiteCore function c4db_getCookie, which gets all the cookies in
         * the database’s cookie store.
         * <p>
         * This function combines the cookies stored in the options map and Set cookies stored in CookieStore into a
         * list and gives it to remote OkHttp to create a "Cookie" header.
         * <p>
         * Full documentation for CBL JAK Cookie Architecture can be found on the mobile wiki page:
         * https://hub.internal.couchbase.com/confluence/display/cbeng/Mobile+Team
         *
         * @param url
         * @return a list of all available cookies
         */
        @NonNull
        @Override
        public List<Cookie> loadForRequest(@NonNull HttpUrl url) {
            final List<Cookie> cookies = new ArrayList<>();
            synchronized (getLock()) {
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
    // Static fields
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

    //-------------------------------------------------------------------------
    // Static methods
    //-------------------------------------------------------------------------

    public static long addKeyManager(@NonNull KeyManager keyManager) {
        final long token = KEY_MANAGERS.reserveKey();
        KEY_MANAGERS.bind(token, keyManager);
        return token;
    }


    //-------------------------------------------------------------------------
    // Fields
    //-------------------------------------------------------------------------

    // assert these are thread-safe
    @NonNull
    private final URI uri;
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
    private final SocketToRemote toRemote;
    @NonNull
    protected final SocketToCore toCore;

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    protected AbstractCBLWebSocket(
        @NonNull SocketToRemote toRemote,
        @NonNull SocketToCore toCore,
        @NonNull URI uri,
        @Nullable byte[] opts,
        @NonNull CBLCookieStore cookieStore,
        @NonNull Fn.Consumer<List<Certificate>> serverCertsListener) {
        this.toCore = toCore;
        this.toRemote = toRemote;
        this.uri = uri;
        this.options = (opts == null) ? null : Collections.unmodifiableMap(FLValue.fromData(opts).asDict());
        this.cookieStore = cookieStore;
        this.serverCertsListener = serverCertsListener;
    }

    @Override
    @NonNull
    public String toString() {
        return "CBLWebSocket@" + ClassUtils.objId(this)
            + "{" + toCore + " <=> " + toRemote + "(" + uri + ")}";
    }

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
    // Implementation of AutoClosable
    //-------------------------------------------------------------------------

    @Override
    public void close() {
        Log.d(LOG_DOMAIN, "%s.close: %s", this, uri);
        synchronized (getLock()) {
            if (state.setState(State.CLOSE_REQUESTED)) {
                toCore.requestCoreClose(C4Constants.WebSocketError.GOING_AWAY, "Closed by client");
                return;
            }
            if (state.setState(State.CLOSING)) {
                closeWebSocket(C4Constants.WebSocketError.GOING_AWAY, "Closed by client");
                return;
            }
            state.setState(State.CLOSED);
        }
    }

    //-------------------------------------------------------------------------
    // Implementation of CoreSocketListener (Core to Remote)
    //-------------------------------------------------------------------------

    // Core needs a connection to the remote
    @Override
    public final void coreRequestedOpen() {
        Log.d(LOG_DOMAIN, "%s.coreRequestedOpen", this);
        synchronized (getLock()) {
            if (!state.setState(State.CONNECTING)) { return; }
            toRemote.openRemote(newRequest());
        }
    }

    // Core wants to transfer data to the remote
    @Override
    public final void coreWrites(@NonNull byte[] data) {
        final int dLen = data.length;
        Log.d(LOG_DOMAIN, "%s.coreWrites(%d)", this, dLen);
        synchronized (getLock()) {
            if (!state.assertState(State.OPEN)) { return; }

            if (!toRemote.sendToRemote(data)) {
                Log.i(LOG_DOMAIN, "CBLWebSocket failed to send data of length = " + dLen);
                return;
            }

            toCore.ackWriteToCore(data.length);
        }
    }

    // Core confirms the reception of n bytes: ignored
    @Override
    public void coreAckReceive(long n) { Log.d(LOG_DOMAIN, "%s.coreAckReceive: %d", this, n); }

    // Core wants to break the connection
    @Override
    public final void coreRequestedClose(int code, String message) {
        Log.d(LOG_DOMAIN, "%s.coreRequestedClose(%d)", this, code);
        synchronized (getLock()) {
            if (!state.setState(State.CLOSING)) { return; }
            closeWebSocket(code, message);
        }
    }

    // Used in byte stream mode: irrelevant here
    @Override
    public final void coreClosed() { Log.d(LOG_DOMAIN, "%s.coreClosed", this); }


    //-------------------------------------------------------------------------
    // Implementation of RemoteSocketListener (Remote to Core)
    //-------------------------------------------------------------------------

    @NonNull
    @Override
    public Object getLock() { return toCore.getLock(); }

    @Override
    public void setupRemoteSocketFactory(@NonNull OkHttpClient.Builder builder) throws GeneralSecurityException {
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
        builder.cookieJar(new AbstractCBLWebSocket.WebSocketCookieJar());

        // Setup SSLFactory and trusted certificate (pinned certificate)
        setupSSLSocketFactory(builder);
    }

    @Override
    public void remoteOpened(@NonNull Response resp) {
        Log.d(LOG_DOMAIN, "%s.remoteOpened: %s", this, resp);
        synchronized (getLock()) {
            if (!state.setState(State.OPEN)) { return; }
            receivedHTTPResponse(resp);
            toCore.ackOpenToCore();
        }
    }

    @Override
    public void remoteWrites(@NonNull byte[] data) {
        Log.d(LOG_DOMAIN, "%s.remoteWrites(%d)", this, data.length);
        synchronized (getLock()) {
            if (!state.assertState(State.OPEN)) { return; }
            toCore.sendToCore(data);
        }
    }

    @Override
    public void remoteRequestedClose(int code, @NonNull String reason) {
        Log.d(LOG_DOMAIN, "%s.remoteRequestedClose: %s", this, reason);
        synchronized (getLock()) {
            if (!state.setState(State.CLOSE_REQUESTED)) { return; }
            toCore.requestCoreClose(code, reason);
        }
    }

    @Override
    public void remoteClosed(int code, @NonNull String reason) {
        Log.d(LOG_DOMAIN, "%s.remoteClosed(%d): %s", this, code, reason);
        synchronized (getLock()) {
            if (!state.setState(State.CLOSED)) { return; }
            closeWithCode(code, reason);
        }
    }

    @Override
    public void remoteFailed(@NonNull Throwable err, @Nullable Response resp) {
        Log.d(LOG_DOMAIN, "%s.remoteFailed: %s", err, this, resp);
        synchronized (getLock()) {
            state.setState(State.FAILED);

            if (resp == null) {
                closeWithError(err);
                return;
            }

            closeWithCode(resp.code(), resp.message());
        }
    }

    //-------------------------------------------------------------------------
    // private methods
    //-------------------------------------------------------------------------

    @GuardedBy("getPeerLock()")
    private void receivedHTTPResponse(@NonNull Response resp) {
        Log.d(LOG_DOMAIN, "CBLWebSocket received HTTP response %s", resp);

        // Post the response headers to LiteCore:
        final Headers hs = resp.headers();
        if ((hs == null) || (hs.size() <= 0)) { return; }

        byte[] headersFleece = null;
        final Map<String, Object> headers = new HashMap<>();
        for (int i = 0; i < hs.size(); i++) { headers.put(hs.name(i), hs.value(i)); }

        try (FLEncoder enc = FLEncoder.getManagedEncoder()) {
            enc.write(headers);
            headersFleece = enc.finish();
        }
        catch (LiteCoreException e) {
            Log.w(LOG_DOMAIN, "CBLWebSocket failed to encode response headers", e);
            Log.d(LOG_DOMAIN, StringUtils.toString(headers));
        }

        toCore.ackHttpToCore(resp.code(), headersFleece);
    }

    // Close the connection to the remote
    @GuardedBy("getPeerLock()")
    private void closeWebSocket(int code, String message) {
        // We've told Core to leave the connection to us, so it might pass us the HTTP status
        // If it does, we need to convert it to a WS status for the other side.
        if ((code > C4Constants.HttpError.STATUS_MIN) && (code < C4Constants.HttpError.STATUS_MAX)) {
            code = C4Constants.WebSocketError.POLICY_ERROR;
        }

        if (!toRemote.closeRemote(code, message)) {
            Log.i(LOG_DOMAIN, "CBLWebSocket failed to initiate a graceful shutdown of this web socket.");
        }
    }

    @GuardedBy("getPeerLock()")
    private void closeWithCode(int code, String reason) {
        Log.v(LOG_DOMAIN, "WebSocket CLOSED with code: %d(%s)", code, reason);

        // success
        if (code == C4Constants.WebSocketError.NORMAL) {
            toCore.closeCore(C4Constants.ErrorDomain.LITE_CORE, C4Constants.LiteCoreError.SUCCESS, null);
            return;
        }

        toCore.closeCore(C4Constants.ErrorDomain.WEB_SOCKET, code, reason);
    }

    @GuardedBy("getPeerLock()")
    private void closeWithError(@Nullable Throwable error) {
        Log.i(LOG_DOMAIN, "WebSocket CLOSED with error", error);

        // this probably doesn't happen
        if (error == null) {
            toCore.closeCore(C4Constants.ErrorDomain.WEB_SOCKET, 0, null);
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

        toCore.closeCore(domain, code, error.toString());
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

        return (route, resp) -> authenticate(resp, (String) username, (String) password);
    }

    private void setupSSLSocketFactory(@NonNull OkHttpClient.Builder builder) throws GeneralSecurityException {
        X509Certificate pinnedServerCert = null;
        boolean acceptOnlySelfSignedServerCert = false;
        KeyManager[] keyManagers = null;
        if (options != null) {
            // Pinned Certificate:
            Object opt = options.get(C4Replicator.REPLICATOR_OPTION_PINNED_SERVER_CERT);
            if (opt instanceof byte[]) {
                try {
                    pinnedServerCert = (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream((byte[]) opt));
                }
                catch (CertificateException e) {
                    Log.w(LOG_DOMAIN, "Can't parse pinned certificate.  Ignored", e);
                }
            }

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
        if (keyManager == null) {
            Log.i(LOG_DOMAIN, "CBLWebSocket: No key manager configured for client certificate authentication");
        }

        return keyManager;
    }

    // http://www.ietf.org/rfc/rfc2617.txt
    @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE")
    @Nullable
    private Request authenticate(@NonNull Response resp, @NonNull String user, @NonNull String pwd) {
        Log.d(LOG_DOMAIN, "%s.authenticate: %s", this, resp);

        // If failed 3 times, give up.
        if (responseCount(resp) >= MAX_AUTH_RETRIES) { return null; }

        final List<Challenge> challenges = resp.challenges();
        Log.d(LOG_DOMAIN, "CBLWebSocket challenges: %s", challenges);
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

    private int responseCount(Response resp) {
        int result = 1;
        while ((resp = resp.priorResponse()) != null) { result++; }
        return result;
    }
}

