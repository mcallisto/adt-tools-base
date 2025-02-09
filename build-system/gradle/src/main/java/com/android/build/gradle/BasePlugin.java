/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.build.gradle;

import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES;
import static com.google.common.base.Preconditions.checkState;
import static java.io.File.separator;

import android.databinding.tool.DataBindingBuilder;
import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.api.transform.Transform;
import com.android.build.gradle.api.AndroidBasePlugin;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.ApiObjectFactory;
import com.android.build.gradle.internal.BadPluginException;
import com.android.build.gradle.internal.BuildCacheUtils;
import com.android.build.gradle.internal.ClasspathVerifier;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.NativeLibraryFactoryImpl;
import com.android.build.gradle.internal.NonFinalPluginExpiry;
import com.android.build.gradle.internal.PluginInitializer;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.VariantManager;
import com.android.build.gradle.internal.api.artifact.BuildableArtifactImpl;
import com.android.build.gradle.internal.api.dsl.extensions.BaseExtension2;
import com.android.build.gradle.internal.dependency.SourceSetManager;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.BuildTypeFactory;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.ProductFlavorFactory;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.dsl.SigningConfigFactory;
import com.android.build.gradle.internal.dsl.Splits;
import com.android.build.gradle.internal.ide.ModelBuilder;
import com.android.build.gradle.internal.ide.NativeModelBuilder;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.internal.packaging.GradleKeystoreHelper;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.plugin.PluginDelegate;
import com.android.build.gradle.internal.plugin.ProjectWrapper;
import com.android.build.gradle.internal.plugin.TypedPluginDelegate;
import com.android.build.gradle.internal.process.GradleJavaProcessExecutor;
import com.android.build.gradle.internal.process.GradleProcessExecutor;
import com.android.build.gradle.internal.profile.AnalyticsUtil;
import com.android.build.gradle.internal.profile.ProfilerInitializer;
import com.android.build.gradle.internal.res.namespaced.Aapt2DaemonManagerService;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.TaskInputHelper;
import com.android.build.gradle.internal.transforms.DexTransform;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.VariantFactory;
import com.android.build.gradle.internal.workeractions.WorkerActionServiceRegistry;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.IntegerOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.StringOption;
import com.android.build.gradle.options.SyncOptions;
import com.android.build.gradle.options.SyncOptions.ErrorFormatMode;
import com.android.build.gradle.tasks.ExternalNativeBuildTaskUtils;
import com.android.build.gradle.tasks.ExternalNativeJsonGenerator;
import com.android.build.gradle.tasks.LintBaseTask;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.BuilderConstants;
import com.android.builder.errors.EvalIssueReporter.Type;
import com.android.builder.internal.compiler.PreDexCache;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Version;
import com.android.builder.profile.ProcessProfileWriter;
import com.android.builder.profile.Recorder;
import com.android.builder.profile.ThreadRecorder;
import com.android.builder.sdk.SdkLibData;
import com.android.builder.sdk.TargetInfo;
import com.android.builder.utils.FileCache;
import com.android.dx.command.dexer.Main;
import com.android.ide.common.repository.GradleVersion;
import com.android.repository.api.Channel;
import com.android.repository.api.ConsoleProgressIndicator;
import com.android.repository.api.Downloader;
import com.android.repository.api.SettingsController;
import com.android.repository.impl.downloader.LocalFileAwareDownloader;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.repository.legacy.LegacyDownloader;
import com.android.tools.lint.gradle.api.ToolingRegistryProvider;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.base.CharMatcher;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan.ExecutionType;
import com.google.wireless.android.sdk.stats.GradleBuildProject;
import java.io.File;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ForkJoinPool;
import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.StopExecutionException;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** Base class for all Android plugins */
public abstract class BasePlugin<E extends BaseExtension2>
        implements Plugin<Project>, ToolingRegistryProvider {

    @VisibleForTesting
    public static final GradleVersion GRADLE_MIN_VERSION =
            GradleVersion.parse(SdkConstants.GRADLE_MINIMUM_VERSION);

    private BaseExtension extension;

    private VariantManager variantManager;

    private TaskManager taskManager;

    protected Project project;

    private ProjectOptions projectOptions;

    private SdkHandler sdkHandler;

    private NdkHandler ndkHandler;

    private AndroidBuilder androidBuilder;

    private DataBindingBuilder dataBindingBuilder;

    private VariantFactory variantFactory;

    private SourceSetManager sourceSetManager;

    private ToolingModelBuilderRegistry registry;

    private LoggerWrapper loggerWrapper;

    protected ExtraModelInfo extraModelInfo;

    private String creator;

    private Recorder threadRecorder;

    private boolean hasCreatedTasks = false;

    BasePlugin(@NonNull ToolingModelBuilderRegistry registry) {
        ClasspathVerifier.checkClasspathSanity();
        this.registry = registry;
        creator = "Android Gradle " + Version.ANDROID_GRADLE_PLUGIN_VERSION;
        NonFinalPluginExpiry.verifyRetirementAge();

        ModelBuilder.clearCaches();
    }

    @NonNull
    protected abstract BaseExtension createExtension(
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull SdkHandler sdkHandler,
            @NonNull NamedDomainObjectContainer<BuildType> buildTypeContainer,
            @NonNull NamedDomainObjectContainer<ProductFlavor> productFlavorContainer,
            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigContainer,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> buildOutputs,
            @NonNull SourceSetManager sourceSetManager,
            @NonNull ExtraModelInfo extraModelInfo);

    @NonNull
    protected abstract GradleBuildProject.PluginType getAnalyticsPluginType();

    @NonNull
    protected abstract VariantFactory createVariantFactory(
            @NonNull GlobalScope globalScope,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull AndroidConfig androidConfig);

    @NonNull
    protected abstract TaskManager createTaskManager(
            @NonNull GlobalScope globalScope,
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull DataBindingBuilder dataBindingBuilder,
            @NonNull AndroidConfig androidConfig,
            @NonNull SdkHandler sdkHandler,
            @NonNull NdkHandler ndkHandler,
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @NonNull Recorder threadRecorder);

    protected abstract int getProjectType();

    @VisibleForTesting
    public VariantManager getVariantManager() {
        return variantManager;
    }

    public BaseExtension getExtension() {
        return extension;
    }

    @VisibleForTesting
    AndroidBuilder getAndroidBuilder() {
        return androidBuilder;
    }

    private ILogger getLogger() {
        if (loggerWrapper == null) {
            loggerWrapper = new LoggerWrapper(project.getLogger());
        }

        return loggerWrapper;
    }

    @Override
    public void apply(@NonNull Project project) {
        // We run by default in headless mode, so the JVM doesn't steal focus.
        System.setProperty("java.awt.headless", "true");

        this.project = project;
        this.projectOptions = new ProjectOptions(project);

        project.getPluginManager().apply(AndroidBasePlugin.class);

        checkConfigureOnDemandGradleVersionCompat();
        checkPathForErrors();
        checkModulesForErrors();

        PluginInitializer.initialize(project);
        ProfilerInitializer.init(project, projectOptions);
        threadRecorder = ThreadRecorder.get();

        ProcessProfileWriter.getProject(project.getPath())
                .setAndroidPluginVersion(Version.ANDROID_GRADLE_PLUGIN_VERSION)
                .setAndroidPlugin(getAnalyticsPluginType())
                .setPluginGeneration(GradleBuildProject.PluginGeneration.FIRST)
                .setOptions(AnalyticsUtil.toProto(projectOptions));

        BuildableArtifactImpl.Companion.disableResolution();
        if (!projectOptions.get(BooleanOption.ENABLE_NEW_DSL_AND_API)) {
            TaskInputHelper.enableBypass();

            threadRecorder.record(
                    ExecutionType.BASE_PLUGIN_PROJECT_CONFIGURE,
                    project.getPath(),
                    null,
                    this::configureProject);

            threadRecorder.record(
                    ExecutionType.BASE_PLUGIN_PROJECT_BASE_EXTENSION_CREATION,
                    project.getPath(),
                    null,
                    this::configureExtension);

            threadRecorder.record(
                    ExecutionType.BASE_PLUGIN_PROJECT_TASKS_CREATION,
                    project.getPath(),
                    null,
                    this::createTasks);
        } else {
            // Apply the Java plugin
            project.getPlugins().apply(JavaBasePlugin.class);

            // create the delegate
            ProjectWrapper projectWrapper = new ProjectWrapper(project);
            PluginDelegate<E> delegate =
                    new PluginDelegate<>(
                            project.getPath(),
                            project.getObjects(),
                            project.getExtensions(),
                            project.getConfigurations(),
                            projectWrapper,
                            projectWrapper,
                            project.getLogger(),
                            projectOptions,
                            getTypedDelegate());

            delegate.prepareForEvaluation();

            // after evaluate callbacks
            project.afterEvaluate(
                    p ->
                            threadRecorder.record(
                                    ExecutionType.BASE_PLUGIN_CREATE_ANDROID_TASKS,
                                    p.getPath(),
                                    null,
                                    delegate::afterEvaluate));
        }
    }

    /**
     * Returns the typed plugin delegate.
     *
     * <p>This is the delegate that is specific to the actual plugin that is applied (app, lib,
     * etc...)
     *
     * <p>In the long term when the old code path is removed this can be passed via the constructor.
     *
     * @return the typed delegate
     */
    protected abstract TypedPluginDelegate<E> getTypedDelegate();

    private void configureProject() {
        final Gradle gradle = project.getGradle();

        extraModelInfo = new ExtraModelInfo(project.getPath(), projectOptions, project.getLogger());
        checkGradleVersion(project, getLogger(), projectOptions);

        sdkHandler = new SdkHandler(project, getLogger());
        if (!gradle.getStartParameter().isOffline()
                && projectOptions.get(BooleanOption.ENABLE_SDK_DOWNLOAD)) {
            SdkLibData sdkLibData = SdkLibData.download(getDownloader(), getSettingsController());
            sdkHandler.setSdkLibData(sdkLibData);
        }

        androidBuilder =
                new AndroidBuilder(
                        project == project.getRootProject() ? project.getName() : project.getPath(),
                        creator,
                        new GradleProcessExecutor(project),
                        new GradleJavaProcessExecutor(project),
                        extraModelInfo.getSyncIssueHandler(),
                        extraModelInfo.getMessageReceiver(),
                        getLogger(),
                        isVerbose());
        dataBindingBuilder = new DataBindingBuilder();
        dataBindingBuilder.setPrintMachineReadableOutput(
                SyncOptions.getErrorFormatMode(projectOptions) == ErrorFormatMode.MACHINE_PARSABLE);

        if (projectOptions.hasRemovedOptions()) {
            androidBuilder
                    .getIssueReporter()
                    .reportWarning(Type.GENERIC, projectOptions.getRemovedOptionsErrorMessage());
        }

        if (projectOptions.hasDeprecatedOptions()) {
            extraModelInfo
                    .getDeprecationReporter()
                    .reportDeprecatedOptions(projectOptions.getDeprecatedOptions());
        }

        // Apply the Java plugin
        project.getPlugins().apply(JavaBasePlugin.class);

        project.getTasks()
                .getByName("assemble")
                .setDescription(
                        "Assembles all variants of all applications and secondary packages.");

        // call back on execution. This is called after the whole build is done (not
        // after the current project is done).
        // This is will be called for each (android) projects though, so this should support
        // being called 2+ times.
        gradle.addBuildListener(
                new BuildListener() {
                    @Override
                    public void buildStarted(@NonNull Gradle gradle) {
                        TaskInputHelper.enableBypass();
                        BuildableArtifactImpl.Companion.disableResolution();
                    }

                    @Override
                    public void settingsEvaluated(@NonNull Settings settings) {}

                    @Override
                    public void projectsLoaded(@NonNull Gradle gradle) {}

                    @Override
                    public void projectsEvaluated(@NonNull Gradle gradle) {}

                    @Override
                    public void buildFinished(@NonNull BuildResult buildResult) {
                        // Do not run buildFinished for included project in composite build.
                        if (buildResult.getGradle().getParent() != null) {
                            return;
                        }
                        sdkHandler.unload();
                        threadRecorder.record(
                                ExecutionType.BASE_PLUGIN_BUILD_FINISHED,
                                project.getPath(),
                                null,
                                () -> {
                                    WorkerActionServiceRegistry.INSTANCE
                                            .shutdownAllRegisteredServices(
                                                    ForkJoinPool.commonPool());
                                    PreDexCache.getCache()
                                            .clear(
                                                    FileUtils.join(
                                                            project.getRootProject().getBuildDir(),
                                                            FD_INTERMEDIATES,
                                                            "dex-cache",
                                                            "cache.xml"),
                                                    getLogger());
                                    Main.clearInternTables();
                                });
                    }
                });

        gradle.getTaskGraph()
                .addTaskExecutionGraphListener(
                        taskGraph -> {
                            TaskInputHelper.disableBypass();
                            Aapt2DaemonManagerService.registerAaptService(
                                    Objects.requireNonNull(androidBuilder.getTargetInfo())
                                            .getBuildTools(),
                                    loggerWrapper,
                                    WorkerActionServiceRegistry.INSTANCE);

                            for (Task task : taskGraph.getAllTasks()) {
                                if (task instanceof TransformTask) {
                                    Transform transform = ((TransformTask) task).getTransform();
                                    if (transform instanceof DexTransform) {
                                        PreDexCache.getCache()
                                                .load(
                                                        FileUtils.join(
                                                                project.getRootProject()
                                                                        .getBuildDir(),
                                                                FD_INTERMEDIATES,
                                                                "dex-cache",
                                                                "cache.xml"));
                                        break;
                                    }
                                }
                            }
                        });

        createLintClasspathConfiguration(project);
    }

    /** Creates a lint class path Configuration for the given project */
    public static void createLintClasspathConfiguration(@NonNull Project project) {
        Configuration config = project.getConfigurations().create(LintBaseTask.LINT_CLASS_PATH);
        config.setVisible(false);
        config.setTransitive(true);
        config.setCanBeConsumed(false);
        config.setDescription("The lint embedded classpath");

        project.getDependencies().add(config.getName(), "com.android.tools.lint:lint-gradle:" +
                Version.ANDROID_TOOLS_BASE_VERSION);
    }

    private void configureExtension() {
        ObjectFactory objectFactory = project.getObjects();
        final NamedDomainObjectContainer<BuildType> buildTypeContainer =
                project.container(
                        BuildType.class,
                        new BuildTypeFactory(
                                objectFactory,
                                project,
                                extraModelInfo.getSyncIssueHandler(),
                                extraModelInfo.getDeprecationReporter()));
        final NamedDomainObjectContainer<ProductFlavor> productFlavorContainer =
                project.container(
                        ProductFlavor.class,
                        new ProductFlavorFactory(
                                objectFactory,
                                project,
                                extraModelInfo.getDeprecationReporter(),
                                project.getLogger()));
        final NamedDomainObjectContainer<SigningConfig> signingConfigContainer =
                project.container(
                        SigningConfig.class,
                        new SigningConfigFactory(
                                objectFactory,
                                GradleKeystoreHelper.getDefaultDebugKeystoreLocation()));

        final NamedDomainObjectContainer<BaseVariantOutput> buildOutputs =
                project.container(BaseVariantOutput.class);

        project.getExtensions().add("buildOutputs", buildOutputs);

        sourceSetManager = createSourceSetManager();

        extension =
                createExtension(
                        project,
                        projectOptions,
                        androidBuilder,
                        sdkHandler,
                        buildTypeContainer,
                        productFlavorContainer,
                        signingConfigContainer,
                        buildOutputs,
                        sourceSetManager,
                        extraModelInfo);

        ndkHandler =
                new NdkHandler(
                        project.getRootDir(),
                        null, /* compileSkdVersion, this will be set in afterEvaluate */
                        "gcc",
                        "" /*toolchainVersion*/,
                        false /* useUnifiedHeaders */);


        @Nullable
        FileCache buildCache = BuildCacheUtils.createBuildCacheIfEnabled(project, projectOptions);

        GlobalScope globalScope =
                new GlobalScope(
                        project,
                        projectOptions,
                        androidBuilder,
                        extension,
                        sdkHandler,
                        ndkHandler,
                        registry,
                        buildCache);

        variantFactory = createVariantFactory(globalScope, androidBuilder, extension);

        taskManager =
                createTaskManager(
                        globalScope,
                        project,
                        projectOptions,
                        androidBuilder,
                        dataBindingBuilder,
                        extension,
                        sdkHandler,
                        ndkHandler,
                        registry,
                        threadRecorder);

        variantManager =
                new VariantManager(
                        globalScope,
                        project,
                        projectOptions,
                        androidBuilder,
                        extension,
                        variantFactory,
                        taskManager,
                        sourceSetManager,
                        threadRecorder);

        registerModels(registry, globalScope, variantManager, extension, extraModelInfo);

        // map the whenObjectAdded callbacks on the containers.
        signingConfigContainer.whenObjectAdded(variantManager::addSigningConfig);

        buildTypeContainer.whenObjectAdded(
                buildType -> {
                    SigningConfig signingConfig =
                            signingConfigContainer.findByName(BuilderConstants.DEBUG);
                    buildType.init(signingConfig);
                    variantManager.addBuildType(buildType);
                });

        productFlavorContainer.whenObjectAdded(variantManager::addProductFlavor);

        // map whenObjectRemoved on the containers to throw an exception.
        signingConfigContainer.whenObjectRemoved(
                new UnsupportedAction("Removing signingConfigs is not supported."));
        buildTypeContainer.whenObjectRemoved(
                new UnsupportedAction("Removing build types is not supported."));
        productFlavorContainer.whenObjectRemoved(
                new UnsupportedAction("Removing product flavors is not supported."));

        // create default Objects, signingConfig first as its used by the BuildTypes.
        variantFactory.createDefaultComponents(
                buildTypeContainer, productFlavorContainer, signingConfigContainer);
    }

    protected void registerModels(
            @NonNull ToolingModelBuilderRegistry registry,
            @NonNull GlobalScope globalScope,
            @NonNull VariantManager variantManager,
            @NonNull AndroidConfig config,
            @NonNull ExtraModelInfo extraModelInfo) {
        // Register a builder for the custom tooling model
        ModelBuilder modelBuilder =
                new ModelBuilder(
                        globalScope,
                        androidBuilder,
                        variantManager,
                        taskManager,
                        config,
                        extraModelInfo,
                        ndkHandler,
                        new NativeLibraryFactoryImpl(ndkHandler),
                        getProjectType(),
                        AndroidProject.GENERATION_ORIGINAL);
        registry.register(modelBuilder);

        // Register a builder for the native tooling model
        NativeModelBuilder nativeModelBuilder = new NativeModelBuilder(variantManager);
        registry.register(nativeModelBuilder);
    }

    private static class UnsupportedAction implements Action<Object> {

        private final String message;

        UnsupportedAction(String message) {
            this.message = message;
        }

        @Override
        public void execute(Object o) {
            throw new UnsupportedOperationException(message);
        }
    }

    private void createTasks() {
        threadRecorder.record(
                ExecutionType.TASK_MANAGER_CREATE_TASKS,
                project.getPath(),
                null,
                () -> taskManager.createTasksBeforeEvaluate());

        project.afterEvaluate(
                project ->
                        threadRecorder.record(
                                ExecutionType.BASE_PLUGIN_CREATE_ANDROID_TASKS,
                                project.getPath(),
                                null,
                                () -> createAndroidTasks(false)));
    }


    static void checkGradleVersion(
            @NonNull Project project,
            @NonNull ILogger logger,
            @Nullable ProjectOptions projectOptions) {
        String currentVersion = project.getGradle().getGradleVersion();
        if (GRADLE_MIN_VERSION.compareTo(currentVersion) > 0) {
            File file = new File("gradle" + separator + "wrapper" + separator +
                    "gradle-wrapper.properties");
            String errorMessage =
                    String.format(
                            "Minimum supported Gradle version is %s. Current version is %s. "
                                    + "If using the gradle wrapper, try editing the distributionUrl in %s "
                                    + "to gradle-%s-all.zip",
                            GRADLE_MIN_VERSION,
                            currentVersion,
                            file.getAbsolutePath(),
                            GRADLE_MIN_VERSION);
            if (projectOptions != null
                    && (projectOptions.get(BooleanOption.VERSION_CHECK_OVERRIDE_PROPERTY)
                            || projectOptions.get(
                                    BooleanOption.VERSION_CHECK_OVERRIDE_PROPERTY_OLD))) {
                logger.warning(errorMessage);
                logger.warning(
                        "As %s is set, continuing anyway.",
                        BooleanOption.VERSION_CHECK_OVERRIDE_PROPERTY.getPropertyName());
            } else {
                throw new RuntimeException(errorMessage);
            }
        }
    }

    @VisibleForTesting
    final void createAndroidTasks(boolean force) {
        // Make sure unit tests set the required fields.
        checkState(extension.getBuildToolsRevision() != null,
                "buildToolsVersion is not specified.");
        checkState(extension.getCompileSdkVersion() != null, "compileSdkVersion is not specified.");

        ndkHandler.setCompileSdkVersion(extension.getCompileSdkVersion());

        // get current plugins and look for the default Java plugin.
        if (project.getPlugins().hasPlugin(JavaPlugin.class)) {
            throw new BadPluginException(
                    "The 'java' plugin has been applied, but it is not compatible with the Android plugins.");
        }

        boolean targetSetupSuccess = ensureTargetSetup();
        sdkHandler.ensurePlatformToolsIsInstalledWarnOnFailure(
                extraModelInfo.getSyncIssueHandler());
        // Stop trying to configure the project if the SDK is not ready.
        // Sync issues will already have been collected at this point in sync.
        if (!targetSetupSuccess) {
            project.getLogger()
                    .warn("Aborting configuration as SDK is missing components in sync mode.");
            return;
        }

        // don't do anything if the project was not initialized.
        // Unless TEST_SDK_DIR is set in which case this is unit tests and we don't return.
        // This is because project don't get evaluated in the unit test setup.
        // See AppPluginDslTest
        if (!force
                && (!project.getState().getExecuted() || project.getState().getFailure() != null)
                && SdkHandler.sTestSdkFolder == null) {
            return;
        }

        if (hasCreatedTasks) {
            return;
        }
        hasCreatedTasks = true;

        extension.disableWrite();

        taskManager.configureCustomLintChecks();

        ProcessProfileWriter.getProject(project.getPath())
                .setCompileSdk(extension.getCompileSdkVersion())
                .setBuildToolsVersion(extension.getBuildToolsRevision().toString())
                .setSplits(AnalyticsUtil.toProto(extension.getSplits()));

        String kotlinPluginVersion = getKotlinPluginVersion();
        if (kotlinPluginVersion != null) {
            ProcessProfileWriter.getProject(project.getPath())
                    .setKotlinPluginVersion(kotlinPluginVersion);
        }

        // setup SDK repositories.
        sdkHandler.addLocalRepositories(project);

        threadRecorder.record(
                ExecutionType.VARIANT_MANAGER_CREATE_ANDROID_TASKS,
                project.getPath(),
                null,
                () -> {
                    variantManager.createAndroidTasks();
                    ApiObjectFactory apiObjectFactory =
                            new ApiObjectFactory(
                                    androidBuilder,
                                    extension,
                                    variantFactory,
                                    project.getObjects());
                    for (VariantScope variantScope : variantManager.getVariantScopes()) {
                        BaseVariantData variantData = variantScope.getVariantData();
                        apiObjectFactory.create(variantData);
                    }

                    // Make sure no SourceSets were added through the DSL without being properly configured
                    // Only do it if we are not restricting to a single variant (with Instant
                    // Run or we can find extra source set
                    if (projectOptions.get(StringOption.IDE_RESTRICT_VARIANT_NAME) == null) {
                        sourceSetManager.checkForUnconfiguredSourceSets();
                    }

                    // must run this after scopes are created so that we can configure kotlin
                    // kapt tasks
                    taskManager.addDataBindingDependenciesIfNecessary(
                            extension.getDataBinding(), variantManager.getVariantScopes());
                });

        // create the global lint task that depends on all the variants
        taskManager.configureGlobalLintTask(variantManager.getVariantScopes());

        // Create and read external native build JSON files depending on what's happening right
        // now.
        //
        // CREATE PHASE:
        // Creates JSONs by shelling out to external build system when:
        //   - Any one of AndroidProject.PROPERTY_INVOKED_FROM_IDE,
        //      AndroidProject.PROPERTY_BUILD_MODEL_ONLY_ADVANCED,
        //      AndroidProject.PROPERTY_BUILD_MODEL_ONLY,
        //      AndroidProject.PROPERTY_REFRESH_EXTERNAL_NATIVE_MODEL are set.
        //   - *and* AndroidProject.PROPERTY_REFRESH_EXTERNAL_NATIVE_MODEL is set
        //      or JSON files don't exist or are out-of-date.
        // Create phase may cause ProcessException (from cmake.exe for example)
        //
        // READ PHASE:
        // Reads and deserializes JSONs when:
        //   - Any one of AndroidProject.PROPERTY_INVOKED_FROM_IDE,
        //      AndroidProject.PROPERTY_BUILD_MODEL_ONLY_ADVANCED,
        //      AndroidProject.PROPERTY_BUILD_MODEL_ONLY,
        //      AndroidProject.PROPERTY_REFRESH_EXTERNAL_NATIVE_MODEL are set.
        // Read phase may produce IOException if the file can't be read for standard IO reasons.
        // Read phase may produce JsonSyntaxException in the case that the content of the file is
        // corrupt.
        boolean forceRegeneration =
                projectOptions.get(BooleanOption.IDE_REFRESH_EXTERNAL_NATIVE_MODEL);

        checkSplitConfiguration();
        if (ExternalNativeBuildTaskUtils.shouldRegenerateOutOfDateJsons(projectOptions)) {
            threadRecorder.record(
                    ExecutionType.VARIANT_MANAGER_EXTERNAL_NATIVE_CONFIG_VALUES,
                    project.getPath(),
                    null,
                    () -> {
                        for (VariantScope variantScope : variantManager.getVariantScopes()) {
                            ExternalNativeJsonGenerator generator =
                                    variantScope.getExternalNativeJsonGenerator();
                            if (generator != null) {
                                // This will generate any out-of-date or non-existent JSONs.
                                // When refreshExternalNativeModel() is true it will also
                                // force update all JSONs.
                                generator.build(forceRegeneration);
                            }
                        }
                    });
        }
        BuildableArtifactImpl.Companion.enableResolution();
    }

    private void checkSplitConfiguration() {
        String configApkUrl = "https://d.android.com/topic/instant-apps/guides/config-splits.html";

        boolean isFeatureModule = project.getPlugins().hasPlugin(FeaturePlugin.class);
        boolean generatePureSplits = extension.getGeneratePureSplits();
        Splits splits = extension.getSplits();
        boolean splitsEnabled =
                splits.getDensity().isEnable()
                        || splits.getAbi().isEnable()
                        || splits.getLanguage().isEnable();

        // The Play Store doesn't allow Pure splits
        if (!isFeatureModule && generatePureSplits) {
            extraModelInfo
                    .getSyncIssueHandler()
                    .reportWarning(
                            Type.GENERIC,
                            "Configuration APKs are supported by the Google Play Store only when publishing Android Instant Apps. To instead generate stand-alone APKs for different device configurations, set generatePureSplits=false. For more information, go to "
                                    + configApkUrl);
        }

        if (!isFeatureModule && !generatePureSplits && splits.getLanguage().isEnable()) {
            extraModelInfo
                    .getSyncIssueHandler()
                    .reportWarning(
                            Type.GENERIC,
                            "Per-language APKs are supported only when building Android Instant Apps. For more information, go to "
                                    + configApkUrl);
        }

        if (isFeatureModule && !generatePureSplits && splitsEnabled) {
            extraModelInfo
                    .getSyncIssueHandler()
                    .reportWarning(
                            Type.GENERIC,
                            "Configuration APKs targeting different device configurations are "
                                    + "automatically built when splits are enabled for a feature module.\n"
                                    + "To suppress this warning, remove \"generatePureSplits false\" "
                                    + "from your build.gradle file.\n"
                                    + "To learn more, see "
                                    + configApkUrl);
        }
    }

    private boolean isVerbose() {
        return project.getLogger().isEnabled(LogLevel.INFO);
    }

    private boolean ensureTargetSetup() {
        // check if the target has been set.
        TargetInfo targetInfo = androidBuilder.getTargetInfo();
        // noinspection VariableNotUsedInsideIf Directly checking if initialized.
        if (targetInfo != null) {
            return true;
        }
        if (extension.getCompileOptions() == null) {
            throw new GradleException("Calling getBootClasspath before compileSdkVersion");
        }

        return sdkHandler.initTarget(
                extension.getCompileSdkVersion(),
                extension.getBuildToolsRevision(),
                extension.getLibraryRequests(),
                androidBuilder,
                SdkHandler.useCachedSdk(projectOptions));
    }

    private void checkConfigureOnDemandGradleVersionCompat() {
        // https://issuetracker.google.com/77910727
        if (project.getGradle().getStartParameter().isConfigureOnDemand()
                && GradleVersion.parse(project.getGradle().getGradleVersion()).compareTo("4.6")
                        >= 0) {
            throw new StopExecutionException(
                    "Configuration on demand is not supported by the current version of the Android"
                            + " Gradle plugin since you are using Gradle version 4.6 or above."
                            + " Suggestion: disable configuration on demand by setting"
                            + " org.gradle.configureondemand=false in your gradle.properties file"
                            + " or use a Gradle version less than 4.6.");
        }
    }

    /**
     * Check the sub-projects structure :
     * So far, checks that 2 modules do not have the same identification (group+name).
     */
    private void checkModulesForErrors() {
        Project rootProject = project.getRootProject();
        Map<String, Project> subProjectsById = new HashMap<>();
        for (Project subProject : rootProject.getAllprojects()) {
            String id = subProject.getGroup().toString() + ":" + subProject.getName();
            if (subProjectsById.containsKey(id)) {
                String message = String.format(
                        "Your project contains 2 or more modules with the same " +
                                "identification %1$s\n" +
                                "at \"%2$s\" and \"%3$s\".\n" +
                                "You must use different identification (either name or group) for " +
                                "each modules.",
                        id,
                        subProjectsById.get(id).getPath(),
                        subProject.getPath() );
                throw new StopExecutionException(message);
            } else {
                subProjectsById.put(id, subProject);
            }
        }
    }

    private void checkPathForErrors() {
        // See if we're on Windows:
        if (!System.getProperty("os.name").toLowerCase(Locale.US).contains("windows")) {
            return;
        }

        // See if the user disabled the check:
        if (projectOptions.get(BooleanOption.OVERRIDE_PATH_CHECK_PROPERTY)
                || projectOptions.get(BooleanOption.OVERRIDE_PATH_CHECK_PROPERTY_OLD)) {
            return;
        }

        // See if the path contains non-ASCII characters.
        if (CharMatcher.ASCII.matchesAllOf(project.getRootDir().getAbsolutePath())) {
            return;
        }

        String message =
                "Your project path contains non-ASCII characters. This will most likely "
                        + "cause the build to fail on Windows. Please move your project to a different "
                        + "directory. See http://b.android.com/95744 for details. "
                        + "This warning can be disabled by adding the line '"
                        + BooleanOption.OVERRIDE_PATH_CHECK_PROPERTY.getPropertyName()
                        + "=true' to gradle.properties file in the project directory.";

        throw new StopExecutionException(message);
    }

    @NonNull
    @Override
    public ToolingModelBuilderRegistry getModelBuilderRegistry() {
        return registry;
    }

    private SettingsController getSettingsController() {
        Proxy proxy = createProxy(System.getProperties(), getLogger());
        return new SettingsController() {
            @Override
            public boolean getForceHttp() {
                return false;
            }

            @Override
            public void setForceHttp(boolean force) {
                // Default, doesn't allow to set force HTTP.
            }

            @Nullable
            @Override
            public Channel getChannel() {
                Integer channel = projectOptions.get(IntegerOption.ANDROID_SDK_CHANNEL);
                if (channel != null) {
                    return Channel.create(channel);
                } else {
                    return Channel.DEFAULT;
                }
            }

            @NonNull
            @Override
            public Proxy getProxy() {
                return proxy;
            }
        };
    }

    @VisibleForTesting
    static Proxy createProxy(@NonNull Properties properties, @NonNull ILogger logger) {
        String host = properties.getProperty("https.proxyHost");
        int port = 443;
        if (host != null) {
            String maybePort = properties.getProperty("https.proxyPort");
            if (maybePort != null) {
                try {
                    port = Integer.parseInt(maybePort);
                } catch (NumberFormatException e) {
                    logger.info("Invalid https.proxyPort '" + maybePort + "', using default 443");
                }
            }
        }
        else {
            host = properties.getProperty("http.proxyHost");
            //noinspection VariableNotUsedInsideIf
            if (host != null) {
                port = 80;
                String maybePort = properties.getProperty("http.proxyPort");
                if (maybePort != null) {
                    try {
                        port = Integer.parseInt(maybePort);
                    } catch (NumberFormatException e) {
                        logger.info("Invalid http.proxyPort '" + maybePort + "', using default 80");
                    }
                }
            }
        }
        if (host != null) {
            InetSocketAddress proxyAddr = createAddress(host, port);
            if (proxyAddr != null) {
                return new Proxy(Proxy.Type.HTTP, proxyAddr);
            }
        }
        return Proxy.NO_PROXY;

    }

    private static InetSocketAddress createAddress(String proxyHost, int proxyPort) {
        try {
            InetAddress address = InetAddress.getByName(proxyHost);
            return new InetSocketAddress(address, proxyPort);
        } catch (UnknownHostException e) {
            new ConsoleProgressIndicator().logWarning("Failed to parse host " + proxyHost);
            return null;
        }
    }

    private Downloader getDownloader() {
        return new LocalFileAwareDownloader(
                new LegacyDownloader(FileOpUtils.create(), getSettingsController()));
    }

    /**
     * returns the kotlin plugin version, or null if plugin is not applied to this project, or
     * "unknown" if plugin is applied but version can't be determined.
     */
    @Nullable
    private String getKotlinPluginVersion() {
        Plugin plugin = project.getPlugins().findPlugin("kotlin-android");
        if (plugin == null) {
            return null;
        }
        try {
            // No null checks below because we're catching all exceptions.
            Method method = plugin.getClass().getMethod("getKotlinPluginVersion");
            method.setAccessible(true);
            return method.invoke(plugin).toString();
        } catch (Throwable e) {
            // Defensively catch all exceptions because we don't want it to crash
            // if kotlin plugin code changes unexpectedly.
            return "unknown";
        }
    }

    private SourceSetManager createSourceSetManager() {
        return new SourceSetManager(
                project,
                isPackagePublished(),
                extraModelInfo.getDeprecationReporter(),
                extraModelInfo.getSyncIssueHandler());
    }

    /**
     * If overridden in a subclass to return "true," the package Configuration will be named
     * "publish" instead of "apk"
     */
    protected boolean isPackagePublished() {
        return false;
    }
}
