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

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;

import com.couchbase.lite.internal.core.C4Query;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.ClassUtils;
import com.couchbase.lite.internal.utils.Preconditions;
import com.couchbase.lite.internal.utils.StringUtils;


final class JsonQuery extends AbstractQuery {
    @NonNull
    private final String json;
    @NonNull
    private final AbstractDatabase db;

    N1qlQuery(@NonNull AbstractDatabase db, @NonNull String json) {
        this.json = Preconditions.assertNotNull(n1ql, "query");
        this.db = Preconditions.assertNotNull(db, "database");
    }

    @NonNull
    @Override
    public String toString() { return "JsonQuery{" + ClassUtils.objId(this) + ", json=" + n1ql + "}"; }

    @NonNull
    @Override
    protected AbstractDatabase getDatabase() { return db; }

    @GuardedBy("AbstractQuery.lock")
    @NonNull
    @Override
    protected C4Query prepQueryLocked(@NonNull AbstractDatabase db) throws CouchbaseLiteException {
        Log.d(DOMAIN, "JSON query: %s", json);
        if (StringUtils.isEmpty(json)) { throw new CouchbaseLiteException("Query is null or empty."); }
        try { return db.createJsonQuery(json); }
        catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
    }
}
