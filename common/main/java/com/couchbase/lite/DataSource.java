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
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

import com.couchbase.lite.internal.utils.Preconditions;


/**
 * A query data source, used for specifying the source of data for a query.
 */
public class DataSource {

    /**
     * Database as a data source for query.
     */
    public static class As extends DataSource {
        //---------------------------------------------
        // Constructors
        //---------------------------------------------
        As(@NonNull Database source) { super(source); }

        //---------------------------------------------
        // API - public methods
        //---------------------------------------------

        /**
         * Set an alias to the database data source.
         *
         * @param alias the alias to set.
         * @return the data source object with the given alias set.
         */
        @NonNull
        public DataSource as(@NonNull String alias) {
            Preconditions.assertNotNull(alias, "alias");
            super.alias = alias;
            return this;
        }
    }

    /**
     * Create a database as a data source.
     *
     * @param database the database used as a source of data for query.
     * @return {@code DataSource.Database} object.
     */
    @NonNull
    public static As database(@NonNull Database database) {
        Preconditions.assertNotNull(database, "database");
        return new As(database);
    }


    //---------------------------------------------
    // Data members
    //---------------------------------------------

    @NonNull
    private final Database source;
    @Nullable
    protected String alias;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    private DataSource(@NonNull Database source) { this.source = source; }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    @NonNull
    Object getSource() { return this.source; }

    @NonNull
    Map<String, Object> asJSON() {
        final Map<String, Object> json = new HashMap<>();
        json.put("AS", (alias != null) ? alias : source.getName());
        return json;
    }
}
