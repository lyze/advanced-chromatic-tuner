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

package com.crcrch.chromatictuner.util;

import android.content.SharedPreferences;

public final class SharedPreferencesUtils {
    private SharedPreferencesUtils() {
        throw new AssertionError("SharedPreferencesUtils should not be instantiated!");
    }

    public static double getDouble(SharedPreferences pref, String key, double defVal) {
        return Double.longBitsToDouble(pref.getLong(key, Double.doubleToLongBits(defVal)));
    }

    public static void putDouble(SharedPreferences.Editor prefEditor, String key, double val) {
        prefEditor.putLong(key, Double.doubleToLongBits(val));
    }
}
