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

package com.android.build.gradle.integration.performance

import com.android.build.gradle.integration.common.fixture.BuildModel
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.integration.common.fixture.RunGradleTasks
import com.android.build.gradle.integration.common.utils.PerformanceTestProjects
import com.android.build.gradle.options.BooleanOption
import com.google.wireless.android.sdk.gradlelogging.proto.Logging
import org.junit.runner.Description
import org.junit.runners.model.Statement

data class Benchmark(
        val scenario: ProjectScenario,
        val benchmark: Logging.Benchmark,
        val benchmarkMode: Logging.BenchmarkMode,
        val projectFactory: (GradleTestProjectBuilder) -> GradleTestProject,
        val postApplyProject: (GradleTestProject) -> GradleTestProject = { p -> p },
        val setup: (GradleTestProject, RunGradleTasks, BuildModel) -> Unit,
        val recordedAction: (RunGradleTasks, BuildModel) -> Unit) {
    fun run() {
        /*
         * Any common project configuration should happen here. Note that it isn't possible to take
         * a subproject until _after_ project.apply has been called, so if you do need to do that
         * you'll have to supply a postApplyProject function and do it in there.
         */
        var project = projectFactory(
                GradleTestProject.builder()
                        .forBenchmarkRecording(BenchmarkRecorder(benchmark, scenario)))

        val statement =
                object : Statement() {
                    override fun evaluate() {
                        /*
                         * Anything that needs to be done to the project before the executor and
                         * model are taken from it should happen in the postApplyProject function.
                         *
                         * This is commonly used to do any initialisation, like changing the
                         * contents of build files and whatnot.
                         */
                        project = postApplyProject(project)

                        /*
                         * Any common configuration for the executor should happen here. The
                         * philosophy is that the benchmark creator should not have to configure
                         * the executor, it should be possible for us to do that based on what
                         * scenario has been passed in.
                         */
                        val executor = project.executor()
                                .withEnableInfoLogging(false)
                                .with(BooleanOption.ENABLE_INTERMEDIATE_ARTIFACTS_CACHE, false)
                                .with(BooleanOption.ENABLE_D8, scenario.useD8())
                                .withUseDexArchive(scenario.useDexArchive())

                        val model = project.model().ignoreSyncIssues().withoutOfflineFlag()
                        PerformanceTestProjects.assertNoSyncErrors(model.multi.modelMap)

                        setup(project, executor, model)

                        /*
                         * Note that both the executor and model are put into recording mode. This
                         * means that if you do something with the model, and do something with the
                         * executor, you will end up with two GradleBenchmarkResults at the end, and
                         * it's highly likely that the distinctness check on these results will fail
                         * because they will be too similar.
                         *
                         * As such, in a recordedAction, you want to either do only something with
                         * the executor _or_ the model, or you want to disable recording on one of
                         * them like so:
                         *
                         *   executor.dontRecord {
                         *     // code here...
                         *   }
                         *
                         * This method has the benefit of returning them to whatever state they
                         * were in previously.
                         */
                        recordedAction(
                                executor.recordBenchmark(benchmarkMode),
                                model.recordBenchmark(benchmarkMode))
                    }
                }

        project.apply(statement, testDescription()).evaluate()
    }

    private fun testDescription(): Description {
        val desc = "{scenario=${scenario.name}, " +
                "benchmark=${benchmark.name}, " +
                "benchmarkMode=${benchmarkMode.name}}"

        return Description.createTestDescription(this.javaClass, desc)
    }
}
