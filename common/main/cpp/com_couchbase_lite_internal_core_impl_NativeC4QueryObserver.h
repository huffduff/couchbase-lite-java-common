/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class com_couchbase_lite_internal_core_NativeC4QueryObserver */

#ifndef _Included_com_couchbase_lite_internal_core_impl_NativeC4QueryObserver
#define _Included_com_couchbase_lite_internal_core_impl_NativeC4QueryObserver
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     com_couchbase_lite_internal_core_NativeC4QueryObserver
 * Method:    create
 * Signature: (JJ)J
 */
JNIEXPORT jlong JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4QueryObserver_create(
        JNIEnv *,
        jclass,
        jlong,
        jlong);

/*
 * Class:     com_couchbase_lite_internal_core_NativeC4QueryObserver
 * Method:    setEnabled
 * Signature: (JZ)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4QueryObserver_setEnabled(
        JNIEnv *,
        jclass,
        jlong,
        jboolean);

/*
 * Class:     com_couchbase_lite_internal_core_NativeC4QueryObserver
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_impl_NativeC4QueryObserver_free(
        JNIEnv *,
        jclass,
        jlong);

/*
 * Class:     com_couchbase_lite_internal_core_NativeC4QueryObserver
 * Method:    getEnumerator
 * Signature: (JZ)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_core_impl_NativeC4QueryObserver_getEnumerator(
        JNIEnv *,
        jclass,
        jlong,
        jboolean);

#ifdef __cplusplus
}
#endif
#endif
