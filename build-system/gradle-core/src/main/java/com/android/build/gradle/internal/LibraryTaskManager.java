/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal;

import static com.android.SdkConstants.FD_AIDL;
import static com.android.SdkConstants.FD_ASSETS;
import static com.android.SdkConstants.FD_JNI;
import static com.android.SdkConstants.FD_RES;
import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.FN_ANNOTATIONS_ZIP;
import static com.android.SdkConstants.FN_CLASSES_JAR;
import static com.android.SdkConstants.FN_INTERMEDIATE_RES_JAR;
import static com.android.SdkConstants.FN_PROGUARD_TXT;
import static com.android.SdkConstants.FN_PUBLIC_TXT;
import static com.android.SdkConstants.FN_RENDERSCRIPT;
import static com.android.SdkConstants.FN_RESOURCE_TEXT;
import static com.android.SdkConstants.LIBS_FOLDER;

import android.databinding.tool.DataBindingBuilder;
import com.android.annotations.NonNull;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Transform;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.CoreBuildType;
import com.android.build.gradle.internal.incremental.BuildInfoWriterTask;
import com.android.build.gradle.internal.incremental.FileType;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantOutputScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.CopyLintConfigAction;
import com.android.build.gradle.internal.tasks.MergeFileTask;
import com.android.build.gradle.internal.tasks.MergeProguardFilesConfigAction;
import com.android.build.gradle.internal.tasks.PackageRenderscriptConfigAction;
import com.android.build.gradle.internal.transforms.LibraryAarJarsTransform;
import com.android.build.gradle.internal.transforms.LibraryBaseTransform;
import com.android.build.gradle.internal.transforms.LibraryIntermediateJarsTransform;
import com.android.build.gradle.internal.transforms.LibraryJniLibsTransform;
import com.android.build.gradle.internal.transforms.MergeJavaResourcesTransform;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.internal.variant.LibVariantOutputData;
import com.android.build.gradle.internal.variant.LibraryVariantData;
import com.android.build.gradle.internal.variant.VariantHelper;
import com.android.build.gradle.tasks.AidlCompile;
import com.android.build.gradle.tasks.ExtractAnnotations;
import com.android.build.gradle.tasks.MergeResources;
import com.android.build.gradle.tasks.MergeSourceSetFolders;
import com.android.build.gradle.tasks.ProcessManifest;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.BuilderConstants;
import com.android.builder.dependency.level2.AndroidDependency;
import com.android.builder.model.SyncIssue;
import com.android.builder.profile.Recorder;
import com.android.utils.FileUtils;
import com.android.utils.StringHelper;
import com.google.common.collect.Sets;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan.ExecutionType;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.tooling.BuildException;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/**
 * TaskManager for creating tasks in an Android library project.
 */
public class LibraryTaskManager extends TaskManager {

    public static final String ANNOTATIONS = "annotations";

    private Task assembleDefault;

    public LibraryTaskManager(
            @NonNull Project project,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull DataBindingBuilder dataBindingBuilder,
            @NonNull AndroidConfig extension,
            @NonNull SdkHandler sdkHandler,
            @NonNull NdkHandler ndkHandler,
            @NonNull DependencyManager dependencyManager,
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @NonNull Recorder recorder) {
        super(
                project,
                androidBuilder,
                dataBindingBuilder,
                extension,
                sdkHandler,
                ndkHandler,
                dependencyManager,
                toolingRegistry,
                recorder);
    }

    @Override
    public void createTasksForVariantData(
            @NonNull final TaskFactory tasks,
            @NonNull final BaseVariantData<? extends BaseVariantOutputData> variantData) {
        final LibraryVariantData libVariantData = (LibraryVariantData) variantData;
        final GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();
        final CoreBuildType buildType = variantConfig.getBuildType();

        final VariantScope variantScope = variantData.getScope();
        GlobalScope globalScope = variantScope.getGlobalScope();

        final File intermediatesDir = globalScope.getIntermediatesDir();
        final Collection<String> variantDirectorySegments = variantConfig.getDirectorySegments();
        final File variantBundleDir = variantScope.getBaseBundleDir();

        final String projectPath = project.getPath();
        final String variantName = variantData.getName();

        final Configuration publishConfiguration = variantData.getVariantDependency()
                .getPublishConfiguration();
        final String publishConfigName = publishConfiguration.getName();

        createAnchorTasks(tasks, variantScope);

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(tasks, variantScope);

        createCheckManifestTask(tasks, variantScope);

        // Add a task to create the res values
        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_GENERATE_RES_VALUES_TASK,
                projectPath,
                variantName,
                () -> createGenerateResValuesTask(tasks, variantScope));

        // Add a task to process the manifest(s)
        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_MERGE_MANIFEST_TASK,
                projectPath,
                variantName,
                () -> {
                    AndroidTask<ProcessManifest> task
                            = createMergeLibManifestsTask(tasks, variantScope);

                    // publish intermediate manifest file
                    variantScope.publishIntermediateArtifact(
                            new File(variantBundleDir, FN_ANDROID_MANIFEST_XML),
                            task.getName(),
                            AndroidArtifacts.TYPE_MANIFEST);
                });

        // Add a task to compile renderscript files.
        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_CREATE_RENDERSCRIPT_TASK,
                projectPath,
                variantName,
                () -> createRenderscriptTask(tasks, variantScope));

        AndroidTask<MergeResources> packageRes =
                recorder.record(
                        ExecutionType.LIB_TASK_MANAGER_CREATE_MERGE_RESOURCES_TASK,
                        projectPath,
                        variantName,
                        () -> createMergeResourcesTask(
                                tasks,
                                variantData,
                                variantScope,
                                variantBundleDir));

        // Add a task to merge the assets folders
        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_MERGE_ASSETS_TASK,
                projectPath,
                variantName,
                () ->  {
                    AndroidTask<MergeSourceSetFolders> task
                            = createMergeAssetsTask(tasks, variantScope);

                    // publish intermediate assets folder
                    variantScope.publishIntermediateArtifact(
                            new File(variantBundleDir, FD_ASSETS),
                            task.getName(),
                            AndroidArtifacts.TYPE_ASSETS);

                });

        // Add a task to create the BuildConfig class
        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_BUILD_CONFIG_TASK,
                projectPath,
                variantName,
                () -> createBuildConfigTask(tasks, variantScope));

        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_PROCESS_RES_TASK,
                projectPath,
                variantName,
                () -> {
                    // Add a task to generate resource source files, directing the location
                    // of the r.txt file to be directly in the bundle.
                    createProcessResTask(
                            tasks,
                            variantScope,
                            () -> variantBundleDir,
                            (vod) -> null /*packageOutputSupplier*/);

                    // Find the first variant output so that we can get its tasks.
                    BaseVariantOutputData vod = variantData.getOutputs().get(0);
                    final VariantOutputScope variantOutputScope = vod.getScope();

                    // publish the intermediates symbol file
                    variantScope.publishIntermediateArtifact(
                            new File(variantBundleDir, FN_RESOURCE_TEXT),
                            variantOutputScope.getProcessResourcesTask().getName(),
                            AndroidArtifacts.TYPE_SYMBOL);

                    // process java resources only, the merge is setup after
                    // the task to generate intermediate jars for project to project publishing.
                    createProcessJavaResTask(tasks, variantScope);
                });

        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_AIDL_TASK,
                projectPath,
                variantName,
                () ->  {
                    AndroidTask<AidlCompile> task = createAidlTask(tasks, variantScope);

                    // publish intermediate aidl folder
                    variantScope.publishIntermediateArtifact(
                            new File(variantBundleDir, FD_AIDL),
                            task.getName(),
                            AndroidArtifacts.TYPE_AIDL);

                });

        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_SHADER_TASK,
                projectPath,
                variantName,
                () -> createShaderTask(tasks, variantScope));

        // Add a compile task
        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_COMPILE_TASK,
                projectPath,
                variantName,
                () -> {
                    // create data binding merge task before the javac task so that it can
                    // parse jars before any consumer
                    createDataBindingMergeArtifactsTaskIfNecessary(tasks, variantScope);
                    AndroidTask<? extends JavaCompile> javacTask =
                            createJavacTask(tasks, variantScope);
                    addJavacClassesStream(variantScope);
                    TaskManager.setJavaCompilerTask(javacTask, tasks, variantScope);
                });

        // Add data binding tasks if enabled
        createDataBindingTasksIfNecessary(tasks, variantScope);

        // Add dependencies on NDK tasks if NDK plugin is applied.
        if (!isComponentModelPlugin) {
            // Add NDK tasks
            recorder.record(
                    ExecutionType.LIB_TASK_MANAGER_CREATE_NDK_TASK,
                    projectPath,
                    variantName,
                    () -> createNdkTasks(tasks, variantScope));
        }
        variantScope.setNdkBuildable(getNdkBuildable(variantData));

        // External native build
        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_EXTERNAL_NATIVE_BUILD_TASK,
                projectPath,
                variantName,
                () -> {
                    createExternalNativeBuildJsonGenerators(variantScope);
                    createExternalNativeBuildTasks(tasks, variantScope);
                });

        // TODO not sure what to do about this...
        createMergeJniLibFoldersTasks(tasks, variantScope);
        createStripNativeLibraryTask(tasks, variantScope);

        // package the renderscript header files files into the bundle folder
        AndroidTask<Sync> packageRenderscriptTask =
                recorder.record(
                        ExecutionType.LIB_TASK_MANAGER_CREATE_PACKAGING_TASK,
                        projectPath,
                        variantName,
                        () ->
                                getAndroidTasks()
                                        .create(
                                                tasks,
                                                new PackageRenderscriptConfigAction(variantScope)));

        // publish the renderscript intermediate files
        variantScope.publishIntermediateArtifact(
                new File(variantBundleDir, FN_RENDERSCRIPT),
                packageRenderscriptTask.getName(),
                AndroidArtifacts.TYPE_RENDERSCRIPT);

        // merge consumer proguard files from different build types and flavors
        AndroidTask<MergeFileTask> mergeProguardFilesTask =
                recorder.record(
                        ExecutionType.LIB_TASK_MANAGER_CREATE_MERGE_PROGUARD_FILE_TASK,
                        projectPath,
                        variantName,
                        () -> createMergeFileTask(tasks, variantScope));

        // publish the proguard intermediate file
        variantScope.publishIntermediateArtifact(
                new File(variantBundleDir, FN_PROGUARD_TXT),
                mergeProguardFilesTask.getName(),
                AndroidArtifacts.TYPE_PROGUARD_RULES);

        // copy lint.jar into the bundle folder
        AndroidTask<Copy> copyLintTask =
                getAndroidTasks().create(tasks, new CopyLintConfigAction(variantScope));
        copyLintTask.dependsOn(tasks, LINT_COMPILE);

        // publish the lint intermediate file
        variantScope.publishIntermediateArtifact(
                FileUtils.join(variantBundleDir, FD_RES),
                copyLintTask.getName(),
                AndroidArtifacts.TYPE_LINT_JAR);

        final Zip bundle = project.getTasks().create(variantScope.getTaskName("bundle"), Zip.class);
        if (AndroidGradleOptions.isImprovedDependencyResolutionEnabled(project)
                || variantData.getVariantDependency().isAnnotationsPresent()) {
            AndroidTask<ExtractAnnotations> extractAnnotationsTask =
                    getAndroidTasks().create(
                            tasks,
                            new ExtractAnnotations.ConfigAction(getExtension(), variantScope));
            extractAnnotationsTask.dependsOn(tasks, libVariantData.getScope().getJavacTask());

            // publish intermediate annotation data
            variantScope.publishIntermediateArtifact(
                    new File(variantBundleDir, FN_ANNOTATIONS_ZIP),
                    extractAnnotationsTask.getName(),
                    AndroidArtifacts.TYPE_EXT_ANNOTATIONS);

            bundle.dependsOn(extractAnnotationsTask.getName());
        }

        final boolean instrumented = variantConfig.getBuildType().isTestCoverageEnabled();

        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_POST_COMPILATION_TASK,
                projectPath,
                variantName,
                new Recorder.Block<Void>() {
                    @Override
                    public Void call() throws Exception {
                        TransformManager transformManager = variantScope.getTransformManager();

                        // ----- Code Coverage first -----
                        if (instrumented) {
                            createJacocoTransform(tasks, variantScope);
                        }

                        // ----- External Transforms -----
                        // apply all the external transforms.
                        List<Transform> customTransforms = extension.getTransforms();
                        List<List<Object>> customTransformsDependencies =
                                extension.getTransformsDependencies();

                        for (int i = 0, count = customTransforms.size(); i < count; i++) {
                            Transform transform = customTransforms.get(i);

                            // Check the transform only applies to supported scopes for libraries:
                            // We cannot transform scopes that are not packaged in the library
                            // itself.
                            Sets.SetView<? super Scope> difference =
                                    Sets.difference(
                                            transform.getScopes(),
                                            TransformManager.PROJECT_ONLY);
                            if (!difference.isEmpty()) {
                                String scopes = difference.toString();
                                androidBuilder
                                        .getErrorReporter()
                                        .handleSyncError(
                                                "",
                                                SyncIssue.TYPE_GENERIC,
                                                String.format(
                                                        "Transforms with scopes '%s' cannot be applied"
                                                                + "to library projects.",
                                                        scopes));
                            }

                            List<Object> deps = customTransformsDependencies.get(i);
                            transformManager
                                    .addTransform(tasks, variantScope, transform)
                                    .ifPresent(
                                            t -> {
                                                if (!deps.isEmpty()) {
                                                    t.dependsOn(tasks, deps);
                                                }

                                                // if the task is a no-op then we make assemble task
                                                // depend on it.
                                                if (transform.getScopes().isEmpty()) {
                                                    variantScope
                                                            .getAssembleTask()
                                                            .dependsOn(tasks, t);
                                                }
                                            });
                        }

                        String packageName = variantConfig.getPackageFromManifest();
                        if (packageName == null) {
                            throw new BuildException("Failed to read manifest", null);
                        }

                        // Now add transforms for intermediate publishing (projects to projects).
                        File intermediateFolder = variantScope.getIntermediateJarOutputFolder();
                        File mainClassJar = new File(intermediateFolder, FN_CLASSES_JAR);
                        File mainResJar = new File(intermediateFolder, FN_INTERMEDIATE_RES_JAR);
                        LibraryIntermediateJarsTransform intermediateTransform =
                                new LibraryIntermediateJarsTransform(
                                        mainClassJar,
                                        mainResJar,
                                        variantScope.getTypedefFile(),
                                        packageName,
                                        getExtension().getPackageBuildConfig());
                        excludeDataBindingClassesIfNecessary(variantScope, intermediateTransform);

                        Optional<AndroidTask<TransformTask>> intermediateTransformTask =
                                transformManager.addTransform(tasks, variantScope, intermediateTransform);

                        intermediateTransformTask.ifPresent(t -> {
                            // publish the intermediate classes.jar.
                            variantScope.publishIntermediateArtifact(
                                    mainClassJar,
                                    t.getName(),
                                    AndroidArtifacts.TYPE_JAR);
                            variantScope.publishIntermediateArtifact(
                                    mainClassJar,
                                    t.getName(),
                                    AndroidArtifacts.TYPE_JAR_AAR_CLASSES);
                            // publish the res jar
                            variantScope.publishIntermediateArtifact(
                                    mainResJar,
                                    t.getName(),
                                    AndroidArtifacts.TYPE_JAVA_RES);
                        });

                        // now add a transform that will take all the native libs and package
                        // them into an intermediary folder. This processes only the PROJECT
                        // scope
                        final File intermediateJniLibsFolder = new File(intermediateFolder, FD_JNI);
                        LibraryJniLibsTransform intermediateJniTransform =
                                new LibraryJniLibsTransform(
                                        "intermediateJniLibs",
                                        intermediateJniLibsFolder,
                                        TransformManager.PROJECT_ONLY);
                        Optional<AndroidTask<TransformTask>> task =
                                transformManager.addTransform(tasks, variantScope, intermediateJniTransform);
                        task.ifPresent(t -> {
                            // publish the jni folder as intermediate
                            variantScope.publishIntermediateArtifact(
                                    intermediateJniLibsFolder,
                                    t.getName(),
                                    AndroidArtifacts.TYPE_JNI);
                        });


                        // Now go back to fill the pipeline with transforms used when
                        // publishing the AAR

                        // first merge the resources. This takes the PROJECT and LOCAL_DEPS
                        // and merges them together.
                        createMergeJavaResTransform(tasks, variantScope);

                        // ----- Minify next -----
                        if (buildType.isMinifyEnabled()) {
                            createMinifyTransform(tasks, variantScope, false);
                        }

                        maybeCreateShrinkResourcesTransform(tasks, variantScope);

                        // now add a transform that will take all the class/res and package them
                        // into the main and secondary jar files that goes in the AAR.
                        // This transform technically does not use its transform output, but that's
                        // ok. We use the transform mechanism to get incremental data from
                        // the streams.
                        // This is used for building the AAR.

                        LibraryAarJarsTransform transform =
                                new LibraryAarJarsTransform(
                                        new File(variantBundleDir, FN_CLASSES_JAR),
                                        new File(variantBundleDir, LIBS_FOLDER),
                                        variantScope.getTypedefFile(),
                                        packageName,
                                        getExtension().getPackageBuildConfig());
                        excludeDataBindingClassesIfNecessary(variantScope, transform);

                        Optional<AndroidTask<TransformTask>> libraryJarTransformTask =
                                transformManager.addTransform(tasks, variantScope, transform);
                        libraryJarTransformTask.ifPresent(t -> {
                            bundle.dependsOn(t.getName());
                        });

                        // now add a transform that will take all the native libs and package
                        // them into the libs folder of the bundle. This processes both the PROJECT
                        // and the LOCAL_PROJECT scopes
                        final File jniLibsFolder = new File(variantBundleDir, FD_JNI);
                        LibraryJniLibsTransform jniTransform =
                                new LibraryJniLibsTransform(
                                        "syncJniLibs",
                                        jniLibsFolder,
                                        TransformManager.SCOPE_FULL_LIBRARY);
                        Optional<AndroidTask<TransformTask>> jniPackagingTask =
                                transformManager.addTransform(tasks, variantScope, jniTransform);
                        jniPackagingTask.ifPresent(t -> bundle.dependsOn(t.getName()));

                        return null;
                    }
                });

        bundle.dependsOn(
                packageRes.getName(),
                packageRenderscriptTask.getName(),
                copyLintTask.getName(),
                mergeProguardFilesTask.getName(),
                // The below dependencies are redundant in a normal build as
                // generateSources depends on them. When generateSourcesOnly is injected they are
                // needed explicitly, as bundle no longer depends on compileJava
                variantScope.getAidlCompileTask().getName(),
                variantScope.getMergeAssetsTask().getName(),
                variantData.getOutputs().get(0).getScope().getManifestProcessorTask().getName());
        bundle.dependsOn(variantScope.getNdkBuildable());

        bundle.setDescription("Assembles a bundle containing the library in " +
                variantConfig.getFullName() + ".");
        bundle.setDestinationDir(variantScope.getOutputBundleFile().getParentFile());
        bundle.setArchiveName(variantScope.getOutputBundleFile().getName());
        bundle.setExtension(BuilderConstants.EXT_LIB_ARCHIVE);
        bundle.from(variantBundleDir);
        bundle.from(
                FileUtils.join(
                        intermediatesDir,
                        StringHelper.toStrings(ANNOTATIONS, variantDirectorySegments)));

        // get the single output for now, though that may always be the case for a library.
        LibVariantOutputData variantOutputData = libVariantData.getOutputs().get(0);
        variantOutputData.packageLibTask = bundle;

        AndroidTask<BuildInfoWriterTask> buildInfoWriterTask = getAndroidTasks().create(
                tasks, new BuildInfoWriterTask.ConfigAction(variantScope, getLogger()));

        variantScope.addTaskOutput(VariantScope.TaskOutputType.APK_METADATA,
                BuildInfoWriterTask.ConfigAction.getBuildInfoFile(variantScope),
                buildInfoWriterTask.getName());

        buildInfoWriterTask.configure(tasks, task -> task.mustRunAfter(bundle));
        buildInfoWriterTask.dependsOn(tasks, bundle);

        variantScope.getBuildContext().addChangedFile(FileType.AAR, bundle.getArchivePath());
        variantScope.getAssembleTask().dependsOn(tasks, bundle, buildInfoWriterTask);
        variantOutputData.getScope().setAssembleTask(variantScope.getAssembleTask());
        variantOutputData.assembleTask = variantData.assembleVariantTask;

        // add the artifact to the matching published-archives configuration
        project.getArtifacts().add(
                publishConfigName,
                AndroidArtifacts.getAarArtifact(bundle, publishConfigName));

        // if the variant is the default published, also add a default artifact to the
        // archives config to have a default one.
        if (getExtension().getDefaultPublishConfig().equals(variantConfig.getFullName())) {
            VariantHelper.setupArchivesConfig(project, publishConfiguration);

            // add the artifact that will be published
            project.getArtifacts().add("archives", bundle);
        }

        // configure the variant to be testable.
        variantConfig.setOutput(AndroidDependency.createLocalTestedAarLibrary(
                bundle.getArchivePath(),
                variantData.getName(),
                project.getPath(),
                variantBundleDir));

        recorder.record(
                ExecutionType.LIB_TASK_MANAGER_CREATE_LINT_TASK,
                projectPath,
                variantName,
                () -> createLintTasks(tasks, variantScope));
    }

    @NonNull
    private AndroidTask<MergeFileTask> createMergeFileTask(
            @NonNull TaskFactory tasks,
            @NonNull VariantScope variantScope) {
        return getAndroidTasks()
                .create(
                        tasks,
                        new MergeProguardFilesConfigAction(project, variantScope));
    }

    @NonNull
    private AndroidTask<MergeResources> createMergeResourcesTask(
            @NonNull TaskFactory tasks,
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData,
            @NonNull VariantScope variantScope,
            @NonNull File variantBundleDir) {
        // Create a merge task to only merge the resources from this library and not
        // the dependencies. This is what gets packaged in the aar.
        File resFolder = FileUtils.join(variantBundleDir, FD_RES);
        AndroidTask<MergeResources> mergeResourceTask =
                basicCreateMergeResourcesTask(
                        tasks,
                        variantScope,
                        MergeType.PACKAGE,
                        resFolder,
                        false /*includeDependencies*/,
                        false /*processResources*/);

        // publish the intermediate res folder
        variantScope.publishIntermediateArtifact(
                resFolder,
                mergeResourceTask.getName(),
                AndroidArtifacts.TYPE_ANDROID_RES);

        if (AndroidGradleOptions.isImprovedDependencyResolutionEnabled(project)
                || variantData.getVariantDependency().hasNonOptionalLibraries()) {
            // Add a task to merge the resource folders, including the libraries, in order to
            // generate the R.txt file with all the symbols, including the ones from
            // the dependencies.
            createMergeResourcesTask(tasks, variantScope, false /*processResources*/);
        }

        File publicTxt = new File(variantBundleDir, FN_PUBLIC_TXT);

        mergeResourceTask.configure(
                tasks, task -> task.setPublicFile(publicTxt));

        // publish the intermediate public res file
        variantScope.publishIntermediateArtifact(
                publicTxt,
                mergeResourceTask.getName(),
                AndroidArtifacts.TYPE_PUBLIC_RES);
        return mergeResourceTask;
    }

    private void excludeDataBindingClassesIfNecessary(
            @NonNull final VariantScope variantScope,
            @NonNull LibraryBaseTransform transform) {
        if (!extension.getDataBinding().isEnabled()) {
            return;
        }
        transform.addExcludeListProvider(
                () -> {
                    final File excludeFile = variantScope.getVariantData().getType()
                            .isExportDataBindingClassList() ? variantScope
                            .getGeneratedClassListOutputFileForDataBinding() : null;
                    return dataBindingBuilder.getJarExcludeList(
                            variantScope.getVariantData().getLayoutXmlProcessor(), excludeFile
                    );
                });
    }

    @NonNull
    @Override
    protected Set<Scope> getResMergingScopes(@NonNull VariantScope variantScope) {
        if (variantScope.getTestedVariantData() != null) {
            return TransformManager.SCOPE_FULL_PROJECT;
        }
        return TransformManager.SCOPE_FULL_LIBRARY;
    }

    private Task getAssembleDefault() {
        if (assembleDefault == null) {
            assembleDefault = project.getTasks().findByName("assembleDefault");
        }
        return assembleDefault;
    }
}
