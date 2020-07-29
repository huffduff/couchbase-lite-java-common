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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Locale;
import java.util.Map;

import com.couchbase.lite.internal.core.C4Replicator;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * SessionAuthenticator class is an authenticator that will authenticate by using the session ID of
 * the session created by a Sync Gateway
 */
public final class SessionAuthenticator extends Authenticator {

    private static final String DEFAULT_SYNC_GATEWAY_SESSION_ID_NAME = "SyncGatewaySession";

    //---------------------------------------------
    // member variables
    //---------------------------------------------

    private final String sessionID;
    private final String cookieName;

    //---------------------------------------------
    // Constructor
    //---------------------------------------------

    /**
     * Initializes with the Sync Gateway session ID and uses the default cookie name.
     *
     * @param sessionID Sync Gateway session ID
     */
    public SessionAuthenticator(@NonNull String sessionID) {
        this(sessionID, DEFAULT_SYNC_GATEWAY_SESSION_ID_NAME);
    }

    /**
     * Initializes with the session ID and the cookie name. If the given cookieName
     * is null, the default cookie name will be used.
     *
     * @param sessionID  Sync Gateway session ID
     * @param cookieName The cookie name
     */
    public SessionAuthenticator(@NonNull String sessionID, @Nullable String cookieName) {
        Preconditions.assertNotNull(sessionID, "sessionID");
        this.sessionID = sessionID;
        this.cookieName = cookieName != null ? cookieName : DEFAULT_SYNC_GATEWAY_SESSION_ID_NAME;
    }

    //---------------------------------------------
    // Getters
    //---------------------------------------------

    /**
     * Return session ID of the session created by a Sync Gateway.
     */
    @NonNull
    public String getSessionID() {
        return sessionID;
    }

    /**
     * Return session cookie name that the session ID value will be set to when communicating
     * the Sync Gateway.
     */
    public String getCookieName() {
        return cookieName;
    }

    //---------------------------------------------
    // Authenticator abstract method implementation
    //---------------------------------------------

    @Override
    void authenticate(Map<String, Object> options) {
        final String current = (String) options.get(C4Replicator.REPLICATOR_OPTION_COOKIES);
        final StringBuffer cookieStr = current != null ? new StringBuffer(current) : new StringBuffer();

        if (cookieStr.length() > 0) { cookieStr.append("; "); }
        cookieStr.append(String.format(Locale.ENGLISH, "%s=%s", cookieName, sessionID));

        options.put(C4Replicator.REPLICATOR_OPTION_COOKIES, cookieStr.toString());
    }
}
