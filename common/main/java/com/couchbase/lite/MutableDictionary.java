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

import com.couchbase.lite.internal.fleece.MCollection;
import com.couchbase.lite.internal.fleece.MDict;
import com.couchbase.lite.internal.fleece.MValue;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * Dictionary provides access to dictionary data.
 */
public final class MutableDictionary extends Dictionary implements MutableDictionaryInterface {
    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /**
     * Initialize a new empty Dictionary object.
     */
    public MutableDictionary() { }

    /**
     * Creates a new MutableDictionary with content from the passed Map.
     * Allowed value types are List, Date, Map, Number, null, String, Array, Blob, and Dictionary.
     * If present, Lists, Maps and Dictionaries may contain only the above types.
     *
     * @param data the dictionary content map.
     */
    public MutableDictionary(@NonNull Map<String, Object> data) { setData(data); }

    /**
     * Creates a new MutableDictionary with content from the passed JSON string.
     *
     * @param json the dictionary content as a JSON string.
     */
    public MutableDictionary(@NonNull String json) { setJSON(json); }

    // to create copy of dictionary
    MutableDictionary(@NonNull MDict mDict, boolean isMutable) { super(mDict, isMutable); }

    MutableDictionary(@NonNull MValue mv, @Nullable MCollection parent) { super(mv, parent); }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Populate a dictionary with content from a Map.
     * Allowed value types are List, Date, Map, Number, null, String, Array, Blob, and Dictionary.
     * If present, Lists, Maps and Dictionaries may contain only the above types.
     * Setting the dictionary content will replace the current data including
     * any existing Array and Dictionary objects.
     *
     * @param data the dictionary object.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setData(@NonNull Map<String, Object> data) {
        synchronized (lock) {
            internalDict.clear();
            for (Map.Entry<String, Object> entry: data.entrySet()) {
                internalDict.set(entry.getKey(), new MValue(Fleece.toCBLObject(entry.getValue())));
            }
            return this;
        }
    }

    /**
     * Populate a dictionary with content from a JSON string.
     * Setting the dictionary content will replace the current data including
     * any existing Array and Dictionary objects.
     *
     * @param json the dictionary object.
     * @return this Document instance
     */
    @NonNull
    @Override
    public MutableDictionary setJSON(@NonNull String json) {
        synchronized (lock) {
            internalDict.clear();
            // !!!JSON: NOT YET IMPLEMENTED
            return this;
        }
    }

    /**
     * Set an object value by key.
     * Allowed value types are List, Date, Map, Number, null, String, Array, Blob, and Dictionary.
     * If present, Lists, Maps and Dictionaries may contain only the above types.
     *
     * @param key   the key.
     * @param value the object value.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setValue(@NonNull String key, @Nullable Object value) {
        Preconditions.assertNotNull(key, "key");
        synchronized (lock) {
            value = Fleece.toCBLObject(value);
            if (Fleece.willMutate(value, internalDict.get(key), internalDict)) {
                internalDict.set(key, new MValue(value));
            }
            return this;
        }
    }

    /**
     * Set a String value for the given key.
     *
     * @param key   The key
     * @param value The String value.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setString(@NonNull String key, @Nullable String value) { return setValue(key, value); }

    /**
     * Set a Number value for the given key.
     *
     * @param key   The key
     * @param value The number value.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setNumber(@NonNull String key, @Nullable Number value) { return setValue(key, value); }

    /**
     * Set an int value for the given key.
     *
     * @param key   The key
     * @param value The int value.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setInt(@NonNull String key, int value) { return setValue(key, value); }

    /**
     * Set a long value for the given key.
     *
     * @param key   The key
     * @param value The long value.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setLong(@NonNull String key, long value) { return setValue(key, value); }

    /**
     * Set a float value for the given key.
     *
     * @param key   The key
     * @param value The float value.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setFloat(@NonNull String key, float value) { return setValue(key, value); }

    /**
     * Set a double value for the given key.
     *
     * @param key   The key
     * @param value The double value.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setDouble(@NonNull String key, double value) { return setValue(key, value); }

    /**
     * Set a boolean value for the given key.
     *
     * @param key   The key
     * @param value The boolean value.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setBoolean(@NonNull String key, boolean value) { return setValue(key, value); }

    /**
     * Set a Blob object for the given key.
     *
     * @param key   The key
     * @param value The Blob object.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setBlob(@NonNull String key, Blob value) { return setValue(key, value); }

    /**
     * Set a Date object for the given key.
     *
     * @param key   The key
     * @param value The Date object.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setDate(@NonNull String key, Date value) { return setValue(key, value); }

    /**
     * Set an Array object for the given key.
     *
     * @param key   The key
     * @param value The Array object.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setArray(@NonNull String key, Array value) { return setValue(key, value); }

    /**
     * Set a Dictionary object for the given key.
     *
     * @param key   The key
     * @param value The Dictionary object.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setDictionary(@NonNull String key, Dictionary value) { return setValue(key, value); }

    /**
     * Removes the mapping for a key from this Dictionary
     *
     * @param key the key.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary remove(@NonNull String key) {
        Preconditions.assertNotNull(key, "key");
        synchronized (lock) {
            internalDict.remove(key);
            return this;
        }
    }

    /**
     * Get a property's value as a Array, which is a mapping object of an array value.
     * Returns null if the property doesn't exists, or its value is not an array.
     *
     * @param key the key.
     * @return the Array object.
     */
    @Nullable
    @Override
    public MutableArray getArray(@NonNull String key) { return (MutableArray) super.getArray(key); }

    /**
     * Get a property's value as a Dictionary, which is a mapping object of an dictionary value.
     * Returns null if the property doesn't exists, or its value is not an dictionary.
     *
     * @param key the key.
     * @return the Dictionary object or null if the key doesn't exist.
     */
    @Nullable
    @Override
    public MutableDictionary getDictionary(@NonNull String key) { return (MutableDictionary) super.getDictionary(key); }


    protected boolean isChanged() {
        synchronized (lock) { return internalDict.isMutated(); }
    }
}
