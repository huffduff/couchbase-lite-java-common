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
package com.couchbase.lite;

import androidx.annotation.NonNull;

import com.couchbase.lite.internal.utils.Preconditions;


public final class QueryBuilder {
    private QueryBuilder() { }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Create a SELECT statement instance that you can use further
     * (e.g. calling the from() function) to construct the complete query statement.
     *
     * @param results The array of the SelectResult object for specifying the returned values.
     * @return A Select object.
     */
    @NonNull
    public static Select select(@NonNull SelectResult... results) {
        Preconditions.assertNotNull(results, "results");
        return new Select(false, results);
    }

    /**
     * Create a SELECT DISTINCT statement instance that you can use further
     * (e.g. calling the from() function) to construct the complete query statement.
     *
     * @param results The array of the SelectResult object for specifying the returned values.
     * @return A Select distinct object.
     */
    @NonNull
    public static Select selectDistinct(@NonNull SelectResult... results) {
        Preconditions.assertNotNull(results, "results");
        return new Select(true, results);
    }

    /**
     * Create Query from a N1QL string
     *
     * @param query A valid N1QL query.
     * @return database The database against which the query will be run.
     */
    @NonNull
    public static Query createQuery(@NonNull String query, @NonNull Database database) {
        return database.createQuery(query);
    }
}
