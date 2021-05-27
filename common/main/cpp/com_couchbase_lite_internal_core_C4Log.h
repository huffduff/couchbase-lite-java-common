/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class com_couchbase_lite_internal_core_C4Log */

#ifndef _Included_com_couchbase_lite_internal_core_C4Log
#define _Included_com_couchbase_lite_internal_core_C4Log
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     com_couchbase_lite_internal_core_C4Log
 * Method:    log
 * Signature: (Ljava/lang/String;ILjava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_com_couchbase_lite_internal_core_C4Log_log
  (JNIEnv *, jclass, jstring, jint, jstring);

/*
 * Class:     com_couchbase_lite_internal_core_C4Log
 * Method:    getBinaryFileLevel
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_com_couchbase_lite_internal_core_C4Log_getBinaryFileLevel
  (JNIEnv *, jclass);

/*
 * Class:     com_couchbase_lite_internal_core_C4Log
 * Method:    setBinaryFileLevel
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_com_couchbase_lite_internal_core_C4Log_setBinaryFileLevel
  (JNIEnv *, jclass, jint);

/*
 * Class:     com_couchbase_lite_internal_core_C4Log
 * Method:    writeToBinaryFile
 * Signature: (Ljava/lang/String;IIJZLjava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_com_couchbase_lite_internal_core_C4Log_writeToBinaryFile
  (JNIEnv *, jclass, jstring, jint, jint, jlong, jboolean, jstring);

/*
 * Class:     com_couchbase_lite_internal_core_C4Log
 * Method:    getLevel
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_com_couchbase_lite_internal_core_C4Log_getLevel
  (JNIEnv *, jclass, jstring);

/*
 * Class:     com_couchbase_lite_internal_core_C4Log
 * Method:    setCallbackLevel
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_com_couchbase_lite_internal_core_C4Log_setCallbackLevel
  (JNIEnv *, jclass, jint);

/*
 * Class:     com_couchbase_lite_internal_core_C4Log
 * Method:    setLevel
 * Signature: (Ljava/lang/String;I)V
 */
JNIEXPORT void JNICALL Java_com_couchbase_lite_internal_core_C4Log_setLevel
  (JNIEnv *, jclass, jstring, jint);

#ifdef __cplusplus
}
#endif
#endif
