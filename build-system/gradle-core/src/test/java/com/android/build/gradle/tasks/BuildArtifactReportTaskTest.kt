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

package com.android.build.gradle.tasks

import com.android.build.api.artifact.BuildArtifactType.JAVAC_CLASSES
import com.android.build.api.artifact.BuildArtifactType.JAVA_COMPILE_CLASSPATH
import com.android.build.gradle.internal.api.artifact.BuildableArtifactImpl
import com.android.build.gradle.internal.fixtures.FakeEvalIssueReporter
import com.android.build.gradle.internal.scope.BuildArtifactHolder
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Test for [BuildArtifactReportTask].
 */
class BuildArtifactReportTaskTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()
    private lateinit var project : Project
    private val issueReporter = FakeEvalIssueReporter(throwOnError = true)
    private val artifactTypes = listOf(JAVAC_CLASSES, JAVA_COMPILE_CLASSPATH)
    private lateinit var artifactHolder : BuildArtifactHolder

    @Before
    fun setUp() {
        project = ProjectBuilder.builder().withProjectDir(temporaryFolder.newFolder()).build()
        artifactHolder =
                BuildArtifactHolder(
                project,
                "debug",
                project.file("root"),
                "debug",
                artifactTypes,
                issueReporter)
        project.tasks.create("task0")
        project.tasks.create("task1")
        artifactHolder.createFirstArtifactFiles(JAVAC_CLASSES, "javac_classes", "task0")
        artifactHolder.createFirstArtifactFiles(JAVA_COMPILE_CLASSPATH, "java_compile_classpath", "task1")
        BuildableArtifactImpl.enableResolution()
    }

    @Test
    fun report() {
        val task = project.tasks.create("report", BuildArtifactReportTask::class.java)
        task.init(artifactHolder, artifactTypes)
        task.report()
    }

    @Test
    fun reportToFile() {
        val task = project.tasks.create("report", BuildArtifactReportTask::class.java)
        val outputFile = project.file("report.txt")
        task.init(artifactHolder, artifactTypes, outputFile)

        artifactHolder.replaceArtifact(JAVAC_CLASSES, listOf("classes"), "task1")

        task.report()

        val report = BuildArtifactReportTask.parseReport(outputFile)
        val javacArtifacts = report[JAVAC_CLASSES] ?: throw NullPointerException()
        assertThat(javacArtifacts).hasSize(2)
        assertThat(javacArtifacts[0].files.map(File::getName)).containsExactly("javac_classes")
        assertThat(javacArtifacts[0].builtBy).containsExactly(":task0")
        assertThat(javacArtifacts[1].files.map(File::getName)).containsExactly("classes")
        assertThat(javacArtifacts[1].builtBy).containsExactly(":task1")
    }
}