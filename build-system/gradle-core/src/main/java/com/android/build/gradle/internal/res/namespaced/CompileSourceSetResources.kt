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
package com.android.build.gradle.internal.res.namespaced

import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.IncrementalTask
import com.android.ide.common.res2.CompileResourceRequest
import com.android.ide.common.res2.FileStatus
import com.android.utils.FileUtils
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.nio.file.Files
import javax.inject.Inject

/**
 * Task to compile a single sourceset's resources in to AAPT intermediate format.
 *
 * The link step handles resource overlays.
 */
open class CompileSourceSetResources
@Inject constructor(private val workerExecutor: WorkerExecutor) : IncrementalTask() {

    @get:InputFiles @get:SkipWhenEmpty lateinit var inputDirectory: File private set
    @get:Input var isPngCrunching: Boolean = false; private set
    @get:Input var isPseudoLocalize: Boolean = false; private set
    @get:OutputDirectory lateinit var outputDirectory: File private set
    @get:OutputDirectory lateinit var aaptIntermediateDirectory: File private set

    override fun isIncremental() = true

    override fun doFullTaskAction() {
        FileUtils.cleanOutputDir(outputDirectory)
        if (!inputDirectory.isDirectory) {
            // This should be covered by the @SkipWhenEmpty above.
            return
        }
        val requests = mutableListOf<CompileResourceRequest>()

        /** Only look at files in first level subdirectories of the input directory */
        Files.list(inputDirectory.toPath()).forEach { subDir ->
            if (Files.isDirectory(subDir)) {
                Files.list(subDir).forEach { resFile ->
                    if (Files.isRegularFile(resFile)) {
                        requests.add(compileRequest(resFile.toFile()))
                    }
                }
            }
        }

        submit(requests)
    }

    override fun doIncrementalTaskAction(changedInputs: MutableMap<File, FileStatus>) {
        val requests = mutableListOf<CompileResourceRequest>()
        val deletes = mutableListOf<File>()
        /** Only consider at files in first level subdirectories of the input directory */
        changedInputs.forEach { file, status ->
            if (willCompile(file) && (inputDirectory == file.parentFile.parentFile)) {
                when (status) {
                    FileStatus.NEW, FileStatus.CHANGED -> {
                        requests.add(compileRequest(file))
                    }
                    FileStatus.REMOVED -> {
                        deletes.add(file)
                    }
                }
            }
        }
        if (!deletes.isEmpty()) {
            workerExecutor.submit(Aapt2CompileDeleteRunnable::class.java) {
                it.isolationMode = IsolationMode.NONE
                it.setParams(Aapt2CompileDeleteRunnable.Params(
                        outputDirectory = outputDirectory,
                        deletedInputs = deletes))
            }
        }
        submit(requests)
    }

    private fun compileRequest(file: File, inputDirectoryName: String = file.parentFile.name) =
            CompileResourceRequest(
                    inputFile = file,
                    outputDirectory = outputDirectory,
                    inputDirectoryName = inputDirectoryName,
                    isPseudoLocalize = isPseudoLocalize,
                    isPngCrunching = isPngCrunching)

    private fun submit(requests: List<CompileResourceRequest>) {
        if (requests.isEmpty()) {
            return
        }
        for (request in requests) {
            workerExecutor.submit(Aapt2CompileRunnable::class.java) {
                it.isolationMode = IsolationMode.NONE
                it.setParams(Aapt2CompileRunnable.Params(
                        revision = builder.buildToolInfo.revision,
                        requests = listOf(request),
                        outputDirectory = outputDirectory))
            }
        }
    }

    // TODO: filtering using same logic as DataSet.isIgnored.
    private fun willCompile(file: File) = !file.name.startsWith(".") && !file.isDirectory

    class ConfigAction(
            private val name: String,
            private val inputDirectory: File,
            private val outputDirectory: File,
            private val variantScope: VariantScope,
            private val aaptIntermediateDirectory: File) : TaskConfigAction<CompileSourceSetResources> {
        override fun getName() = name
        override fun getType() = CompileSourceSetResources::class.java
        override fun execute(task: CompileSourceSetResources) {
            task.inputDirectory = inputDirectory
            task.outputDirectory = outputDirectory
            task.variantName = variantScope.fullVariantName
            task.isPngCrunching = variantScope.isCrunchPngs
            task.isPseudoLocalize = variantScope.variantData.variantConfiguration.buildType.isPseudoLocalesEnabled
            task.aaptIntermediateDirectory = aaptIntermediateDirectory
            task.setAndroidBuilder(variantScope.globalScope.androidBuilder)
        }
    }


}
