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

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.impl.NativeC4QueryEnumerator;
import com.couchbase.lite.internal.fleece.FLArrayIterator;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * C4QueryEnumerator
 * A query result enumerator
 * Created by c4db_query. Must be freed with c4queryenum_free.
 * The fields of this struct represent the current matched index row.
 * They are valid until the next call to c4queryenum_next or c4queryenum_free.
 */
public class C4QueryEnumerator extends C4NativePeer {
    public interface NativeImpl {
        boolean nNext(long peer) throws LiteCoreException;
        void nFree(long peer);
        long nGetColumns(long peer);
        long nGetMissingColumns(long peer);
        long nGetFullTextMatchCount(long peer);
        long nGetFullTextMatch(long peer, int idx);
    }

    @NonNull
    private static final NativeImpl NATIVE_IMPL = new NativeC4QueryEnumerator();

    //-------------------------------------------------------------------------
    // Static factory method
    //-------------------------------------------------------------------------

    // Use a peer factory here, as some sort of assurance that we are the only ones with the peer handle.
    @NonNull
    static C4QueryEnumerator create(long peer) {
        return new C4QueryEnumerator(NATIVE_IMPL, Preconditions.assertNotZero(peer, "query enumerator peer"));
    }


    //-------------------------------------------------------------------------
    // Fields
    //-------------------------------------------------------------------------

    @NonNull
    private final NativeImpl impl;

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    @VisibleForTesting
    C4QueryEnumerator(@NonNull NativeImpl impl, long peer) {
        super(peer);
        this.impl = impl;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    public boolean next() throws LiteCoreException { return impl.nNext(getPeer()); }

    /**
     * FLArrayIterator columns
     * The columns of this result, in the same order as in the query's `WHAT` clause.
     * NOTE: FLArrayIterator is member variable of C4QueryEnumerator. Not necessary to release.
     */
    @NonNull
    public FLArrayIterator getColumns() {
        return FLArrayIterator.getUnmanagedArrayIterator(impl.nGetColumns(getPeer()));
    }

    /**
     * Returns a bitmap in which a 1 bit represents a column whose value is MISSING.
     * This is how you tell a missing property value from a value that is JSON 'null',
     * since the value in the `columns` array will be a Fleece `null` either way.
     */
    public long getMissingColumns() { return impl.nGetMissingColumns(getPeer()); }

    @CallSuper
    @Override
    public void close() { closePeer(null); }

    //-------------------------------------------------------------------------
    // protected methods
    //-------------------------------------------------------------------------

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try { closePeer(LogDomain.QUERY); }
        finally { super.finalize(); }
    }

    //-------------------------------------------------------------------------
    // package protected methods
    //-------------------------------------------------------------------------

    /**
     * Return the number of full-text matches (i.e. the number of items in `getFullTextMatches`)
     */
    @VisibleForTesting
    long getFullTextMatchCount() { return impl.nGetFullTextMatchCount(getPeer()); }

    /**
     * Return an array of details of each full-text match
     */
    @VisibleForTesting
    @NonNull
    C4FullTextMatch getFullTextMatches(int idx) { return new C4FullTextMatch(impl.nGetFullTextMatch(getPeer(), idx)); }

    //-------------------------------------------------------------------------
    // Private methods
    //-------------------------------------------------------------------------

    private void closePeer(@Nullable LogDomain domain) { releasePeer(domain, impl::nFree); }
}
