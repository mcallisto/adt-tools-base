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
package com.android.build.gradle.internal.res

import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH
import com.android.build.gradle.internal.scope.ExistingBuildElements
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.TaskOutputHolder
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.variant.TaskContainer
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.tasks.ProcessAndroidResources
import com.android.builder.symbols.processLibraryMainSymbolTable
import com.android.ide.common.symbols.IdProvider
import com.android.ide.common.symbols.ResourceDirectoryParser
import com.android.ide.common.symbols.SymbolIo
import com.android.ide.common.symbols.SymbolTable
import com.google.common.base.Strings
import com.google.common.collect.Iterables
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.File
import java.io.IOException

@CacheableTask
open class GenerateLibraryRFileTask : ProcessAndroidResources() {

    @get:OutputDirectory @get:Optional var sourceOutputDirectory: File? = null; private set
    @Input fun outputSources() = sourceOutputDirectory != null

    @get:OutputFile @get:Optional var rClassOutputJar: File? = null; private set
    @Input fun outputRClassJar() = rClassOutputJar != null

    override fun getSourceOutputDir() = sourceOutputDirectory ?: rClassOutputJar

    @get:OutputFile lateinit var textSymbolOutputFile: File
        private set

    @get:OutputFile lateinit var symbolsWithPackageNameOutputFile: File
        private set

    @get:OutputFile
    @get:Optional
    var proguardOutputFile: File? = null
        private set

    @Suppress("unused")
    // Needed to trigger rebuild if proguard file is requested (https://issuetracker.google.com/67418335)
    @get:Input private val hasProguardOutputFile: Boolean
        get() = proguardOutputFile != null

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE) lateinit var dependencies: FileCollection
        private set

    @get:Input lateinit var packageForR: String
        private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY) lateinit var platformAttrRTxt: FileCollection
        private set

    @get:Input lateinit var applicationId: String
        private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var inputResourcesDir: FileCollection

    @Throws(IOException::class)
    override fun doFullTaskAction() {
        val manifest = Iterables.getOnlyElement(
                ExistingBuildElements.from(TaskOutputHolder.TaskOutputType.MERGED_MANIFESTS, manifestFiles))
                .outputFile

        val androidAttrSymbol = getAndroidAttrSymbols(platformAttrRTxt.singleFile)

        val symbolTable = ResourceDirectoryParser.parseDirectory(
                inputResourcesDir.singleFile,
                IdProvider.sequential(),
                androidAttrSymbol)

        processLibraryMainSymbolTable(
                librarySymbols = symbolTable,
                libraries = this.dependencies.files,
                mainPackageName = packageForR,
                manifestFile = manifest,
                sourceOut = sourceOutputDirectory,
                rClassOutputJar = rClassOutputJar,
                symbolFileOut = textSymbolOutputFile,
                proguardOut = proguardOutputFile,
                mergedResources = inputResourcesDir.singleFile,
                platformSymbols = androidAttrSymbol,
                disableMergeInLib = true)

        SymbolIo.writeSymbolTableWithPackage(
                textSymbolOutputFile.toPath(),
                manifest.toPath(),
                symbolsWithPackageNameOutputFile.toPath())
    }

    private fun getAndroidAttrSymbols(androidJar: File) =
            if (androidJar.exists())
                SymbolIo.read(androidJar, "android")
            else
                SymbolTable.builder().tablePackage("android").build()



    class ConfigAction(
            private val variantScope: VariantScope,
            private val symbolFile: File,
            private val symbolsWithPackageNameOutputFile: File
    ) : TaskConfigAction<GenerateLibraryRFileTask> {

        override fun getName() = variantScope.getTaskName("generate", "RFile")

        override fun getType() = GenerateLibraryRFileTask::class.java

        override fun execute(task: GenerateLibraryRFileTask) {
            variantScope.variantData.addTask(TaskContainer.TaskKind.PROCESS_ANDROID_RESOURCES, task)

            task.variantName = variantScope.fullVariantName

            task.platformAttrRTxt = variantScope
                    .globalScope
                    .getOutput(TaskOutputHolder.TaskOutputType.PLATFORM_R_TXT)

            task.applicationId = variantScope.variantData.variantConfiguration.applicationId

            task.dependencies = variantScope.getArtifactFileCollection(
                    RUNTIME_CLASSPATH,
                    ALL,
                    AndroidArtifacts.ArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME)
            if (variantScope.globalScope.projectOptions.get(BooleanOption.ENABLE_SEPARATE_R_CLASS_COMPILATION)) {
                val rJar = File(variantScope.getIntermediateDir(TaskOutputHolder.TaskOutputType.COMPILE_ONLY_NOT_NAMESPACED_R_CLASS_JAR), "R.jar")
                task.rClassOutputJar = rJar
                variantScope.addTaskOutput(
                        TaskOutputHolder.TaskOutputType.COMPILE_ONLY_NOT_NAMESPACED_R_CLASS_JAR,
                        rJar,
                        task.name)
            } else {
                val sourceOutputDirectory = variantScope.rClassSourceOutputDir
                task.sourceOutputDirectory = sourceOutputDirectory
                variantScope.addTaskOutput(
                        TaskOutputHolder.TaskOutputType.NOT_NAMESPACED_R_CLASS_SOURCES,
                        sourceOutputDirectory,
                        task.name)
            }
            task.textSymbolOutputFile = symbolFile
            task.symbolsWithPackageNameOutputFile = symbolsWithPackageNameOutputFile

            if (variantScope.codeShrinker != null) {
                task.proguardOutputFile = variantScope.processAndroidResourcesProguardOutputFile
            }

            task.packageForR = Strings.nullToEmpty(variantScope.variantConfiguration.originalApplicationId)

            task.manifestFiles = variantScope.getOutput(TaskOutputHolder.TaskOutputType.MERGED_MANIFESTS)

            task.inputResourcesDir = variantScope.getOutput(TaskOutputHolder.TaskOutputType.PACKAGED_RES)

            task.outputScope = variantScope.outputScope
        }
    }
}
