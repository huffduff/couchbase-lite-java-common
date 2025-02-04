//
// Copyright (c) 2020, 2019 Couchbase, Inc All rights reserved.
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

import java.util.EnumMap;

import com.couchbase.lite.internal.support.Log;


/**
 * Log level.
 */
public enum LogLevel {

    /**
     * Debugging information.
     */
    DEBUG,

    /**
     * Low level logging.
     */
    VERBOSE,

    /**
     * Essential state info and client errors that are recoverable
     */
    INFO,

    /**
     * Internal errors that are recoverable; client errors that may not be recoverable
     */
    WARNING,

    /**
     * Internal errors that are unrecoverable
     */
    ERROR,

    /**
     * Disabling log messages of a given log domain.
     */
    NONE;

    private static final EnumMap<LogLevel, String> LEVELS = new EnumMap<>(LogLevel.class);
    static {
        LEVELS.put(DEBUG, "D");
        LEVELS.put(VERBOSE, "V");
        LEVELS.put(INFO, "I");
        LEVELS.put(WARNING, "W");
        LEVELS.put(ERROR, "E");
        LEVELS.put(NONE, "");
    }
    @NonNull
    @Override
    public String toString() {
        final String s = LEVELS.get(this);
        if (s != null) { return s; }
        Log.d(LogDomain.DATABASE, "Unrecognized log level: %s", this);
        return "?";
    }
}
