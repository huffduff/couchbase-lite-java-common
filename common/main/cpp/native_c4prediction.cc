//
// native_c4prediction.cc
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
#include "com_couchbase_lite_internal_core_C4Prediction.h"

using namespace litecore;
using namespace litecore::jni;



#ifdef COUCHBASE_ENTERPRISE

#include <c4PredictiveQuery.h>

static jclass cls_C4PrediciveModel;
static jmethodID m_prediction;

static C4SliceResult prediction(void* context, FLDict input, C4Database* c4db, C4Error* error) {
    JNIEnv *env = nullptr;
    jint getEnvStat = gJVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (getEnvStat == JNI_EDETACHED)
        attachCurrentThread(&env);

    auto model = (jobject)context;
    jlong result = env->CallLongMethod(model, m_prediction, (jlong)input, (jlong)c4db);

    auto resultSlice = *(C4SliceResult*)result;
    ::free(reinterpret_cast<void *>(result));

    if (getEnvStat == JNI_EDETACHED)
        gJVM->DetachCurrentThread();

    return resultSlice;
}

static void unregistered(void* context) {
    deleteGlobalRef((jobject)context);
}

#endif


JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_C4Prediction_registerModel(
        JNIEnv *env,
        jclass ignore,
        jstring jname,
        jobject jmodel) {
#ifdef COUCHBASE_ENTERPRISE
    jstringSlice name(env, jname);

    jobject gModel = env->NewGlobalRef(jmodel);
    if (cls_C4PrediciveModel == nullptr) {
        cls_C4PrediciveModel = env->GetObjectClass(gModel);
        m_prediction = env->GetMethodID(cls_C4PrediciveModel, "predict", "(JJ)J");
    }

    C4PredictiveModel predModel = {
            gModel,           // .context
            &prediction,      // .prediction
            &unregistered };  // .unregistered

    c4pred_registerModel(name.c_str(), predModel);
#endif
}

/*
 * Class:     com_couchbase_lite_internal_core_C4Prediction
 * Method:    unregisterModel
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT void JNICALL
Java_com_couchbase_lite_internal_core_C4Prediction_unregisterModel (JNIEnv *env, jclass ignore, jstring jname) {
#ifdef COUCHBASE_ENTERPRISE
    jstringSlice name(env, jname);
    c4pred_unregisterModel(name.c_str());
#endif
}
