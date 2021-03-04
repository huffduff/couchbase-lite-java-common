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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.ClassUtils;


@SuppressWarnings("PMD.AbstractClassWithoutAbstractMethod")
public abstract class InternalReplicator implements AutoCloseable {
    private final Object lock = new Object();

    @Nullable
    private C4Replicator c4Replicator;

    @Override
    public void close() {
        final C4Replicator c4Repl;
        synchronized (getLock()) {
            c4Repl = c4Replicator;
            c4Replicator = null;
        }
        closeC4Replicator(c4Repl);
    }

    protected void setC4Replicator(@NonNull C4Replicator c4Repl) {
        Log.d(
            LogDomain.REPLICATOR,
            "Setting c4 replicator " + ClassUtils.objId(c4Repl) + " for replicator " + ClassUtils.objId(this));
        synchronized (getLock()) { c4Replicator = c4Repl; }
    }

    @Nullable
    protected C4Replicator getC4Replicator() {
        synchronized (getLock()) { return c4Replicator; }
    }

    protected Object getLock() { return lock; }

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try {
            // ??? this call seizes the lock
            if (closeC4Replicator(getC4Replicator())) {
                Log.i(LogDomain.REPLICATOR, "Replicator was not closed " + ClassUtils.objId(this));
            }
        }
        finally { super.finalize(); }
    }

    private boolean closeC4Replicator(@Nullable C4Replicator c4Repl) {
        if (c4Repl == null) { return false; }

        c4Repl.close();

        return true;
    }
}
