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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.os.Build;
import android.view.View;

public final class AnimationUtils {
    private AnimationUtils() {
        throw new AssertionError("AnimationUtils should not be instantiated!");
    }

    public static void crossFade(final View toBeGone, View toBeVisible, int
            shortAnimationDuration) {
        if (Build.VERSION.SDK_INT >= 12) {
            toBeVisible.setAlpha(0f);
            toBeVisible.setVisibility(View.VISIBLE);
            toBeVisible.animate()
                    .alpha(1f)
                    .setDuration(shortAnimationDuration)
                    .setListener(null);

            toBeGone.animate()
                    .alpha(0f)
                    .setDuration(shortAnimationDuration)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            toBeGone.setVisibility(View.GONE);
                        }
                    });

        } else {
            switchOutIn(toBeGone, toBeVisible);
        }
    }

    public static void switchOutIn(View toBeGone, View toBeVisible){
        toBeVisible.setVisibility(View.VISIBLE);
        toBeGone.setVisibility(View.GONE);
    }
}
