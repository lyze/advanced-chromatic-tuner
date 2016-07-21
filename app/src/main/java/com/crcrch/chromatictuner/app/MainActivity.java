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
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import com.crcrch.chromatictuner.util.MyAsyncTask;
import org.jtransforms.fft.FloatFFT_1D;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String USE_UNPROCESSED_AUDIO_SOURCE = "use unprocessed audio source";
    private static final int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 17;
    private AudioAnalyzer audioAnalyzer;
    private PowerSpectrumFragment powerSpectrumFrag;

    private static int getSampleRateToUse() {
        if (Build.VERSION.SDK_INT >= 24) {
            return AudioFormat.SAMPLE_RATE_UNSPECIFIED;
        }
        return 44100;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        powerSpectrumFrag = (PowerSpectrumFragment) getSupportFragmentManager().findFragmentById(
                R.id.power_spectrum);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        audioAnalyzer = new AudioAnalyzer();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            audioAnalyzer.execute();
            return;
        }
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.RECORD_AUDIO)) {
            Snackbar.make(getWindow().getDecorView().getRootView(),
                    R.string.permission_record_audio_rationale,
                    Snackbar.LENGTH_INDEFINITE).setAction(R.string.ok, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[] {Manifest.permission.RECORD_AUDIO},
                            MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
                }
            }).show();
        } else {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.RECORD_AUDIO},
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
                        audioAnalyzer.execute();
                    } else {
                        powerSpectrumFrag.getGraph().setNoDataTextDescription(getString(
                                R.string.graph_no_data_description_permission_denied));
                        Log.e(TAG, "Record audio permission denied.");
                    }
                }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (audioAnalyzer != null) {
            audioAnalyzer.cancel(true);
            audioAnalyzer = null;
        }
    }

    private int getAudioSourceToUse() {
        int audioSource;
        if (Build.VERSION.SDK_INT >= 24) {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
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
                getSampleRateToUse(),
                AudioFormat.CHANNEL_IN_DEFAULT,
                encoding,
                AudioRecord.getMinBufferSize(getSampleRateToUse(), AudioFormat.CHANNEL_IN_DEFAULT,
                        encoding));
    }

    public void toggleLiveSpectrum(View view) {
        audioAnalyzer.togglePaused();
    }

    /**
     * Computes power and phase spectra. This class should only be used in the visible lifecycle
     * of the app.
     */
    private class AudioAnalyzer extends MyAsyncTask<Void, Void, Void> {
        private static final String TAG = "AudioAnalyzer";
        private static final int UPDATES_PER_SECOND = 60;

        @Override
        protected Void doInBackground(Void... voids) {
            Log.d(TAG, "Starting audio analysis...");
            AudioRecord audioRecord;
            if (Build.VERSION.SDK_INT >= 23) {
                audioRecord = newAudioRecord(AudioFormat.ENCODING_PCM_FLOAT);
            } else {
                audioRecord = newAudioRecord(AudioFormat.ENCODING_PCM_16BIT);
            }

            int sampleRate = audioRecord.getSampleRate();
            int sampleSize = (int) ((double) sampleRate / UPDATES_PER_SECOND);
            FloatFFT_1D fft = new FloatFFT_1D(sampleSize);

            float[] data = new float[2 * sampleSize];
            short[] data16Bit;
            if (Build.VERSION.SDK_INT >= 23) {
                data16Bit = new short[0];
            } else {
                data16Bit = new short[sampleSize];
            }

            float[] powerSpectrum = new float[sampleSize];
            float[] phaseSpectrum = new float[sampleSize];

            powerSpectrumFrag.setPowerSpectrumArray(powerSpectrum, sampleRate);

            audioRecord.startRecording();

            while (!isCancelled()) {
                try {
                    maybePause();
                } catch (InterruptedException e) {
                    break;
                }
                if (Build.VERSION.SDK_INT >= 23) {
                    audioRecord.read(data, 0, sampleSize, AudioRecord.READ_BLOCKING);
                } else {
                    audioRecord.read(data16Bit, 0, sampleSize);
                    for (int i = 0; i < data16Bit.length; i++) {
                        data[i] = data16Bit[i] > 0 ? data16Bit[i] / 32767f : data16Bit[i] / 32768f;
                    }
                }
                fft.realForwardFull(data); // TODO native FFT
                float max = Float.NEGATIVE_INFINITY;
                for (int i = 0; i < data.length / 2; i++) {
                    float re = data[2 * i];
                    float im = data[2 * i + 1];
                    powerSpectrum[i] = re * re + im * im;
                    if (powerSpectrum[i] > max) {
                        max = powerSpectrum[i];
                    }
                }
// TODO mic spec req: http://source.android.com/compatibility/4.4/android-4.4-cdd.xhtml#section-5.4
                for (int i = 0; i < powerSpectrum.length; i++) {
                    powerSpectrum[i] = 10 * (float) Math.log10(powerSpectrum[i] / max);
                }
                publishProgress();
            }
            Log.d(TAG, "Stopping audio analysis...");
            audioRecord.stop();
            audioRecord.release();
            //noinspection UnusedAssignment
            audioRecord = null;
            return null;
        }

        @Override
        protected void onProgressUpdate(Void... values) {
            powerSpectrumFrag.powerSpectrumComputed();
        }
    }
}
