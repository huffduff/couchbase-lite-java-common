//
// Copyright (c) 2020, 2018 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal.listener;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;

import com.couchbase.lite.ChangeListener;
import com.couchbase.lite.ListenerToken;
import com.couchbase.lite.internal.utils.Preconditions;


public  class ChangeNotifier<T> {
    private final Object lock = new Object();
    private final Set<ChangeListenerToken<T>> listenerTokens;

    public ChangeNotifier() { listenerTokens = new HashSet<>(); }

    @NonNull
    public ChangeListenerToken<T> addChangeListener(@Nullable Executor executor, @NonNull ChangeListener<T> listener) {
        Preconditions.assertNotNull(listener, "listener");
        synchronized (lock) {
            final ChangeListenerToken<T> token = new ChangeListenerToken<>(executor, listener);
            listenerTokens.add(token);
            return token;
        }
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    public int removeChangeListener(@NonNull ListenerToken token) {
        Preconditions.assertNotNull(token, "token");
        synchronized (lock) {
            listenerTokens.remove(token);
            return listenerTokens.size();
        }
    }

    public void postChange(@NonNull T change) {
        Preconditions.assertNotNull(change, "change");
        synchronized (lock) {
            for (ChangeListenerToken<T> token: listenerTokens) { token.postChange(change); }
        }
    }
}
