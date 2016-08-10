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
import com.crcrch.chromatictuner.WaveformBeatsFragment;
import com.crcrch.chromatictuner.util.AnimationUtils;
import com.crcrch.chromatictuner.util.MiscMath;
import com.crcrch.chromatictuner.util.MyAsyncTask;

public class MainActivity extends RecordAudioActivity<Void, Integer, Void>
        implements NotePickerFragment.OnFrequencySelectedListener {
    private static final String TAG = "MainActivity";

    private static final String STATE_TUNING_FREQUENCY = "tuningFreq";

    private AnalysisConfiguration analysisConfig;

    private ProgressBar loadingView;
    private int shortAnimationDuration;

    private LinearLayout analysisView;

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

        analysisView = (LinearLayout) findViewById(R.id.analysis_view);

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

    private void reconfigureAnalyzer() {
        Log.d(TAG, "Reconfiguring audio analyzer...");
        AnimationUtils.switchOutIn(analysisView, loadingView);

        audioAnalyzer.cancel(true);
        audioAnalyzer = createAudioAnalyzer();
        audioAnalyzer.execute();
    }

    @Override
    protected AudioAnalyzer createAudioAnalyzer() {
        return new AudioAnalyzer(tuningFrequency);
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
        AnimationUtils.switchOutIn(analysisView, loadingView);
        super.onAudioRecordPermissionGranted();
    }

    @Override
    protected void executeAudioAnalyzer(@NonNull MyAsyncTask<Void, Integer, Void> audioAnalyzer) {
        audioAnalyzer.execute();
    }

    @Override
    protected void onRecordAudioPermissionDenied() {
        waveformFrag.getGraph().setNoDataTextDescription(
                getString(R.string.graph_no_data_description_permission_denied));
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
        private static final int UPDATES_PER_SECOND = 60;
        private final double tuningFrequency;

        public AudioAnalyzer(double tuningFrequency) {
            if (tuningFrequency <= 0) {
                throw new IllegalArgumentException("non-positive frequency: " + tuningFrequency);
            }
            this.tuningFrequency = tuningFrequency;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            Log.d(TAG, "Starting audio analysis...");
            AudioRecord audioRecord;
            if (Build.VERSION.SDK_INT >= 23) {
                audioRecord = new AudioRecord(analysisConfig.getAudioSourceToUse(),
                        analysisConfig.getPreferredSampleRate(), AudioFormat.CHANNEL_IN_DEFAULT,
                        AudioFormat.ENCODING_PCM_FLOAT,
                        analysisConfig.getAudioBufferSize(AudioFormat.ENCODING_PCM_FLOAT));
            } else {
                audioRecord = new AudioRecord(analysisConfig.getAudioSourceToUse(),
                        analysisConfig.getPreferredSampleRate(), AudioFormat.CHANNEL_IN_DEFAULT,
                        AudioFormat.ENCODING_PCM_16BIT,
                        analysisConfig.getAudioBufferSize(AudioFormat.ENCODING_PCM_16BIT));
            }

            int sampleRate = audioRecord.getSampleRate();
            float[] data = new float[sampleRate / UPDATES_PER_SECOND];
            float[] waveform = new float[data.length];

            double framesPerDroneCycle = sampleRate / tuningFrequency;
            int totalCompleteDroneFrames = (int) (data.length / framesPerDroneCycle);
            int dataWaveOffsetDelta = data.length - totalCompleteDroneFrames;
            int dataWaveOffset = 0;
            int droneWaveOffsetDelta = data.length % (int) framesPerDroneCycle;
            int droneWaveOffset = 0;

            publishProgress(0);

            try {
                maybePause();
            } catch (InterruptedException e) {
                return null;
            }

            waveformFrag.setReferenceFrequency(tuningFrequency);
            waveformFrag.setData(waveform);

            PcmFloatReader pcmFloatReader = new PcmFloatReader(audioRecord, data.length);
            audioRecord.startRecording();

            while (!isCancelled()) {
                try {
                    maybePause();
                } catch (InterruptedException e) {
                    break;
                }
                int n = pcmFloatReader.read(data, 0, data.length);
                if (n < 0) {
                    Log.e(TAG, "AudioRecord read error " + n);
                    publishProgress(-1);
                    break;
                }

                double droneAmplitude = MiscMath.rms(data, 0, data.length);
                for (int i = 0; i < data.length - dataWaveOffset; i++) {
                    double t = (double) i / sampleRate;
                    double a = droneAmplitude * Math.sin(tuningFrequency * t);
                    waveform[i] = 0.5f * (float) a + 0.5f * data[i + dataWaveOffset];
                }
                for (int i = data.length - dataWaveOffset; i < data.length; i++) {
                    waveform[i] = 0f;
                }
                dataWaveOffset += dataWaveOffsetDelta;
                dataWaveOffset %= framesPerDroneCycle;
                droneWaveOffset += droneWaveOffsetDelta;
                droneWaveOffset %= framesPerDroneCycle;

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
            if (isCancelled()) {
                return;
            }
            switch (values[0]) {
                case -1:
                    Snackbar.make(findViewById(android.R.id.content),
                            R.string.error_audio_record_failure, Snackbar.LENGTH_INDEFINITE).show();
                    waveformFrag.getGraph().clear();
                    return;

                case 0:
                    AnimationUtils.crossFade(loadingView, analysisView, shortAnimationDuration);
                    return;

                case 1:
                    waveformFrag.notifyDataSetChanged();
                    return;

                default:
                    Log.wtf(TAG, "unhandled progress update code: " + values[0]);
            }
        }
    }
}
