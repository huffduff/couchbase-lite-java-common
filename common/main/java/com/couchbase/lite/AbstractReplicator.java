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

import java.net.URI;
import java.security.cert.Certificate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.ExecutionService;
import com.couchbase.lite.internal.SocketFactory;
import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.core.C4DocumentEnded;
import com.couchbase.lite.internal.core.C4Error;
import com.couchbase.lite.internal.core.C4ReplicationFilter;
import com.couchbase.lite.internal.core.C4Replicator;
import com.couchbase.lite.internal.core.C4ReplicatorListener;
import com.couchbase.lite.internal.core.C4ReplicatorMode;
import com.couchbase.lite.internal.core.C4ReplicatorStatus;
import com.couchbase.lite.internal.core.C4Socket;
import com.couchbase.lite.internal.core.InternalReplicator;
import com.couchbase.lite.internal.fleece.FLDict;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.replicator.CBLCookieStore;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.ClassUtils;
import com.couchbase.lite.internal.utils.Fn;
import com.couchbase.lite.internal.utils.Preconditions;
import com.couchbase.lite.internal.utils.StringUtils;


/**
 * A replicator for replicating document changes between a local database and a target database.
 * The replicator can be bidirectional or either push or pull. The replicator can also be one-shot
 * or continuous. The replicator runs asynchronously, so observe the status to
 * be notified of progress.
 */
@SuppressWarnings({"PMD.GodClass", "PMD.TooManyFields", "PMD.CyclomaticComplexity"})
public abstract class AbstractReplicator extends InternalReplicator {
    private static final LogDomain DOMAIN = LogDomain.REPLICATOR;

    /**
     * The replication direction
     * <p>
     * PUSH_AND_PULL: Bidirectional; both push and pull
     * PUSH: Pushing changes to the target
     * PULL: Pulling changes from the target
     */
    public enum Type {PUSH_AND_PULL, PUSH, PULL}

    /**
     * Activity level of a replicator.
     */
    public enum ActivityLevel {
        /**
         * The replication is finished or hit a fatal error.
         */
        STOPPED,
        /**
         * The replicator is offline because the remote host is unreachable.
         */
        OFFLINE,
        /**
         * The replicator is connecting to the remote host.
         */
        CONNECTING,
        /**
         * The replication is inactive; either waiting for changes or offline
         * as the remote host is unreachable.
         */
        IDLE,
        /**
         * The replication is actively transferring data.
         */
        BUSY
    }


    /**
     * Progress of a replicator. If `total` is zero, the progress is indeterminate; otherwise,
     * dividing the two will produce a fraction that can be used to draw a progress bar.
     */
    public static final class Progress {
        //---------------------------------------------
        // member variables
        //---------------------------------------------

        // The number of completed changes processed.
        private final long completed;

        // The total number of changes to be processed.
        private final long total;

        //---------------------------------------------
        // Constructors
        //---------------------------------------------

        private Progress(long completed, long total) {
            this.completed = completed;
            this.total = total;
        }

        //---------------------------------------------
        // API - public methods
        //---------------------------------------------

        /**
         * The number of completed changes processed.
         */
        public long getCompleted() { return completed; }

        /**
         * The total number of changes to be processed.
         */
        public long getTotal() { return total; }

        @NonNull
        @Override
        public String toString() { return "Progress{" + "completed=" + completed + ", total=" + total + '}'; }
    }


    /**
     * Combined activity level and progress of a replicator.
     */
    public static final class Status {
        private static final Map<Integer, ActivityLevel> ACTIVITY_LEVEL_FROM_C4;
        static {
            final Map<Integer, ActivityLevel> m = new HashMap<>();
            m.put(C4ReplicatorStatus.ActivityLevel.STOPPED, ActivityLevel.STOPPED);
            m.put(C4ReplicatorStatus.ActivityLevel.OFFLINE, ActivityLevel.OFFLINE);
            m.put(C4ReplicatorStatus.ActivityLevel.CONNECTING, ActivityLevel.CONNECTING);
            m.put(C4ReplicatorStatus.ActivityLevel.IDLE, ActivityLevel.IDLE);
            m.put(C4ReplicatorStatus.ActivityLevel.BUSY, ActivityLevel.BUSY);
            ACTIVITY_LEVEL_FROM_C4 = Collections.unmodifiableMap(m);
        }
        private static ActivityLevel getActivityLevelFromC4(int c4ActivityLevel) {
            final ActivityLevel level = ACTIVITY_LEVEL_FROM_C4.get(c4ActivityLevel);
            if (level != null) { return level; }

            Log.w(LogDomain.REPLICATOR, "Unrecognized replicator activity level: " + c4ActivityLevel);

            // Per @Pasin, unrecognized states report as busy.
            return ActivityLevel.BUSY;
        }

        static boolean isStopped(C4ReplicatorStatus c4Status) {
            return c4Status.getActivityLevel() == C4ReplicatorStatus.ActivityLevel.STOPPED;
        }

        static boolean isOffline(C4ReplicatorStatus c4Status) {
            return c4Status.getActivityLevel() == C4ReplicatorStatus.ActivityLevel.OFFLINE;
        }

        //---------------------------------------------
        // member variables
        //---------------------------------------------
        @NonNull
        private final ActivityLevel activityLevel;
        @NonNull
        private final Progress progress;
        @Nullable
        private final CouchbaseLiteException error;

        //---------------------------------------------
        // Constructors
        //---------------------------------------------

        Status(@NonNull C4ReplicatorStatus c4Status) {
            this(
                getActivityLevelFromC4(c4Status.getActivityLevel()),
                new Progress((int) c4Status.getProgressUnitsCompleted(), (int) c4Status.getProgressUnitsTotal()),
                (c4Status.getErrorCode() == 0) ? null : CouchbaseLiteException.convertC4Error(c4Status.getC4Error()));
        }

        private Status(@NonNull Status status) { this(status.activityLevel, status.progress, status.error); }

        private Status(
            @NonNull ActivityLevel activityLevel,
            @NonNull Progress progress,
            @Nullable CouchbaseLiteException error) {
            this.activityLevel = activityLevel;
            this.progress = progress;
            this.error = error;
        }

        //---------------------------------------------
        // API - public methods
        //---------------------------------------------

        /**
         * The current activity level.
         */
        @NonNull
        public ActivityLevel getActivityLevel() { return activityLevel; }

        /**
         * The current progress of the replicator.
         */
        @NonNull
        public Replicator.Progress getProgress() { return progress; }

        @Nullable
        public CouchbaseLiteException getError() { return error; }

        @NonNull
        @Override
        public String toString() {
            return "Status{" + "activityLevel=" + activityLevel + ", progress=" + progress + ", error=" + error + '}';
        }
    }

    // just queue everything up for in-order processing.
    static final class ReplicatorListener implements C4ReplicatorListener {
        private final Executor dispatcher;

        ReplicatorListener(@NonNull Executor dispatcher) { this.dispatcher = dispatcher; }

        @Override
        public void statusChanged(
            @Nullable C4Replicator c4Repl,
            @Nullable C4ReplicatorStatus status,
            @Nullable Object repl) {
            Log.v(DOMAIN, "C4ReplicatorListener.statusChanged, repl: %s, status: %s", repl, status);

            final AbstractReplicator replicator = verifyReplicator(c4Repl, repl);
            if (replicator == null) { return; }

            if (status == null) {
                Log.w(DOMAIN, "C4ReplicatorListener.statusChanged, status is null");
                return;
            }

            dispatcher.execute(() -> replicator.c4StatusChanged(status));
        }

        @Override
        public void documentEnded(
            @NonNull C4Replicator c4Repl,
            boolean pushing,
            @Nullable C4DocumentEnded[] documents,
            @Nullable Object repl) {
            Log.i(DOMAIN, "C4ReplicatorListener.documentEnded, repl: %s, pushing: %s", repl, pushing);

            if (!(repl instanceof AbstractReplicator)) {
                Log.w(DOMAIN, "C4ReplicatorListener.documentEnded, repl is null");
                return;
            }

            final AbstractReplicator replicator = verifyReplicator(c4Repl, repl);
            if (replicator == null) { return; }

            if (documents == null) {
                Log.w(DOMAIN, "C4ReplicatorListener.documentEnded, documents is null");
                return;
            }

            dispatcher.execute(() -> replicator.documentEnded(pushing, documents));
        }

        @Nullable
        private AbstractReplicator verifyReplicator(@Nullable C4Replicator c4Repl, @Nullable Object repl) {
            final AbstractReplicator replicator
                = (!(repl instanceof AbstractReplicator)) ? null : (AbstractReplicator) repl;

            if ((replicator != null) && (c4Repl == replicator.getC4Replicator())) { return replicator; }

            Log.w(DOMAIN, "C4ReplicatorListener: c4replicator and replicator don't match: " + c4Repl + " :: " + repl);

            return null;
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////  R E P L I C A T O R   ////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    @NonNull
    final ReplicatorConfiguration config;

    private final Executor dispatcher = CouchbaseLiteInternal.getExecutionService().getSerialExecutor();

    @GuardedBy("lock")
    private final Set<ReplicatorChangeListenerToken> changeListeners = new HashSet<>();
    @GuardedBy("lock")
    private final Set<DocumentReplicationListenerToken> docEndedListeners = new HashSet<>();

    @NonNull
    private final Set<Fn.Consumer<CouchbaseLiteException>> pendingResolutions = new HashSet<>();
    @NonNull
    private final Deque<C4ReplicatorStatus> pendingStatusNotifications = new LinkedList<>();
    @NonNull
    private final C4ReplicatorListener c4ReplListener;
    @NonNull
    private final SocketFactory socketFactory;

    @GuardedBy("lock")
    @NonNull
    private Status status = new Status(ActivityLevel.STOPPED, new Progress(0, 0), null);

    @GuardedBy("lock")
    private C4ReplicationFilter c4ReplPushFilter;
    @GuardedBy("lock")
    private C4ReplicationFilter c4ReplPullFilter;

    @GuardedBy("lock")
    private CouchbaseLiteException lastError;

    private volatile String desc;

    // Server certificates received from the server during the TLS handshake
    private final AtomicReference<List<Certificate>> serverCertificates = new AtomicReference<>();

    /**
     * Initializes a replicator with the given configuration.
     *
     * @param config replicator configuration
     */
    protected AbstractReplicator(@NonNull ReplicatorConfiguration config) {
        Preconditions.assertNotNull(config, "config");
        this.config = config.readonlyCopy();
        this.socketFactory = new SocketFactory(config, getCookieStore(), this::setServerCertificates);
        this.c4ReplListener = new ReplicatorListener(dispatcher);
    }

    /**
     * Start the replicator.
     */
    public void start() { start(false); }

    /**
     * Start the replicator.
     * This method does not wait for the replicator to start.
     * The replicator runs asynchronously and reports its progress
     * through replicator change notifications.
     */
    public void start(boolean resetCheckpoint) {
        Log.i(DOMAIN, "Replicator is starting");

        getDatabase().addActiveReplicator(this);

        final C4Replicator repl = getOrCreateC4Replicator();
        synchronized (getLock()) {
            repl.start(resetCheckpoint);

            C4ReplicatorStatus status = repl.getStatus();
            if (status == null) {
                status = new C4ReplicatorStatus(
                    C4ReplicatorStatus.ActivityLevel.STOPPED,
                    C4Constants.ErrorDomain.LITE_CORE,
                    C4Constants.LiteCoreError.UNEXPECTED_ERROR);
            }

            status = updateStatus(status);

            c4ReplListener.statusChanged(repl, status, this);
        }
    }

    /**
     * Stop a running replicator.
     * This method does not wait for the replicator to stop.
     * When it does actually stop it will a new state, STOPPED, to change listeners.
     */
    public void stop() {
        final C4Replicator c4repl = getC4Replicator();
        Log.i(DOMAIN, "%s: Replicator is stopping (%s)", this, c4repl);
        if (c4repl == null) { return; }
        c4repl.stop();
    }

    /**
     * The replicator's configuration.
     *
     * @return this replicator's configuration
     */
    @NonNull
    public ReplicatorConfiguration getConfig() { return config.readonlyCopy(); }

    /**
     * The replicator's current status: its activity level and progress. Observable.
     *
     * @return this replicator's status
     */
    @NonNull
    public Status getStatus() {
        synchronized (getLock()) { return new Status(status); }
    }

    /**
     * The server certificates received from the server during the TLS handshake.
     *
     * @return this replicator's server certificates.
     */
    @Nullable
    public List<Certificate> getServerCertificates() {
        final List<Certificate> serverCerts = serverCertificates.get();
        return ((serverCerts == null) || serverCerts.isEmpty())
            ? null
            : new ArrayList<>(serverCerts);
    }

    /**
     * Get a best effort list of documents still pending replication.
     *
     * @return a set of ids for documents still awaiting replication.
     */
    @NonNull
    public Set<String> getPendingDocumentIds() throws CouchbaseLiteException {
        if (config.getType().equals(Type.PULL)) {
            throw new CouchbaseLiteException(
                "PullOnlyPendingDocIDs",
                CBLError.Domain.CBLITE,
                CBLError.Code.UNSUPPORTED);
        }

        final Set<String> pending;
        try { pending = getOrCreateC4Replicator().getPendingDocIDs(); }
        catch (LiteCoreException e) {
            throw CouchbaseLiteException.convertException(e, "Failed fetching pending documentIds");
        }

        if (pending == null) { throw new IllegalStateException("Pending doc ids is unexpectedly null"); }

        return Collections.unmodifiableSet(pending);
    }

    /**
     * Best effort check to see if the document whose ID is passed is still pending replication.
     *
     * @param docId Document id
     * @return true if the document is pending
     */
    public boolean isDocumentPending(@NonNull String docId) throws CouchbaseLiteException {
        Preconditions.assertNotNull(docId, "document ID");

        if (config.getType().equals(Type.PULL)) {
            throw new CouchbaseLiteException(
                "PullOnlyPendingDocIDs",
                CBLError.Domain.CBLITE,
                CBLError.Code.UNSUPPORTED);
        }

        try { return getOrCreateC4Replicator().isDocumentPending(docId); }
        catch (LiteCoreException e) {
            throw CouchbaseLiteException.convertException(e, "Failed getting document pending status");
        }
    }

    /**
     * Adds a change listener for the changes in the replication status and progress.
     * <p>
     * The changes will be delivered on the UI thread for the Android platform
     * On other Java platforms, the callback will occur on an arbitrary thread.
     * <p>
     * When developing a Java Desktop application using Swing or JavaFX that needs to update the UI after
     * receiving the changes, make sure to schedule the UI update on the UI thread by using
     * SwingUtilities.invokeLater(Runnable) or Platform.runLater(Runnable) respectively.
     *
     * @param listener callback
     */
    @NonNull
    public ListenerToken addChangeListener(@NonNull ReplicatorChangeListener listener) {
        Preconditions.assertNotNull(listener, "listener");
        return addChangeListener(null, listener);
    }

    /**
     * Adds a change listener for the changes in the replication status and progress with an executor on which
     * the changes will be posted to the listener. If the executor is not specified, the changes will be delivered
     * on the UI thread on Android platform and on an arbitrary thread on other Java platform.
     *
     * @param executor executor on which events will be delivered
     * @param listener callback
     */
    @NonNull
    public ListenerToken addChangeListener(Executor executor, @NonNull ReplicatorChangeListener listener) {
        Preconditions.assertNotNull(listener, "listener");
        synchronized (getLock()) {
            final ReplicatorChangeListenerToken token = new ReplicatorChangeListenerToken(executor, listener);
            changeListeners.add(token);
            setProgressLevel();
            return token;
        }
    }

    /**
     * Adds a listener for receiving the replication status of the specified document. The status will be
     * delivered on the UI thread for the Android platform and on an arbitrary thread for the Java platform.
     * When developing a Java Desktop application using Swing or JavaFX that needs to update the UI after
     * receiving the status, make sure to schedule the UI update on the UI thread by using
     * SwingUtilities.invokeLater(Runnable) or Platform.runLater(Runnable) respectively.
     *
     * @param listener callback
     * @return A ListenerToken that can be used to remove the handler in the future.
     */
    @NonNull
    public ListenerToken addDocumentReplicationListener(@NonNull DocumentReplicationListener listener) {
        Preconditions.assertNotNull(listener, "listener");
        return addDocumentReplicationListener(null, listener);
    }

    /**
     * Adds a listener for receiving the replication status of the specified document with an executor on which
     * the status will be posted to the listener. If the executor is not specified, the status will be delivered
     * on the UI thread for the Android platform and on an arbitrary thread for the Java platform.
     *
     * @param executor executor on which events will be delivered
     * @param listener callback
     */
    @NonNull
    public ListenerToken addDocumentReplicationListener(
        @Nullable Executor executor,
        @NonNull DocumentReplicationListener listener) {
        Preconditions.assertNotNull(listener, "listener");
        synchronized (getLock()) {
            final DocumentReplicationListenerToken token = new DocumentReplicationListenerToken(executor, listener);
            docEndedListeners.add(token);
            setProgressLevel();
            return token;
        }
    }

    /**
     * Remove the given ReplicatorChangeListener or DocumentReplicationListener from the this replicator.
     *
     * @param token returned by a previous call to addChangeListener or addDocumentListener.
     */
    public void removeChangeListener(@NonNull ListenerToken token) {
        Preconditions.assertNotNull(token, "token");
        synchronized (getLock()) {
            if (token instanceof ReplicatorChangeListenerToken) { changeListeners.remove(token); }
            else if (token instanceof DocumentReplicationListenerToken) { docEndedListeners.remove(token); }
            else { throw new IllegalArgumentException("unexpected token: " + token); }
            setProgressLevel();
        }
    }

    @NonNull
    @Override
    public String toString() {
        if (desc == null) { desc = description(); }
        return desc;
    }

    //---------------------------------------------
    // Protected methods
    //---------------------------------------------

    @GuardedBy("lock")
    protected abstract C4Replicator createReplicatorForTarget(Endpoint target) throws LiteCoreException;

    protected abstract void handleOffline(ActivityLevel prevState, boolean nowOnline);

    /**
     * Create and return a c4Replicator targeting the passed URI
     *
     * @param remoteUri a URI for the replication target
     * @return the c4Replicator
     * @throws LiteCoreException on failure to create the replicator
     */
    @GuardedBy("lock")
    @NonNull
    protected final C4Replicator getRemoteC4Replicator(@NonNull URI remoteUri) throws LiteCoreException {
        // Set up the port: core uses 0 for not set
        final int p = remoteUri.getPort();
        final int port = Math.max(0, p);

        // get db name and path
        final Deque<String> splitPath = splitPath(remoteUri.getPath());
        final String dbName = (splitPath.size() <= 0) ? "" : splitPath.removeLast();
        final String path = "/" + StringUtils.join("/", splitPath);

        final boolean continuous = config.isContinuous();

        return getDatabase().createRemoteReplicator(
            (Replicator) this,
            remoteUri.getScheme(),
            remoteUri.getHost(),
            port,
            path,
            dbName,
            makeMode(config.isPush(), continuous),
            makeMode(config.isPull(), continuous),
            getFleeceOptions(),
            c4ReplListener,
            c4ReplPushFilter,
            c4ReplPullFilter,
            socketFactory,
            C4Socket.NO_FRAMING);
    }

    /**
     * Create and return a c4Replicator targeting the passed Database
     *
     * @param otherDb a local database for the replication target
     * @return the c4Replicator
     * @throws LiteCoreException on failure to create the replicator
     */
    @GuardedBy("lock")
    @NonNull
    protected final C4Replicator getLocalC4Replicator(@NonNull Database otherDb) throws LiteCoreException {
        final boolean continuous = config.isContinuous();
        return getDatabase().createLocalReplicator(
            (Replicator) this,
            otherDb,
            makeMode(config.isPush(), continuous),
            makeMode(config.isPull(), continuous),
            getFleeceOptions(),
            c4ReplListener,
            c4ReplPushFilter,
            c4ReplPullFilter);
    }

    /**
     * Create and return a c4Replicator.
     * The socket factory is responsible for setting up the target
     *
     * @param framing the framing mode (C4Socket.XXX_FRAMING)
     * @return the c4Replicator
     * @throws LiteCoreException on failure to create the replicator
     */
    @GuardedBy("lock")
    @NonNull
    protected final C4Replicator getMessageC4Replicator(int framing) throws LiteCoreException {
        final boolean continuous = config.isContinuous();
        return getDatabase().createRemoteReplicator(
            (Replicator) this,
            C4Replicator.MESSAGE_SCHEME,
            null,
            0,
            null,
            null,
            makeMode(config.isPush(), continuous),
            makeMode(config.isPull(), continuous),
            getFleeceOptions(),
            c4ReplListener,
            c4ReplPushFilter,
            c4ReplPullFilter,
            socketFactory,
            framing);
    }

    //---------------------------------------------
    // Package visible methods
    //
    // Some of these are package protected only to avoid a synthetic accessor
    //---------------------------------------------

    @VisibleForTesting
    CouchbaseLiteException getLastError() { return lastError; }

    @NonNull
    ActivityLevel getState() {
        synchronized (getLock()) { return status.getActivityLevel(); }
    }

    void c4StatusChanged(@NonNull C4ReplicatorStatus c4Status) {
        final ReplicatorChange change;
        final List<ReplicatorChangeListenerToken> tokens;
        synchronized (getLock()) {
            Log.i(
                DOMAIN,
                "status changed: (%d, %d) @%s for %s",
                pendingResolutions.size(), pendingStatusNotifications.size(), c4Status, this);

            if (config.isContinuous()) { handleOffline(status.getActivityLevel(), !Status.isOffline(c4Status)); }

            if (!pendingResolutions.isEmpty()) { pendingStatusNotifications.add(c4Status); }
            if (!pendingStatusNotifications.isEmpty()) { return; }

            // Update my properties:
            updateStatus(c4Status);

            // Post notification
            // Replicator.getStatus() creates a copy of Status.
            change = new ReplicatorChange((Replicator) this, this.getStatus());
            tokens = new ArrayList<>(changeListeners);
        }

        // this will probably make this instance eligible for garbage collection...
        if (Status.isStopped(c4Status)) { getDatabase().removeActiveReplicator(this); }

        for (ReplicatorChangeListenerToken token: tokens) { token.notify(change); }
    }

    void documentEnded(boolean pushing, C4DocumentEnded... docEnds) {
        final List<ReplicatedDocument> unconflictedDocs = new ArrayList<>();

        for (C4DocumentEnded docEnd: docEnds) {
            final String docId = docEnd.getDocID();
            final C4Error c4Error = docEnd.getC4Error();

            CouchbaseLiteException error = null;

            if ((c4Error != null) && (c4Error.getCode() != 0)) {
                if (!pushing && docEnd.isConflicted()) {
                    queueConflictResolution(docId, docEnd.getFlags());
                    continue;
                }

                error = CouchbaseLiteException.convertC4Error(c4Error);
            }

            unconflictedDocs.add(new ReplicatedDocument(docId, docEnd.getFlags(), error, docEnd.errorIsTransient()));
        }

        if (!unconflictedDocs.isEmpty()) { notifyDocumentEnded(pushing, unconflictedDocs); }
    }

    // callback from queueConflictResolution
    void onConflictResolved(
        Fn.Consumer<CouchbaseLiteException> task,
        String docId,
        int flags,
        CouchbaseLiteException err) {
        Log.i(DOMAIN, "Conflict resolved: %s", err, docId);
        List<C4ReplicatorStatus> pendingNotifications = null;
        synchronized (getLock()) {
            pendingResolutions.remove(task);
            // if no more resolutions, deliver any outstanding status notifications
            if (pendingResolutions.isEmpty()) {
                pendingNotifications = new ArrayList<>(pendingStatusNotifications);
                pendingStatusNotifications.clear();
            }
        }

        notifyDocumentEnded(false, Arrays.asList(new ReplicatedDocument(docId, flags, err, false)));

        if ((pendingNotifications != null) && (!pendingNotifications.isEmpty())) {
            for (C4ReplicatorStatus status: pendingNotifications) { dispatcher.execute(() -> c4StatusChanged(status)); }
        }
    }

    void notifyDocumentEnded(boolean pushing, List<ReplicatedDocument> docs) {
        final DocumentReplication update = new DocumentReplication((Replicator) this, pushing, docs);
        final List<DocumentReplicationListenerToken> tokens;
        synchronized (getLock()) { tokens = new ArrayList<>(docEndedListeners); }
        for (DocumentReplicationListenerToken token: tokens) { token.notify(update); }
        Log.i(DOMAIN, "notifyDocumentEnded: %s" + update);
    }

    @NonNull
    @VisibleForTesting
    SocketFactory getSocketFactory() { return socketFactory; }

    @VisibleForTesting
    int getListenerCount() {
        synchronized (getLock()) { return changeListeners.size() + docEndedListeners.size(); }
    }

    //---------------------------------------------
    // Private methods
    //---------------------------------------------

    @NonNull
    private C4Replicator getOrCreateC4Replicator() {
        // createReplicatorForTarget is going to seize this lock anyway: force in-order seizure
        synchronized (config.getDatabase().getLock()) {
            C4Replicator c4Repl = getC4Replicator();

            if (c4Repl != null) {
                c4Repl.setOptions(getFleeceOptions());
                // ??? This is probably a bug.  SetOptions should not clear the progress level
                setProgressLevel();
                return c4Repl;
            }

            setupFilters();
            try {
                c4Repl = createReplicatorForTarget(config.getTarget());
                synchronized (getLock()) {
                    setC4Replicator(c4Repl);
                    setProgressLevel();
                }
                return c4Repl;
            }
            catch (LiteCoreException e) {
                throw new IllegalStateException(
                    "Could not create replicator",
                    CouchbaseLiteException.convertException(e));
            }
        }
    }

    @GuardedBy("lock")
    private C4ReplicatorStatus updateStatus(@NonNull C4ReplicatorStatus c4Status) {
        final Status oldStatus = status;
        status = new Status(c4Status);

        if (c4Status.getErrorCode() != 0) { lastError = status.error; }

        Log.i(DOMAIN, "State changed %s => %s(%d/%d): %s for %s",
            oldStatus.activityLevel,
            status.activityLevel,
            c4Status.getProgressUnitsCompleted(),
            c4Status.getProgressUnitsTotal(),
            status.error,
            this);

        return c4Status.copy();
    }

    private void queueConflictResolution(@NonNull String docId, int flags) {
        Log.i(DOMAIN, "%s: pulled conflicting version of '%s'", this, docId);

        final ExecutionService.CloseableExecutor executor
            = CouchbaseLiteInternal.getExecutionService().getConcurrentExecutor();
        final Database db = getDatabase();
        final ConflictResolver resolver = config.getConflictResolver();
        final Fn.Consumer<CouchbaseLiteException> task = new Fn.Consumer<CouchbaseLiteException>() {
            public void accept(CouchbaseLiteException err) { onConflictResolved(this, docId, flags, err); }
        };

        synchronized (getLock()) {
            executor.execute(() -> db.resolveReplicationConflict(resolver, docId, task));
            pendingResolutions.add(task);
        }
    }

    private byte[] getFleeceOptions() {
        final Map<String, Object> options = config.effectiveOptions();

        byte[] optionsFleece = null;
        if (!options.isEmpty()) {
            try (FLEncoder enc = FLEncoder.getManagedEncoder()) {
                enc.write(options);
                optionsFleece = enc.finish();
            }
            catch (LiteCoreException e) { Log.w(DOMAIN, "Failed encoding replicator options", e); }
        }

        return optionsFleece;
    }

    private void setupFilters() {
        synchronized (getLock()) {
            if (config.getPushFilter() != null) {
                c4ReplPushFilter = (docID, revId, flags, dict, isPush, repl) ->
                    repl.filterDocument(docID, revId, getDocumentFlags(flags), dict, isPush);
            }

            if (config.getPullFilter() != null) {
                c4ReplPullFilter = (docID, revId, flags, dict, isPush, repl) ->
                    repl.filterDocument(docID, revId, getDocumentFlags(flags), dict, isPush);
            }
        }
    }

    private int makeMode(boolean active, boolean continuous) {
        final C4ReplicatorMode mode = (!active)
            ? C4ReplicatorMode.C4_DISABLED
            : ((continuous) ? C4ReplicatorMode.C4_CONTINUOUS : C4ReplicatorMode.C4_ONE_SHOT);
        return mode.getVal();
    }

    private EnumSet<DocumentFlag> getDocumentFlags(int flags) {
        final EnumSet<DocumentFlag> documentFlags = EnumSet.noneOf(DocumentFlag.class);
        if ((flags & C4Constants.RevisionFlags.DELETED) == C4Constants.RevisionFlags.DELETED) {
            documentFlags.add(DocumentFlag.DocumentFlagsDeleted);
        }
        if ((flags & C4Constants.RevisionFlags.PURGED) == C4Constants.RevisionFlags.PURGED) {
            documentFlags.add(DocumentFlag.DocumentFlagsAccessRemoved);
        }
        return documentFlags;
    }

    private boolean filterDocument(
        String docId,
        String revId,
        EnumSet<DocumentFlag> flags,
        long dict,
        boolean isPush) {
        final ReplicationFilter filter = (isPush) ? config.getPushFilter() : config.getPullFilter();
        return (filter != null) && filter.filtered(new Document(getDatabase(), docId, revId, new FLDict(dict)), flags);
    }

    @GuardedBy("getLock()")
    private void setProgressLevel() {
        final C4Replicator c4Repl = getC4Replicator();
        if (c4Repl == null) { return; }

        try {
            c4Repl.setProgressLevel(
                docEndedListeners.isEmpty()
                    ? C4Replicator.PROGRESS_OVERALL
                    : C4Replicator.PROGRESS_PER_DOC);
        }
        catch (LiteCoreException e) {
            Log.w(LogDomain.REPLICATOR, "failed setting progress level");
        }
    }

    private Database getDatabase() { return config.getDatabase(); }

    // Decompose a path into its elements.
    private Deque<String> splitPath(String fullPath) {
        final Deque<String> path = new ArrayDeque<>();
        for (String element: fullPath.split("/")) {
            if (element.length() > 0) { path.addLast(element); }
        }
        return path;
    }

    // CookieStore:
    @NonNull
    private CBLCookieStore getCookieStore() {
        return new CBLCookieStore() {
            @Override
            public void setCookie(@NonNull URI uri, @NonNull String header) { getDatabase().setCookie(uri, header); }

            @Nullable
            @Override
            public String getCookies(@NonNull URI uri) {
                final Database db = getDatabase();
                return (!db.isOpen()) ? null : db.getCookies(uri);
            }
        };
    }

    // Consumer callback to set the server certificates received during the TLS Handshake
    private void setServerCertificates(List<Certificate> certificates) { serverCertificates.set(certificates); }

    private String description() { return baseDesc() + "," + getDatabase() + " => " + config.getTarget() + "}"; }

    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private String simpleDesc() { return baseDesc() + "}"; }

    private String baseDesc() {
        return "Replicator{" + ClassUtils.objId(this) + "("
            + (config.isPull() ? "<" : "")
            + (config.isContinuous() ? "*" : "-")
            + (config.isPush() ? ">" : "")
            + ")";
    }
}
