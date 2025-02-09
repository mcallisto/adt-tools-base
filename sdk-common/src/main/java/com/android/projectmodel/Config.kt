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
package com.android.projectmodel

import kotlin.reflect.full.memberProperties

/**
 * Represents a group of source files and configuration data that contribute to one or more [Artifact]s. For those familiar with Gradle,
 * this is the union of the Gradle concepts of a build type, flavor, and source provider. Unlike a Gradle source provider, this contains
 * more than just a list of path names - it also contains all of the metadata supplied by the flavor or build type. There is also no
 * distinction between flavors and build types. They only differ in terms of which attributes are set in the project metadata.
 *
 * New properties may be added in the future; clients that invoke the constructor are encouraged to
 * use Kotlin named arguments to stay source compatible.
 */
data class Config(
        /**
         * Returns the application ID suffix for this [Config] or null if none.
         */
        val applicationIdSuffix: String? = null,
        /**
         * Returns the version name suffix for this [Config] or null if none.
         */
        val versionNameSuffix: String? = null,
        /**
         * Metadata representing the set of manifest attributes that are set explicitly by this [Config]. These attributes will
         * override values from lower-priority [Config] instances.
         */
        val manifestValues: ManifestAttributes = ManifestAttributes(),
        /**
         * The map of key value pairs for placeholder substitution in the android manifest file. This map will be used by the manifest merger.
         */
        val manifestPlaceholderValues: Map<String, Any> = emptyMap(),
        /**
         * List of sources contributed by this [Config].
         */
        val sources: SourceSet = SourceSet(),
        /**
         * Dynamic resources. Map of resource names onto resource values.
         */
        val resValues: Map<String, ResValue> = emptyMap(),
        /**
         * List of dependencies needed to build an artifact that uses this [Config]. Jars and class files in this list may be interface-only.
         * That is, they are required to contain enough information for compilation - such as method signatures. Any content that would
         * only be used for execution - such as method bodies - is not required.
         *
         * This is null if the [Config]'s compile-time dependencies are unknown.
         */
        val compileDeps: List<ArtifactDependency>? = null,
        /**
         * True if legacy multi-dex is enabled. Null if not set.
         */
        val legacyMultidexEnabled: Boolean? = null,
        /**
         * True if there's a possibility that a proguard-like tool will be used to perform
         * minification in this configuration.
         */
        val minifyEnabled: Boolean = false,
        /**
         * The fully qualified class name of the test instrumentation runner, or null if none.
         */
        val testInstrumentationRunner: String? = null,
        /**
         * The arguments for the test instrumentation runner.
         */
        val testInstrumentationRunnerArguments: Map<String, String> = emptyMap(),
        /**
         * The resource configurations for this [Config]. This is the list of -c parameters passed to aapt.
         */
        val resourceConfigurations: Collection<String> = emptyList(),
        /**
         * True iff the [Config] is using the support library to draw vectors at runtime.
         */
        val usingSupportLibVectors: Boolean = false
) {
    override fun toString(): String {
        val componentStrings = ArrayList<String>()
        val defaultValues = Config()
        for (prop in Config::class.memberProperties) {
            val defaultValue = prop.get(defaultValues)
            val actualValue = prop.get(this)
            if (defaultValue != actualValue) {
                componentStrings.add("${prop.name}=$actualValue")
            }
        }
        return componentStrings.joinToString(",", "Config(", ")")
    }

  /**
   * Merges this [Config] with another higher-priority [Config].
   */
  fun mergeWith(other: Config): Config =
      Config(
          applicationIdSuffix = mergeNullable(applicationIdSuffix, other.applicationIdSuffix, { a, b -> a + b} ),
          versionNameSuffix = mergeNullable(versionNameSuffix, other.versionNameSuffix, { a, b -> a + b} ),
          manifestValues = manifestValues + other.manifestValues,
          manifestPlaceholderValues = manifestPlaceholderValues + other.manifestPlaceholderValues,
          sources = sources + other.sources,
          resValues = resValues + other.resValues,
          compileDeps = mergeNullable(compileDeps, other.compileDeps, { a, b -> a + b}),
          legacyMultidexEnabled = other.legacyMultidexEnabled ?: legacyMultidexEnabled,
          minifyEnabled = minifyEnabled || other.minifyEnabled,
          testInstrumentationRunner = other.testInstrumentationRunner ?: testInstrumentationRunner,
          testInstrumentationRunnerArguments = if (other.testInstrumentationRunner != null)
            other.testInstrumentationRunnerArguments
          else testInstrumentationRunnerArguments,
          resourceConfigurations = resourceConfigurations + other.resourceConfigurations,
          usingSupportLibVectors = usingSupportLibVectors || other.usingSupportLibVectors
      )
}

private fun <T> mergeNullable(deps: T?, moreDeps: T?, merger: (T, T)-> T) : T? =
  if (deps == null)
    moreDeps
  else if (moreDeps == null)
    deps
  else
    merger(deps, moreDeps)
