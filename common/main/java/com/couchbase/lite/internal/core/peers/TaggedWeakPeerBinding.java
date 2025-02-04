package com.couchbase.lite.internal.core.peers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.couchbase.lite.internal.utils.MathUtils;


/**
 * This class provides a way for native objects to reference their
 * Java peers.  The <code>reserveKey()</code> method creates a unique token
 * that the native code can redeem, using <code>getObjFromContext</code> for
 * the Java object that is its peer.
 * Note that the token is a 31 bit integer (a positive int) so that it is
 * relatively immune to sign extension.
 * The internal map holds only a weak reference to the Java object.
 * If nobody in java-land cares about the peer-pair anymore, calls to
 * <code>getBinding</code> will return null.
 * While it would be possible to accomplish something similar, perhaps by
 * passing the actual java reference to the native object, such an implementation
 * would require the native code to manage LocalRefs.... with the distinct
 * possibility of running out.
 * <p>
 * !!! There should be a nanny thread cleaning out all the ref -> null
 *
 * @param <T> The type of the Java peer.
 */
public class TaggedWeakPeerBinding<T> extends WeakPeerBinding<T> {

    /**
     * Reserve a token.
     * Sometimes the object to be put into the map needs to know
     * its own token.  Pre-reserving it makes it possible to make it final.
     *
     * @return a unique value 0 <= key < Integer.MAX_VALUE.
     */
    public synchronized long reserveKey() {
        long key;

        do { key = MathUtils.RANDOM.get().nextInt(Integer.MAX_VALUE); }
        while (exists(key));
        super.set(key, null);

        return key;
    }

    /**
     * Bind an object to a token.
     *
     * @param key a previously reserved token
     * @param obj the object to be bound to the token.
     */
    @Override
    public synchronized void bind(long key, @NonNull T obj) {
        if (!exists(key)) { throw new IllegalStateException("attempt to use un-reserved key"); }
        super.bind(key, obj);
    }

    /**
     * Get the object bound to the passed token.
     * Returns null if no object is bound to the key.
     * For legacy reasons, core holds these "contexts" as (void *), so they are longs.
     *
     * @param key a token created by <code>reserveKey()</code>
     * @return the bound object, or null if none exists.
     */
    @Nullable
    @Override
    public synchronized T getBinding(long key) {
        if ((key < 0) || (key > Integer.MAX_VALUE)) {
            throw new IllegalArgumentException("Key out of bounds: " + key);
        }

        return super.getBinding(key);
    }
}
