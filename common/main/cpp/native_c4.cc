//
// native_c4.cc
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
#if defined(__ANDROID__)
#include <android/log.h>
#elif defined(__linux__) || defined(__APPLE__)
#include <sys/time.h>
#endif

#include "com_couchbase_lite_internal_core_C4.h"
#include "com_couchbase_lite_internal_core_C4Log.h"
#include "com_couchbase_lite_internal_core_C4Key.h"
#include "native_glue.hh"

using namespace litecore;
using namespace litecore::jni;

//-------------------------------------------------------------------------
// Package initialization
//-------------------------------------------------------------------------

static jclass cls_C4Log;
static jmethodID m_C4Log_logCallback;

static void logCallback(C4LogDomain domain, C4LogLevel level, const char *fmt, va_list ignore);

bool litecore::jni::initC4Logging(JNIEnv *env) {
    jclass localClass = env->FindClass("com/couchbase/lite/internal/core/C4Log");
    if (!localClass)
        return false;

    cls_C4Log = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
    if (!cls_C4Log)
        return false;

    m_C4Log_logCallback = env->GetStaticMethodID(
            cls_C4Log,
            "logCallback",
            "(Ljava/lang/String;ILjava/lang/String;)V");

    if (!m_C4Log_logCallback)
        return false;

    c4log_writeToCallback((C4LogLevel) kC4LogDebug, logCallback, true);

    return true;
}

// The default logging callback writes to stderr, or on Android to __android_log_write.
// ??? Need to do something better for web service and Windows logging
void vLogError(const char *fmt, va_list args) {
#if defined(__ANDROID__)
    __android_log_vprint(ANDROID_LOG_ERROR, "LiteCore/JNI", fmt, args);
#else
#if defined(__linux__) || defined(__APPLE__)
    struct timeval tv;
    gettimeofday(&tv, NULL);

    struct tm tm;
    localtime_r(&tv.tv_sec, &tm);

    char timestamp[100];
    strftime(timestamp, sizeof(timestamp), "%T", &tm);

    fprintf(stderr, "%s.%03u ", timestamp, tv.tv_usec / 1000);
#endif

    fprintf(stderr, "E/LiteCore/JNI: ");
    vfprintf(stderr, fmt, args);
    fputc('\n', stderr);
#endif
}

void litecore::jni::logError(const char *fmt, ...) {
    va_list args;
    va_start(args, fmt);
    vLogError(fmt, args);
    va_end(args);
}

static void logCallback(C4LogDomain domain, C4LogLevel level, const char *fmt, va_list ignore) {
    if (!cls_C4Log || !m_C4Log_logCallback) {
        logError("logCallback(): Logging not initialized");
        return;
    }

    JNIEnv *env = nullptr;
    jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (getEnvStat == JNI_EDETACHED) {
        if (attachCurrentThread(&env) != 0) {
            logError("logCallback(): Failed to attach the current thread to a Java VM)");
            return;
        }
    } else if (getEnvStat != JNI_OK) {
        logError("logCallback(): Failed to get the environment: getEnvStat -> %d", getEnvStat);
        return;
    }

    if (env->ExceptionCheck() == JNI_TRUE) {
        logError("logCallback(): Cannot log while an exception is outstanding");
        return;
    }

    jstring message = UTF8ToJstring(env, const_cast<char *>(fmt), strlen(fmt));
    if (!message) {
        logError("logCallback(): Failed encoding error message");
        return;
    }

    const char *domainNameRaw = c4log_getDomainName(domain);
    jstring domainName = UTF8ToJstring(env, domainNameRaw, strlen(domainNameRaw));
    if (!domainName)
        domainName = env->NewStringUTF("???");

    env->CallStaticVoidMethod(cls_C4Log, m_C4Log_logCallback, domainName, (jint) level, message);

    // If this method is on a thread that is was previously attached but was called
    // from some long running method, we need to release the local refs.
    env->DeleteLocalRef(message);
    if (domainName)
        env->DeleteLocalRef(domainName);

    if (getEnvStat == JNI_EDETACHED) {
        if (gJVM->DetachCurrentThread() != 0) {
            C4Warn("logCallback(): doRequestClose(): Failed to detach the current thread from a Java VM");
        }
    }
}


extern "C" {
// ----------------------------------------------------------------------------
// com_couchbase_lite_internal_core_C4
// ----------------------------------------------------------------------------
/*
 * Class:     com_couchbase_lite_internal_core_C4
 * Method:    setenv
 * Signature: (Ljava/lang/String;Ljava/lang/String;I)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_C4_setenv(
        JNIEnv *env,
        jclass ignore,
        jstring jname,
        jstring jvalue,
        jint overwrite) {
    jstringSlice name(env, jname);
    jstringSlice value(env, jvalue);

#ifdef _MSC_VER
    _putenv_s(name.c_str(), value.c_str());
#else
    setenv(name.c_str(), value.c_str(), overwrite);
#endif
}

/*
 * Class:     com_couchbase_lite_internal_core_C4
 * Method:    getenv
 * Signature: (Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_lite_internal_core_C4_getenv(JNIEnv *env, jclass ignore, jstring jname) {
    jstringSlice name(env, jname);
    return env->NewStringUTF(getenv(name.c_str()));
}

/*
 * Class:     com_couchbase_lite_internal_core_C4
 * Method:    getBuildInfo
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_lite_internal_core_C4_getBuildInfo(JNIEnv *env, jclass ignore) {
    C4StringResult result = c4_getBuildInfo();
    jstring jstr = toJString(env, result);
    c4slice_free(result);
    return jstr;
}

/*
 * Class:     com_couchbase_lite_internal_core_C4
 * Method:    getVersion
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_com_couchbase_lite_internal_core_C4_getVersion(JNIEnv *env, jclass ignore) {
    C4StringResult result = c4_getVersion();
    jstring jstr = toJString(env, result);
    c4slice_free(result);
    return jstr;
}

// ----------------------------------------------------------------------------
// com_couchbase_lite_internal_core_C4Log
// ----------------------------------------------------------------------------
/*
 * Class:     com_couchbase_lite_internal_core_C4Log
 * Method:    getLevel
 * Signature: (Ljava/lang/String;I)V
 */
JNIEXPORT jint JNICALL
Java_com_couchbase_lite_internal_core_C4Log_getLevel(JNIEnv *env, jclass ignore, jstring jdomain) {
    jstringSlice domain(env, jdomain);
    C4LogDomain logDomain = c4log_getDomain(domain.c_str(), false);
    return (!logDomain) ? -1 : (jint) c4log_getLevel(logDomain);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Log
 * Method:    setLevel
 * Signature: (Ljava/lang/String;I)V
 *
 * Since the Java code can only talk about domains that are instance of the LogDomain enum,
 * it is ok to let this code create new domains (2nd arg to c4log_getDomain).
 * The advantage of allowing this method to create new LogDomain instances is that if,
 * for debugging, we need to log for a dynamically created domain, we can initialize
 * that domain at any time, including before Core creates it.
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_C4Log_setLevel(
        JNIEnv *env,
        jclass ignore,
        jstring jdomain,
        jint jlevel) {
    jstringSlice domain(env, jdomain);
    C4LogDomain logDomain = c4log_getDomain(domain.c_str(), true);
    c4log_setLevel(logDomain, (C4LogLevel) jlevel);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Log
 * Method:    log
 * Signature: (Ljava/lang/String;I;Ljava/lang/String)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_C4Log_log(
        JNIEnv *env,
        jclass ignore,
        jstring jdomain,
        jint jlevel,
        jstring jmessage) {
    jstringSlice message(env, jmessage);
    const char *domain = env->GetStringUTFChars(jdomain, nullptr);
    C4LogDomain logDomain = c4log_getDomain(domain, true);
    c4slog(logDomain, (C4LogLevel) jlevel, message);
    env->ReleaseStringUTFChars(jdomain, domain);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Log
 * Method:    getBinaryFileLevel
 * Signature: (V)I
 */
JNIEXPORT jint JNICALL
Java_com_couchbase_lite_internal_core_C4Log_getBinaryFileLevel(JNIEnv *env, jclass ignore) {
    return c4log_binaryFileLevel();
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Log
 * Method:    setBinaryFileLevel
 * Signature: (I)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_C4Log_setBinaryFileLevel(JNIEnv *env, jclass ignore, jint level) {
    c4log_setBinaryFileLevel((C4LogLevel) level);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Log
 * Method:    writeToBinaryFile
 * Signature: (Ljava/lang/String;IIJZLjava/lang/String;)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_C4Log_writeToBinaryFile(
        JNIEnv *env,
        jclass ignore,
        jstring jpath,
        jint jlevel,
        jint jmaxrotatecount,
        jlong jmaxsize,
        jboolean juseplaintext,
        jstring jheader) {
    jstringSlice path(env, jpath);
    jstringSlice header(env, jheader);
    C4LogFileOptions options{
            (C4LogLevel) jlevel,
            path,
            jmaxsize,
            jmaxrotatecount,
            (bool) juseplaintext,
            header
    };

    C4Error err;
    if (!c4log_writeToBinaryFile(options, &err))
        throwError(env, err);
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Log
 * Method:    setCallbackLevel
 * Signature: (I)V
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_C4Log_setCallbackLevel(JNIEnv *env, jclass clazz, jint jlevel) {
    c4log_setCallbackLevel((C4LogLevel) jlevel);
}

// ----------------------------------------------------------------------------
// com_couchbase_lite_internal_core_C4Key
// ----------------------------------------------------------------------------

/*
 * Class:     com_couchbase_lite_internal_core_C4Key
 * Method:    pbkdf2
 * Signature: (Ljava/lang/String;[BII)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_com_couchbase_lite_internal_core_C4Key_pbkdf2(JNIEnv *env, jclass ignore, jstring password) {
    jstringSlice pwd(env, password);

    C4EncryptionKey key;
    if (!c4key_setPasswordSHA1(&key, pwd, kC4EncryptionAES256))
        return nullptr;

    int keyLen = sizeof(key.bytes);
    jbyteArray result = env->NewByteArray(keyLen);
    env->SetByteArrayRegion(result, 0, keyLen, (jbyte *) &key.bytes);

    return result;
}

/*
 * Class:     Java_com_couchbase_lite_internal_core_C4Key
 * Method:    deriveKeyFromPassword
 * Signature: (Ljava/lang/String;I)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_com_couchbase_lite_internal_core_C4Key_deriveKeyFromPassword(JNIEnv *env, jclass ignore, jstring password) {
    jstringSlice pwd(env, password);

    C4EncryptionKey key;
    if (!c4key_setPassword(&key, pwd, kC4EncryptionAES256))
        return nullptr;

    int keyLen = sizeof(key.bytes);
    jbyteArray result = env->NewByteArray(keyLen);
    env->SetByteArrayRegion(result, 0, keyLen, (jbyte *) &key.bytes);

    return result;
}
}