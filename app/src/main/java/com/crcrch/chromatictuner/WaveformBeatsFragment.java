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

package com.crcrch.chromatictuner;

import android.support.annotation.NonNull;
import com.crcrch.chromatictuner.app.R;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.List;

public class WaveformBeatsFragment extends GraphFragment {
    private static final float DEFAULT_MIN_Y = -1;
    private static final float DEFAULT_MAX_Y = 1;
    private double referenceFrequency;

    public void setReferenceFrequency(double referenceFrequency) {
        this.referenceFrequency = referenceFrequency;
    }

    @Override
    protected LineDataSet createLineDataSet(@NonNull float[] data) {
        List<Entry> amplitudes = new ArrayList<>(data.length);
        for (int i = 0; i < data.length; i++) {
            amplitudes.add(new FloatArrayEntry(data, i));
        }
        return new LineDataSet(amplitudes, null);
    }

    @Override
    protected void configureGraph(LineChart graph, LineDataSet series) {
        graph.setDescription(String.format(
                getActivity().getString(R.string.graph_description_waveform_beats),
                referenceFrequency));

        graph.getXAxis().setDrawLabels(false);
        graph.getXAxis().setDrawGridLines(false);

        graph.getAxisLeft().setDrawLabels(false);
        graph.getAxisLeft().setDrawGridLines(false);
        graph.getAxisLeft().setDrawZeroLine(true);
        graph.getAxisLeft().setAxisMinValue(DEFAULT_MIN_Y);
        graph.getAxisLeft().setAxisMaxValue(DEFAULT_MAX_Y);

        graph.getAxisRight().setDrawLabels(false);
        graph.getAxisRight().setDrawGridLines(false);
        graph.getAxisRight().setDrawZeroLine(true);
        graph.getAxisRight().setAxisMinValue(DEFAULT_MIN_Y);
        graph.getAxisRight().setAxisMaxValue(DEFAULT_MAX_Y);

        graph.getLegend().setEnabled(false);

        series.setDrawCircles(false);
        series.setColor(R.color.app_primary);
    }
}
