/*
 * Copyright 2016 David Xu. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.crcrch.chromatictuner;

import android.os.Parcel;
import com.github.mikephil.charting.data.Entry;

/**
 * Compatibility for MPAndroidChart.
 */
public class FloatArrayEntry extends Entry {
    private final float[] array;
    protected final int index;

    public FloatArrayEntry(float[] array, int index) {
        this.array = array;
        this.index = index;
    }

    @Override
    public float getX() {
        return index;
    }

    @Override
    public void setX(float x) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Entry copy() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int describeContents() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        throw new UnsupportedOperationException();
    }

    @Override
    public float getY() {
        return array[index];
    }

    @Override
    public void setY(float y) {
        array[index] = y;
    }
}
