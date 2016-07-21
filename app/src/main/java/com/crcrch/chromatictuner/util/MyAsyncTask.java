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

package com.crcrch.chromatictuner.util;

import android.os.AsyncTask;

public abstract class MyAsyncTask<Params, Progress, Result>
        extends AsyncTask<Params, Progress, Result> {
    private final Object lock;
    private boolean paused;
    public MyAsyncTask() {
        lock = new Object();
    }

    protected void maybePause() throws InterruptedException {
        synchronized (lock) {
            while (paused) {
                lock.wait();
            }
        }
    }

    public void pause() {
        synchronized (lock) {
            paused = true;
            lock.notify();
        }
    }

    public void resume() {
        synchronized (lock) {
            paused = false;
            lock.notify();
        }
    }

    public void togglePaused() {
        synchronized (lock) {
            if (paused) {
                resume();
            } else {
                pause();
            }
        }
    }
}
