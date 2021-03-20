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

import android.support.annotation.GuardedBy;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import org.json.JSONException;

import com.couchbase.lite.internal.core.C4Query;
import com.couchbase.lite.internal.core.C4QueryEnumerator;
import com.couchbase.lite.internal.core.C4QueryOptions;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.ClassUtils;
import com.couchbase.lite.internal.utils.JSONUtils;
import com.couchbase.lite.internal.utils.Preconditions;


@SuppressWarnings("PMD.GodClass")
abstract class AbstractQuery implements Query {
    //---------------------------------------------
    // constants
    //---------------------------------------------
    private static final LogDomain DOMAIN = LogDomain.QUERY;

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private final Object lock = new Object();

    @GuardedBy("lock")
    private C4Query c4query;

    @GuardedBy("lock")
    private LiveQuery liveQuery;

    // NOTE:
    // https://sqlite.org/lang_select.html

    // SELECT
    private Select select;
    // FROM
    private DataSource from; // FROM table-or-subquery
    private Joins joins;     // FROM join-clause
    // WHERE
    private Expression where; // WHERE expr
    // GROUP BY
    private GroupBy groupBy; // GROUP BY expr(s)
    private Having having; // Having expr
    // ORDER BY
    private OrderBy orderBy; // ORDER BY ordering-term(s)
    // LIMIT
    private Limit limit; // LIMIT expr

    // PARAMETERS
    private Parameters parameters;

    // column names
    private Map<String, Integer> columnNames;

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Returns a copies of the current parameters.
     */
    @Override
    public Parameters getParameters() { return parameters; }

    /**
     * Set parameters should copy the given parameters. Set a new parameter will
     * also re-execute the query if there is at least one listener listening for
     * changes.
     */
    @Override
    public void setParameters(Parameters parameters) {
        final LiveQuery newQuery;
        synchronized (lock) {
            this.parameters = (parameters == null) ? null : parameters.readonlyCopy();
            newQuery = liveQuery;
        }

        // https://github.com/couchbase/couchbase-lite-android/issues/1727
        // Shouldn't call start() method inside the lock to prevent deadlock:
        if (newQuery != null) { newQuery.start(true); }
    }

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
    @Override
    public ResultSet execute() throws CouchbaseLiteException {
        try {
            final C4QueryOptions options = new C4QueryOptions();
            if (parameters == null) { parameters = new Parameters(); }
            final C4QueryEnumerator c4enum;
            try (FLSliceResult params = parameters.encode()) {
                synchronized (getDbLock()) {
                    synchronized (lock) {
                        if (c4query == null) { c4query = prepQueryLocked(); }
                        c4enum = c4query.run(options, params);
                    }
                }
            }
            return new ResultSet(this, c4enum, columnNames);
        }
        catch (LiteCoreException e) {
            throw CouchbaseLiteException.convertException(e);
        }
    }

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
    @Override
    public String explain() throws CouchbaseLiteException {
        synchronized (getDbLock()) {
            synchronized (lock) {
                if (c4query == null) { c4query = prepQueryLocked(); }
                final String exp = c4query.explain();
                if (exp == null) { throw new CouchbaseLiteException("Could not explain query"); }
                return exp;
            }
        }
    }

    /**
     * Adds a query change listener. Changes will be posted on the main queue.
     *
     * @param listener The listener to post changes.
     * @return An opaque listener token object for removing the listener.
     */
    @NonNull
    @Override
    public ListenerToken addChangeListener(@NonNull QueryChangeListener listener) {
        return addChangeListener(null, listener);
    }

    /**
     * Adds a query change listener with the dispatch queue on which changes
     * will be posted. If the dispatch queue is not specified, the changes will be
     * posted on the main queue.
     *
     * @param executor The executor object that calls listener. If null, use default executor.
     * @param listener The listener to post changes.
     * @return An opaque listener token object for removing the listener.
     */
    @NonNull
    @Override
    public ListenerToken addChangeListener(Executor executor, @NonNull QueryChangeListener listener) {
        Preconditions.assertNotNull(listener, "listener");
        return getLiveQuery().addChangeListener(executor, listener);
    }

    /**
     * Removes a change listener wih the given listener token.
     *
     * @param token The listener token.
     */
    @Override
    public void removeChangeListener(@NonNull ListenerToken token) {
        Preconditions.assertNotNull(token, "token");
        getLiveQuery().removeChangeListener(token);
    }

    @NonNull
    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + ClassUtils.objId(this) + ",json=" + marshalAsJSONSafely() + "}";
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    Database getDatabase() { return (Database) from.getSource(); }

    void setSelect(Select select) { this.select = select; }

    void setFrom(DataSource from) { this.from = from; }

    void setJoins(Joins joins) { this.joins = joins; }

    void setWhere(Expression where) { this.where = where; }

    void setGroupBy(GroupBy groupBy) { this.groupBy = groupBy; }

    void setHaving(Having having) { this.having = having; }

    void setOrderBy(OrderBy orderBy) { this.orderBy = orderBy; }

    void setLimit(Limit limit) { this.limit = limit; }

    void copy(AbstractQuery query) {
        this.select = query.select;
        this.from = query.from;
        this.joins = query.joins;
        this.where = query.where;
        this.groupBy = query.groupBy;
        this.having = query.having;
        this.orderBy = query.orderBy;
        this.limit = query.limit;
        this.parameters = query.parameters;
    }

    @VisibleForTesting
    LiveQuery getLiveQuery() {
        synchronized (lock) {
            if (liveQuery == null) { liveQuery = new LiveQuery(this); }
            return liveQuery;
        }
    }

    //---------------------------------------------
    // Private methods
    //---------------------------------------------

    @GuardedBy("lock")
    private C4Query prepQueryLocked() throws CouchbaseLiteException {
        final String json = marshalAsJSONSafely();
        Log.v(DOMAIN, "Encoded query: %s", json);
        if (json == null) { throw new CouchbaseLiteException("Failed to generate JSON query."); }

        if (columnNames == null) { columnNames = getColumnNames(); }

        try { return getDatabase().createQuery(json); }
        catch (LiteCoreException e) { throw CouchbaseLiteException.convertException(e); }
    }

    // https://issues.couchbase.com/browse/CBL-21
    // Using c4query_columnTitle is not an improvement, as of 12/2019
    private Map<String, Integer> getColumnNames() throws CouchbaseLiteException {
        final Map<String, Integer> map = new HashMap<>();
        int index = 0;
        int provisionKeyIndex = 0;
        for (SelectResult selectResult: select.getSelectResults()) {
            String name = selectResult.getColumnName();

            if (name != null && name.equals(PropertyExpression.PROPS_ALL)) { name = from.getColumnName(); }

            if (name == null) { name = "$" + (++provisionKeyIndex); }

            if (map.containsKey(name)) {
                throw new CouchbaseLiteException(
                    Log.formatStandardMessage("DuplicateSelectResultName", name),
                    CBLError.Domain.CBLITE,
                    CBLError.Code.INVALID_QUERY);
            }
            map.put(name, index);
            index++;
        }
        return map;
    }

    @Nullable
    private String marshalAsJSONSafely() {
        try { return marshalAsJSON(); }
        catch (JSONException e) { Log.w(LogDomain.QUERY, "Failed marshalling query as JSON query", e); }
        return null;
    }

    @SuppressWarnings({"PMD.CyclomaticComplexity", "PMD.NPathComplexity", "PMD.AvoidDeeplyNestedIfStmts"})
    @NonNull
    private String marshalAsJSON() throws JSONException {
        final JSONUtils.Marshaller json = new JSONUtils.Marshaller();

        boolean first = true;
        json.startObject();

        // DISTINCT:
        if (select != null && select.isDistinct()) {
            json.writeKey("DISTINCT");
            json.writeBoolean(true);
            first = false;
        }

        // result-columns / SELECT-RESULTS
        if (select != null && select.hasSelectResults()) {
            if (!first) { json.nextMember(); }
            json.writeKey("WHAT");
            json.writeValue(select.asJSON());
            first = false;
        }

        final List<Object> froms = new ArrayList<>();

        final Map<String, Object> as = from.asJSON();
        if (!as.isEmpty()) { froms.add(as); }

        if (joins != null) { froms.addAll((List<?>) joins.asJSON()); }

        if (!froms.isEmpty()) {
            if (!first) { json.nextMember(); }
            json.writeKey("FROM");
            json.writeArray(froms);
            first = false;
        }

        if (where != null) {
            if (!first) { json.nextMember(); }
            json.writeKey("WHERE");
            json.writeValue(where.asJSON());
            first = false;
        }

        if (groupBy != null) {
            if (!first) { json.nextMember(); }
            json.writeKey("GROUP_BY");
            json.writeValue(groupBy.asJSON());
            first = false;
        }

        if (having != null) {
            final Object havingJson = having.asJSON();
            if (havingJson != null) {
                if (!first) { json.nextMember(); }
                json.writeKey("HAVING");
                json.writeValue(havingJson);
                first = false;
            }
        }

        if (orderBy != null) {
            if (!first) { json.nextMember(); }
            json.writeKey("ORDER_BY");
            json.writeArray((List<?>) orderBy.asJSON());
            first = false;
        }

        if (limit != null) {
            final List<?> limits = (List<?>) limit.asJSON();
            if (!first) { json.nextMember(); }
            json.writeKey("LIMIT");
            json.writeValue(limits.get(0));
            if (limits.size() > 1) {
                json.nextMember();
                json.writeKey("OFFSET");
                json.writeValue(limits.get(1));
            }
        }

        json.endObject();

        return json.toString();
    }

    private Object getDbLock() {
        final Database db = getDatabase();
        if (db != null) { return db.getLock(); }
        throw new IllegalStateException("Cannot seize DB lock");
    }
}
