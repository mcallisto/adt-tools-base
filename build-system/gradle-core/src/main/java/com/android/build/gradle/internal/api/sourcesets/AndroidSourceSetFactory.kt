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

package com.android.build.gradle.internal.api.sourcesets

import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.builder.errors.EvalIssueReporter
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.model.ObjectFactory

class AndroidSourceSetFactory(
            private val filesProvider: FilesProvider,
            private val publishPackage: Boolean,
            private val objectFactory: ObjectFactory,
            private val deprecationReporter: DeprecationReporter,
            private val issueReporter: EvalIssueReporter)
        : NamedDomainObjectFactory<DefaultAndroidSourceSet> {

    override fun create(name: String): DefaultAndroidSourceSet {
        return objectFactory.newInstance(DefaultAndroidSourceSet::class.java,
                name, filesProvider, publishPackage, deprecationReporter, issueReporter)
    }
}