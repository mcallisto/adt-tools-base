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

package com.android.build.gradle.internal.dsl.tester.negative

import com.android.build.gradle.internal.api.dsl.sealing.SealableObject
import com.android.build.gradle.internal.dsl.tester.positive.TopLevelInterface
import com.android.builder.errors.EvalIssueReporter

interface UnprotectedFinalMap {
    val unprotectedFinalMapProperty : Map<String, TopLevelInterface>
}

class UnprotectedFinalMapImpl(issueReporter: EvalIssueReporter)
    : SealableObject(issueReporter), UnprotectedFinalMap {
    override val unprotectedFinalMapProperty = HashMap<String, TopLevelInterface>()
}