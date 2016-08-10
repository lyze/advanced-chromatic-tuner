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

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.crcrch.chromatictuner.app.R;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.AxisValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class PowerSpectrumFragment extends GraphFragment {
    private static final float DEFAULT_MIN_Y = 0;
    private static final float DEFAULT_MAX_Y = 120;
    private static final float DEFAULT_MIN_X = 0;
    private static final float DEFAULT_MAX_X = 5000;
    private static final String STATE_R = "r";
    private static final String STATE_MIN_FREQ = "minFreq";
    private double r;
    private double minFreq;

    public void configureSpectrum(double r, double minFreq) {
        this.r = r;
        this.minFreq = minFreq;
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState != null) {
            r = savedInstanceState.getDouble(STATE_R);
            minFreq = savedInstanceState.getDouble(STATE_MIN_FREQ);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putDouble(STATE_R, r);
        outState.putDouble(STATE_MIN_FREQ, minFreq);
    }

    @Override
    protected void configureGraph(final LineChart graph, LineDataSet series) {
        series.setDrawCircles(false);
        series.setColor(R.color.app_primary);

        graph.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                graph.highlightValue(h);
            }

            @Override
            public void onNothingSelected() {
                // no-op
            }
        });

        graph.getLegend().setEnabled(false);

        graph.getXAxis().setValueFormatter(new UnLogarithmicValueFormatter(r, minFreq));

        graph.getAxisLeft().setAxisMinValue(DEFAULT_MIN_Y);
        graph.getAxisLeft().setAxisMaxValue(DEFAULT_MAX_Y);

        graph.getAxisRight().setAxisMinValue(DEFAULT_MIN_Y);
        graph.getAxisRight().setAxisMaxValue(DEFAULT_MAX_Y);

        graph.setVisibleXRangeMaximum(DEFAULT_MAX_X - DEFAULT_MIN_X);
        graph.moveViewToX(DEFAULT_MIN_X);
    }

    private static class UnLogarithmicValueFormatter implements AxisValueFormatter {
        private static final DecimalFormat formatter = new DecimalFormat("#.##");
        private final double r;
        private final double minFreq;

        public UnLogarithmicValueFormatter(double r, double minFreq) {
            this.r = r;
            this.minFreq = minFreq;
        }

        @Override
        public String getFormattedValue(float value, AxisBase axis) {
            return formatter.format(minFreq * Math.pow(r, value));
        }

        @Override
        public int getDecimalDigits() {
            return -1;
        }
    }
}
