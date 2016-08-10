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

public final class MiscMath {
    private MiscMath() {
    }

    public static int toIntExact(long n) {
        if ((int) n != n) {
            throw new ArithmeticException("integer overflow");
        }
        return (int) n;
    }

    public static double rms(float[] a, int offset, int length) {
        if (a.length == 0) {
            return 0;
        }

        float sumOfSquares = 0;
        for (int i = offset; i < offset + length; i++) {
            sumOfSquares += a[i] * a[i];
        }
        return Math.sqrt(sumOfSquares / length);

    }

    /**
     * Returns the result of dividing {@code p} by {@code q} by rounding up.
     * @param p the numerator
     * @param q the denominator
     * @return {@code p/q} rounding up
     */
    public static int divideRoundingUp(int p, int q) {
        return (p - 1) / q + 1;
    }
}
