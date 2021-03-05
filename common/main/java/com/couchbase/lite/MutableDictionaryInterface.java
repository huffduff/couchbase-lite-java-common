//
// Copyright (c) 2020, 2017 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Date;
import java.util.Map;


/**
 * Note: MutableDictionaryInterface is an internal interface. This should not be public.
 */
public interface MutableDictionaryInterface extends DictionaryInterface {
    @Nullable
    @Override
    MutableArrayInterface getArray(@NonNull String key);

    @Nullable
    @Override
    MutableDictionaryInterface getDictionary(@NonNull String key);

    // remove

    @NonNull
    MutableDictionaryInterface remove(@NonNull String key);

    // set

    @NonNull
    MutableDictionaryInterface setInt(@NonNull String key, int value);

    @NonNull
    MutableDictionaryInterface setLong(@NonNull String key, long value);

    @NonNull
    MutableDictionaryInterface setFloat(@NonNull String key, float value);

    @NonNull
    MutableDictionaryInterface setDouble(@NonNull String key, double value);

    @NonNull
    MutableDictionaryInterface setBoolean(@NonNull String key, boolean value);

    @NonNull
    MutableDictionaryInterface setNumber(@NonNull String key, Number value);

    @NonNull
    MutableDictionaryInterface setString(@NonNull String key, String value);

    @NonNull
    MutableDictionaryInterface setDate(@NonNull String key, Date value);

    @NonNull
    MutableDictionaryInterface setBlob(@NonNull String key, Blob value);

    @NonNull
    MutableDictionaryInterface setValue(@NonNull String key, Object value);

    @NonNull
    MutableDictionaryInterface setArray(@NonNull String key, Array value);

    @NonNull
    MutableDictionaryInterface setDictionary(@NonNull String key, Dictionary value);

    @NonNull
    MutableDictionaryInterface setData(@NonNull Map<String, Object> data);

    @NonNull
    MutableDictionaryInterface setJSON(@NonNull String json);
}
