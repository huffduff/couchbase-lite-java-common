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

import android.support.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.ExecutionService;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Fn;


/**
 * Platform test class for Java.
 */
public abstract class PlatformBaseTest implements PlatformTest {
    public static final String PRODUCT = "Java";

    public static final String LEGAL_FILE_NAME_CHARS = "`~@#$%&'()_+{}][=-.,;'ABCDEabcde";

    public static final String DB_EXTENSION = AbstractDatabase.DB_EXTENSION;

    public static final String SCRATCH_DIR = "cbl-scratch";
    public static final String LOG_DIR = "cbl-logs";

    private static final long MAX_LOG_FILE_BYTES = Long.MAX_VALUE; // lots
    private static final int MAX_LOG_FILES = Integer.MAX_VALUE; // lots

    private static final Map<String, Fn.Provider<Boolean>> PLATFORM_DEPENDENT_TESTS;
    static {
        final Map<String, Fn.Provider<Boolean>> m = new HashMap<>();
        m.put("windows", () -> {
            final String os = System.getProperty("os.name");
            return (os != null) && os.toLowerCase().contains("win");
        });
        PLATFORM_DEPENDENT_TESTS = Collections.unmodifiableMap(m);
    }

    private static LogFileConfiguration logConfig;

    static { CouchbaseLite.init(); }

    public static String getScratchDirPath() {
        final File scratchDir = new File(SCRATCH_DIR);
        try { return scratchDir.getCanonicalPath(); }
        catch (IOException e) { throw new IllegalStateException("Could not find scratch directory: " + scratchDir, e); }
    }


    // set up the file logger...
    @Override
    public void setupPlatform() {
        if (logConfig == null) {
            logConfig = new LogFileConfiguration(getScratchDirectoryPath(LOG_DIR))
                .setUsePlaintext(true)
                .setMaxSize(MAX_LOG_FILE_BYTES)
                .setMaxRotateCount(MAX_LOG_FILES);
        }

        final com.couchbase.lite.Log logger = Database.log;
        final FileLogger fileLogger = logger.getFile();
        if (!logConfig.equals(fileLogger.getConfig())) { fileLogger.setConfig(logConfig); }
        fileLogger.setLevel(LogLevel.DEBUG);

        final ConsoleLogger consoleLogger = logger.getConsole();
        consoleLogger.setLevel(LogLevel.DEBUG);
        consoleLogger.setDomains(LogDomain.ALL_DOMAINS);
    }

    @Override
    public final String getScratchDirectoryPath(@NonNull String name) {
        String scratchPath = name;
        try {
            final File scratchDir = new File(getScratchDirPath(), name);
            scratchPath = scratchDir.getCanonicalPath();
            if (scratchDir.mkdirs() || (scratchDir.exists() && scratchDir.isDirectory())) { return scratchPath; }
            throw new IOException("Cannot create directory");
        }
        catch (IOException e) {
            throw new IllegalStateException("Failed creating scratch directory: " + scratchPath, e);
        }
    }

    @Override
    public void reloadStandardErrorMessages() { Log.initLogging(CouchbaseLiteInternal.loadErrorMessages()); }

    @Override
    public final boolean handlePlatformSpecially(String tag) {
        final Fn.Provider<Boolean> test = PLATFORM_DEPENDENT_TESTS.get(tag);
        return (test != null) && test.get();
    }

    @Override
    public void executeAsync(long delayMs, Runnable task) {
        ExecutionService executionService = CouchbaseLiteInternal.getExecutionService();
        executionService.postDelayedOnExecutor(delayMs, executionService.getMainExecutor(), task);
    }
}



