//
// Copyright (c) 2021 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.concurrent.Executor;

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.utils.Preconditions;


final class DocumentReplicationListenerToken implements ListenerToken {
    @NonNull
    private final DocumentReplicationListener listener;
    @Nullable
    private final Executor executor;

    DocumentReplicationListenerToken(@Nullable Executor executor, @NonNull DocumentReplicationListener listener) {
        Preconditions.assertNotNull(listener, "listener");
        this.executor = executor;
        this.listener = listener;
    }

    void notify(@NonNull final DocumentReplication update) {
        getExecutor().execute(() -> listener.replication(update));
    }

    @NonNull
    Executor getExecutor() {
        return (executor != null) ? executor : CouchbaseLiteInternal.getExecutionService().getDefaultExecutor();
    }
}
