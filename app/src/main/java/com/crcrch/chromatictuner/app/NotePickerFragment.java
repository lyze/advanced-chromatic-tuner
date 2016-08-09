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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.NumberPicker;
import android.widget.TextView;

public class NotePickerFragment extends DialogFragment {
    private static final int MAX_OCTAVE = 8;
    private static final String[] CHROMATIC_SCALE = new String[] {
            "C", "C♯/D♭", "D", "E♭/D♯", "E", "F", "F♯/G♭", "G", "A♭/G♯", "A", "B♭/A♯", "B"};
    private static final String ARG_INITIAL_FREQ = "initialFreq";
    private static final String STATE_FREQUENCY = "frequency";
    private static final double INITIAL_FREQUENCY_FALLBACK = 440;
    private static final String TAG = "NotePickerFragment";

    private OnFrequencySelectedListener listener;

    private double initialFrequency;

    private double frequency;

    /**
     * Creates a new instance of a {@code NotePickerFragment}.
     *
     * @param initialFreq the frequency that will be displayed initially
     * @param maxFreq the maximum pickable frequency
     * @return a new {@code NotePickerFragment} instance
     */
    public static NotePickerFragment newInstance(double initialFreq, double maxFreq) {
        NotePickerFragment fragment = new NotePickerFragment();
        Bundle args = new Bundle();
        args.putDouble(ARG_INITIAL_FREQ, initialFreq);
        fragment.setArguments(args);
        return fragment;
    }

    private static int getNoteForFrequency(double f) {
        return (int) (CHROMATIC_SCALE.length * Math.log(f / 440) / Math.log(2)) + 57;
    }

    private static int getOctaveForNote(int note) {
        return note / CHROMATIC_SCALE.length;
    }

    private static double getFrequencyForNote(int note) {
        return Math.pow(2, (note - 57.0) / CHROMATIC_SCALE.length) * 440;
    }

    private static int getOctaveForFrequency(double f) {
        return getOctaveForNote(getNoteForFrequency(f));
    }

    private static double getFrequencyForNoteIndexAndOctave(int i, int octave) {
        return getFrequencyForNote(octave * CHROMATIC_SCALE.length + i);
    }

    private static int getNoteIndexForNote(int note) {
        return note % CHROMATIC_SCALE.length;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            initialFrequency = getArguments().getDouble(ARG_INITIAL_FREQ);
        }
        if (initialFrequency <= 0) {
            Log.w(TAG, "Invalid initial frequency: " + initialFrequency);
            initialFrequency = INITIAL_FREQUENCY_FALLBACK;
        }
    }

    private String selectFrequency(double f) {
        frequency = f;
        return String.format("%.2f %s", frequency, getString(R.string.hz));
    }

    private String selectFrequency(int i, int octave) {
        frequency = getFrequencyForNoteIndexAndOctave(i, octave);
        return selectFrequency(frequency);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getContext());

        View dialogContent = LayoutInflater.from(dialogBuilder.getContext())
                .inflate(R.layout.fragment_note_picker, null);

        final NumberPicker notePicker = (NumberPicker) dialogContent.findViewById(R.id.picker_note);
        notePicker.setMinValue(0);
        notePicker.setMaxValue(CHROMATIC_SCALE.length - 1);
        notePicker.setDisplayedValues(CHROMATIC_SCALE);
        notePicker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);

        final NumberPicker octavePicker = (NumberPicker) dialogContent.findViewById(R.id.picker_octave);
        octavePicker.setMinValue(0);
        octavePicker.setMaxValue(MAX_OCTAVE);
        octavePicker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        octavePicker.setWrapSelectorWheel(false);

        final TextView frequencyView = (TextView) dialogContent.findViewById(R.id.frequency);
        notePicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                frequencyView.setText(selectFrequency(newVal, octavePicker.getValue()));
            }
        });
        octavePicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                frequencyView.setText(selectFrequency(notePicker.getValue(), newVal));
            }
        });

        double frequencyToDisplay;
        if (savedInstanceState == null) {
            frequencyToDisplay = initialFrequency;
        } else {
            frequencyToDisplay = savedInstanceState.getDouble(STATE_FREQUENCY);
        }
        frequencyView.setText(selectFrequency(frequencyToDisplay));
        int note = getNoteForFrequency(frequencyToDisplay);
        octavePicker.setValue(getOctaveForNote(note));
        octavePicker.invalidate();
        notePicker.setValue(getNoteIndexForNote(note));
        notePicker.invalidate();

        return dialogBuilder
                .setTitle(R.string.dialog_title_note_picker)
                .setView(dialogContent)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        listener.onFrequencySelected(frequency);
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putDouble(STATE_FREQUENCY, frequency);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            listener = (OnFrequencySelectedListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(
                    context + " must implement OnFrequencySelectedListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    public interface OnFrequencySelectedListener {
        void onFrequencySelected(double frequency);
    }
}
