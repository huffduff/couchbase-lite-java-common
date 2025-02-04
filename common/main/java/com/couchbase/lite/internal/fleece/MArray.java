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
package com.couchbase.lite.internal.fleece;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;


// ??? Why isn't this iterable?
public class MArray extends MCollection {
    @NonNull
    private List<MValue> values = new ArrayList<>();

    @Nullable
    private FLArray baseArray;

    public void initInSlot(@NonNull MValue mv, @Nullable MCollection parent) {
        initInSlot(mv, parent, (parent != null) && parent.hasMutableChildren());
    }

    public void initAsCopyOf(@NonNull MArray array, boolean isMutable) {
        super.initAsCopyOf(array, isMutable);
        baseArray = array.getBaseArray();
        values = new ArrayList<>(array.values);
    }

    @Nullable
    public FLArray getBaseArray() { return baseArray; }

    public long count() { return values.size(); }

    /**
     * Returns a reference to the MValue of the item at the given index.
     * If the index is out of range, returns an empty MValue.
     */
    @NonNull
    public MValue get(long index) {
        if (index < 0 || index >= values.size()) { return MValue.EMPTY; }

        MValue value = values.get((int) index);
        if (value.isEmpty() && (baseArray != null)) {
            value = new MValue(baseArray.get(index));
            values.set((int) index, value);
        }

        return value;
    }

    public boolean set(long index, Object value) {
        if (!isMutable()) { throw new IllegalStateException("Cannot set items in a non-mutable MArray"); }

        if ((index < 0) || (index >= count())) { return false; }

        mutate();
        values.set((int) index, new MValue(value));

        return true;
    }

    public boolean insert(long index, Object value) {
        if (!isMutable()) { throw new IllegalStateException("Cannot insert items in a non-mutable MArray"); }

        if (index < 0 || index > count()) { return false; }

        if (index < count()) { populateValues(); }

        mutate();
        values.add((int) index, new MValue(value));

        return true;
    }

    public boolean append(Object value) { return insert(count(), value); }

    public boolean remove(long start, long num) {
        if (!isMutable()) { throw new IllegalStateException("Cannot remove items in a non-mutable MArray"); }

        final long end = start + num;
        if (end <= start) { return end == start; }

        final long count = count();
        if (end > count) { return false; }

        if (end < count) { populateValues(); }

        mutate();
        values.subList((int) start, (int) end).clear();

        return true;
    }

    public boolean remove(long index) { return remove(index, 1); }

    public boolean clear() {
        if (!isMutable()) { throw new IllegalStateException("Cannot clear items in a non-mutable MArray"); }

        if (values.isEmpty()) { return true; }

        mutate();
        values.clear();

        return true;
    }

    /* Encodable */

    public void encodeTo(@NonNull FLEncoder enc) {
        if (!isMutated()) {
            if (baseArray != null) {
                enc.writeValue(baseArray);
                return;
            }

            enc.beginArray(0);
            enc.endArray();
            return;
        }

        enc.beginArray(count());
        long i = 0;
        for (MValue value: values) {
            if (!value.isEmpty()) { value.encodeTo(enc); }
            else if (baseArray != null) { enc.writeValue(baseArray.get(i)); }
            i++;
        }
        enc.endArray();
    }


    @Override
    protected void initInSlot(@NonNull MValue mv, MCollection parent, boolean isMutable) {
        super.initInSlot(mv, parent, isMutable);
        if (baseArray != null) { throw new IllegalStateException("base array is not null."); }

        final FLValue value = mv.getValue();
        if (value == null) {
            baseArray = null;
            resize(0);
            return;
        }

        baseArray = value.asFLArray();
        resize(baseArray.count());
    }

    void resize(long newSize) {
        final int count = values.size();
        if (newSize < count) { values.subList((int) newSize, count).clear(); }
        else if (newSize > count) {
            for (int i = 0; i < newSize - count; i++) { values.add(MValue.EMPTY); }
        }
    }

    void populateValues() {
        if (baseArray == null) { return; }

        final int size = values.size();
        for (int i = 0; i < size; i++) {
            if (values.get(i).isEmpty()) { values.set(i, new MValue(baseArray.get(i))); }
        }
    }
}
