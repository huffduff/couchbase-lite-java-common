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


/**
 * Full-text Index Item.
 */
public final class FullTextIndexItem {
    /**
     * Creates a full-text search index item with the given property.
     *
     * @param property A property used to perform the match operation against with.
     * @return The full-text search index item.
     */
    @NonNull
    public static FullTextIndexItem property(@NonNull String property) {
        Preconditions.assertNotNull(property, "property");
        return new FullTextIndexItem(Expression.property(property));
    }

    @NonNull
    final Expression expression;

    private FullTextIndexItem(@NonNull Expression expression) { this.expression = expression; }
}
