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

import android.graphics.drawable.Drawable;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import com.crcrch.chromatictuner.PowerSpectrumFragment;
import com.crcrch.chromatictuner.analysis.ConstantQTransform;
import com.crcrch.chromatictuner.util.AnimationUtils;
import com.crcrch.chromatictuner.util.MyAsyncTask;

public class ConstantQActivity extends RecordAudioActivity<Void, Integer, Void> {
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

    private static final String STATE_USER_PAUSED = "userPaused";

    private PowerSpectrumFragment powerSpectrumFrag;
    private AnalysisConfiguration analysisConfig;
    private int shortAnimationDuration;
    private ProgressBar loadingView;
    private FloatingActionButton pausePlay;

    private Drawable pauseIcon;
    private Drawable playIcon;

    private boolean userPaused;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_constant_q);

        loadingView = (ProgressBar) findViewById(R.id.loading_spinner);
        shortAnimationDuration = getResources().getInteger(android.R.integer.config_shortAnimTime);

        pauseIcon = ContextCompat.getDrawable(this, R.drawable.ic_pause_black_24dp);
        playIcon = ContextCompat.getDrawable(this, R.drawable.ic_play_arrow_black_24dp);

        analysisConfig = new AnalysisConfiguration(this);

        powerSpectrumFrag = (PowerSpectrumFragment) getSupportFragmentManager().findFragmentById(
                R.id.power_spectrum);
        pausePlay = (FloatingActionButton) findViewById(R.id.floating_toggle);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_constant_q, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState.getBoolean(STATE_USER_PAUSED)) {
            userPaused = true;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (userPaused) {
            outState.putBoolean(STATE_USER_PAUSED, true);
        }
    }

    @Override
    protected void onAudioRecordPermissionGranted() {
        AnimationUtils.switchOutIn(powerSpectrumFrag.getView(), loadingView);
        super.onAudioRecordPermissionGranted();
    }

    @Override
    protected void executeAudioAnalyzer(@NonNull MyAsyncTask<Void, Integer, Void> audioAnalyzer) {
        if (userPaused) {
            audioAnalyzer.pause();
        }
        audioAnalyzer.execute();
    }

    @Override
    protected void onRecordAudioPermissionDenied() {
        powerSpectrumFrag.getGraph().setNoDataTextDescription(getString(
                R.string.graph_no_data_description_permission_denied));
    }

    @Override
    protected MyAsyncTask<Void, Integer, Void> createAudioAnalyzer() {
        return new AudioAnalyzer(analysisConfig.getMinFrequencyBin(),
                analysisConfig.getFrequencyBinRatio(),
                analysisConfig.getNumFrequencyBins());
    }

    public void toggleLiveSpectrum(View view) {
        audioAnalyzer.togglePaused();
        userPaused = !userPaused;
        updatePausePlayButton();
    }

    private void updatePausePlayButton() {
        if (userPaused) {
            pausePlay.setImageDrawable(playIcon);
        } else {
            pausePlay.setImageDrawable(pauseIcon);
        }
    }

    /**
     * Computes the power spectrum. This class should only be used in the visible lifecycle of
     * the app.
     */
    private class AudioAnalyzer extends MyAsyncTask<Void, Integer, Void> {
        private static final String TAG = "AudioAnalyzer";
        private final double freqBinRatio;
        private final double minFreqBin;
        private final int numFreqBins;

        public AudioAnalyzer(double minFreqBin, double freqBinRatio, int numFreqBins) {
            this.freqBinRatio = freqBinRatio;
            this.minFreqBin = minFreqBin;
            this.numFreqBins = numFreqBins;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            Log.d(TAG, "Starting audio analysis...");
            AudioRecord audioRecord;
            if (Build.VERSION.SDK_INT >= 23) {
                audioRecord = new AudioRecord(analysisConfig.getAudioSourceToUse(),
                        analysisConfig.getPreferredSampleRate(), AudioFormat.CHANNEL_IN_DEFAULT,
                        AudioFormat.ENCODING_PCM_FLOAT,
                        analysisConfig.getAudioBufferSizeForFft(AudioFormat.ENCODING_PCM_FLOAT));
            } else {
                audioRecord = new AudioRecord(analysisConfig.getAudioSourceToUse(),
                        analysisConfig.getPreferredSampleRate(), AudioFormat.CHANNEL_IN_DEFAULT,
                        AudioFormat.ENCODING_PCM_16BIT,
                        analysisConfig.getAudioBufferSizeForFft(AudioFormat.ENCODING_PCM_16BIT));
            }

            int sampleRate = audioRecord.getSampleRate();

            Log.d(TAG, "Will use FFT of size "
                    + ConstantQTransform.getFftSize(sampleRate, minFreqBin, freqBinRatio));
            ConstantQTransform constantQ = new ConstantQTransform(null, sampleRate, minFreqBin,
                    freqBinRatio, numFreqBins);
            int numSamples = constantQ.getFftSize();

            float[] data = new float[2 * numSamples];
            float[] powerSpectrum = new float[constantQ.getNumCoefficients()];

            publishProgress(0);

            try {
                maybePause();
            } catch (InterruptedException e) {
                return null;
            }

            powerSpectrumFrag.configureSpectrum(constantQ.getRatio(), constantQ.getMinFrequency());
            powerSpectrumFrag.setData(powerSpectrum);

            PcmFloatReader pcmFloatReader = new PcmFloatReader(audioRecord, numSamples);
            audioRecord.startRecording();

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
            if (isCancelled()) {
                return;
            }
            switch (values[0]) {
                case -1:
                    Snackbar.make(findViewById(R.id.coordinator),
                            R.string.error_audio_record_failure, Snackbar.LENGTH_INDEFINITE).show();
                    powerSpectrumFrag.getGraph().clear();
                    return;

                case 0:
                    AnimationUtils.crossFade(loadingView, powerSpectrumFrag.getView(),
                            shortAnimationDuration);
                    updatePausePlayButton();
                    pausePlay.show();
                    return;

                case 1:
                    powerSpectrumFrag.notifyDataSetChanged();
                    return;

                default:
                    Log.wtf(TAG, "unhandled progress update code: " + values[0]);
            }
        }
    }
}
