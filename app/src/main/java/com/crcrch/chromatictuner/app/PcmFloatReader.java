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

package com.crcrch.chromatictuner.app;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.Build;

/**
 * Wraps {@link android.media.AudioRecord} to support reading PCM data into a float
 * array for old versions.
 */
public class PcmFloatReader {
    private final AudioRecord audioRecord;
    private final short[] buffer;

    /**
     * Constructs a reader that will read float data. The
     * {@link AudioRecord} instance must be recording prior to calling
     * {@link #read(float[], int, int)}. The conversion buffer is only allocated if needed.
     *
     * @param audioRecord the {@link AudioRecord} instance.
     * @param conversionBufferSize the size of the buffer to use for conversion. If more data
     * than the buffer size is read, then an {@link ArrayIndexOutOfBoundsException}
     * exception will occur in {@link #read(float[], int, int)}.
     */
    public PcmFloatReader(AudioRecord audioRecord, int conversionBufferSize) {
        if (Build.VERSION.SDK_INT >= 23) {
            if (audioRecord.getAudioFormat() != AudioFormat.ENCODING_PCM_FLOAT) {
                throw new IllegalArgumentException("expected AudioFormat.ENCODING_PCM_FLOAT "
                        + "because it is supported on " + Build.VERSION.SDK_INT);
            }
            buffer = null;
        } else {
            buffer = new short[conversionBufferSize];
        }
        this.audioRecord = audioRecord;
    }

    public AudioRecord getAudioRecord() {
        return audioRecord;
    }

    public int read(float[] audioData, int offsetInFloats, int sizeInFloats) {
        if (Build.VERSION.SDK_INT >= 23) {
            return audioRecord.read(audioData, offsetInFloats, sizeInFloats,
                    AudioRecord.READ_BLOCKING);
        }
        int n = audioRecord.read(buffer, offsetInFloats, sizeInFloats);
        if (n < 0) {
            return n;
        }
        for (int i = offsetInFloats; i < sizeInFloats; i++) {
            audioData[i] = buffer[i] > 0 ? buffer[i] / 32767f : buffer[i] / 32768f;
        }
        return n;
    }
}
