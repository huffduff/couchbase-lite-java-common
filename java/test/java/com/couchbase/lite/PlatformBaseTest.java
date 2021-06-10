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

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.JavaExecutionService;
import com.couchbase.lite.internal.exec.AbstractExecutionService;
import com.couchbase.lite.internal.exec.ExecutionService;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.FileUtils;
import com.couchbase.lite.internal.utils.Fn;


/**
 * Platform test class for Java.
 */
public abstract class PlatformBaseTest implements PlatformTest {
    public static final String PRODUCT = "Java";

    public static final String LEGAL_FILE_NAME_CHARS = "`~@#$%&'()_+{}][=-.,;'ABCDEabcde";

    public static final String LOG_DIR = "logs";

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

    static { CouchbaseLite.init(true); }


    // set up the file logger...
    @Override
    public void setupPlatform() {
        if (logConfig == null) {
            final String logDirPath;
            try {
                logDirPath = FileUtils.verifyDir(new File(new File("").getCanonicalFile(), LOG_DIR))
                    .getCanonicalPath();
            }
            catch (IOException e) { throw new IllegalStateException("Could not find log directory", e); }

            logConfig = new LogFileConfiguration(logDirPath)
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
    public AbstractExecutionService getExecutionService(ThreadPoolExecutor executor) {
        return new JavaExecutionService(executor);
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
        executionService.postDelayedOnExecutor(delayMs, executionService.getDefaultExecutor(), task);
    }
}
