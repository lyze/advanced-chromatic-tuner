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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
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

public class MainActivity extends RecordAudioActivity<Void, Integer, Void>
        implements NotePickerFragment.OnFrequencySelectedListener {
    private static final String TAG = "MainActivity";

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

    private static final String STATE_TUNING_FREQUENCY = "tuningFreq";

    private AnalysisConfiguration analysisConfig;

    private ProgressBar loadingView;
    private int shortAnimationDuration;

    private LinearLayout analysisView;

    private PowerSpectrumFragment powerSpectrumFrag;
    private WaveformBeatsFragment waveformFrag;

    private EditText freqInput;
    private double tuningFrequency;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        analysisConfig = new AnalysisConfiguration(this);

        if (savedInstanceState == null) {
            tuningFrequency = analysisConfig.getDefaultTuningFrequency();
        } else {
            tuningFrequency = savedInstanceState.getDouble(STATE_TUNING_FREQUENCY);
        }

        shortAnimationDuration = getResources().getInteger(android.R.integer.config_shortAnimTime);

        setContentView(R.layout.activity_main);

        loadingView = (ProgressBar) findViewById(R.id.loading_spinner);
        loadingView.setVisibility(View.GONE);

        analysisView = (LinearLayout) findViewById(R.id.analysis_view);

        powerSpectrumFrag = (PowerSpectrumFragment) getSupportFragmentManager().findFragmentById(
                R.id.power_spectrum);
        waveformFrag = (WaveformBeatsFragment) getSupportFragmentManager().findFragmentById(
                R.id.waveform);

        freqInput = (EditText) findViewById(R.id.tuning_frequency);
        setTuningFrequencyInputField(tuningFrequency);

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
                    if (f > analysisConfig.getMaxFrequency()) {
                        freqInput.setError(String.format(getString(
                                R.string.error_frequency_exceeded_maximum),
                                analysisConfig.getMaxFrequency()));
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

    private void setTuningFrequencyInputField(double f) {
        freqInput.setText(String.valueOf(f));
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
        audioAnalyzer = createAudioAnalyzer();
        audioAnalyzer.execute();
    }

    @Override
    protected AudioAnalyzer createAudioAnalyzer() {
        return new AudioAnalyzer(tuningFrequency, analysisConfig.getMinFrequencyBin(),
                analysisConfig.getFrequencyBinRatio(),
                analysisConfig.getNumFrequencyBins());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_constant_q:
                startActivity(new Intent(this, ConstantQActivity.class));
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
        tuningFrequency = savedInstanceState.getDouble(STATE_TUNING_FREQUENCY);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putDouble(STATE_TUNING_FREQUENCY, tuningFrequency);
    }

    @Override
    protected void onAudioRecordPermissionGranted() {
        super.onAudioRecordPermissionGranted();
        crossFade(analysisView, loadingView);
    }

    @Override
    protected void executeAudioAnalyzer(MyAsyncTask<Void, Integer, Void> audioAnalyzer) {
        audioAnalyzer.execute();
    }

    @Override
    protected void onRecordAudioPermissionDenied() {
        powerSpectrumFrag.getGraph().setNoDataTextDescription(
                getString(R.string.graph_no_data_description_permission_denied));
        waveformFrag.getGraph().setNoDataTextDescription(
                getString(R.string.graph_no_data_description_permission_denied));
    }

    private AudioRecord newAudioRecord(int encoding) {
        if (Build.VERSION.SDK_INT >= 23) {
            AudioRecord.Builder builder = new AudioRecord.Builder()
                    .setAudioSource(analysisConfig.getAudioSourceToUse())
                    .setAudioFormat(new AudioFormat.Builder().setEncoding(encoding).build());

            if (Build.VERSION.SDK_INT >= 24
                    && analysisConfig.getPreferredSampleRate() == AudioFormat.SAMPLE_RATE_UNSPECIFIED) {
                return builder.build();
            }
            return builder.setBufferSizeInBytes(analysisConfig.getBufferSizeToUse(encoding))
                    .build();
        }

        return new AudioRecord(analysisConfig.getAudioSourceToUse(),
                analysisConfig.getPreferredSampleRate(), AudioFormat.CHANNEL_IN_DEFAULT, encoding,
                analysisConfig.getBufferSizeToUse(encoding));
    }

    public void toggleLiveSpectrum(View view) {
        userPaused = !userPaused;
        audioAnalyzer.togglePaused();
    }

    @Override
    public void onFrequencySelected(double frequency) {
        setTuningFrequencyInputField(frequency);
    }

    public void showNotePicker(View view) {
        NotePickerFragment.newInstance(tuningFrequency, analysisConfig.getMaxFrequency())
                .show(getSupportFragmentManager(), null);
    }

    /**
     * Computes the power spectrum. This class should only be used in the visible lifecycle of
     * the app.
     */
    private class AudioAnalyzer extends MyAsyncTask<Void, Integer, Void> {
        private static final String TAG = "AudioAnalyzer";
        private final double tuningFrequency;
        private final double freqBinRatio;
        private final double minFreqBin;
        private final int numFreqBins;

        public AudioAnalyzer(double tuningFrequency, double minFreqBin, double freqBinRatio,
                             int numFreqBins) {
            if (tuningFrequency <= 0) {
                throw new IllegalArgumentException("non-positive frequency: " + tuningFrequency);
            }
            this.tuningFrequency = tuningFrequency;
            this.freqBinRatio = freqBinRatio;
            this.minFreqBin = minFreqBin;
            this.numFreqBins = numFreqBins;
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

            // TODO ensure sane constant Q performance
            Log.d(TAG, "Will use FFT of size "
                    + ConstantQTransform.getFftSize(sampleRate, minFreqBin, freqBinRatio));
            ConstantQTransform constantQ = new ConstantQTransform(null, sampleRate, minFreqBin,
                    freqBinRatio, numFreqBins);
            int numSamples = constantQ.getFftSize();

            float[] data = new float[2 * numSamples];

            float[] waveform = new float[numSamples];
            int numSamplesPerReferenceCycle = (int) (numSamples / tuningFrequency);

            // Since the number of samples taken will not always be exactly an integer multiple
            // of the reference frequency, we apply an offset to the sampled wave before adding
            // it to the reference wave.
            int referenceWaveformOffset =
                    numSamplesPerReferenceCycle - (numSamples % numSamplesPerReferenceCycle);

            float[] powerSpectrum = new float[constantQ.getNumCoefficients()];

            waveformFrag.setReferenceFrequency(tuningFrequency);
            waveformFrag.setData(waveform);

            powerSpectrumFrag.configureSpectrum(constantQ.getRatio(), constantQ.getMinFrequency());
            powerSpectrumFrag.setData(powerSpectrum);

            PcmFloatReader pcmFloatReader = new PcmFloatReader(audioRecord, numSamples);
            audioRecord.startRecording();

            publishProgress(0);

            try {
                maybePause();
            } catch (InterruptedException e) {
                return null;
            }

            while (!isCancelled()) {
                try {
                    maybePause();
                } catch (InterruptedException e) {
                    break;
                }
                int n = pcmFloatReader.read(data, 0, numSamples);
                if (n < 0) {
                    Log.e(TAG, "AudioRecord read error " + n);
                    publishProgress(-1);
                    break;
                }

                double referenceAmplitude = MiscMath.rms(data, 0, numSamples);

                int numReferenceSamples = (int) (numSamplesPerReferenceCycle * tuningFrequency);
                for (int i = 0; i < numReferenceSamples; i++) {
                    double t = (double) i / sampleRate;
                    double a = referenceAmplitude * Math.sin(2 * Math.PI * tuningFrequency * t);
                    waveform[i] = 0.5f * (float) a + 0.5f * data[i + referenceWaveformOffset];
                }

                constantQ.realConstantQPowerDbFull(data, powerSpectrum, P_0);

                publishProgress(1);
            }
            Log.d(TAG, "Stopping audio analysis...");
            audioRecord.stop();
            audioRecord.release();
            //noinspection UnusedAssignment
            audioRecord = null;
            return null;
        }


        @Override
        protected void onProgressUpdate(Integer... values) {
            switch (values[0]) {
                case -1:
                    Snackbar.make(findViewById(android.R.id.content),
                            R.string.error_audio_record_failure, Snackbar.LENGTH_INDEFINITE).show();
                    powerSpectrumFrag.getGraph().clear();
                    waveformFrag.getGraph().clear();
                    return;

                case 0:
                    crossFade(loadingView, analysisView);
                    return;

                case 1:
                    powerSpectrumFrag.notifyDataSetChanged();
                    waveformFrag.notifyDataSetChanged();
                    return;

                default:
                    Log.wtf(TAG, "unhandled progress update code: " + values[0]);
            }
        }
    }
}
