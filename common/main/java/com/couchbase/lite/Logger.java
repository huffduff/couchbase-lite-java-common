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


/**
 * The logging interface for Couchbase Lite.  An application that wishes
 * to route log messages to an arbitrary endpoint can do so by
 * installing an implemention of this interface with {@link Log#setCustom(Logger)}.
 */
// !!! There is, currently, no way to tell when the log level changes
// for a custom logger.  Setting a more verbose log level will not
// have any effect until the logger is polled for its level.
public interface Logger {
    /**
     * Gets the level that will be logged via this logger.
     *
     * @return The maximum level to log
     */
    @NonNull
    LogLevel getLevel();

    /**
     * Performs the actual logging logic
     *
     * @param level   The level of the message to log
     * @param domain  The domain of the message to log
     * @param message The content of the message to log
     */
    void log(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message);
}
