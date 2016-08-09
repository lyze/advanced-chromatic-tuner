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
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
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
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import com.crcrch.chromatictuner.PowerSpectrumFragment;
import com.crcrch.chromatictuner.WaveformBeatsFragment;
import com.crcrch.chromatictuner.analysis.ConstantQTransform;
import com.crcrch.chromatictuner.util.MiscMath;
import com.crcrch.chromatictuner.util.MyAsyncTask;

public class MainActivity extends AppCompatActivity
        implements NotePickerFragment.OnFrequencySelectedListener {
    private static final String TAG = "MainActivity";
    private static final String USE_UNPROCESSED_AUDIO_SOURCE = "use unprocessed audio source";
    private static final int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 17;
    private static final String STATE_USER_PAUSED = "userPaused";

    // The reference sound pressure level.
    //
    // From http://source.android.com/compatibility/4.4/android-4.4-cdd.xhtml#section-5.4:
    // "Audio input sensitivity SHOULD be set such that a 90 dB sound power level (SPL) source
    // at 1000 Hz yields RMS of 2500 for 16-bit samples."
    //
    // From https://en.wikipedia.org/wiki/Sound_power#Sound_power_level:
    // L_W = 10 log10 (P / P_0)
    //
    // From https://en.wikipedia.org/wiki/Sound_pressure#Sound_pressure_level:
    // L_p = 20 log10 (P / P_0)
    //
    // Assuming that the android specification is referring to pressure and not power, we should use
    // P_0 = P / 10^(L/20)
    private static final double P_0 = 2500 / Math.pow(10, 90.0 / 20); // TODO verify reference p_0

    private static final double FREQUENCY_RESOLUTION = 60; // ==> time resolution of 1/60 s
    private static final String STATE_TUNING_FREQUENCY = "tuningFreq";

    private AudioAnalyzer audioAnalyzer;

    private ProgressBar loadingView;
    private int shortAnimationDuration;

    private LinearLayout analysisView;

    private PowerSpectrumFragment powerSpectrumFrag;
    private WaveformBeatsFragment waveformFrag;

    private EditText freqInput;
    private double tuningFrequency;

    private boolean userPaused;

    private static int getSampleRateToUse() {
        if (Build.VERSION.SDK_INT >= 24) {
            return AudioFormat.SAMPLE_RATE_UNSPECIFIED;
        }
        return 44100;
    }

    private static int getBufferSizeToUse(int encoding) {
        int sampleRateToUse = getSampleRateToUse();
        int minBufferSize = AudioRecord.getMinBufferSize(sampleRateToUse,
                AudioFormat.CHANNEL_IN_DEFAULT, encoding);
        double fftBinResolution = sampleRateToUse / FREQUENCY_RESOLUTION;
        int computedBufferSize = (int) (2 * fftBinResolution);
        return Math.max(2 * minBufferSize, computedBufferSize);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            tuningFrequency = getDefaultTuningFrequency();
        } else {
            tuningFrequency = savedInstanceState.getDouble(STATE_TUNING_FREQUENCY);
        }

        setContentView(R.layout.activity_main);

        loadingView = (ProgressBar) findViewById(R.id.loading_spinner);
        loadingView.setVisibility(View.GONE);
        shortAnimationDuration = getResources().getInteger(android.R.integer.config_shortAnimTime);

        analysisView = (LinearLayout) findViewById(R.id.analysis_view);

        powerSpectrumFrag = (PowerSpectrumFragment) getSupportFragmentManager().findFragmentById(
                R.id.power_spectrum);
        waveformFrag = (WaveformBeatsFragment) getSupportFragmentManager().findFragmentById(
                R.id.waveform);

        freqInput = (EditText) findViewById(R.id.tuning_frequency);

        setTuningFrequencyEditText(tuningFrequency);
        // Changes to the frequency EditText in the view will reconfigure the analysis
        freqInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count,
                                          int after) { /* no-op*/ }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int after) {
                /* no-op*/
            }

            @Override
            public void afterTextChanged(Editable s) {
                try {
                    double f = Double.parseDouble(s.toString());
                    if (f <= 0) {
                        freqInput.setError(getString(R.string.error_not_positive_decimal));
                        return;
                    }
                    if (f > getMaxFrequency()) {
                        freqInput.setError(String.format(getString(
                                R.string.error_frequency_exceeded_maximum),
                                getMaxFrequency()));
                        return;
                    }

                    freqInput.setError(null);

                    if (tuningFrequency != f) {
                        tuningFrequency = f;
                        reconfigureAnalyzer();
                    }

                } catch (NumberFormatException e) {
                    freqInput.setError(getString(R.string.error_not_positive_decimal));
                }
            }
        });
    }

    private void setTuningFrequencyEditText(double f) {
        freqInput.setText(String.valueOf(f));
    }

    private double getDefaultTuningFrequency() { // TODO
        return 440;
    }

    private double getMaxFrequency() { // TODO also determine valid sampling rates
        return 880.69;
    }

    private void crossFade(final View toBeGone, View toBeVisible) {
        if (Build.VERSION.SDK_INT > 11) {
            toBeVisible.setAlpha(0f);
            toBeVisible.setVisibility(View.VISIBLE);
            toBeVisible.animate()
                    .alpha(1f)
                    .setDuration(shortAnimationDuration)
                    .setListener(null);

            toBeGone.animate()
                    .alpha(0f)
                    .setDuration(shortAnimationDuration)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            toBeGone.setVisibility(View.GONE);
                        }
                    });
        } else {
            toBeVisible.setVisibility(View.VISIBLE);
            toBeGone.setVisibility(View.GONE);
        }
    }

    private void reconfigureAnalyzer() {
        Log.d(TAG, "Reconfiguring audio analyzer...");
        crossFade(analysisView, loadingView);

        audioAnalyzer.cancel(true);
        audioAnalyzer = new AudioAnalyzer(tuningFrequency);
        audioAnalyzer.execute();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_analysis_settings:
                // TODO analysis settings
                return true;
            case R.id.action_app_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState.getBoolean(STATE_USER_PAUSED)) {
            userPaused = true;
        }
        tuningFrequency = savedInstanceState.getDouble(STATE_TUNING_FREQUENCY);
    }

    @Override
    public void onStart() {
        super.onStart();
        audioAnalyzer = new AudioAnalyzer(tuningFrequency);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startAudioAnalyzer();
            return;
        }
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.RECORD_AUDIO)) {
            Snackbar.make(getWindow().getDecorView().getRootView(),
                    R.string.permission_record_audio_rationale,
                    Snackbar.LENGTH_INDEFINITE).setAction(android.R.string.ok,
                    new View.OnClickListener() {
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
                        startAudioAnalyzer();
                    } else {
                        powerSpectrumFrag.getGraph().setNoDataTextDescription(
                                getString(R.string.graph_no_data_description_permission_denied));
                        waveformFrag.getGraph().setNoDataTextDescription(
                                getString(R.string.graph_no_data_description_permission_denied));
                        Log.e(TAG, "Record audio permission denied.");
                    }
                }
        }
    }

    private void startAudioAnalyzer() {
        if (AsyncTask.Status.PENDING == audioAnalyzer.getStatus()) {
            if (userPaused) {
                audioAnalyzer.pause();
            }
            audioAnalyzer.execute();
        } else if (!userPaused) {
            audioAnalyzer.resume();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (userPaused) {
            outState.putBoolean(STATE_USER_PAUSED, true);
            outState.putDouble(STATE_TUNING_FREQUENCY, tuningFrequency);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        audioAnalyzer.pause();
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

            AudioRecord.Builder builder = new AudioRecord.Builder()
                    .setAudioSource(getAudioSourceToUse())
                    .setAudioFormat(new AudioFormat.Builder().setEncoding(encoding).build());

            if (Build.VERSION.SDK_INT >= 24
                    && getSampleRateToUse() == AudioFormat.SAMPLE_RATE_UNSPECIFIED) {
                return builder.build();
            }
            return builder.setBufferSizeInBytes(getBufferSizeToUse(encoding)).build();
        }

        return new AudioRecord(getAudioSourceToUse(), getSampleRateToUse(),
                AudioFormat.CHANNEL_IN_DEFAULT, encoding, getBufferSizeToUse(encoding));
    }

    public void toggleLiveSpectrum(View view) {
        userPaused = !userPaused;
        audioAnalyzer.togglePaused();
    }

    @Override
    public void onFrequencySelected(double frequency) {
        setTuningFrequencyEditText(frequency);
    }

    public void showNotePicker(View view) {
        NotePickerFragment.newInstance(tuningFrequency, getMaxFrequency())
                .show(getSupportFragmentManager(), null);
    }

    /**
     * Computes the power spectrum. This class should only be used in the visible lifecycle of
     * the app.
     */
    private class AudioAnalyzer extends MyAsyncTask<Void, Boolean, Void> {
        private static final String TAG = "AudioAnalyzer";
        private final double tuningFrequency;

        public AudioAnalyzer(double tuningFrequency) {
            if (tuningFrequency <= 0) {
                throw new IllegalArgumentException("nonpositive frequency: " + tuningFrequency);
            }
            this.tuningFrequency = tuningFrequency;
        }

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

            ConstantQTransform constantQ = ConstantQTransform.new12TetConstantQTransform(null,
                    sampleRate, tuningFrequency / Math.pow(2, 7.0 / 12), 36); // TODO center transform and ensure sane performance
            int numSamples = constantQ.getNumSamples();

            float[] data = new float[2 * numSamples];
            short[] data16Bit;
            if (Build.VERSION.SDK_INT >= 23) {
                data16Bit = new short[0];
            } else {
                data16Bit = new short[numSamples];
            }

            float[] waveform = new float[numSamples];
            int numSamplesPerReferenceCycle = (int) (numSamples / tuningFrequency);

            // Since the number of samples taken will not always be exactly an integer multiple
            // of the reference frequency, we apply an offset to the sampled wave before adding
            // it to the reference wave.
            int referenceWaveformOffset =
                    numSamplesPerReferenceCycle - (numSamples % numSamplesPerReferenceCycle);

            float[] powerSpectrum = new float[constantQ.getNumCoefficients()];

            try {
                maybePause();
            } catch (InterruptedException e) {
                return null;
            }

            waveformFrag.setReferenceFrequency(tuningFrequency);
            waveformFrag.setData(waveform);

            powerSpectrumFrag.configureSpectrum(constantQ.getRatio(), constantQ.getMinFrequency());
            powerSpectrumFrag.setData(powerSpectrum);

            audioRecord.startRecording();

            publishProgress(true);

            while (!isCancelled()) {
                try {
                    maybePause();
                } catch (InterruptedException e) {
                    break;
                }
                if (Build.VERSION.SDK_INT >= 23) {
                    audioRecord.read(data, 0, numSamples, AudioRecord.READ_BLOCKING);
                } else {
                    audioRecord.read(data16Bit, 0, numSamples);
                    for (int i = 0; i < data16Bit.length; i++) {
                        data[i] = data16Bit[i] > 0 ? data16Bit[i] / 32767f : data16Bit[i] / 32768f;
                    }
                }

                double referenceAmplitude = MiscMath.rms(data, 0, numSamples);

                int numReferenceSamples = (int) (numSamplesPerReferenceCycle * tuningFrequency);
                for (int i = 0; i < numReferenceSamples; i++) {
                    double t = (double) i / sampleRate;
                    double a = referenceAmplitude * Math.sin(2 * Math.PI * tuningFrequency * t);
                    waveform[i] = 0.5f * (float) a + 0.5f * data[i + referenceWaveformOffset];
                }

                constantQ.realConstantQPowerDbFull(data, powerSpectrum, P_0);

                publishProgress(false);
            }
            Log.d(TAG, "Stopping audio analysis...");
            audioRecord.stop();
            audioRecord.release();
            //noinspection UnusedAssignment
            audioRecord = null;
            return null;
        }


        @Override
        protected void onProgressUpdate(Boolean... values) {
            if (values[0]) {
                crossFade(loadingView, analysisView);
            } else {
                waveformFrag.notifyDataSetChanged();
                powerSpectrumFrag.notifyDataSetChanged();
            }
        }
    }
}
