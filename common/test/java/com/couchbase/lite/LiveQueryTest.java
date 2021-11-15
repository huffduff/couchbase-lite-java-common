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


import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


@SuppressWarnings("ConstantConditions")
public class LiveQueryTest extends BaseDbTest {
    // query run time ranges from 1ms - 20ms depending on core,
    // doubling it should guarantee query finishes running in tests everytime
    private static final long QUERY_RUN_TIME_MS = 20;
    private static final long TOLERABLE_QUERY_RUN_TIME_MS = QUERY_RUN_TIME_MS * 2;

    // when there's a rapid change within 250ms of previous query run,
    // delay is 500ms + query run time. Add some fudge to guarantee we get callback from core
    private static final long QUERY_DEBOUNCE_DELAY_MS = 500;
    private static final long TOLERABLE_DATABASE_CHANGE_DELAY_MS =
        QUERY_DEBOUNCE_DELAY_MS + TOLERABLE_QUERY_RUN_TIME_MS + ((QUERY_DEBOUNCE_DELAY_MS + QUERY_RUN_TIME_MS) / 10);

    private static final String KEY = "number";

    /**
     * When a query observer is first registered,
     * the query should get notified after the time it takes the query to run
     */
    @Test
    public void testCreateBasicListener() throws InterruptedException {
        final Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(baseTestDb))
            .where(Expression.property(KEY).greaterThanOrEqualTo(Expression.intValue(0)))
            .orderBy(Ordering.property(KEY).ascending());

        final CountDownLatch latch = new CountDownLatch(1);
        ListenerToken token = query.addChangeListener(testSerialExecutor, change -> latch.countDown());
        try { assertTrue(latch.await(TOLERABLE_QUERY_RUN_TIME_MS, TimeUnit.MILLISECONDS)); }
        finally { query.removeChangeListener(token); }
    }

    /**
     * When a second observer is registered, it should get call back after query done running
     * The first observer should NOT get notified when the second observer is created
     * When there's a db change, both observers should get notified in a tolerable amount of time
     */
    @Test
    public void testMultipleListeners() throws InterruptedException, CouchbaseLiteException {
        ListenerToken token1 = null;
        final Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(baseTestDb))
            .where(Expression.property(KEY).greaterThanOrEqualTo(Expression.intValue(0)))
            .orderBy(Ordering.property(KEY).ascending());
        final CountDownLatch[] latch1 = new CountDownLatch[2];
        final CountDownLatch[] latch2 = new CountDownLatch[2];

        for (int i = 0; i < latch1.length; i++) { latch1[i] = new CountDownLatch(1); }
        for (int i = 0; i < latch2.length; i++) { latch2[i] = new CountDownLatch(1); }

        final AtomicIntegerArray atmCount = new AtomicIntegerArray(2);

        try {
            token1 = query.addChangeListener(
                testSerialExecutor,
                change -> latch1[atmCount.getAndIncrement(0)].countDown());

            ListenerToken token2 = null;
            // listener 1 gets notified after observer subscribed
            assertTrue(latch1[0].await(TOLERABLE_QUERY_RUN_TIME_MS, TimeUnit.MILLISECONDS));
            try {
                token2 = query.addChangeListener(
                    testSerialExecutor,
                    change -> latch2[atmCount.getAndIncrement(1)].countDown());
                // listener should get notify after query run time
                assertTrue(latch2[0].await(TOLERABLE_QUERY_RUN_TIME_MS, TimeUnit.MILLISECONDS));

                // creation of the second listener should not trigger first listener callback
                assertFalse(latch1[1].await(TOLERABLE_QUERY_RUN_TIME_MS * 2, TimeUnit.MILLISECONDS));

                createDocNumbered(11);

                // introducing change in database should trigger both listener callbacks after the time it takes
                // for core to wait and rerun query
                assertTrue(latch1[1].await(TOLERABLE_DATABASE_CHANGE_DELAY_MS, TimeUnit.MILLISECONDS));
                assertTrue(latch2[1].await(TOLERABLE_DATABASE_CHANGE_DELAY_MS, TimeUnit.MILLISECONDS));
            }
            finally { query.removeChangeListener(token2); }
        }
        finally { query.removeChangeListener(token1); }
    }

    // When a result set is closed, we should still be able to introduce a change
    @Test
    public void testCloseResultsInLiveQueryListener() throws CouchbaseLiteException, InterruptedException {
        final Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(baseTestDb));

        final AtomicIntegerArray atmCount = new AtomicIntegerArray(1);
        final CountDownLatch[] latches = new CountDownLatch[2];
        for (int i = 0; i < latches.length; i++) { latches[i] = new CountDownLatch(1); }

        ListenerToken token = query.addChangeListener(
            testSerialExecutor,
            change -> {
                change.getResults().close();
                latches[atmCount.getAndIncrement(0)].countDown();
            });
        try {
            createDocNumbered(10);
            assertTrue(latches[0].await(TOLERABLE_DATABASE_CHANGE_DELAY_MS, TimeUnit.MILLISECONDS));

            createDocNumbered(11);
            assertTrue(latches[1].await(TOLERABLE_DATABASE_CHANGE_DELAY_MS, TimeUnit.MILLISECONDS));
        }
        finally { query.removeChangeListener(token); }
    }

    /**
     * Two observers should have two independent result sets.
     * When two observers try to iterate through the result set,
     * values in that rs should not be skipped because of the other observer
     */
    @Test
    public void testIterateRSWith2Listeners() throws InterruptedException, CouchbaseLiteException {
        final Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(baseTestDb));

        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);

        ListenerToken token = query.addChangeListener(
            testSerialExecutor,
            change -> {
                // even if the other listener finishes running first and iterates through doc-11,
                // this listener should get an independent rs, thus iterates from the beginning, getting doc-11
                try (ResultSet rs = change.getResults()) {
                    Result r = rs.next();
                    if (Objects.equals(r.getString(0), "doc-11")) { latch1.countDown(); }
                }
            });
        ListenerToken token1 = query.addChangeListener(
            testSerialExecutor, change -> {
                // even if the other listener finishes running first and iterates through doc-11,
                // this listener should get an independent rs, thus iterates from the beginning, getting doc-11
                try (ResultSet rs = change.getResults()) {
                    Result r = rs.next();
                    if (Objects.equals(r.getString(0), "doc-11")) { latch2.countDown(); }
                }
            });
        try {
            createDocNumbered(11);

            // both listeners get notified after doc-11 is created in database
            // rs iterates through the correct value
            assertTrue(latch1.await(TOLERABLE_DATABASE_CHANGE_DELAY_MS, TimeUnit.MILLISECONDS));
            assertTrue(latch2.await(TOLERABLE_DATABASE_CHANGE_DELAY_MS, TimeUnit.MILLISECONDS));
        }
        finally {
            query.removeChangeListener(token);
            query.removeChangeListener(token1);
        }
    }

    // Changing query parameters should cause an update within tolerable time
    @Test
    public void testChangeParameters() throws CouchbaseLiteException, InterruptedException {
        createDocNumbered(1);
        createDocNumbered(2);

        Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(baseTestDb))
            .where(Expression.property(KEY).greaterThanOrEqualTo(Expression.parameter("VALUE")))
            .orderBy(Ordering.property(KEY).ascending());

        final CountDownLatch[] latch = new CountDownLatch[3];
        for (int i = 0; i < latch.length; i++) { latch[i] = new CountDownLatch(1); }

        // count is used to get the next latch and also check size of rs
        final AtomicIntegerArray atmCount = new AtomicIntegerArray(1);

        // VALUE is set to 2, we should expect that query will only get notification for doc 2, rs size is 1
        Parameters params = new Parameters();
        params.setInt("VALUE", 2);
        query.setParameters(params);

        ListenerToken token = query.addChangeListener(
            testSerialExecutor,
            change -> {
                try (ResultSet rs = change.getResults()) {
                    //  query should only be notified 2 times:
                    //  1. query first gets doc 2, the rs size is 1
                    //  2. after param changes to 1, query gets a new rs that has doc 1 and 2, rs size is now 2
                    //  query should not be notified when doc 0 is added to the db
                    if (rs.allResults().size() == atmCount.get(0) + 1) {
                        latch[atmCount.getAndIncrement(0)].countDown();
                    }
                }
            });
        try {
            assertTrue(latch[0].await(TOLERABLE_DATABASE_CHANGE_DELAY_MS, TimeUnit.MILLISECONDS));

            params = new Parameters();
            params.setInt("VALUE", 1);
            query.setParameters(params);

            // VALUE changes to 1, query now gets a new rs for doc 1 and 2
            assertTrue(latch[1].await(TOLERABLE_DATABASE_CHANGE_DELAY_MS, TimeUnit.MILLISECONDS));

            // This doc does not meet the condition of the query, thus query should not get notified
            createDocNumbered(0);
            assertFalse(latch[2].await(TOLERABLE_DATABASE_CHANGE_DELAY_MS * 2, TimeUnit.MILLISECONDS));
        }
        finally { query.removeChangeListener(token); }
    }

    // CBL-2344: Live query may stop refreshing
    @Test
    public void testLiveQueryRefresh() throws CouchbaseLiteException, InterruptedException {
        final AtomicReference<CountDownLatch> latchHolder = new AtomicReference<>();
        final AtomicReference<List<Result>> resultsHolder = new AtomicReference<>();

        createDocNumbered(10);

        final Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(baseTestDb))
            .where(Expression.property(KEY).greaterThan(Expression.intValue(0)));

        latchHolder.set(new CountDownLatch(1));
        ListenerToken token = query.addChangeListener(
            testSerialExecutor,
            change -> {
                resultsHolder.set(change.getResults().allResults());
                latchHolder.get().countDown();
            }
        );

        try {
            // this update should happen nearly instantaneously
            assertTrue(latchHolder.get().await(TOLERABLE_QUERY_RUN_TIME_MS, TimeUnit.MILLISECONDS));
            assertEquals(1, resultsHolder.get().size());

            // adding this document will trigger the query but since it does not meet the query
            // criteria, it will not produce a new result. The listener should not be called.
            // Wait for 2 full update intervals and a little bit more.
            latchHolder.set(new CountDownLatch(1));
            createDocNumbered(0);
            assertFalse(latchHolder.get().await((2 * TOLERABLE_DATABASE_CHANGE_DELAY_MS), TimeUnit.MILLISECONDS));

            // adding this document should cause a call to the listener in not much more than an update interval
            latchHolder.set(new CountDownLatch(1));
            createDocNumbered(11);
            assertTrue(latchHolder.get().await(TOLERABLE_DATABASE_CHANGE_DELAY_MS, TimeUnit.MILLISECONDS));
            assertEquals(2, resultsHolder.get().size());
        }
        finally {
            query.removeChangeListener(token);
        }
    }

    // create test docs
    private void createDocNumbered(int i) throws CouchbaseLiteException {
        String docID = "doc-" + i;
        MutableDocument doc = new MutableDocument(docID);
        doc.setValue(KEY, i);
        saveDocInBaseTestDb(doc);
    }
}
