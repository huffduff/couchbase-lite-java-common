/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class com_couchbase_lite_internal_core_C4BlobWriteStream */

#ifndef _Included_com_couchbase_lite_internal_core_C4BlobWriteStream
#define _Included_com_couchbase_lite_internal_core_C4BlobWriteStream
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     com_couchbase_lite_internal_core_C4BlobWriteStream
 * Method:    write
 * Signature: (J[BI)V
 */
JNIEXPORT void JNICALL Java_com_couchbase_lite_internal_core_C4BlobWriteStream_write
  (JNIEnv *, jclass, jlong, jbyteArray, jint);

/*
 * Class:     com_couchbase_lite_internal_core_C4BlobWriteStream
 * Method:    computeBlobKey
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_core_C4BlobWriteStream_computeBlobKey
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_C4BlobWriteStream
 * Method:    install
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_couchbase_lite_internal_core_C4BlobWriteStream_install
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_C4BlobWriteStream
 * Method:    close
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_couchbase_lite_internal_core_C4BlobWriteStream_close
  (JNIEnv *, jclass, jlong);

#ifdef __cplusplus
}
#endif
#endif
