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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.internal.Util;

import com.couchbase.lite.internal.BaseImmutableReplicatorConfiguration;
import com.couchbase.lite.internal.replicator.AbstractCBLWebSocket;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * Replicator configuration.
 */
@SuppressWarnings({"PMD.GodClass", "PMD.TooManyFields"})
public abstract class AbstractReplicatorConfiguration {
    /**
     * This is a long time.  This many seconds, however, is less than Integer.MAX_INT millis
     */
    public static final long DISABLE_HEARTBEAT = 35000L;

    /**
     * Replicator type
     * PUSH_AND_PULL: Bidirectional; both push and pull
     * PUSH: Pushing changes to the target
     * PULL: Pulling changes from the target
     *
     * @deprecated Use AbstractReplicator.ReplicatorType
     */
    @Deprecated
    public enum ReplicatorType {PUSH_AND_PULL, PUSH, PULL}


    //---------------------------------------------
    // member variables
    //---------------------------------------------

    @NonNull
    private final Database database;
    @NonNull
    private Replicator.Type type;
    private boolean continuous;
    @Nullable
    private Authenticator authenticator;
    @Nullable
    private Map<String, String> headers;
    @Nullable
    private byte[] pinnedServerCertificate;
    @Nullable
    private List<String> channels;
    @Nullable
    private List<String> documentIDs;
    @Nullable
    private ReplicationFilter pushFilter;
    @Nullable
    private ReplicationFilter pullFilter;
    @Nullable
    private ConflictResolver conflictResolver;
    private int maxRetries;
    private long maxRetryWaitTime;
    private long heartbeat;
    private final Endpoint target;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    protected AbstractReplicatorConfiguration(@NonNull Database database, @NonNull Endpoint target) {
        this(
            Preconditions.assertNotNull(database, "database"),
            Replicator.Type.PUSH_AND_PULL,
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            -1,
            AbstractCBLWebSocket.DEFAULT_MAX_RETRY_WAIT_SEC,
            AbstractCBLWebSocket.DEFAULT_HEARTBEAT_SEC,
            Preconditions.assertNotNull(target, "target")
        );
    }

    protected AbstractReplicatorConfiguration(@NonNull AbstractReplicatorConfiguration config) {
        this(
            Preconditions.assertNotNull(config, "config").database,
            config.type,
            config.continuous,
            config.authenticator,
            config.headers,
            config.pinnedServerCertificate,
            config.channels,
            config.documentIDs,
            config.pullFilter,
            config.pushFilter,
            config.conflictResolver,
            config.maxRetries,
            config.maxRetryWaitTime,
            config.heartbeat,
            config.target);
    }

    protected AbstractReplicatorConfiguration(@NonNull BaseImmutableReplicatorConfiguration config) {
        this(
            Preconditions.assertNotNull(config, "config").getDatabase(),
            config.getType(),
            config.isContinuous(),
            config.getAuthenticator(),
            config.getHeaders(),
            config.getPinnedServerCertificate(),
            config.getChannels(),
            config.getDocumentIDs(),
            config.getPullFilter(),
            config.getPushFilter(),
            config.getConflictResolver(),
            config.getMaxRetries(),
            config.getMaxRetryWaitTime(),
            config.getHeartbeat(),
            config.getTarget());
    }

    @SuppressWarnings("PMD.ExcessiveParameterList")
    protected AbstractReplicatorConfiguration(
        @NonNull Database database,
        @NonNull Replicator.Type type,
        boolean continuous,
        @Nullable Authenticator authenticator,
        @Nullable Map<String, String> headers,
        @Nullable byte[] pinnedServerCertificate,
        @Nullable List<String> channels,
        @Nullable List<String> documentIDs,
        @Nullable ReplicationFilter pushFilter,
        @Nullable ReplicationFilter pullFilter,
        @Nullable ConflictResolver conflictResolver,
        int maxRetries,
        long maxRetryWaitTime, long heartbeat, Endpoint target) {
        this.database = database;
        this.type = type;
        this.continuous = continuous;
        this.authenticator = authenticator;
        this.headers = headers;
        this.channels = channels;
        this.documentIDs = documentIDs;
        this.pullFilter = pullFilter;
        this.pushFilter = pushFilter;
        this.conflictResolver = conflictResolver;
        this.target = target;

        setPinnedServerCertificateInternal(pinnedServerCertificate);
        setMaxRetriesInternal(maxRetries);
        setMaxRetryWaitTimeInternal(maxRetryWaitTime);
        setHeartbeatInternal(heartbeat);
    }

//---------------------------------------------
    // Setters
    //---------------------------------------------

    /**
     * Sets the authenticator to authenticate with a remote target server.
     * Currently there are two types of the authenticators,
     * BasicAuthenticator and SessionAuthenticator, supported.
     *
     * @param authenticator The authenticator.
     * @return this.
     */
    @NonNull
    public final ReplicatorConfiguration setAuthenticator(@NonNull Authenticator authenticator) {
        this.authenticator = Preconditions.assertNotNull(authenticator, "authenticator");
        return getReplicatorConfiguration();
    }

    /**
     * Sets a set of Sync Gateway channel names to pull from. Ignored for
     * push replication. If unset, all accessible channels will be pulled.
     * Note: channels that are not accessible to the user will be ignored
     * by Sync Gateway.
     *
     * @param channels The Sync Gateway channel names.
     * @return this.
     */
    @NonNull
    public final ReplicatorConfiguration setChannels(@Nullable List<String> channels) {
        this.channels = (channels == null) ? null : new ArrayList<>(channels);
        return getReplicatorConfiguration();
    }

    /**
     * Sets the the conflict resolver.
     *
     * @param conflictResolver A conflict resolver.
     * @return this.
     */
    @Nullable
    public final ReplicatorConfiguration setConflictResolver(@Nullable ConflictResolver conflictResolver) {
        this.conflictResolver = conflictResolver;
        return getReplicatorConfiguration();
    }

    /**
     * Sets whether the replicator stays active indefinitely to replicate
     * changed documents. The default value is false, which means that the
     * replicator will stop after it finishes replicating the changed
     * documents.
     *
     * @param continuous The continuous flag.
     * @return this.
     */
    @NonNull
    public final ReplicatorConfiguration setContinuous(boolean continuous) {
        this.continuous = continuous;
        return getReplicatorConfiguration();
    }

    /**
     * Sets a set of document IDs to filter by: if given, only documents
     * with these IDs will be pushed and/or pulled.
     *
     * @param documentIDs The document IDs.
     * @return this.
     */
    @NonNull
    public final ReplicatorConfiguration setDocumentIDs(@Nullable List<String> documentIDs) {
        this.documentIDs = (documentIDs == null) ? null : new ArrayList<>(documentIDs);
        return getReplicatorConfiguration();
    }

    /**
     * Sets the extra HTTP headers to send in all requests to the remote target.
     *
     * @param headers The HTTP Headers.
     * @return this.
     */
    @NonNull
    public final ReplicatorConfiguration setHeaders(@Nullable Map<String, String> headers) {
        this.headers = (headers == null) ? null : new HashMap<>(headers);
        return getReplicatorConfiguration();
    }

    /**
     * Sets the target server's SSL certificate.
     *
     * @param pinnedCert the SSL certificate.
     * @return this.
     */
    @NonNull
    public final ReplicatorConfiguration setPinnedServerCertificate(@Nullable byte[] pinnedCert) {
        setPinnedServerCertificateInternal(pinnedCert);
        return getReplicatorConfiguration();
    }

    /**
     * Sets a filter object for validating whether the documents can be pulled from the
     * remote endpoint. Only documents for which the object returns true are replicated.
     *
     * @param pullFilter The filter to filter the document to be pulled.
     * @return this.
     */
    @NonNull
    public final ReplicatorConfiguration setPullFilter(@Nullable ReplicationFilter pullFilter) {
        this.pullFilter = pullFilter;
        return getReplicatorConfiguration();
    }

    /**
     * Sets a filter object for validating whether the documents can be pushed
     * to the remote endpoint.
     *
     * @param pushFilter The filter to filter the document to be pushed.
     * @return this.
     */
    @NonNull
    public final ReplicatorConfiguration setPushFilter(ReplicationFilter pushFilter) {
        this.pushFilter = pushFilter;
        return getReplicatorConfiguration();
    }

    /**
     * Old setter for replicator type, indicating the direction of the replicator.
     * The default value is PUSH_AND_PULL which is bi-directional.
     *
     * @param replicatorType The replicator type.
     * @return this.
     * @deprecated Use setType(AbstractReplicator.ReplicatorType)
     */
    @Deprecated
    @NonNull
    public final ReplicatorConfiguration setReplicatorType(@NonNull ReplicatorType replicatorType) {
        final Replicator.Type type;
        switch (Preconditions.assertNotNull(replicatorType, "replicator type")) {
            case PUSH_AND_PULL:
                type = Replicator.Type.PUSH_AND_PULL;
                break;
            case PUSH:
                type = Replicator.Type.PUSH;
                break;
            case PULL:
                type = Replicator.Type.PULL;
                break;
            default:
                throw new IllegalStateException("Unrecognized replicator type: " + replicatorType);
        }
        return setType(type);
    }

    /**
     * Sets the replicator type indicating the direction of the replicator.
     * The default value is .pushAndPull which is bi-directional.
     *
     * @param type The replicator type.
     * @return this.
     */
    @NonNull
    public final ReplicatorConfiguration setType(@NonNull Replicator.Type type) {
        this.type = Preconditions.assertNotNull(type, "replicator type");
        return getReplicatorConfiguration();
    }

    /**
     * Set the max number of retry attempts made after a connection failure.
     *
     * @param maxRetries max retry attempts
     */
    public final ReplicatorConfiguration setMaxRetries(int maxRetries) {
        setMaxRetriesInternal(maxRetries);
        return getReplicatorConfiguration();
    }

    /**
     * Set the max time between retry attempts (exponential backoff).
     *
     * @param maxRetryWaitTime max retry wait time
     */
    public final ReplicatorConfiguration setMaxRetryWaitTime(long maxRetryWaitTime) {
        setMaxRetryWaitTimeInternal(maxRetryWaitTime);
        return getReplicatorConfiguration();
    }

    /**
     * Set the heartbeat interval, in seconds.
     * Must be positive and less than Integer.MAX_VALUE milliseconds
     */
    public final ReplicatorConfiguration setHeartbeat(long heartbeat) {
        setHeartbeatInternal(heartbeat);
        return getReplicatorConfiguration();
    }

    //---------------------------------------------
    // Getters
    //---------------------------------------------

    /**
     * Return the Authenticator to authenticate with a remote target.
     */
    @Nullable
    public final Authenticator getAuthenticator() { return authenticator; }

    /**
     * A set of Sync Gateway channel names to pull from. Ignored for push replication.
     * The default value is null, meaning that all accessible channels will be pulled.
     * Note: channels that are not accessible to the user will be ignored by Sync Gateway.
     */
    @Nullable
    public final List<String> getChannels() { return (channels == null) ? null : new ArrayList<>(channels); }

    /**
     * Return the conflict resolver.
     */
    @Nullable
    public final ConflictResolver getConflictResolver() { return conflictResolver; }

    /**
     * Return the continuous flag indicating whether the replicator should stay
     * active indefinitely to replicate changed documents.
     */
    public final boolean isContinuous() { return continuous; }

    /**
     * Return the local database to replicate with the replication target.
     */
    @NonNull
    public final Database getDatabase() { return database; }

    /**
     * A set of document IDs to filter by: if not nil, only documents with these IDs will be pushed
     * and/or pulled.
     */
    @Nullable
    public final List<String> getDocumentIDs() { return (documentIDs == null) ? null : new ArrayList<>(documentIDs); }

    /**
     * Return Extra HTTP headers to send in all requests to the remote target.
     */
    @Nullable
    public final Map<String, String> getHeaders() { return (headers == null) ? null : new HashMap<>(headers); }

    /**
     * Return the remote target's SSL certificate.
     */
    @Nullable
    public final byte[] getPinnedServerCertificate() { return copyCert(pinnedServerCertificate); }

    /**
     * Gets a filter object for validating whether the documents can be pulled
     * from the remote endpoint.
     */
    @Nullable
    public final ReplicationFilter getPullFilter() { return pullFilter; }

    /**
     * Gets a filter object for validating whether the documents can be pushed
     * to the remote endpoint.
     */
    @Nullable
    public final ReplicationFilter getPushFilter() { return pushFilter; }

    /**
     * Old getter for Replicator type indicating the direction of the replicator.
     *
     * @deprecated Use getType()
     */
    @Deprecated
    @NonNull
    public final ReplicatorType getReplicatorType() {
        switch (type) {
            case PUSH_AND_PULL:
                return ReplicatorType.PUSH_AND_PULL;
            case PUSH:
                return ReplicatorType.PUSH;
            case PULL:
                return ReplicatorType.PULL;
            default:
                throw new IllegalStateException("Unrecognized replicator type: " + type);
        }
    }

    /**
     * Return Replicator type indicating the direction of the replicator.
     */
    @NonNull
    public final Replicator.Type getType() { return type; }

    /**
     * Return the replication target to replicate with.
     */
    @NonNull
    public final Endpoint getTarget() { return target; }

    /**
     * Return the max number of retry attempts made after connection failure.
     */
    public final int getMaxRetries() {
        return (maxRetries >= 0)
            ? maxRetries
            : ((continuous)
                ? AbstractCBLWebSocket.DEFAULT_ONE_SHOT_MAX_RETRIES
                : AbstractCBLWebSocket.DEFAULT_CONTINUOUS_MAX_RETRIES);
    }

    /**
     * Return the max time between retry attempts (exponential backoff).
     *
     * @return max retry wait time
     */
    public long getMaxRetryWaitTime() { return maxRetryWaitTime; }

    /**
     * Return the heartbeat interval, in seconds.
     *
     * @return heartbeat interval in seconds
     */
    public long getHeartbeat() { return heartbeat; }

    @SuppressWarnings("PMD.NPathComplexity")
    @NonNull
    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();

        if (pullFilter != null) { buf.append('|'); }
        if ((type == Replicator.Type.PULL) || (type == Replicator.Type.PUSH_AND_PULL)) {
            buf.append('<');
        }

        buf.append(continuous ? '*' : '=');

        if ((type == Replicator.Type.PUSH) || (type == Replicator.Type.PUSH_AND_PULL)) {
            buf.append('>');
        }
        if (pushFilter != null) { buf.append('|'); }

        buf.append('(');
        if (authenticator != null) { buf.append('@'); }
        if (pinnedServerCertificate != null) { buf.append('^'); }
        buf.append(')');

        if (conflictResolver != null) { buf.append('!'); }

        return "ReplicatorConfig{" + database + buf.toString() + target + '}';
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    abstract ReplicatorConfiguration getReplicatorConfiguration();

    private void setPinnedServerCertificateInternal(@Nullable byte[] pinnedCert) {
        pinnedServerCertificate = copyCert(pinnedCert);
    }

    private void setMaxRetriesInternal(int maxRetries) {
        this.maxRetries = Preconditions.assertNotNegative(maxRetries, "max retries");
    }

    private void setMaxRetryWaitTimeInternal(long maxRetryWaitTime) {
        this.maxRetryWaitTime = Preconditions.assertPositive(maxRetryWaitTime, "max retry wait time");
    }

    private void setHeartbeatInternal(long heartbeat) {
        Util.checkDuration("heartbeat", Preconditions.assertPositive(heartbeat, "heartbeat"), TimeUnit.SECONDS);
        this.heartbeat = heartbeat;
    }

    private static byte[] copyCert(@Nullable byte[] cert) {
        if (cert == null) { return null; }
        final byte[] newCert = new byte[cert.length];
        System.arraycopy(cert, 0, newCert, 0, newCert.length);
        return newCert;
    }
}
