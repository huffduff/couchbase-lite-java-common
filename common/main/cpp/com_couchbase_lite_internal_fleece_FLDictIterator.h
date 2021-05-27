/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class com_couchbase_lite_internal_fleece_FLDictIterator */

#ifndef _Included_com_couchbase_lite_internal_fleece_FLDictIterator
#define _Included_com_couchbase_lite_internal_fleece_FLDictIterator
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     com_couchbase_lite_internal_fleece_FLDictIterator
 * Method:    init
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_fleece_FLDictIterator_init
  (JNIEnv *, jclass);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLDictIterator
 * Method:    getCount
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_fleece_FLDictIterator_getCount
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLDictIterator
 * Method:    begin
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL Java_com_couchbase_lite_internal_fleece_FLDictIterator_begin
  (JNIEnv *, jclass, jlong, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLDictIterator
 * Method:    getKeyString
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_couchbase_lite_internal_fleece_FLDictIterator_getKeyString
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLDictIterator
 * Method:    getValue
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_fleece_FLDictIterator_getValue
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLDictIterator
 * Method:    next
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_com_couchbase_lite_internal_fleece_FLDictIterator_next
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLDictIterator
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_couchbase_lite_internal_fleece_FLDictIterator_free
  (JNIEnv *, jclass, jlong);

#ifdef __cplusplus
}
#endif
#endif
