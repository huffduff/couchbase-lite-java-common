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

import java.io.File;

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.utils.FileUtils;


public final class CouchbaseLite {
    // Utility class
    private CouchbaseLite() { }

    /**
     * Initialize CouchbaseLite library. This method MUST be called before using CouchbaseLite.
     * <p>
     * This method expects the current directory to be writeable
     * and will throw an <code>IllegalStateException</code> if it is not.
     * Use <code>init(boolean, File, File)</code> to specify alternative root and scratch directories.
     *
     * @throws IllegalStateException on initialization failure
     */
    public static void init() { init(false); }

    /**
     * Initialize CouchbaseLite library. This method MUST be called before using CouchbaseLite.
     * <p>
     * This method expects the current directory to be writeable
     * and will throw an <code>IllegalStateException</code> if it is not.
     * Use <code>init(boolean, File, File)</code> to specify alternative root and scratch directories.
     *
     * @param debug true if debugging
     * @throws IllegalStateException on initialization failure
     */
    public static void init(boolean debug) {
        final File curDir = FileUtils.getCurrentDirectory();
        init(debug, curDir, new File(curDir, CouchbaseLiteInternal.SCRATCH_DIR_NAME));
    }

    /**
     * Initialize CouchbaseLite library.
     * This method allows specifying a root directory for CBL files.
     *
     * @param debug      true if debugging
     * @param rootDir    default directory for databases
     * @param scratchDir scratch directory for SQLite
     * @throws IllegalStateException on initialization failure
     */
    public static void init(boolean debug, @NonNull File rootDir, @NonNull File scratchDir) {
        CouchbaseLiteInternal.init(new MValueDelegate(), debug, rootDir, scratchDir);
    }
}
