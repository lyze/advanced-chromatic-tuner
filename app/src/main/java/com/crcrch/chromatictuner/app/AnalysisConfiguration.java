package com.crcrch.chromatictuner.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import com.crcrch.chromatictuner.util.MiscMath;
import com.crcrch.chromatictuner.util.MiscMusic;
import com.crcrch.chromatictuner.util.SharedPreferencesUtils;

import java.util.ArrayList;
import java.util.List;

public class AnalysisConfiguration {
    private static final String PREF_USE_UNPROCESSED_AUDIO_SOURCE = "use unprocessed audio source";
    private static final String PREF_SAMPLE_RATE = "sample rate";
    private static final String PREF_DEFAULT_TUNING_FREQUENCY = "default tuning frequency";
    private static final String PREF_FREQ_BIN_RATIO = "frequency bin ratio";
    private static final String PREF_MIN_FREQ_BIN = "minimum frequency bin";
    private static final String PREF_NUM_FREQUENCY_BINS = "number of frequency bins";

    private static final int FALLBACK_SAMPLE_RATE = 44100; // guaranteed to be available
    private static final double DEFAULT_TUNING_FREQUENCY = MiscMusic.A4;
    private static final double DEFAULT_FREQ_BIN_RATIO =
            Math.pow(2, 1.0 / MiscMusic.CHROMATIC_SCALE.length);
    private static final int DEFAULT_NUM_FREQUENCY_BINS = 2 * MiscMusic.CHROMATIC_SCALE.length + 1;

    private static final int[] UNVERIFIED_SAMPLE_RATES = new int[] {
            8000, 11025, 16000, 22050, 32000, 37800, 44056, 47250, 48000, 50000, 50400, 88200,
            96000, 176400, 192000, 325800, 2822400, 5644800
    };

    private static final double FREQUENCY_RESOLUTION = 60; // ==> time resolution of 1/60

    private final SharedPreferences pref;
    private final Context context;

    protected AnalysisConfiguration(Context context) {
        this.context = context;
        pref = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static AnalysisConfiguration from(Context context) {
        return new AnalysisConfiguration(context);
    }

    private static int getAudioEncoding() {
        if (Build.VERSION.SDK_INT >= 21) {
            return AudioFormat.ENCODING_PCM_FLOAT;
        }
        return AudioFormat.ENCODING_PCM_16BIT;
    }

    private static int getDefaultSampleRate() {
        if (Build.VERSION.SDK_INT >= 24) {
            return AudioFormat.SAMPLE_RATE_UNSPECIFIED;
        }
        return FALLBACK_SAMPLE_RATE;
    }

    public double getDefaultTuningFrequency() {
        return SharedPreferencesUtils.getDouble(pref, PREF_DEFAULT_TUNING_FREQUENCY,
                DEFAULT_TUNING_FREQUENCY);
    }

    public int getPreferredSampleRate() {
        return pref.getInt(PREF_SAMPLE_RATE, getDefaultSampleRate());
    }

    private int guessSampleRateToUse() {
        int r = getPreferredSampleRate();
        if (r != AudioFormat.SAMPLE_RATE_UNSPECIFIED) {
            return r;
        }
        return FALLBACK_SAMPLE_RATE;
    }

    @NonNull
    public List<Integer> getAvailableSampleRates() { // TODO put this method somewhere sensible
        List<Integer> sampleRates = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 24) {
            sampleRates.add(AudioFormat.SAMPLE_RATE_UNSPECIFIED);
        }
        for (int r : UNVERIFIED_SAMPLE_RATES) {
            if (AudioRecord.getMinBufferSize(r, AudioFormat.CHANNEL_IN_DEFAULT,
                    getAudioEncoding()) > 0) {
                sampleRates.add(r);
            }
        }
        return sampleRates;
    }

    public int getAudioBufferSizeForFft(int encoding) {
        int sampleRateToUse = guessSampleRateToUse();
        int minBufferSize = AudioRecord.getMinBufferSize(sampleRateToUse,
                AudioFormat.CHANNEL_IN_DEFAULT, encoding);

        int minFftBufferSize = (int) (sampleRateToUse / FREQUENCY_RESOLUTION);
        int computedFftBufferSize =
                minBufferSize * MiscMath.divideRoundingUp(minFftBufferSize, minBufferSize);

        return Math.max(2 * minBufferSize, computedFftBufferSize);
    }

    public int getAudioBufferSize(int encoding) {
        int sampleRateToUse = guessSampleRateToUse();
        int minBufferSize = AudioRecord.getMinBufferSize(sampleRateToUse,
                AudioFormat.CHANNEL_IN_DEFAULT, encoding);
        return 2 * minBufferSize;
    }

    public double getMaxFrequency() {
        return guessSampleRateToUse() / 2;
    }

    public int getNumFrequencyBins() {
        return pref.getInt(PREF_NUM_FREQUENCY_BINS, DEFAULT_NUM_FREQUENCY_BINS);
    }

    public double getFrequencyBinRatio() {
        return SharedPreferencesUtils.getDouble(pref, PREF_FREQ_BIN_RATIO, DEFAULT_FREQ_BIN_RATIO);
    }

    public double getMinFrequencyBin() {
        double f = SharedPreferencesUtils.getDouble(pref, PREF_MIN_FREQ_BIN, -1.0);
        if (f > 0) {
            return f;
        }
        return getDefaultTuningFrequency()
                / Math.pow(getFrequencyBinRatio(), getNumFrequencyBins() / 2);
    }

    public int getAudioSourceToUse() {
        int audioSource;
        if (Build.VERSION.SDK_INT >= 24) {
            if (pref.getBoolean(PREF_USE_UNPROCESSED_AUDIO_SOURCE, true)) {
                audioSource = MediaRecorder.AudioSource.UNPROCESSED;
            } else {
                audioSource = MediaRecorder.AudioSource.MIC;
            }
        } else {
            audioSource = MediaRecorder.AudioSource.MIC;
        }
        return audioSource;
    }
}