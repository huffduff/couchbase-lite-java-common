//
// LiveQuery.java
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
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

import java.util.EnumSet;


/**
 * Log domain
 */
public enum LogDomain {
    /**
     * @deprecated redundant and confusing
     */
    @Deprecated ALL,
    DATABASE, QUERY, REPLICATOR, NETWORK;

    public static final EnumSet<LogDomain> ALL_DOMAINS = EnumSet.of(DATABASE, QUERY, REPLICATOR, NETWORK);
}
