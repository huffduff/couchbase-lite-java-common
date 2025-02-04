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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.couchbase.lite.AbstractIndex;
import com.couchbase.lite.AbstractReplicator;
import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.MaintenanceType;
import com.couchbase.lite.internal.SocketFactory;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLSharedKeys;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.sockets.MessageFraming;
import com.couchbase.lite.internal.utils.Preconditions;


@SuppressWarnings({
    "PMD.TooManyMethods",
    "PMD.ExcessivePublicCount",
    "PMD.ExcessiveParameterList",
    "PMD.CyclomaticComplexity"})
public abstract class C4Database extends C4NativePeer {

    @VisibleForTesting
    public static final String DB_EXTENSION = ".cblite2";

    // unmanaged: the native code will free it
    static final class UnmanagedC4Database extends C4Database {
        UnmanagedC4Database(long peer) { super(peer); }

        @Override
        public void close() { releasePeer(); }
    }

    // managed: Java code is responsible for freeing it
    static final class ManagedC4Database extends C4Database {
        ManagedC4Database(long peer) { super(peer); }

        @Override
        public void close() { closePeer(null); }

        @SuppressWarnings("NoFinalizer")
        @Override
        protected void finalize() throws Throwable {
            try { closePeer(LogDomain.DATABASE); }
            finally { super.finalize(); }
        }

        private void closePeer(@Nullable LogDomain domain) { releasePeer(domain, C4Database::free); }
    }

    // These enum values must match the ones in DataFile::MaintenanceType
    @NonNull
    private static final Map<MaintenanceType, Integer> MAINTENANCE_TYPE_MAP;

    static {
        final Map<MaintenanceType, Integer> m = new HashMap<>();
        m.put(MaintenanceType.COMPACT, 0);
        m.put(MaintenanceType.REINDEX, 1);
        m.put(MaintenanceType.INTEGRITY_CHECK, 2);
        m.put(MaintenanceType.OPTIMIZE, 3);
        m.put(MaintenanceType.FULL_OPTIMIZE, 4);
        MAINTENANCE_TYPE_MAP = Collections.unmodifiableMap(m);
    }
    public static void copyDb(
        @NonNull String sourcePath,
        @NonNull String parentDir,
        @NonNull String name,
        int flags,
        int algorithm,
        @Nullable byte[] encryptionKey)
        throws LiteCoreException {
        if (sourcePath.charAt(sourcePath.length() - 1) != File.separatorChar) { sourcePath += File.separator; }

        if (parentDir.charAt(parentDir.length() - 1) != File.separatorChar) { parentDir += File.separator; }

        copy(sourcePath, parentDir, name, flags, algorithm, encryptionKey);
    }

    // This will throw domain = 0, code = 0 if called for a non-existent name/dir pair
    public static void deleteNamedDb(@NonNull String directory, @NonNull String name) throws LiteCoreException {
        deleteNamed(name, directory);
    }

    @NonNull
    public static File getDatabaseFile(@NonNull File directory, @NonNull String name) {
        return new File(directory, name + DB_EXTENSION);
    }

    static void rawFreeDocument(long rawDoc) throws LiteCoreException { rawFree(rawDoc); }


    //-------------------------------------------------------------------------
    // Factory Methods
    //-------------------------------------------------------------------------

    // unmanaged: someone else owns it
    @NonNull
    public static C4Database getUnmanagedDatabase(long peer) { return new UnmanagedC4Database(peer); }

    // managed: Java code is responsible for freeing it
    @NonNull
    public static C4Database getDatabase(
        @NonNull String parentDirPath,
        @NonNull String name,
        int flags,
        int algorithm,
        @Nullable byte[] encryptionKey)
        throws LiteCoreException {

        // Stupid LiteCore will throw a total hissy fit if we pass
        // it something that it decides isn't a directory.
        boolean pathOk = false;
        try {
            final File parentDir = new File(parentDirPath);
            parentDirPath = parentDir.getCanonicalPath();
            pathOk = (parentDir.exists()) && (parentDir.isDirectory());
        }
        catch (IOException ignore) { }

        if (!pathOk) {
            throw new LiteCoreException(
                C4Constants.ErrorDomain.LITE_CORE,
                C4Constants.LiteCoreError.WRONG_FORMAT,
                "Parent directory does not exist or is not a directory: " + parentDirPath);
        }

        return new ManagedC4Database(open(
            parentDirPath,
            name,
            flags,
            algorithm,
            encryptionKey));
    }


    //-------------------------------------------------------------------------
    // Fields
    //-------------------------------------------------------------------------

    final AtomicReference<File> dbFile = new AtomicReference<>();

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    protected C4Database(long peer) { super(peer); }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    // The meaning of "close" changes at this level.
    // C4Database is AutoCloseable: this call frees it.
    // Database is not AutoCloseable.  In it, "close" means close the database.
    @Override
    public abstract void close();

    // this is the full name of the database directory, e.g., /foo/bar.cblite
    @Nullable
    public String getDbPath() {
        final File file = getDbFile();
        return (file == null) ? null : file.getPath() + File.separator;
    }

    @Nullable
    public String getDbDirectory() {
        final File file = getDbFile();
        return (file == null) ? null : file.getParent();
    }

    @Nullable
    public String getDbFileName() {
        final File file = getDbFile();
        return (file == null) ? null : file.getName();
    }

    @Nullable
    public String getDbName() {
        String dbFileName = getDbFileName();
        if (dbFileName == null) { return null; }

        if (dbFileName.endsWith(DB_EXTENSION)) {
            dbFileName = dbFileName.substring(0, dbFileName.length() - DB_EXTENSION.length());
        }

        return dbFileName;
    }

    public void rekey(int keyType, byte[] newKey) throws LiteCoreException { rekey(getPeer(), keyType, newKey); }

    // - Lifecycle

    // This is subtle
    // The call to close() will fail horribly if the db is currently in a transaction.
    // On the other hand, the call to close(peer) will throw an exception if the db is in a transaction.
    // That means that close() will never be called and the failure will be reported normally.
    // The finalizer will backstop this rare case, so that the Database doesn't leak.
    public void closeDb() throws LiteCoreException {
        close(getPeer());
        close();
    }

    // This is subtle: see above.
    public void deleteDb() throws LiteCoreException {
        delete(getPeer());
        close();
    }

    // - Accessors

    public long getDocumentCount() { return getDocumentCount(getPeer()); }

    public void purgeDoc(String docID) throws LiteCoreException { purgeDoc(getPeer(), docID); }

    @NonNull
    public byte[] getPublicUUID() throws LiteCoreException { return getPublicUUID(getPeer()); }

    // - Transactions

    public void beginTransaction() throws LiteCoreException { beginTransaction(getPeer()); }

    public void endTransaction(boolean commit) throws LiteCoreException { endTransaction(getPeer(), commit); }

    // c4Document+Fleece.h

    // - Fleece-related
    // This must be called holding both the document and the database locks!
    @NonNull
    public FLEncoder getSharedFleeceEncoder() {
        return FLEncoder.getUnmanagedEncoder(getSharedFleeceEncoder(getPeer()));
    }

    ////////////////////////////////
    // C4Document
    ////////////////////////////////

    @NonNull
    public C4Document get(@NonNull String docID) throws LiteCoreException {
        return new C4Document(getPeer(), docID, true);
    }

    // - Purging and Expiration

    public void setExpiration(@NonNull String docID, long timestamp) throws LiteCoreException {
        C4Document.setExpiration(getPeer(), docID, timestamp);
    }

    public long getExpiration(@NonNull String docID) throws LiteCoreException {
        return C4Document.getExpiration(getPeer(), docID);
    }

    @NonNull
    public C4Document create(@NonNull String docID, @Nullable FLSliceResult body, int flags) throws LiteCoreException {
        return new C4Document(C4Document.create2(getPeer(), docID, body != null ? body.getHandle() : 0, flags));
    }

    ////////////////////////////////////////////////////////////////
    // C4DatabaseObserver/C4DocumentObserver
    ////////////////////////////////////////////////////////////////

    @NonNull
    public C4DatabaseObserver createDatabaseObserver(
        @NonNull Object context,
        @NonNull C4DatabaseObserverListener listener) {
        return C4DatabaseObserver.newObserver(getPeer(), listener, context);
    }

    @NonNull
    public C4DocumentObserver createDocumentObserver(
        @NonNull String docID,
        @NonNull Object context,
        @NonNull C4DocumentObserverListener listener) {
        return C4DocumentObserver.newObserver(getPeer(), docID, listener, context);
    }

    ////////////////////////////////
    // C4BlobStore
    ////////////////////////////////

    @NonNull
    public C4BlobStore getBlobStore() throws LiteCoreException { return C4BlobStore.getUnmanagedBlobStore(getPeer()); }

    ////////////////////////////////
    // C4Query
    ////////////////////////////////

    @NonNull
    public C4Query createJsonQuery(@NonNull String expression) throws LiteCoreException {
        return new C4Query(getPeer(), AbstractIndex.QueryLanguage.JSON, expression);
    }

    @NonNull
    public C4Query createN1qlQuery(@NonNull String expression) throws LiteCoreException {
        return new C4Query(getPeer(), AbstractIndex.QueryLanguage.N1QL, expression);
    }

    public void createIndex(
        @NonNull String name,
        @NonNull String queryExpression,
        @NonNull AbstractIndex.QueryLanguage queryLanguage,
        @NonNull AbstractIndex.IndexType indexType,
        @Nullable String language,
        boolean ignoreDiacritics)
        throws LiteCoreException {
        C4Query.createIndex(this, name, queryExpression, queryLanguage, indexType, language, ignoreDiacritics);
    }

    public void deleteIndex(String name) throws LiteCoreException { C4Query.deleteIndex(this, name); }

    @NonNull
    public FLValue getIndexesInfo() throws LiteCoreException { return C4Query.getIndexInfo(this); }

    public boolean performMaintenance(MaintenanceType type) throws LiteCoreException {
        return maintenance(
            getPeer(),
            Preconditions.assertNotNull(MAINTENANCE_TYPE_MAP.get(type), "Unrecognized maintenance type: " + type));
    }

    ////////////////////////////////
    // C4Replicator
    ////////////////////////////////

    @SuppressWarnings("CheckFunctionalParameters")
    @NonNull
    public C4Replicator createRemoteReplicator(
        @Nullable String scheme,
        @Nullable String host,
        int port,
        @Nullable String path,
        @Nullable String remoteDatabaseName,
        int push,
        int pull,
        @Nullable byte[] options,
        @Nullable C4ReplicatorListener listener,
        @Nullable C4ReplicationFilter pushFilter,
        @Nullable C4ReplicationFilter pullFilter,
        @NonNull AbstractReplicator replicatorContext,
        @Nullable SocketFactory socketFactoryContext,
        @NonNull MessageFraming framing)
        throws LiteCoreException {
        return C4Replicator.createRemoteReplicator(
            getPeer(),
            scheme,
            host,
            port,
            path,
            remoteDatabaseName,
            push,
            pull,
            options,
            listener,
            pushFilter,
            pullFilter,
            replicatorContext,
            socketFactoryContext,
            framing);
    }

    @SuppressWarnings("CheckFunctionalParameters")
    @NonNull
    public C4Replicator createLocalReplicator(
        @NonNull C4Database otherLocalDB,
        int push,
        int pull,
        @Nullable byte[] options,
        @Nullable C4ReplicatorListener listener,
        @Nullable C4ReplicationFilter pushFilter,
        @Nullable C4ReplicationFilter pullFilter,
        @NonNull AbstractReplicator replicatorContext)
        throws LiteCoreException {
        return C4Replicator.createLocalReplicator(
            getPeer(),
            otherLocalDB,
            push,
            pull,
            options,
            listener,
            pushFilter,
            pullFilter,
            replicatorContext);
    }

    @NonNull
    public C4Replicator createTargetReplicator(
        @NonNull C4Socket c4Socket,
        int push,
        int pull,
        @Nullable byte[] options,
        @Nullable C4ReplicatorListener listener,
        @NonNull Object replicatorContext)
        throws LiteCoreException {
        return C4Replicator.createTargetReplicator(
            getPeer(),
            c4Socket,
            push,
            pull,
            options,
            listener,
            replicatorContext);
    }

    ////////////////////////////////
    // Cookie Store
    ////////////////////////////////

    public void setCookie(@NonNull URI uri, @NonNull String setCookieHeader) throws LiteCoreException {
        setCookie(getPeer(), uri.toString(), setCookieHeader);
    }

    @Nullable
    public String getCookies(@NonNull URI uri) throws LiteCoreException {
        return getCookies(getPeer(), uri.toString());
    }

    @VisibleForTesting
    @NonNull
    public C4Document get(@NonNull String docID, boolean mustExist) throws LiteCoreException {
        return new C4Document(getPeer(), docID, mustExist);
    }

    //-------------------------------------------------------------------------
    // package access
    //-------------------------------------------------------------------------

    // !!!  Exposes the peer handle
    long getHandle() { return getPeer(); }

    @NonNull
    FLSharedKeys getFLSharedKeys() { return new FLSharedKeys(getFLSharedKeys(getPeer())); }

    @VisibleForTesting
    @NonNull
    C4Document create(@NonNull String docID, @NonNull byte[] body, int revisionFlags) throws LiteCoreException {
        return new C4Document(C4Document.create(getPeer(), docID, body, revisionFlags));
    }

    @VisibleForTesting
    void compact() throws LiteCoreException { maintenance(getPeer(), 0); }

    @VisibleForTesting
    long getLastSequence() { return getLastSequence(getPeer()); }

    @NonNull
    @VisibleForTesting
    byte[] getPrivateUUID() throws LiteCoreException { return getPrivateUUID(getPeer()); }

    @VisibleForTesting
    @NonNull
    FLSliceResult encodeJSON(@NonNull String data) throws LiteCoreException {
        return FLSliceResult.getManagedSliceResult(encodeJSON(getPeer(), data.getBytes(StandardCharsets.UTF_8)));
    }

    @VisibleForTesting
    @NonNull
    C4Document getBySequence(long sequence) throws LiteCoreException { return new C4Document(getPeer(), sequence); }

    @VisibleForTesting
    @NonNull
    public C4Document put(
        @NonNull byte[] body,
        @NonNull String docID,
        int revFlags,
        boolean existingRevision,
        boolean allowConflict,
        @NonNull String[] history,
        boolean save,
        int maxRevTreeDepth,
        int remoteDBID)
        throws LiteCoreException {
        return new C4Document(C4Document.put(
            getPeer(),
            body,
            docID,
            revFlags,
            existingRevision,
            allowConflict,
            history,
            save,
            maxRevTreeDepth,
            remoteDBID));
    }

    @NonNull
    @VisibleForTesting
    C4Document put(
        @NonNull FLSliceResult body, // C4Slice*
        @NonNull String docID,
        int revFlags,
        boolean existingRevision,
        boolean allowConflict,
        @NonNull String[] history,
        boolean save,
        int maxRevTreeDepth,
        int remoteDBID)
        throws LiteCoreException {
        return new C4Document(C4Document.put2(
            getPeer(),
            body.getHandle(),
            docID,
            revFlags,
            existingRevision,
            allowConflict,
            history,
            save,
            maxRevTreeDepth,
            remoteDBID));
    }

    @VisibleForTesting
    void rawPut(String storeName, String key, String meta, byte[] body) throws LiteCoreException {
        rawPut(getPeer(), storeName, key, meta, body);
    }

    @VisibleForTesting
    @NonNull
    C4RawDocument rawGet(@NonNull String storeName, @NonNull String docID) throws LiteCoreException {
        return new C4RawDocument(rawGet(getPeer(), storeName, docID));
    }

    //-------------------------------------------------------------------------
    // Private methods
    //-------------------------------------------------------------------------

    @Nullable
    private File getDbFile() {
        final File file = dbFile.get();
        if (file != null) { return file; }

        final String path = getPath(getPeer());
        if (path == null) { return null; }

        try { dbFile.compareAndSet(null, new File(path).getCanonicalFile()); }
        catch (IOException ignore) { }

        return dbFile.get();
    }

    //-------------------------------------------------------------------------
    // Native methods
    //-------------------------------------------------------------------------

    // - Lifecycle
    static native long open(
        @NonNull String parentDir,
        @NonNull String name,
        int flags,
        int algorithm,
        byte[] encryptionKey)
        throws LiteCoreException;

    static native void free(long db);

    private static native long rawGet(long db, String storeName, String docID) throws LiteCoreException;

    private static native void rawPut(
        long db,
        String storeName,
        String key,
        String meta,
        byte[] body)
        throws LiteCoreException;

    private static native void copy(
        String sourcePath,
        String parentDir,
        String name,
        int flags,
        int algorithm,
        byte[] encryptionKey)
        throws LiteCoreException;

    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private static native void close(long db) throws LiteCoreException;

    private static native void delete(long db) throws LiteCoreException;

    private static native void deleteNamed(@NonNull String name, @NonNull String dir) throws LiteCoreException;

    private static native void rekey(long db, int keyType, byte[] newKey) throws LiteCoreException;

    // - Accessors

    @Nullable
    private static native String getPath(long db);

    private static native long getDocumentCount(long db);

    private static native long getLastSequence(long db);

    private static native void purgeDoc(long db, String id) throws LiteCoreException;

    @NonNull
    private static native byte[] getPublicUUID(long db) throws LiteCoreException;

    @NonNull
    private static native byte[] getPrivateUUID(long db) throws LiteCoreException;

    // - Transactions

    private static native void beginTransaction(long db) throws LiteCoreException;

    private static native void endTransaction(long db, boolean commit) throws LiteCoreException;

    // - Raw Documents (i.e. info or _local)

    private static native void rawFree(long rawDoc) throws LiteCoreException;

    // - Cookie Store

    private static native void setCookie(long db, String url, String setCookieHeader) throws LiteCoreException;

    @NonNull
    private static native String getCookies(long db, @NonNull String url) throws LiteCoreException;

    ////////////////////////////////
    // c4Document+Fleece.h
    ////////////////////////////////

    // - Fleece-related

    private static native long getSharedFleeceEncoder(long db);

    private static native long encodeJSON(long db, byte[] jsonData) throws LiteCoreException;

    private static native long getFLSharedKeys(long db);

    private static native boolean maintenance(long db, int type) throws LiteCoreException;
}
