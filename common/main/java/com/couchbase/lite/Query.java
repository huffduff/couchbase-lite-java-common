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

import java.util.concurrent.Executor;


/**
 * A database query built using the fluent interface in {@link QueryBuilder}.
 */
public interface Query {

    //---------------------------------------------
    // APIs
    //---------------------------------------------

    /**
     * Returns a copies of the current parameters.
     */
    @Nullable
    Parameters getParameters();

    /**
     * Set parameters should copy the given parameters. Set a new parameter will
     * also re-execute the query if there is at least one listener listening for
     * changes.
     */
    void setParameters(Parameters parameters);

    /**
     * Executes the query. The returning a result set that enumerates result rows one at a time.
     * You can run the query any number of times, and you can even have multiple ResultSet active at
     * once.
     * <p>
     * The results come from a snapshot of the database taken at the moment the run() method
     * is called, so they will not reflect any changes made to the database afterwards.
     * </p>
     *
     * @return the ResultSet for the query result.
     * @throws CouchbaseLiteException if there is an error when running the query.
     */
    @NonNull
    ResultSet execute() throws CouchbaseLiteException;

    /**
     * Returns a string describing the implementation of the compiled query.
     * This is intended to be read by a developer for purposes of optimizing the query, especially
     * to add database indexes. It's not machine-readable and its format may change.
     * As currently implemented, the result is two or more lines separated by newline characters:
     * * The first line is the SQLite SELECT statement.
     * * The subsequent lines are the output of SQLite's "EXPLAIN QUERY PLAN" command applied to that
     * statement; for help interpreting this, see https://www.sqlite.org/eqp.html . The most
     * important thing to know is that if you see "SCAN TABLE", it means that SQLite is doing a
     * slow linear scan of the documents instead of using an index.
     *
     * @return a string describing the implementation of the compiled query.
     * @throws CouchbaseLiteException if an error occurs
     */
    @NonNull
    String explain() throws CouchbaseLiteException;

    /**
     * Adds a change listener for the changes that occur in the query results.
     * The changes will be delivered on the UI thread for the Android platform and on an arbitrary
     * thread for the Java platform. When developing a Java Desktop application using Swing or JavaFX
     * that needs to update the UI after receiving the changes, make sure to schedule the UI update
     * on the UI thread by using SwingUtilities.invokeLater(Runnable) or Platform.runLater(Runnable)
     * respectively.
     *
     * @param listener The listener to post changes.
     * @return An opaque listener token object for removing the listener.
     */
    @NonNull
    ListenerToken addChangeListener(@NonNull QueryChangeListener listener);

    /**
     * Adds a change listener for the changes that occur in the query results with an executor
     * on which the changes will be posted to the listener. If the executor is not specified,
     * the changes will be delivered on the UI thread for the Android platform and on an
     * arbitrary thread for the Java platform.
     *
     * @param executor The executor object that calls listener
     * @param listener The listener to post changes.
     * @return An opaque listener token object for removing the listener.
     */
    @NonNull
    ListenerToken addChangeListener(@Nullable Executor executor, @NonNull QueryChangeListener listener);

    /**
     * Removes a change listener wih the given listener token.
     *
     * @param token The listener token.
     */
    void removeChangeListener(@NonNull ListenerToken token);
}
