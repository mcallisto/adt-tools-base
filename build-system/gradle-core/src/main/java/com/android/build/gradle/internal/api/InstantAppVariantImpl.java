/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.api.InstantAppVariant;
import com.android.build.gradle.internal.variant.InstallableVariantData;
import com.android.build.gradle.internal.variant.InstantAppVariantData;
import com.android.build.gradle.tasks.BundleInstantApp;
import com.android.builder.core.AndroidBuilder;
import org.gradle.api.NamedDomainObjectContainer;


/**
 * Implementation of the {@link InstantAppVariant} interface around a
 * {@link InstantAppVariantData} object.
 */
public class InstantAppVariantImpl extends InstallableVariantImpl implements InstantAppVariant {

    @NonNull
    private final InstantAppVariantData variantData;

    public InstantAppVariantImpl(
            @NonNull InstantAppVariantData variantData,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull ReadOnlyObjectProvider readOnlyObjectProvider,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> outputs) {
        super(androidBuilder, readOnlyObjectProvider, outputs);
        this.variantData = variantData;
    }

    @Nullable
    @Override
    public String getVersionName() {
        return getVariantData().getVariantConfiguration().getVersionName();
    }

    @Override
    public int getVersionCode() {
        return getVariantData().getVariantConfiguration().getVersionCode();
    }

    @NonNull
    @Override
    public InstallableVariantData getVariantData() {
        return variantData;
    }

    @Nullable
    @Override
    public BundleInstantApp getBundleInstantApp() {
        return variantData.bundleInstantAppTask;
    }
}
