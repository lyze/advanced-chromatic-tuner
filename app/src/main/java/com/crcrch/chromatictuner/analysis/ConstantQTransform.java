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

package com.crcrch.chromatictuner.analysis;


import android.support.annotation.Nullable;
import org.jtransforms.fft.FloatFFT_1D;
import org.jtransforms.utils.CommonUtils;

/**
 * Computes the constant Q transform for {@code float} data points. Uses the kernel method of
 * Brown and Puckette [1992].
 * <p/>
 * Judith C. Brown and Miller S. Puckette. An efficient algorithm for the calculation of a
 * constant Q
 * transform. <em>The Journal of the Acoustical Society of America</em> 92, 5 (Nov. 1992),
 * 2698-2701.
 * DOI:<a href="http://dx.doi.org/10.1121/1.404385">http://dx.doi.org/10.1121/1.404385</a>
 */
public class ConstantQTransform {

    /**
     * The kernel. For each {@code k_cq} in {@code [0, N[k_cq] - 1]}, the entry {@code kernel[k_cq]}
     * is a lookup table of frequency bin to real/imaginary parts, given by {@code
     * kernel[k_cq][i]} and {@code kernel[k_cq][i + 1]} respectively for the {@code i}-th
     * coefficient.
     */
    private final float[][] spectralKernel;
    private final FloatFFT_1D fft;
    private final int numSamples;
    private final double ratio;
    private final double minFrequency;

    /**
     * Constructs an instance to compute the constant Q transform. The constant Q bins that will
     * be computed are
     * {@code {minFreq, minFreq * r, minFreq * r^2, ..., minFreq * r^(numConstantQBins - 1)}}.
     *
     * @param window the window function to apply. If null, then no windowing is used.
     * @param sampleRate the sample rate
     * @param minFreq the minimum frequency to compute
     * @param r the geometric ratio between neighboring frequencies
     * @param numConstantQBins the number of coefficients to compute
     */
    public ConstantQTransform(@Nullable WindowFunction window,
                              double sampleRate, double minFreq, double r, int numConstantQBins) {
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("non-positive sample rate: " + sampleRate);
        }
        if (minFreq <= 0) {
            throw new IllegalArgumentException("non-positive frequency: " + minFreq);
        }
        if (r <= 1) {
            throw new IllegalArgumentException("ratio must be r" + r);
        }
        if (numConstantQBins <= 0) {
            throw new IllegalArgumentException(
                    "non-positive number of coefficients: " + numConstantQBins);
        }
        this.ratio = r;
        minFrequency = minFreq;

        double bandwidth = ConstantQTransform.getResolution(minFreq, r);
        double q = minFreq / bandwidth;

        numSamples = (int) (sampleRate / bandwidth);
        fft = new FloatFFT_1D(numSamples);

        spectralKernel = new float[numConstantQBins][];
        for (int i = 0; i < spectralKernel.length; i++) {
            spectralKernel[i] = new float[2 * numSamples];

            double fkcq = minFreq * Math.pow(r, i);
            int windowLength = (int) (q * sampleRate / fkcq);

            for (int j = 0; j < windowLength; j++) {
                double angle = -2 * Math.PI * fkcq * j / sampleRate;
                float wn;
                if (window == null) {
                    wn = 1.0f;
                } else {
                    wn = window.apply(j, windowLength);
                }
                spectralKernel[i][2 * j] = (float) (wn * Math.cos(angle));
                spectralKernel[i][2 * j + 1] = (float) (wn * Math.sin(angle));
            }

            fft.complexForward(spectralKernel[i]);
            scale(spectralKernel[i]);
        }
    }

    public static int getFftSize(double sampleRate, double minFreq, double ratio) {
        return (int) (sampleRate / getResolution(minFreq, ratio));
    }

    public static double getResolution(double minFreq, double ratio) {
        return minFreq * ratio - minFreq;
    }

    /**
     * Applies scaling by {@code 1/N}.
     *
     * @param a the array
     */
    private void scale(float[] a) {
        CommonUtils.scale(numSamples, 1.0f / numSamples, a, 0, false);
    }

    public int getFftSize() {
        return numSamples;
    }

    public int getNumCoefficients() {
        return spectralKernel.length;
    }

    /**
     * Computes a power spectrum of a constant Q transform on real data.
     *
     * @param input an array of size exactly {@code 2n} with the first {@code n} elements filled
     * with the real data points, where {@code n} equals the value of {@link #getFftSize()}
     * @param output the output array of size exactly {@link #getNumCoefficients()}.
     * @param p0 the reference power level
     */
    public void realConstantQPowerDbFull(float[] input, float[] output, double p0) {
        if (input.length % 2 != 0) {
            throw new IllegalArgumentException("length of input array is not a multiple of 2");
        }
        if (output.length < getNumCoefficients()) {
            throw new IllegalArgumentException(
                    "length of output array be at least the number of constant Q coefficients");
        }
        fft.realForwardFull(input);
        scale(input);
        for (int i = 0; i < output.length; i++) {
            // Compute the real and imaginary parts of the ith constant Q coefficient.
            float cqRe = 0;
            float cqIm = 0;

            float[] kernel = spectralKernel[i];

            for (int j = 0; j < input.length / 2; j++) {
                float xRe = input[2 * j];
                float xIm = input[2 * j + 1];
                float kRe = kernel[2 * j];
                float kIm = kernel[2 * j + 1];

                // Do the complex multiplication in terms of real and imaginary parts
                cqRe += xRe * kRe - xIm * kIm;
                cqIm += xRe * kIm + xIm * kRe;
            }
            float sq = cqRe * cqRe + cqIm * cqIm;
            output[i] = (float) (10 * Math.log10(sq / p0));
        }
    }

    public double getRatio() {
        return ratio;
    }

    public double getMinFrequency() {
        return minFrequency;
    }

    public interface WindowFunction {
        /**
         * Computes the window function at a specified time.
         *
         * @param n the time in the range {@code [0..n)}
         * @param windowLength length of the window
         * @return the result of applying the window
         */
        float apply(int n, int windowLength);
    }
}
