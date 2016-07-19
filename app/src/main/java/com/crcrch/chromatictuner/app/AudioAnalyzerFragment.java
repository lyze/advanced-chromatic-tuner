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

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import org.jtransforms.fft.FloatFFT_1D;

public class AudioAnalyzerFragment extends Fragment {
    private static final String USE_UNPROCESSED_AUDIO_SOURCE = "use unprocessed audio source";
    private static final int UPDATES_PER_SECOND = 10;
    private static final int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 17;
    private static final String TAG = "AudioAnalyzerFragment";

    private AudioAnalyzer audioAnalyzer;
    private OnSpectrumCalculatedListener callback;

    public static AudioAnalyzerFragment newInstance() {
        return new AudioAnalyzerFragment();
    }

    private static int getSampleRate() {
        if (Build.VERSION.SDK_INT >= 24) {
            return AudioFormat.SAMPLE_RATE_UNSPECIFIED;
        }
        return 44100;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            callback = (OnSpectrumCalculatedListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(
                    context + " must implement " + OnSpectrumCalculatedListener.class);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        callback = null;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startAnalysis();
            return;
        }
        if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            Snackbar.make(getActivity().getWindow().getDecorView().getRootView(),
                    R.string.permission_record_audio_rationale, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.ok, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            ActivityCompat.requestPermissions(getActivity(),
                                    new String[] {Manifest.permission.RECORD_AUDIO},
                                    MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
                        }
                    });
        } else {
            requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO},
                    MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_RECORD_AUDIO:
                if (grantResults.length > 0) {
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        startAnalysis();
                    } else {
                        Log.e(TAG, "Record audio permission denied.");
                    }
                }
        }
    }

    private void startAnalysis() {
        audioAnalyzer = new AudioAnalyzer();
        audioAnalyzer.execute();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (audioAnalyzer != null) {
            audioAnalyzer.cancel(true);
        }
    }

    private int getAudioSourceToUse() {
        int audioSource;
        if (Build.VERSION.SDK_INT >= 24) {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());
            if (pref.getBoolean(USE_UNPROCESSED_AUDIO_SOURCE, false)) {
                audioSource = MediaRecorder.AudioSource.UNPROCESSED;
            } else {
                audioSource = MediaRecorder.AudioSource.MIC;
            }
        } else {
            audioSource = MediaRecorder.AudioSource.MIC;
        }
        return audioSource;
    }

    private AudioRecord newAudioRecord(int encoding) {
        if (Build.VERSION.SDK_INT >= 23) {
            return new AudioRecord.Builder()
                    .setAudioSource(getAudioSourceToUse())
                    .setAudioFormat(new AudioFormat.Builder().setEncoding(encoding).build())
                    .build();
        }
        return new AudioRecord(
                getAudioSourceToUse(),
                getSampleRate(),
                AudioFormat.CHANNEL_IN_DEFAULT,
                encoding,
                AudioRecord.getMinBufferSize(getSampleRate(), AudioFormat.CHANNEL_IN_DEFAULT,
                        encoding));
    }

    public interface OnSpectrumCalculatedListener {
        void onSpectrumCalculated(float[] spectrum);
    }

    private class AudioAnalyzer extends AsyncTask<Void, float[], Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            AudioRecord audioRecord;
            if (Build.VERSION.SDK_INT >= 23) {
                audioRecord = newAudioRecord(AudioFormat.ENCODING_PCM_FLOAT);
            } else {
                audioRecord = newAudioRecord(AudioFormat.ENCODING_PCM_16BIT);
            }

            audioRecord.startRecording();

            int sampleSize = (int) ((double) audioRecord.getSampleRate() / UPDATES_PER_SECOND);
            FloatFFT_1D fft = new FloatFFT_1D(sampleSize);

            float[] data = new float[2 * sampleSize];
            short[] data16Bit;
            if (Build.VERSION.SDK_INT >= 23) {
                data16Bit = new short[0];
            } else {
                data16Bit = new short[sampleSize];
            }
            while (!isCancelled()) {
                if (Build.VERSION.SDK_INT >= 23) {
                    audioRecord.read(data, 0, sampleSize, AudioRecord.READ_BLOCKING);
                } else {
                    audioRecord.read(data16Bit, 0, sampleSize);
                    for (int i = 0; i < data16Bit.length; i++) {
                        data[i] = (float) data16Bit[i];
                    }
                }
                fft.realForwardFull(data);
                publishProgress(data);
            }
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
            return null;
        }

        @Override
        protected void onProgressUpdate(float[]... values) {
            callback.onSpectrumCalculated(values[0]);
        }
    }

}
