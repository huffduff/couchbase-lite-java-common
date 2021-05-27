/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class com_couchbase_lite_internal_fleece_FLValue */

#ifndef _Included_com_couchbase_lite_internal_fleece_FLValue
#define _Included_com_couchbase_lite_internal_fleece_FLValue
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     com_couchbase_lite_internal_fleece_FLValue
 * Method:    fromTrustedData
 * Signature: ([B)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_fleece_FLValue_fromTrustedData
  (JNIEnv *, jclass, jbyteArray);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLValue
 * Method:    fromData
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_fleece_FLValue_fromData
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLValue
 * Method:    getType
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_com_couchbase_lite_internal_fleece_FLValue_getType
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLValue
 * Method:    isInteger
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_com_couchbase_lite_internal_fleece_FLValue_isInteger
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLValue
 * Method:    isUnsigned
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_com_couchbase_lite_internal_fleece_FLValue_isUnsigned
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLValue
 * Method:    isDouble
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_com_couchbase_lite_internal_fleece_FLValue_isDouble
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLValue
 * Method:    toString
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_couchbase_lite_internal_fleece_FLValue_toString
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLValue
 * Method:    toJSON
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_couchbase_lite_internal_fleece_FLValue_toJSON
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLValue
 * Method:    toJSON5
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_couchbase_lite_internal_fleece_FLValue_toJSON5
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLValue
 * Method:    asData
 * Signature: (J)[B
 */
JNIEXPORT jbyteArray JNICALL Java_com_couchbase_lite_internal_fleece_FLValue_asData
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLValue
 * Method:    asBool
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_com_couchbase_lite_internal_fleece_FLValue_asBool
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLValue
 * Method:    asUnsigned
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_fleece_FLValue_asUnsigned
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLValue
 * Method:    asInt
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_fleece_FLValue_asInt
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLValue
 * Method:    asFloat
 * Signature: (J)F
 */
JNIEXPORT jfloat JNICALL Java_com_couchbase_lite_internal_fleece_FLValue_asFloat
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLValue
 * Method:    asDouble
 * Signature: (J)D
 */
JNIEXPORT jdouble JNICALL Java_com_couchbase_lite_internal_fleece_FLValue_asDouble
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLValue
 * Method:    asString
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_couchbase_lite_internal_fleece_FLValue_asString
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLValue
 * Method:    asArray
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_fleece_FLValue_asArray
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLValue
 * Method:    asDict
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_couchbase_lite_internal_fleece_FLValue_asDict
  (JNIEnv *, jclass, jlong);

/*
 * Class:     com_couchbase_lite_internal_fleece_FLValue
 * Method:    json5toJson
 * Signature: (Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_couchbase_lite_internal_fleece_FLValue_json5toJson
  (JNIEnv *, jclass, jstring);

#ifdef __cplusplus
}
#endif
#endif
