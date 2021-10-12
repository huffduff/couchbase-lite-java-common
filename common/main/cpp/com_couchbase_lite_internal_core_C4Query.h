/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class com_couchbase_lite_internal_core_C4Query */

#ifndef _Included_com_couchbase_lite_internal_core_C4Query
#define _Included_com_couchbase_lite_internal_core_C4Query
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     com_couchbase_lite_internal_core_C4Query
 * Method:    getIndexInfo
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_core_C4Query_getIndexInfo
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_C4Query
 * Method:    deleteIndex
 * Signature: (JLjava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_com_couchbase_lite_internal_core_C4Query_deleteIndex
  (JNIEnv *, jclass, jlong, jstring);

/*
 * Class:     com_couchbase_lite_internal_core_C4Query
 * Method:    createIndex
 * Signature: (JLjava/lang/String;Ljava/lang/String;IILjava/lang/String;Z)Z
 */
JNIEXPORT jboolean JNICALL Java_com_couchbase_lite_internal_core_C4Query_createIndex
  (JNIEnv *, jclass, jlong, jstring, jstring, jint, jint, jstring, jboolean);

/*
 * Class:     com_couchbase_lite_internal_core_C4Query
 * Method:    init
 * Signature: (JILjava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_core_C4Query_createQuery
  (JNIEnv *, jclass, jlong, jint, jstring);

/*
 * Class:     com_couchbase_lite_internal_core_C4Query
 * Method:    setParameters
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_C4Query_setParameters(JNIEnv *, jclass, jlong, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_C4Query
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_couchbase_lite_internal_core_C4Query_free
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_C4Query
 * Method:    explain
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_couchbase_lite_internal_core_C4Query_explain
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_C4Query
 * Method:    columnCount
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_com_couchbase_lite_internal_core_C4Query_columnCount
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_C4Query
 * Method:    columnCount
 * Signature: (JI)Ljava/lang/String
 */
JNIEXPORT jstring JNICALL Java_com_couchbase_lite_internal_core_C4Query_columnName
        (JNIEnv *, jclass, jlong, jint);

/*
 * Class:     com_couchbase_lite_internal_core_C4Query
 * Method:    run
 * Signature: (JZJ)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_core_C4Query_run
  (JNIEnv *, jclass, jlong, jboolean, jlong);

/*
 * Class:     com_couchbase_lite_internal_core_C4Query
 * Method:    getFullTextMatched
 * Signature: (JJ)[B
 */
JNIEXPORT jbyteArray JNICALL Java_com_couchbase_lite_internal_core_C4Query_getFullTextMatched
  (JNIEnv *, jclass, jlong, jlong);

#ifdef __cplusplus
}
#endif
#endif
