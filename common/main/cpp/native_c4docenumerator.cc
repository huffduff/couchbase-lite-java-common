//
// native_c4docenumerator.cc
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
#include <errno.h>
#include "com_couchbase_lite_internal_core_C4DocEnumerator.h"
#include "c4DocEnumerator.h"
#include "native_glue.hh"

using namespace litecore;
using namespace litecore::jni;

extern "C" {

// ----------------------------------------------------------------------------
// com_couchbase_lite_internal_core_C4DocEnumerator
//
// THIS CODE FOR TESTING ONLY
// Unfortunately, the build system depends on having all JNI code in the main 
// source tree.  Moving this class to the test tree would require major changes
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_lite_internal_core_C4DocEnumerator
 * Method:    enumerateChanges
 * Signature: (JJI)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_C4DocEnumerator_enumerateChanges(
        JNIEnv *env,
        jclass ignore,
        jlong jdb,
        jlong since,
        jint jflags) {
    const C4EnumeratorOptions options = {C4EnumeratorFlags(jflags)};
    C4Error error;
    C4DocEnumerator *e = c4db_enumerateChanges((C4Database *) jdb, (uint16_t) since, &options, &error);
    if (!e) {
        throwError(env, error);
        return 0;
    }
    return (jlong) e;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4DocEnumerator
 * Method:    enumerateAllDocs
 * Signature: (JI)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_C4DocEnumerator_enumerateAllDocs(
        JNIEnv *env,
        jclass ignore,
        jlong jdb,
        jint jflags) {
    const C4EnumeratorOptions options = {C4EnumeratorFlags(jflags)};
    C4Error error;
    C4DocEnumerator *e = c4db_enumerateAllDocs((C4Database *) jdb, &options, &error);
    if (!e) {
        throwError(env, error);
        return 0;
    }
    return (jlong) e;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4DocEnumerator
 * Method:    next
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_core_C4DocEnumerator_next(JNIEnv *env, jclass ignore, jlong handle) {
    C4Error error = {};
    bool res = c4enum_next((C4DocEnumerator *) handle, &error);
    if (!res && error.code != 0) {
        throwError(env, error);
        return false;
    }
    return (jboolean) res;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4DocEnumerator
 * Method:    getDocument
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_C4DocEnumerator_getDocument(JNIEnv *env, jclass ignore, jlong handle) {
    C4Error error = {};
    C4Document *doc = c4enum_getDocument((C4DocEnumerator *) handle, &error);
    if (!doc) {
        throwError(env, error);
        return 0;
    }
    return (jlong) doc;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4DocEnumerator
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_C4DocEnumerator_free(JNIEnv *env, jclass ignore, jlong handle) {
    c4enum_free((C4DocEnumerator *) handle);
}
}
