package com.couchbase.lite.internal.core;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import javax.annotation.Nullable;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.QueryChange;
import com.couchbase.lite.internal.core.peers.TaggedWeakPeerBinding;
import com.couchbase.lite.internal.listener.ChangeListenerToken;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.ClassUtils;


public class C4QueryObserver extends C4NativePeer {
    @FunctionalInterface
    public interface QueryChangeCallback {
        void onQueryChanged(
            @NonNull ChangeListenerToken<QueryChange> listener,
            @Nullable C4QueryEnumerator results,
            @Nullable LiteCoreException err);
    }

    public interface NativeImpl {
        long nCreate(long token, long c4Query);
        void nSetEnabled(long peer, boolean enabled);
        void nFree(long peer);
        long nGetEnumerator(long peer, boolean forget) throws LiteCoreException;
    }

    @NonNull
    @VisibleForTesting
    static final TaggedWeakPeerBinding<C4QueryObserver> QUERY_OBSERVER_CONTEXT = new TaggedWeakPeerBinding<>();

    // Not final for testing.
    @NonNull
    @VisibleForTesting
    static volatile NativeImpl nativeImpl = new NativeC4QueryObserver();

    //-------------------------------------------------------------------------
    // Static Factory Methods
    //-------------------------------------------------------------------------

    @NonNull
    public static C4QueryObserver create(
        @NonNull C4Query query,
        @NonNull ChangeListenerToken<QueryChange> listener,
        @NonNull QueryChangeCallback callback) {
        final long token = QUERY_OBSERVER_CONTEXT.reserveKey();
        final C4QueryObserver observer = new C4QueryObserver(token, nativeImpl, query, listener, callback);
        QUERY_OBSERVER_CONTEXT.bind(token, observer);
        return observer;
    }

    //-------------------------------------------------------------------------
    // Native callback method
    //-------------------------------------------------------------------------

    // This method is called by reflection.  Don't change its signature.
    static void onQueryChanged(long token) {
        final C4QueryObserver observer = QUERY_OBSERVER_CONTEXT.getBinding(token);
        if (observer == null) {
            Log.w(LogDomain.QUERY, "No observer for token: " + token);
            return;
        }
        observer.queryChanged();
    }


    private final long token;
    @NonNull
    private final C4QueryObserver.NativeImpl impl;
    @NonNull
    private final ChangeListenerToken<QueryChange> listener;
    @NonNull
    private final QueryChangeCallback callback;

    @VisibleForTesting
    C4QueryObserver(
        long token,
        @NonNull NativeImpl impl,
        @NonNull C4Query query,
        @NonNull ChangeListenerToken<QueryChange> listener,
        @NonNull QueryChangeCallback callback) {
        this.token = token;
        this.impl = impl;
        this.listener = listener;
        this.callback = callback;
        setPeer(impl.nCreate(token, query.getPeer()));
    }

    @CallSuper
    @Override
    public void close() {
        QUERY_OBSERVER_CONTEXT.unbind(token);
        closePeer(null);
    }

    @NonNull
    @Override
    public String toString() {
        return "C4QueryObserver{" + ClassUtils.objId(this) + "/" + super.toString() + ": " + token + "}";
    }

    public void setEnabled(boolean enabled) { impl.nSetEnabled(getPeer(), enabled); }

    @Nullable
    public C4QueryEnumerator getEnumerator(boolean forget) throws LiteCoreException {
        return new C4QueryEnumerator(impl.nGetEnumerator(getPeer(), forget));
    }

    @Override
    protected void finalize() throws Throwable {
        try { closePeer(LogDomain.LISTENER); }
        finally { super.finalize(); }
    }

    void queryChanged() {
        C4QueryEnumerator results = null;
        LiteCoreException err = null;
        try { results = new C4QueryEnumerator(impl.nGetEnumerator(getPeer(), false)); }
        catch (LiteCoreException e) { err = e; }
        callback.onQueryChanged(listener, results, err);
    }

    private void closePeer(LogDomain domain) { releasePeer(domain, impl::nFree); }
}
