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

public class FloatArrayConstantXScalingEntry extends FloatArrayEntry {
    private final float xConstantScale;

    public FloatArrayConstantXScalingEntry(float[] array, int index, float xConstantScale) {
        super(array, index);
        this.xConstantScale = xConstantScale;
    }

    @Override
    public float getX() {
        return index * xConstantScale;
    }
}
