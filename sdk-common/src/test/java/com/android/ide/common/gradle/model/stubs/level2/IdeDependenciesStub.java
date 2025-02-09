/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.ide.common.gradle.model.stubs.level2;

import com.android.annotations.NonNull;
import com.android.builder.model.level2.Library;
import com.android.ide.common.gradle.model.level2.IdeDependencies;
import java.util.ArrayList;
import java.util.Collection;

public class IdeDependenciesStub implements IdeDependencies {
    @NonNull private final Collection<Library> myAndroidLibraries;
    @NonNull private final Collection<Library> myJavaLibraries;
    @NonNull private final Collection<Library> myModuleDependencies;

    public IdeDependenciesStub() {
        this(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    IdeDependenciesStub(
            @NonNull Collection<Library> androidLibraries,
            @NonNull Collection<Library> javaLibraries,
            @NonNull Collection<Library> moduleDependencies) {
        myAndroidLibraries = androidLibraries;
        myJavaLibraries = javaLibraries;
        myModuleDependencies = moduleDependencies;
    }

    @Override
    @NonNull
    public Collection<Library> getAndroidLibraries() {
        return myAndroidLibraries;
    }

    @Override
    @NonNull
    public Collection<Library> getJavaLibraries() {
        return myJavaLibraries;
    }

    @Override
    @NonNull
    public Collection<Library> getModuleDependencies() {
        return myModuleDependencies;
    }

    public void addAndroidLibrary(@NonNull Library library) {
        myAndroidLibraries.add(library);
    }

    public void addJavaLibrary(@NonNull Library library) {
        myJavaLibraries.add(library);
    }

    public void addModuleDependency(@NonNull Library library) {
        myModuleDependencies.add(library);
    }
}
