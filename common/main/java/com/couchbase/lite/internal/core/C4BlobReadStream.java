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
import androidx.annotation.Nullable;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;


/**
 * An open stream for reading data from a blob.
 */
public class C4BlobReadStream extends C4NativePeer {

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    C4BlobReadStream(long peer) { super(peer); }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    /**
     * Reads from an open stream.
     *
     * @param maxBytesToRead The maximum number of bytes to read to the buffer
     */
    public int read(byte[] b, int offset, long maxBytesToRead) throws LiteCoreException {
        return read(getPeer(), b, offset, maxBytesToRead);
    }

    /**
     * Returns the exact length in bytes of the stream.
     */
    public long getLength() throws LiteCoreException { return getLength(getPeer()); }

    /**
     * Moves to a random location in the stream; the next c4stream_read call will read from that
     * location.
     */
    public void seek(long position) throws LiteCoreException { seek(getPeer(), position); }

    /**
     * Closes a read-stream.
     */
    @CallSuper
    @Override
    public void close() { closePeer(null); }

    //-------------------------------------------------------------------------
    // protected methods
    //-------------------------------------------------------------------------
    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try { closePeer(LogDomain.DATABASE); }
        finally { super.finalize(); }
    }

    //-------------------------------------------------------------------------
    // private methods
    //-------------------------------------------------------------------------

    private void closePeer(@Nullable LogDomain domain) { releasePeer(domain, C4BlobReadStream::close); }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------
    private static native int read(long peer, byte[] b, int offset, long maxBytesToRead) throws LiteCoreException;

    private static native long getLength(long peer) throws LiteCoreException;

    private static native void seek(long peer, long position) throws LiteCoreException;

    private static native void close(long peer);
}
