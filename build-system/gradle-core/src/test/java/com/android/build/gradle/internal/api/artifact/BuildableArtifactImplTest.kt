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

package com.android.build.gradle.internal.api.artifact

import com.android.build.gradle.internal.fixtures.FakeEvalIssueReporter
import com.android.build.gradle.internal.fixtures.FakeFilesProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.test.assertFailsWith

/**
 * Test for [BuildableArtifactImpl]
 */
class BuildableArtifactImplTest {
    private val provider = FakeFilesProvider()
    private val issueReporter = FakeEvalIssueReporter(throwOnError = true)

    @Test
    fun default() {
        BuildableArtifactImpl.enableResolution()
        val collection = BuildableArtifactImpl(provider.files(), issueReporter)
        assertThat(collection.fileCollection).isNotNull()
        assertThat(collection.isEmpty()).isTrue()
        assertThat(collection.files).isEmpty()
        assertThat(collection.iterator().hasNext()).isFalse()
    }

    @Test
    fun singleFile() {
        BuildableArtifactImpl.enableResolution()
        val file = provider.file("foo")
        val collection = BuildableArtifactImpl(provider.files(file), issueReporter)
        assertThat(collection.isEmpty()).isFalse()
        assertThat(collection.files).hasSize(1)
        assertThat(collection).containsExactly(file)
    }

    @Test
    fun multipleFiles() {
        BuildableArtifactImpl.enableResolution()
        val files = listOf(provider.file("foo"), provider.file("bar"))
        val collection = BuildableArtifactImpl(provider.files(files), issueReporter)
        assertThat(collection.isEmpty()).isFalse()
        assertThat(collection.files).containsExactlyElementsIn(files)
        assertThat(collection).containsExactlyElementsIn(files)
    }

    /** All getter invocation should throw when resolution is not allowed */
    @Test
    fun disabledResolution() {
        BuildableArtifactImpl.disableResolution()
        val collection = BuildableArtifactImpl(provider.files(), issueReporter)
        assertFailsWith<RuntimeException> { collection.isEmpty() }
        assertFailsWith<RuntimeException> { collection.files }
        assertFailsWith<RuntimeException> { collection.iterator() }
    }

}