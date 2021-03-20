//
// native_flencoder.cc
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
#include "native_glue.hh"
#include "com_couchbase_lite_internal_fleece_FLEncoder.h"

#pragma ide diagnostic ignored "UnusedLocalVariable"

using namespace litecore;
using namespace litecore::jni;

extern "C" {
// ----------------------------------------------------------------------------
// FLEncoder
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    init
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_fleece_FLEncoder_newFleeceEncoder(JNIEnv *env, jclass ignore) {
    return (jlong) FLEncoder_New();
}

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_fleece_FLEncoder_free(JNIEnv *env, jclass ignore, jlong jenc) {
    FLEncoder_Free((FLEncoder) jenc);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    writeNull
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_fleece_FLEncoder_writeNull(JNIEnv *env, jclass ignore, jlong jenc) {
    return (jboolean) FLEncoder_WriteNull((FLEncoder) jenc);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    writeBool
 * Signature: (JZ)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_fleece_FLEncoder_writeBool(
        JNIEnv *env,
        jclass ignore,
        jlong jenc,
        jboolean jvalue) {
    return (jboolean) FLEncoder_WriteBool((FLEncoder) jenc, (bool) jvalue);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    writeInt
 * Signature: (JJ)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_fleece_FLEncoder_writeInt(
        JNIEnv *env,
        jclass ignore,
        jlong jenc,
        jlong jvalue) {
    return (jboolean) FLEncoder_WriteInt((FLEncoder) jenc, (int64_t) jvalue);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    writeFloat
 * Signature: (JF)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_fleece_FLEncoder_writeFloat(
        JNIEnv *env,
        jclass ignore,
        jlong jenc,
        jfloat jvalue) {
    return (jboolean) FLEncoder_WriteFloat((FLEncoder) jenc, (float) jvalue);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    writeDouble
 * Signature: (JD)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_fleece_FLEncoder_writeDouble(
        JNIEnv *env,
        jclass ignore,
        jlong jenc,
        jdouble jvalue) {
    return (jboolean) FLEncoder_WriteDouble((FLEncoder) jenc, (double) jvalue);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    writeString
 * Signature: (JLjava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_fleece_FLEncoder_writeString(
        JNIEnv *env,
        jclass ignore,
        jlong jenc,
        jstring jvalue) {
    jstringSlice value(env, jvalue);
    return (jboolean) FLEncoder_WriteString((FLEncoder) jenc, value);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    writeData
 * Signature: (J[B)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_fleece_FLEncoder_writeData(
        JNIEnv *env,
        jclass ignore,
        jlong jenc,
        jbyteArray jvalue) {
    jbyteArraySlice value(env, jvalue, true);
    return (jboolean) FLEncoder_WriteData((FLEncoder) jenc, value);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    writeValue
 * Signature: (JJ)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_fleece_FLEncoder_writeValue(
        JNIEnv *env,
        jclass ignore,
        jlong jenc,
        jlong jvalue) {
    return (jboolean) FLEncoder_WriteValue((FLEncoder) jenc, (FLValue) jvalue);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    beginArray
 * Signature: (JJ)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_fleece_FLEncoder_beginArray(
        JNIEnv *env,
        jclass ignore,
        jlong jenc,
        jlong jreserve) {
    return (jboolean) FLEncoder_BeginArray((FLEncoder) jenc, (size_t) jreserve);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    endArray
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_fleece_FLEncoder_endArray(JNIEnv *env, jclass ignore, jlong jenc) {
    return (jboolean) FLEncoder_EndArray((FLEncoder) jenc);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    beginDict
 * Signature: (JJ)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_fleece_FLEncoder_beginDict(
        JNIEnv *env,
        jclass ignore,
        jlong jenc,
        jlong jreserve) {
    return (jboolean) FLEncoder_BeginDict((FLEncoder) jenc, (size_t) jreserve);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    endDict
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_fleece_FLEncoder_endDict(JNIEnv *env, jclass ignore, jlong jenc) {
    return (jboolean) FLEncoder_EndDict((FLEncoder) jenc);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    writeKey
 * Signature: (J[B)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_couchbase_lite_internal_fleece_FLEncoder_writeKey(
        JNIEnv *env,
        jclass ignore,
        jlong jenc,
        jstring jkey) {
    if (jkey == nullptr)
        return false;
    jstringSlice key(env, jkey);
    return (jboolean) FLEncoder_WriteKey((FLEncoder) jenc, key);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    finish
 * Signature: (J)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_com_couchbase_lite_internal_fleece_FLEncoder_finish(JNIEnv *env, jclass ignore, jlong jenc) {
    FLError error = kFLNoError;
    FLSliceResult result = FLEncoder_Finish((FLEncoder) jenc, &error);
    if (error != kFLNoError)
        throwError(env, {FleeceDomain, error});
    jbyteArray res = toJByteArray(env, (C4Slice) result);
    FLSliceResult_Release(result);
    return res;
}

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    finish2
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_fleece_FLEncoder_finish2(JNIEnv *env, jclass ignore, jlong jenc) {
    FLError error = kFLNoError;
    FLSliceResult res = FLEncoder_Finish((FLEncoder) jenc, &error);
    if (error != kFLNoError)
        throwError(env, {FleeceDomain, error});
    auto *sliceResult = (C4SliceResult *) ::malloc(sizeof(C4SliceResult));
    sliceResult->buf = res.buf;
    sliceResult->size = res.size;
    return (jlong) sliceResult;
}

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    reset
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_fleece_FLEncoder_reset(JNIEnv *env, jclass ignore, jlong jenc) {
    FLEncoder_Reset((FLEncoder) jenc);
}

// ----------------------------------------------------------------------------
// JsonEncoder
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    init
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_fleece_JSONEncoder_newJSONEncoder(JNIEnv *env, jclass ignore) {
    return (jlong) FLEncoder_NewWithOptions(kFLEncodeJSON, 0, false);
}

/*
 * Class:     com_couchbase_lite_internal_fleece_FLEncoder
 * Method:    init
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_lite_internal_fleece_JSONEncoder_finishJSON(JNIEnv *env, jclass ignore, jlong jenc) {
    FLError error = kFLNoError;
    FLSliceResult result = FLEncoder_Finish((FLEncoder) jenc, &error);
    if (error != kFLNoError)
        throwError(env, {FleeceDomain, error});

    jstring json = toJString(env, result);

    FLSliceResult_Release(result);

    return json;
}
}