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

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import com.crcrch.chromatictuner.util.MyAsyncTask;

public abstract class RecordAudioActivity<Params, Progress, Result> extends AppCompatActivity {
    private static final int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 17;

    protected MyAsyncTask<Params, Progress, Result> audioAnalyzer;


    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            onAudioRecordPermissionGranted();
            return;
        }
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.RECORD_AUDIO)) {
            Snackbar.make(findViewById(android.R.id.content),
                    R.string.permission_record_audio_rationale,
                    Snackbar.LENGTH_INDEFINITE).setAction(android.R.string.ok,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            ActivityCompat.requestPermissions(RecordAudioActivity.this,
                                    new String[] {Manifest.permission.RECORD_AUDIO},
                                    MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
                        }
                    }).show();
        } else {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.RECORD_AUDIO},
                    MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_RECORD_AUDIO:
                if (grantResults.length > 0) {
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        onAudioRecordPermissionGranted();
                    } else {
                        onRecordAudioPermissionDenied();
                    }
                }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        audioAnalyzer.pause();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (audioAnalyzer != null) {
            audioAnalyzer.cancel(true);
            audioAnalyzer = null;
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        audioAnalyzer = createAudioAnalyzer();
    }

    @CallSuper
    protected void onAudioRecordPermissionGranted() {
        if (AsyncTask.Status.PENDING == audioAnalyzer.getStatus()) {
            executeAudioAnalyzer(audioAnalyzer);
        } else {
            audioAnalyzer.resume();
        }
    }

    protected abstract void executeAudioAnalyzer(
            @NonNull MyAsyncTask<Params, Progress, Result> audioAnalyzer);

    protected abstract void onRecordAudioPermissionDenied();

    protected abstract MyAsyncTask<Params, Progress, Result> createAudioAnalyzer();
}
