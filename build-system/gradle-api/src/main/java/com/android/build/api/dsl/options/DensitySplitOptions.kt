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

package com.android.build.api.dsl.options

import org.gradle.api.Incubating

/**
 * DSL object for configuring per-density splits options.
 *
 *
 * See [APK
 * Splits](https://developer.android.com/studio/build/configure-apk-splits.html).
 */
@Incubating
interface DensitySplitOptions : SplitOptions {

    /** TODO: Document.  */
    var strict: Boolean

    /**
     * A list of compatible screens.
     *
     * This will inject a matching `<compatible-screens><screen ...>` node
     * in the manifest. This is optional.
     */
    val compatibleScreens: Set<String>

    /**
     * Sets whether the build system should automatically determine the splits based on the
     * "language-*" folders in the resources.
     *
     * FIXME this makes no sense for Density?
     */
    var auto: Boolean
}
