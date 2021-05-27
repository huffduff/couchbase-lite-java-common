/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class com_couchbase_lite_internal_core_C4DocEnumerator */

#ifndef _Included_com_couchbase_lite_internal_core_C4DocEnumerator
#define _Included_com_couchbase_lite_internal_core_C4DocEnumerator
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     com_couchbase_lite_internal_core_C4DocEnumerator
 * Method:    enumerateAllDocs
 * Signature: (JI)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_core_C4DocEnumerator_enumerateAllDocs
  (JNIEnv *, jclass, jlong, jint);

/*
 * Class:     com_couchbase_lite_internal_core_C4DocEnumerator
 * Method:    enumerateChanges
 * Signature: (JJI)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_core_C4DocEnumerator_enumerateChanges
  (JNIEnv *, jclass, jlong, jlong, jint);

/*
 * Class:     com_couchbase_lite_internal_core_C4DocEnumerator
 * Method:    next
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_com_couchbase_lite_internal_core_C4DocEnumerator_next
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_C4DocEnumerator
 * Method:    getDocument
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_core_C4DocEnumerator_getDocument
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_C4DocEnumerator
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_couchbase_lite_internal_core_C4DocEnumerator_free
  (JNIEnv *, jclass, jlong);

#ifdef __cplusplus
}
#endif
#endif
