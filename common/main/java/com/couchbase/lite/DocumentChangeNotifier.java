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

package com.couchbase.lite;

import androidx.annotation.NonNull;

import com.couchbase.lite.internal.core.C4DocumentObserver;
import com.couchbase.lite.internal.listener.ChangeNotifier;


class DocumentChangeNotifier extends ChangeNotifier<DocumentChange> {
    @NonNull
    private final Database db;
    @NonNull
    private final String docID;
    @NonNull
    private final C4DocumentObserver observer;

    DocumentChangeNotifier(@NonNull final Database db, @NonNull final String docID) {
        this.db = db;
        this.docID = docID;
        this.observer = db.createDocumentObserver(
            this,
            docID,
            (ign1, ign2, ign3, context)
                -> db.scheduleOnPostNotificationExecutor(((DocumentChangeNotifier) context)::postChange, 0)
        );
    }

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        try { observer.close(); }
        finally { super.finalize(); }
    }

    private void postChange() { postChange(new DocumentChange(db, docID)); }
}
