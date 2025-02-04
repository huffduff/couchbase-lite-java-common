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
package com.couchbase.lite.internal.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.utils.Report;
import com.couchbase.lite.internal.utils.SlowTest;

import static org.junit.Assert.assertEquals;


public class C4CollatedQueryTest extends C4QueryBaseTest {
    @Before
    public final void setUpC4CollatedQueryTest() throws LiteCoreException, IOException {
        loadJsonAsset("iTunesMusicLibrary.json");
    }

    // @formatter:off
    @SlowTest
    @Test
    public void testDBQueryCollated() throws LiteCoreException {
        String json = "{WHAT: [ ['.Name'] ], "
            + "WHERE: ['COLLATE', {'unicode': true, 'case': false, 'diacritic': false}, ['=', ['.Artist'], 'Benoît Pioulard']], "
            + "ORDER_BY: [ ['COLLATE', {'unicode': true, 'case': false, 'diacritic': false}, ['.Name']] ]}";
        Report.log("COLLATED QUERY: " + json);
        compileSelect(json5(json));
        List<String> tracks = run();
        assertEquals(2, tracks.size());
    }
    // @formatter:on

    @SlowTest
    @Test
    public void testDBQueryAggregateCollated() throws LiteCoreException {
        String json = "{WHAT: [ ['COLLATE', {'unicode': true, 'case': false, 'diacritic': false}, ['.Artist']] ], "
            + "DISTINCT: true, "
            + "ORDER_BY: [ ['COLLATE', {'unicode': true, 'case': false, 'diacritic': false}, ['.Artist']] ]}";
        Report.log("COLLATED QUERY: " + json);
        compileSelect(json5(json));
        List<String> artists = run();
        assertEquals(2097, artists.size());

        // Benoît Pioulard appears twice in the database, once mis-capitalized as BenoÎt Pioulard.
        // Check that these got coalesced by the DISTINCT operator:
        assertEquals("Benny Goodman", artists.get(214));
        assertEquals("Benoît Pioulard", artists.get(215));
        assertEquals("Bernhard Weiss", artists.get(216));

        // Make sure "Zoë Keating" sorts correctly:
        assertEquals("ZENИTH (feat. saåad)", artists.get(2082));
        assertEquals("Zoë Keating", artists.get(2083));
        assertEquals("Zola Jesus", artists.get(2084));
    }

    protected List<String> run() throws LiteCoreException {
        try (C4QueryEnumerator e = query.run(new C4QueryOptions())) {
            List<String> results = new ArrayList<>();
            while (e.next()) { results.add(e.getColumns().getValueAt(0).asString()); }
            return results;
        }
    }
}
