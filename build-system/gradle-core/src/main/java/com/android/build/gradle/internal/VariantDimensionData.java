/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.internal;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet;
import com.android.builder.core.VariantType;

/** Common parts of build type and product flavor data objects. */
public abstract class VariantDimensionData {

    private final DefaultAndroidSourceSet sourceSet;
    private final DefaultAndroidSourceSet androidTestSourceSet;
    private final DefaultAndroidSourceSet unitTestSourceSet;

    public VariantDimensionData(
            @NonNull DefaultAndroidSourceSet sourceSet,
            @Nullable DefaultAndroidSourceSet androidTestSourceSet,
            @Nullable DefaultAndroidSourceSet unitTestSourceSet) {
        this.sourceSet = sourceSet;
        this.androidTestSourceSet = androidTestSourceSet;
        this.unitTestSourceSet = unitTestSourceSet;
    }

    @NonNull
    public final DefaultAndroidSourceSet getSourceSet() {
        return sourceSet;
    }

    @Nullable
    public final DefaultAndroidSourceSet getTestSourceSet(@NonNull VariantType type) {
        switch (type) {
            case ANDROID_TEST:
                return androidTestSourceSet;
            case UNIT_TEST:
                return unitTestSourceSet;
            default:
                throw new IllegalArgumentException(
                        String.format("Unknown test variant type %s", type));
        }
    }


}
