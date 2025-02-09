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
package com.android.ide.common.gradle.model.level2;

import static com.android.builder.model.level2.Library.*;
import static com.android.ide.common.gradle.model.IdeLibraries.computeAddress;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.BaseArtifact;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.GlobalLibraryMap;
import com.android.builder.model.level2.GraphItem;
import com.android.builder.model.level2.Library;
import com.android.ide.common.gradle.model.ModelCache;
import com.android.ide.common.repository.GradleVersion;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/** Creates {@link IdeDependencies} from {@link BaseArtifact}. */
public class IdeDependenciesFactory {
    // Map from unique artifact address to level2 library instance. The library instances are supposed to be shared by all artifacts.
    // When creating IdeLevel2Dependencies, check if current library is available in this map,
    // if it's available, don't create new one, simple add reference to it.
    // If it's not available, create new instance and save to this map, so it can be reused the next time when the same library is added.
    @NonNull private final Map<String, Library> myLibrariesById = new HashMap<>();

    @NonNull private final IdeLibraryFactory myLibraryFactory = new IdeLibraryFactory();
    @NonNull private final BuildFolderPaths myBuildFolderPaths = new BuildFolderPaths();

    /**
     * Stores the "build" folder path for the given module.
     *
     * @param moduleGradlePath module's gradle path.
     * @param buildFolderPath path to the module's build directory.
     */
    public void findAndAddBuildFolderPath(
            @NonNull String moduleGradlePath, @NonNull File buildFolder) {
        myBuildFolderPaths.addBuildFolderMapping(moduleGradlePath, buildFolder);
    }

    /**
     * Create {@link IdeDependencies} from {@link BaseArtifact}.
     *
     * @param artifact Instance of {@link BaseArtifact} returned from Android plugin.
     * @param modelVersion Version of Android plugin.
     * @return New instance of {@link IdeDependencies}.
     */
    public IdeDependencies create(
            @NonNull BaseArtifact artifact, @Nullable GradleVersion modelVersion) {
        // Create a fresh model cache for this class, since current instance is based on dependencyGraphs or dependencies, which
        // have been copied in the constructor of IdeBaseArtifact.
        ModelCache modelCache = new ModelCache();
        if (modelVersion != null
                && modelVersion.getMajor() >= 3
                && !artifact.getDependencyGraphs().getCompileDependencies().isEmpty()) {
            return createFromDependencyGraphs(artifact.getDependencyGraphs());
        }
        return createFromDependencies(artifact.getDependencies(), modelCache);
    }

    /** Call this method on 3.0+ models. */
    @VisibleForTesting
    @NonNull
    IdeDependencies createFromDependencyGraphs(@NonNull DependencyGraphs graphs) {
        return createInstance(
                graphs.getCompileDependencies()
                        .stream()
                        .map(GraphItem::getArtifactAddress)
                        .collect(Collectors.toList()));
    }

    /** Call this method on pre-3.0 models. */
    @NonNull
    private IdeDependencies createFromDependencies(
            @NonNull Dependencies dependencies, @NonNull ModelCache modelCache) {
        Set<String> visited = new HashSet<>();
        populateAndroidLibraries(dependencies.getLibraries(), visited, modelCache);
        populateJavaLibraries(dependencies.getJavaLibraries(), visited, modelCache);
        populateModuleDependencies(dependencies, visited, modelCache);
        return createInstance(visited);
    }

    private void populateModuleDependencies(
            @NonNull Dependencies dependencies,
            @NonNull Set<String> visited,
            @NonNull ModelCache modelCache) {
        try {
            for (Dependencies.ProjectIdentifier identifier : dependencies.getJavaModules()) {
                createModuleLibrary(
                        visited,
                        identifier.getProjectPath(),
                        computeAddress(identifier),
                        modelCache,
                        identifier.getBuildId());
            }
        } catch (UnsupportedOperationException ignored) {
            // Dependencies::getJavaModules is available for AGP 3.1+. Use Dependencies::getProjects for the old plugins.
            for (String projectPath : dependencies.getProjects()) {
                createModuleLibrary(visited, projectPath, projectPath, modelCache, null);
            }
        }
    }

    private void createModuleLibrary(
            @NonNull Set<String> visited,
            @NonNull String projectPath,
            @NonNull String artifactAddress,
            @NonNull ModelCache modelCache,
            @Nullable String buildId) {
        if (!visited.contains(artifactAddress)) {
            visited.add(artifactAddress);
            myLibrariesById.computeIfAbsent(
                    artifactAddress,
                    id ->
                            IdeLibraryFactory.create(
                                    projectPath, artifactAddress, modelCache, buildId));
        }
    }

    private void populateAndroidLibraries(
            @NonNull Collection<? extends AndroidLibrary> androidLibraries,
            @NonNull Set<String> visited,
            @NonNull ModelCache modelCache) {
        for (AndroidLibrary androidLibrary : androidLibraries) {
            String address = computeAddress(androidLibrary);
            if (!visited.contains(address)) {
                visited.add(address);
                myLibrariesById.computeIfAbsent(
                        address,
                        id ->
                                myLibraryFactory.create(
                                        androidLibrary, myBuildFolderPaths, modelCache));
                populateAndroidLibraries(
                        androidLibrary.getLibraryDependencies(), visited, modelCache);
                populateJavaLibraries(getJavaDependencies(androidLibrary), visited, modelCache);
            }
        }
    }

    @NonNull
    private static Collection<? extends JavaLibrary> getJavaDependencies(
            AndroidLibrary androidLibrary) {
        try {
            return androidLibrary.getJavaDependencies();
        } catch (UnsupportedOperationException e) {
            return Collections.emptyList();
        }
    }

    private void populateJavaLibraries(
            @NonNull Collection<? extends JavaLibrary> javaLibraries,
            @NonNull Set<String> visited,
            @NonNull ModelCache modelCache) {
        for (JavaLibrary javaLibrary : javaLibraries) {
            String address = computeAddress(javaLibrary);
            if (!visited.contains(address)) {
                visited.add(address);
                myLibrariesById.computeIfAbsent(
                        address, k -> myLibraryFactory.create(javaLibrary, modelCache));
                populateJavaLibraries(javaLibrary.getDependencies(), visited, modelCache);
            }
        }
    }

    @NonNull
    private IdeDependencies createInstance(@NonNull Collection<String> artifactAddresses) {
        ImmutableList.Builder<Library> androidLibraries = ImmutableList.builder();
        ImmutableList.Builder<Library> javaLibraries = ImmutableList.builder();
        ImmutableList.Builder<Library> moduleDependencies = ImmutableList.builder();

        for (String address : artifactAddresses) {
            Library library = myLibrariesById.get(address);
            assert library != null;
            switch (library.getType()) {
                case LIBRARY_ANDROID:
                    androidLibraries.add(library);
                    break;
                case LIBRARY_JAVA:
                    javaLibraries.add(library);
                    break;
                case LIBRARY_MODULE:
                    moduleDependencies.add(library);
                    break;
                default:
                    throw new UnsupportedOperationException(
                            "Unknown library type " + library.getType());
            }
        }
        return new IdeDependenciesImpl(
                androidLibraries.build(), javaLibraries.build(), moduleDependencies.build());
    }

    /**
     * Populate global library map from {@link GlobalLibraryMap} by making a deep copy.
     *
     * @param globalLibraryMap GlobalLibraryMap model returned from Android Plugin.
     */
    public void setUpGlobalLibraryMap(@NonNull GlobalLibraryMap globalLibraryMap) {
        ModelCache modelCache = new ModelCache();
        for (Library library : globalLibraryMap.getLibraries().values()) {
            myLibrariesById.computeIfAbsent(
                    library.getArtifactAddress(),
                    k -> myLibraryFactory.create(library, modelCache));
        }
    }
}
