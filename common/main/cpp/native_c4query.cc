//
// native_c4query.cc
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
#include <c4.h>
#include <c4Base.h>
#include "com_couchbase_lite_internal_core_C4Query.h"
#include "native_glue.hh"

using namespace litecore;
using namespace litecore::jni;

extern "C" {
// ----------------------------------------------------------------------------
// com_couchbase_lite_internal_core_C4Query
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_lite_internal_core_C4Query
 * Method:    init
 * Signature: (JLjava/lang/String;)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_C4Query_createQuery(
        JNIEnv *env,
        jclass ignore,
        jlong db,
        jint lang,
        jstring jexpr) {
    jstringSlice expr(env, jexpr);
    int errorLoc = -1;
    C4Error error = {};

    C4Query *query = c4query_new2(
            (C4Database *) db,
            (C4QueryLanguage) lang,
            expr,
            &errorLoc,
            &error);

    // !!! Should put the error location into the exception message.
    if (!query) {
        throwError(env, error);
        return 0;
    }

    return (jlong) query;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Query
 * Method:    setParameters
 * Signature: (JJ)V;
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_C4Query_setParameters(
        JNIEnv *env,
        jclass ignore,
        jlong jquery,
        jlong jparameters) {
    auto params = (FLSliceResult *) jparameters;
    C4String s = {params->buf, params->size};
    c4query_setParameters((C4Query *) jquery, s);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Query
 * Method:    explain
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_lite_internal_core_C4Query_explain(JNIEnv *env, jclass ignore, jlong jquery) {
    C4StringResult result = c4query_explain((C4Query *) jquery);
    jstring jstr = toJString(env, result);
    c4slice_free(result);
    return jstr;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Query
 * Method:    run
 * Signature: (JZJ)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_C4Query_run(
        JNIEnv *env,
        jclass ignore,
        jlong jquery,
        jboolean jrankFullText,
        jlong jparameters) {
    C4QueryOptions options = {(bool) jrankFullText};
    auto params = (FLSliceResult *) jparameters;
    C4Error error = {};
    C4Slice s = {params->buf, params->size};
    C4QueryEnumerator *e = c4query_run((C4Query *) jquery, &options, s, &error);
    if (!e) {
        throwError(env, error);
        return 0;
    }
    return (jlong) e;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Query
 * Method:    columnCount
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL
Java_com_couchbase_lite_internal_core_C4Query_columnCount(JNIEnv *env, jclass ignore, jlong jquery) {
    return c4query_columnCount((C4Query *) jquery);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Query
 * Method:    columnName
 * Signature: (JI)Ljava/lang/String;
 *
 * ??? Check this to see how expensive it is...
 * Might want to replace it with a function that creates
 * the entire map of column names to indices in one fell swoop...
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_lite_internal_core_C4Query_columnName(JNIEnv *env, jclass ignore, jlong jquery, jint colIdx) {
    return toJString(env, c4query_columnTitle((C4Query *) jquery, colIdx));
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Query
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_C4Query_free(JNIEnv *env, jclass ignore, jlong jquery) {
    c4query_release((C4Query *) jquery);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Query
 * Method:    createIndex
 * Signature: (JLjava/lang/String;Ljava/lang/String;ILjava/lang/String;Z)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_core_C4Query_createIndex(
        JNIEnv *env,
        jclass ignore,
        jlong db,
        jstring jname,
        jstring jqueryExpressions,
        jint queryLanguage,
        jint indexType,
        jstring jlanguage,
        jboolean ignoreDiacritics) {
    jstringSlice name(env, jname);
    jstringSlice queryExpressions(env, jqueryExpressions);
    jstringSlice language(env, jlanguage);

    C4IndexOptions options = {};
    options.language = language.c_str();
    options.ignoreDiacritics = (bool) ignoreDiacritics;

    C4Error error = {};
    bool res = c4db_createIndex2(
            (C4Database *) db,
            name,
            (C4Slice) queryExpressions,
            (C4QueryLanguage) queryLanguage,
            (C4IndexType) indexType,
            &options,
            &error);
    if (!res) {
        throwError(env, error);
        return false;
    }

    return (jboolean) res;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Query
 * Method:    getIndexesInfo
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_C4Query_getIndexInfo(JNIEnv *env, jclass ignore, jlong jdb) {
    C4SliceResult data = c4db_getIndexesInfo((C4Database *) jdb, nullptr);
    return (jlong) FLValue_FromData({data.buf, data.size}, kFLTrusted);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Query
 * Method:    deleteIndex
 * Signature: (JLjava/lang/String;)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_C4Query_deleteIndex(
        JNIEnv *env,
        jclass ignore,
        jlong jdb,
        jstring jname) {
    jstringSlice name(env, jname);
    C4Error error = {};
    bool res = c4db_deleteIndex((C4Database *) jdb, name, &error);
    if (!res)
        throwError(env, error);
}
}