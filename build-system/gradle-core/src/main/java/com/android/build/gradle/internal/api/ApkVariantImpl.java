/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.build.gradle.api.ApkVariant;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.builder.core.AndroidBuilder;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.model.ObjectFactory;

/**
 * Implementation of the apk-generating variant.
 *
 * This is a wrapper around the internal data model, in order to control what is accessible
 * through the external API.
 */
public abstract class ApkVariantImpl extends InstallableVariantImpl implements ApkVariant {

    protected ApkVariantImpl(
            @NonNull ObjectFactory objectFactory,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull ReadOnlyObjectProvider immutableObjectProvider,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> outputs) {
        super(objectFactory, androidBuilder, immutableObjectProvider, outputs);
    }

    @Nullable
    @Override
    public Object getDex() {
        throw new RuntimeException("Access to the dex task is now impossible, starting with 1.4.0\n"
                + "1.4.0 introduces a new Transform API allowing manipulation of the .class files.\n"
                + "See more information: https://developer.android.com/studio/plugins/index.html");
    }
}
