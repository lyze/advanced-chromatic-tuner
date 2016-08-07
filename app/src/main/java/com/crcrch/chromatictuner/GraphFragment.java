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

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.crcrch.chromatictuner.app.R;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

public abstract class GraphFragment extends Fragment {
    private static final String STATE_DATA = "data";

    private String graphDescription;

    private LineChart graph;
    private float[] data;

    @Override
    public void onInflate(Context context, AttributeSet attrs, Bundle savedInstanceState) {
        super.onInflate(context, attrs, savedInstanceState);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.GraphFragment);
        graphDescription = a.getString(R.styleable.GraphFragment_graphDescription);
        a.recycle();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_power_spectrum, container, false);

        graph = (LineChart) view.findViewById(R.id.graph);
        graph.setNoDataText(getString(R.string.graph_no_data));
        graph.setDescription(graphDescription);

        if (savedInstanceState != null) {
            float[] savedPowerSpectrum = savedInstanceState.getFloatArray(STATE_DATA);
            if (savedPowerSpectrum != null) {
                setData(savedPowerSpectrum);
            }
        }

        return view;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putFloatArray(STATE_DATA, data);
    }

    /**
     * Should be called to give this fragment the array that will be subsequently updated with the
     * power data.
     *
     * @param data the array that will be updated when {@link #notifyDataSetChanged()} is
     * called
     */
    public void setData(@NonNull float[] data) {
        this.data = data;

        LineDataSet series = createLineDataSet(data);
        LineData graphData = new LineData(series);
        graph.setData(graphData);

        configureGraph(graph, series);
    }

    protected abstract LineDataSet createLineDataSet(@NonNull float[] data);

    protected abstract void configureGraph(LineChart graph, LineDataSet series);

    public LineChart getGraph() {
        return graph;
    }

    /**
     * Should be called the underlying data is changed.
     */
    public void notifyDataSetChanged() {
        graph.notifyDataSetChanged();
        graph.invalidate();
    }
}
