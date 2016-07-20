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
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.*;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity
        implements AudioAnalyzerFragment.OnSpectrumCalculatedListener {

    private static final String TAG = "MainActivity";
    private LineChart graph;
    private LineData graphData;
    private float[] frequencySpectrum;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        PagerAdapter pagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());
//        ViewPager viewPager = (ViewPager) findViewById(R.id.pager);
//        viewPager.setAdapter(pagerAdapter);

//        TabLayout tabLayout = (TabLayout) findViewById(R.id.tab_layout);
//        tabLayout.setupWithViewPager(viewPager);

        FragmentManager fm = getSupportFragmentManager();
        AudioAnalyzerFragment analyzerFragment =
                (AudioAnalyzerFragment) fm.findFragmentByTag("analyzer");
        if (analyzerFragment == null) {
            analyzerFragment = AudioAnalyzerFragment.newInstance();
            fm.beginTransaction().add(analyzerFragment, "analyzer").commit();
        }

        graph = (LineChart) findViewById(R.id.graph);
        graph.setNoDataTextDescription(getString(R.string.graph_no_data));
        graph.setDescription(getString(R.string.graph_description_frequency));
        graph.setPinchZoom(true);
    }

    @Override
    public void onFrequencySpectrumCalculated(@NonNull float[] frequencySpectrum) {
        if (graphData == null || this.frequencySpectrum != frequencySpectrum) {
            this.frequencySpectrum = frequencySpectrum;
            List<Entry> realParts = new ArrayList<>(frequencySpectrum.length / 2);
            List<Entry> imagParts = new ArrayList<>(frequencySpectrum.length / 2);
            for (int i = 0; i < frequencySpectrum.length / 2; i++) {
                realParts.add(new FloatArrayBackedEntry(frequencySpectrum, 2 * i));
                imagParts.add(new FloatArrayBackedEntry(frequencySpectrum, 2 * i + 1));
            }
            LineDataSet realData = new LineDataSet(realParts, "real");
            realData.setCircleRadius(realData.getLineWidth() / 2);
            realData.setCircleColor(R.color.app_primary);
            realData.setColor(R.color.app_primary);

            LineDataSet imagData = new LineDataSet(imagParts, "imaginary");
            imagData.setCircleRadius(imagData.getLineWidth() / 2);
            imagData.setCircleColor(R.color.app_accent);
            imagData.setCircleColor(R.color.app_accent);

            graphData = new LineData(realData, imagData);
            graph.setData(graphData);
        } else {
            graphData.notifyDataChanged();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
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

    public static class RecorderFragment extends Fragment {
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        public PlaceholderFragment() {
        }

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static PlaceholderFragment newInstance(int sectionNumber) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_main, container, false);
        }
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    private class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            return PlaceholderFragment.newInstance(position + 1);
        }

        @Override
        public int getCount() {
            // Show 3 total pages.
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            Locale l = Locale.getDefault();
            switch (position) {
                case 0:
                    return getString(R.string.title_section1).toUpperCase(l);
                case 1:
                    return getString(R.string.title_section2).toUpperCase(l);
                case 2:
                    return getString(R.string.title_section3).toUpperCase(l);
            }
            return null;
        }
    }

}
