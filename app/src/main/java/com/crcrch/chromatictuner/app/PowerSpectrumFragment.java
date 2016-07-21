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

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.List;


public class PowerSpectrumFragment extends Fragment {
    private static final float DEFAULT_MIN_Y = -100;
    private static final float DEFAULT_MAX_Y = 0;
//    private static final float DEFAULT_MIN_Y = 0;
//    private static final float DEFAULT_MAX_Y = 120;
    private static final float DEFAULT_MIN_X = 0;
    private static final float DEFAULT_MAX_X = 5000;
    private LineChart graph;

    public static PowerSpectrumFragment newInstance() {
        return new PowerSpectrumFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_power_spectrum, container, false);

        graph = (LineChart) view.findViewById(R.id.graph);
        graph.setNoDataText(getString(R.string.graph_no_data));
        graph.setDescription(getString(R.string.graph_description));

        return view;
    }

    /**
     * Should be called to give this fragment the array that will be subsequently updated with the
     * power spectrum.
     *
     * @param powerSpectrum the array that will hold results for when
     *                      {@link #powerSpectrumComputed()} is called
     * @param sampleRate    the sample rate
     */
    public void setPowerSpectrumArray(@NonNull float[] powerSpectrum, int sampleRate) {
        float frequencyBinSize = (float) sampleRate / powerSpectrum.length;
        List<Entry> magnitudes = new ArrayList<>(powerSpectrum.length);
        for (int i = 0; i < powerSpectrum.length; i++) {
            magnitudes.add(new FloatArrayConstantXScalingEntry(powerSpectrum, i, frequencyBinSize));
        }

        LineDataSet series = new LineDataSet(magnitudes, "power");
        LineData graphData = new LineData(series);
        graph.setData(graphData);

        series.setDrawCircles(false);
//        series.setCircleRadius(series.getLineWidth() / 2);
//        series.setCircleColor(R.color.app_primary);
        series.setColor(R.color.app_primary);

        graph.getAxisLeft().setAxisMinValue(DEFAULT_MIN_Y);
        graph.getAxisLeft().setAxisMaxValue(DEFAULT_MAX_Y);
        graph.getAxisRight().setAxisMinValue(DEFAULT_MIN_Y);
        graph.getAxisRight().setAxisMaxValue(DEFAULT_MAX_Y);
        graph.setVisibleXRangeMaximum(DEFAULT_MAX_X - DEFAULT_MIN_X);
        graph.moveViewToX(DEFAULT_MIN_X);
    }

    public LineChart getGraph() {
        return graph;
    }

    /**
     * Should be called when a power spectrum is computed.
     */
    public void powerSpectrumComputed() {
        graph.notifyDataSetChanged();
        graph.invalidate();
    }
}
