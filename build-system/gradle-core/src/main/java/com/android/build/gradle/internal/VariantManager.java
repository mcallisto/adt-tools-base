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

package com.android.build.gradle.internal;

import static com.android.builder.core.BuilderConstants.LINT;
import static com.android.builder.core.VariantType.ANDROID_TEST;
import static com.android.builder.core.VariantType.FEATURE;
import static com.android.builder.core.VariantType.UNIT_TEST;
import static org.gradle.api.internal.artifacts.ArtifactAttributes.ARTIFACT_FORMAT;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.attributes.BuildTypeAttr;
import com.android.build.api.attributes.ProductFlavorAttr;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.TestedAndroidConfig;
import com.android.build.gradle.api.AndroidSourceSet;
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet;
import com.android.build.gradle.internal.api.ReadOnlyObjectProvider;
import com.android.build.gradle.internal.api.VariantFilter;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dependency.AarTransform;
import com.android.build.gradle.internal.dependency.AlternateCompatibilityRule;
import com.android.build.gradle.internal.dependency.AlternateDisambiguationRule;
import com.android.build.gradle.internal.dependency.AndroidTypeAttr;
import com.android.build.gradle.internal.dependency.AndroidTypeAttrCompatRule;
import com.android.build.gradle.internal.dependency.AndroidTypeAttrDisambRule;
import com.android.build.gradle.internal.dependency.ExtractAarTransform;
import com.android.build.gradle.internal.dependency.JarTransform;
import com.android.build.gradle.internal.dependency.LibrarySymbolTableTransform;
import com.android.build.gradle.internal.dependency.SourceSetManager;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.dsl.BaseFlavor;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.CoreBuildType;
import com.android.build.gradle.internal.dsl.CoreProductFlavor;
import com.android.build.gradle.internal.dsl.CoreSigningConfig;
import com.android.build.gradle.internal.profile.AnalyticsUtil;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.TaskContainer;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.build.gradle.internal.variant.TestVariantFactory;
import com.android.build.gradle.internal.variant.TestedVariantData;
import com.android.build.gradle.internal.variant.VariantFactory;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.SigningOptions;
import com.android.build.gradle.options.StringOption;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.DefaultManifestParser;
import com.android.builder.core.DefaultProductFlavor;
import com.android.builder.core.DefaultProductFlavor.DimensionRequest;
import com.android.builder.core.ManifestAttributeSupplier;
import com.android.builder.core.VariantType;
import com.android.builder.errors.EvalIssueReporter;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.SigningConfig;
import com.android.builder.profile.ProcessProfileWriter;
import com.android.builder.profile.Recorder;
import com.android.utils.StringHelper;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.wireless.android.sdk.stats.ApiVersion;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan.ExecutionType;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeMatchingStrategy;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.model.ObjectFactory;

/**
 * Class to create, manage variants.
 */
public class VariantManager implements VariantModel {

    private static final String MULTIDEX_VERSION = "1.0.2";

    protected static final String COM_ANDROID_SUPPORT_MULTIDEX =
            "com.android.support:multidex:" + MULTIDEX_VERSION;
    protected static final String COM_ANDROID_SUPPORT_MULTIDEX_INSTRUMENTATION =
            "com.android.support:multidex-instrumentation:" + MULTIDEX_VERSION;

    @NonNull private final Project project;
    @NonNull private final ProjectOptions projectOptions;
    @NonNull private final AndroidBuilder androidBuilder;
    @NonNull private final AndroidConfig extension;
    @NonNull private final VariantFactory variantFactory;
    @NonNull private final TaskManager taskManager;
    @NonNull private final SourceSetManager sourceSetManager;
    @NonNull private final Recorder recorder;
    @NonNull private final ProductFlavorData<CoreProductFlavor> defaultConfigData;
    @NonNull private final Map<String, BuildTypeData> buildTypes;
    @NonNull private final VariantFilter variantFilter;
    @NonNull private final List<VariantScope> variantScopes;
    @NonNull private final Map<String, ProductFlavorData<CoreProductFlavor>> productFlavors;
    @NonNull private final Map<String, SigningConfig> signingConfigs;
    @NonNull private final Map<File, ManifestAttributeSupplier> manifestParserMap;
    @NonNull protected final GlobalScope globalScope;
    @Nullable private final CoreSigningConfig signingOverride;

    public VariantManager(
            @NonNull GlobalScope globalScope,
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull AndroidConfig extension,
            @NonNull VariantFactory variantFactory,
            @NonNull TaskManager taskManager,
            @NonNull SourceSetManager sourceSetManager,
            @NonNull Recorder recorder) {
        this.globalScope = globalScope;
        this.extension = extension;
        this.androidBuilder = androidBuilder;
        this.project = project;
        this.projectOptions = projectOptions;
        this.variantFactory = variantFactory;
        this.taskManager = taskManager;
        this.sourceSetManager = sourceSetManager;
        this.recorder = recorder;
        this.signingOverride = createSigningOverride();
        this.variantFilter = new VariantFilter(new ReadOnlyObjectProvider());
        this.buildTypes = Maps.newHashMap();
        this.variantScopes = Lists.newArrayList();
        this.productFlavors = Maps.newHashMap();
        this.signingConfigs = Maps.newHashMap();
        this.manifestParserMap = Maps.newHashMap();

        DefaultAndroidSourceSet mainSourceSet =
                (DefaultAndroidSourceSet) extension.getSourceSets().getByName(extension.getDefaultConfig().getName());

        DefaultAndroidSourceSet androidTestSourceSet = null;
        DefaultAndroidSourceSet unitTestSourceSet = null;
        if (variantFactory.hasTestScope()) {
            androidTestSourceSet =
                    (DefaultAndroidSourceSet) extension.getSourceSets()
                            .getByName(ANDROID_TEST.getPrefix());
            unitTestSourceSet =
                    (DefaultAndroidSourceSet) extension.getSourceSets()
                            .getByName(UNIT_TEST.getPrefix());
        }

        this.defaultConfigData =
                new ProductFlavorData<>(
                        extension.getDefaultConfig(),
                        mainSourceSet,
                        androidTestSourceSet,
                        unitTestSourceSet);
    }

    /**
     * Registers a new variant.
     *
     * <p>Unfortunately VariantData and VariantScope are tangled together and are really parts of
     * the same, but we'll try to gradually shift all the immutable state to VariantScope and
     * pretend that there's only an edge from scope to data.
     */
    public void addVariant(BaseVariantData variantData) {
        variantScopes.add(variantData.getScope());
    }

    @NonNull
    @Override
    public ProductFlavorData<CoreProductFlavor> getDefaultConfig() {
        return defaultConfigData;
    }

    @Override
    @NonNull
    public Map<String, BuildTypeData> getBuildTypes() {
        return buildTypes;
    }

    @Override
    @NonNull
    public Map<String, ProductFlavorData<CoreProductFlavor>> getProductFlavors() {
        return productFlavors;
    }

    @Override
    @NonNull
    public Map<String, SigningConfig> getSigningConfigs() {
        return signingConfigs;
    }

    public void addSigningConfig(@NonNull SigningConfig signingConfig) {
        signingConfigs.put(signingConfig.getName(), signingConfig);
    }

    /**
     * Adds new BuildType, creating a BuildTypeData, and the associated source set,
     * and adding it to the map.
     * @param buildType the build type.
     */
    public void addBuildType(@NonNull CoreBuildType buildType) {
        String name = buildType.getName();
        checkName(name, "BuildType");

        if (productFlavors.containsKey(name)) {
            throw new RuntimeException("BuildType names cannot collide with ProductFlavor names");
        }

        DefaultAndroidSourceSet mainSourceSet =
                (DefaultAndroidSourceSet) sourceSetManager.setUpSourceSet(name);

        DefaultAndroidSourceSet androidTestSourceSet = null;
        DefaultAndroidSourceSet unitTestSourceSet = null;
        if (variantFactory.hasTestScope()) {
            if (buildType.getName().equals(extension.getTestBuildType())) {
                androidTestSourceSet =
                        (DefaultAndroidSourceSet)
                                sourceSetManager.setUpTestSourceSet(
                                        computeSourceSetName(buildType.getName(), ANDROID_TEST));
            }

            unitTestSourceSet =
                    (DefaultAndroidSourceSet)
                            sourceSetManager.setUpTestSourceSet(
                                    computeSourceSetName(buildType.getName(), UNIT_TEST));
        }

        BuildTypeData buildTypeData =
                new BuildTypeData(
                        buildType, mainSourceSet, androidTestSourceSet, unitTestSourceSet);

        buildTypes.put(name, buildTypeData);
    }

    /**
     * Adds a new ProductFlavor, creating a ProductFlavorData and associated source sets,
     * and adding it to the map.
     *
     * @param productFlavor the product flavor
     */
    public void addProductFlavor(@NonNull CoreProductFlavor productFlavor) {
        String name = productFlavor.getName();
        checkName(name, "ProductFlavor");

        if (buildTypes.containsKey(name)) {
            throw new RuntimeException("ProductFlavor names cannot collide with BuildType names");
        }

        DefaultAndroidSourceSet mainSourceSet =
                (DefaultAndroidSourceSet) sourceSetManager.setUpSourceSet(productFlavor.getName());

        DefaultAndroidSourceSet androidTestSourceSet = null;
        DefaultAndroidSourceSet unitTestSourceSet = null;
        if (variantFactory.hasTestScope()) {
            androidTestSourceSet =
                    (DefaultAndroidSourceSet)
                            sourceSetManager.setUpTestSourceSet(
                                    computeSourceSetName(productFlavor.getName(), ANDROID_TEST));
            unitTestSourceSet =
                    (DefaultAndroidSourceSet)
                            sourceSetManager.setUpTestSourceSet(
                                    computeSourceSetName(productFlavor.getName(), UNIT_TEST));
        }

        ProductFlavorData<CoreProductFlavor> productFlavorData =
                new ProductFlavorData<>(
                        productFlavor, mainSourceSet, androidTestSourceSet, unitTestSourceSet);

        productFlavors.put(productFlavor.getName(), productFlavorData);
    }

    /** Returns a list of all created {@link VariantScope}s. */
    @NonNull
    public List<VariantScope> getVariantScopes() {
        return variantScopes;
    }

    /**
     * Returns the {@link BaseVariantData} for every {@link VariantScope} known. Don't use this, get
     * the {@link VariantScope}s instead.
     *
     * @see #getVariantScopes()
     * @deprecated Kept only not to break the Kotlin plugin.
     */
    @NonNull
    @Deprecated
    public List<BaseVariantData> getVariantDataList() {
        List<BaseVariantData> result = Lists.newArrayListWithExpectedSize(variantScopes.size());
        for (VariantScope variantScope : variantScopes) {
            result.add(variantScope.getVariantData());
        }
        return result;
    }

    /**
     * Variant/Task creation entry point.
     */
    public void createAndroidTasks() {
        variantFactory.validateModel(this);
        variantFactory.preVariantWork(project);

        if (variantScopes.isEmpty()) {
            recorder.record(
                    ExecutionType.VARIANT_MANAGER_CREATE_VARIANTS,
                    project.getPath(),
                    null /*variantName*/,
                    this::populateVariantDataList);
        }

        // Create top level test tasks.
        recorder.record(
                ExecutionType.VARIANT_MANAGER_CREATE_TESTS_TASKS,
                project.getPath(),
                null /*variantName*/,
                () -> taskManager.createTopLevelTestTasks(!productFlavors.isEmpty()));



        for (final VariantScope variantScope : variantScopes) {
            recorder.record(
                    ExecutionType.VARIANT_MANAGER_CREATE_TASKS_FOR_VARIANT,
                    project.getPath(),
                    variantScope.getFullVariantName(),
                    () -> createTasksForVariantData(variantScope));
        }

        taskManager.createReportTasks(variantScopes);
    }

    /** Create assemble task for VariantData. */
    private void createAssembleTaskForVariantData(final BaseVariantData variantData) {
        final VariantScope variantScope = variantData.getScope();
        if (variantData.getType().isForTesting()) {
            variantScope.setAssembleTask(taskManager.createAssembleTask(variantData));
        } else {
            BuildTypeData buildTypeData =
                    buildTypes.get(variantData.getVariantConfiguration().getBuildType().getName());

            Preconditions.checkNotNull(buildTypeData.getAssembleTask());

            if (productFlavors.isEmpty()) {
                // Reuse assemble task for build type if there is no product flavor.
                variantScope.setAssembleTask(buildTypeData.getAssembleTask());

                variantData.addTask(
                        TaskContainer.TaskKind.ASSEMBLE, buildTypeData.getAssembleTask());
            } else {
                variantScope.setAssembleTask(taskManager.createAssembleTask(variantData));

                // setup the task dependencies
                // build type
                buildTypeData.getAssembleTask().dependsOn(variantScope.getAssembleTask());

                // each flavor
                GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();
                for (CoreProductFlavor flavor : variantConfig.getProductFlavors()) {
                    ProductFlavorData productFlavorData = productFlavors.get(flavor.getName());

                    DefaultTask flavorAssembleTask = productFlavorData.getAssembleTask();
                    if (flavorAssembleTask == null) {
                        flavorAssembleTask = taskManager.createAssembleTask(productFlavorData);
                        productFlavorData.setAssembleTask(flavorAssembleTask);
                    }
                    flavorAssembleTask.dependsOn(variantScope.getAssembleTask());
                }

                // assembleTask for this flavor(dimension), created on demand if needed.
                if (variantConfig.getProductFlavors().size() > 1) {
                    final String name = StringHelper.capitalize(variantConfig.getFlavorName());
                    final String variantAssembleTaskName =
                            StringHelper.appendCapitalized("assemble", name);
                    if (!taskManager.getTaskFactory().containsKey(variantAssembleTaskName)) {
                        Task task = taskManager.getTaskFactory().create(variantAssembleTaskName);
                        task.setDescription("Assembles all builds for flavor combination: " + name);
                        task.setGroup("Build");
                        task.dependsOn(variantScope.getAssembleTask().getName());
                    }
                    taskManager
                            .getTaskFactory()
                            .configure(
                                    "assemble", task1 -> task1.dependsOn(variantAssembleTaskName));
                }
            }
        }
    }

    /** Create tasks for the specified variant. */
    public void createTasksForVariantData(final VariantScope variantScope) {
        final BaseVariantData variantData = variantScope.getVariantData();
        final VariantType variantType = variantData.getType();

        final GradleVariantConfiguration variantConfig = variantScope.getVariantConfiguration();

        final BuildTypeData buildTypeData = buildTypes.get(variantConfig.getBuildType().getName());
        if (buildTypeData.getAssembleTask() == null) {
            buildTypeData.setAssembleTask(taskManager.createAssembleTask(buildTypeData));
        }

        // Add dependency of assemble task on assemble build type task.
        taskManager
                .getTaskFactory()
                .configure(
                        "assemble",
                        task -> {
                            assert buildTypeData.getAssembleTask() != null;
                            task.dependsOn(buildTypeData.getAssembleTask().getName());
                        });

        createAssembleTaskForVariantData(variantData);
        if (variantType.isForTesting()) {
            final BaseVariantData testedVariantData =
                    (BaseVariantData) ((TestVariantData) variantData).getTestedVariantData();

            // Add the container of dependencies, the order of the libraries is important.
            // In descending order: build type (only for unit test), flavors, defaultConfig.

            // Add the container of dependencies.
            // The order of the libraries is important, in descending order:
            // variant-specific, build type (, multi-flavor, flavor1, flavor2, ..., defaultConfig.
            // variant-specific if the full combo of flavors+build type. Does not exist if no flavors.
            // multi-flavor is the combination of all flavor dimensions. Does not exist if <2 dimension.
            List<CoreProductFlavor> testProductFlavors = variantConfig.getProductFlavors();
            List<DefaultAndroidSourceSet> testVariantSourceSets =
                    Lists.newArrayListWithExpectedSize(4 + testProductFlavors.size());

            // 1. add the variant-specific if applicable.
            if (!testProductFlavors.isEmpty()) {
                testVariantSourceSets.add(
                        (DefaultAndroidSourceSet) variantConfig.getVariantSourceProvider());
            }

            // 2. the build type.
            DefaultAndroidSourceSet buildTypeConfigurationProvider =
                    buildTypeData.getTestSourceSet(variantType);
            if (buildTypeConfigurationProvider != null) {
                testVariantSourceSets.add(buildTypeConfigurationProvider);
            }

            // 3. the multi-flavor combination
            if (testProductFlavors.size() > 1) {
                testVariantSourceSets.add(
                        (DefaultAndroidSourceSet) variantConfig.getMultiFlavorSourceProvider());
            }

            // 4. the flavors.
            for (CoreProductFlavor productFlavor : testProductFlavors) {
                testVariantSourceSets.add(
                        this.productFlavors
                                .get(productFlavor.getName())
                                .getTestSourceSet(variantType));
            }

            // now add the default config
            testVariantSourceSets.add(defaultConfigData.getTestSourceSet(variantType));

            // If the variant being tested is a library variant, VariantDependencies must be
            // computed after the tasks for the tested variant is created.  Therefore, the
            // VariantDependencies is computed here instead of when the VariantData was created.
            VariantDependencies.Builder builder =
                    VariantDependencies.builder(
                                    project,
                                    variantScope.getGlobalScope().getErrorHandler(),
                                    variantConfig)
                            .setConsumeType(
                                    getConsumeType(
                                            testedVariantData.getVariantConfiguration().getType()))
                            .addSourceSets(testVariantSourceSets)
                            .setFlavorSelection(getFlavorSelection(variantConfig))
                            .setTestedVariantDependencies(testedVariantData.getVariantDependency())
                            .setBaseSplit(
                                    variantType == VariantType.FEATURE
                                            && extension.getBaseFeature());

            final VariantDependencies variantDep = builder.build();
            variantData.setVariantDependency(variantDep);

            if (variantType == VariantType.ANDROID_TEST && variantConfig.isLegacyMultiDexMode()) {
                project.getDependencies().add(
                        variantDep.getCompileClasspath().getName(), COM_ANDROID_SUPPORT_MULTIDEX_INSTRUMENTATION);
                project.getDependencies().add(
                        variantDep.getRuntimeClasspath().getName(), COM_ANDROID_SUPPORT_MULTIDEX_INSTRUMENTATION);
            }

            if (testedVariantData.getVariantConfiguration().getRenderscriptSupportModeEnabled()) {
                project.getDependencies()
                        .add(
                                variantDep.getCompileClasspath().getName(),
                                project.files(androidBuilder.getRenderScriptSupportJar()));
            }

            switch (variantType) {
                case ANDROID_TEST:
                    taskManager.createAndroidTestVariantTasks((TestVariantData) variantData);
                    break;
                case UNIT_TEST:
                    taskManager.createUnitTestVariantTasks((TestVariantData) variantData);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown test type " + variantType);
            }
        } else {
            taskManager.createTasksForVariantScope(variantScope);
        }
    }

    @NonNull
    private Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> getFlavorSelection(
            @NonNull GradleVariantConfiguration config) {
        ProductFlavor mergedFlavors = config.getMergedFlavor();
        if (mergedFlavors instanceof DefaultProductFlavor) {
            ObjectFactory factory = project.getObjects();

            return ((DefaultProductFlavor) mergedFlavors)
                    .getMissingDimensionStrategies()
                    .entrySet()
                    .stream()
                    .collect(
                            Collectors.toMap(
                                    entry -> Attribute.of(entry.getKey(), ProductFlavorAttr.class),
                                    entry ->
                                            factory.named(
                                                    ProductFlavorAttr.class,
                                                    entry.getValue().getRequested())));
        }

        return ImmutableMap.of();
    }

    @NonNull
    private AndroidTypeAttr getConsumeType(@NonNull VariantType type) {
        switch (type) {
            case DEFAULT:
                if (variantFactory instanceof TestVariantFactory) {
                    return project.getObjects().named(AndroidTypeAttr.class, AndroidTypeAttr.APK);
                }
                return project.getObjects().named(AndroidTypeAttr.class, AndroidTypeAttr.AAR);
            case LIBRARY:
                return project.getObjects().named(AndroidTypeAttr.class, AndroidTypeAttr.AAR);
            case FEATURE:
            case INSTANTAPP:
                return project.getObjects().named(AndroidTypeAttr.class, AndroidTypeAttr.FEATURE);
            case ANDROID_TEST:
            case UNIT_TEST:
                throw new IllegalStateException(
                        "Variant type '" + type + "' should not be publishing anything");
        }
        throw new IllegalStateException(
                "Unsupported VariantType requested in getConsumeType(): " + type);
    }

    @NonNull
    private AndroidTypeAttr getPublishingType(@NonNull VariantType type) {
        switch (type) {
            case DEFAULT:
                return project.getObjects().named(AndroidTypeAttr.class, AndroidTypeAttr.APK);
            case LIBRARY:
                return project.getObjects().named(AndroidTypeAttr.class, AndroidTypeAttr.AAR);
            case FEATURE:
            case INSTANTAPP:
                return project.getObjects().named(AndroidTypeAttr.class, AndroidTypeAttr.FEATURE);
            case ANDROID_TEST:
            case UNIT_TEST:
                throw new IllegalStateException(
                        "Variant type '" + type + "' should not be publishing anything");
        }
        throw new IllegalStateException(
                "Unsupported VariantType requested in getPublishingType(): " + type);
    }

    public void configureDependencies() {
        final DependencyHandler dependencies = project.getDependencies();

        // register transforms.
        final String explodedAarType = ArtifactType.EXPLODED_AAR.getType();
        dependencies.registerTransform(
                reg -> {
                    reg.getFrom().attribute(ARTIFACT_FORMAT, AndroidArtifacts.TYPE_AAR);
                    reg.getTo().attribute(ARTIFACT_FORMAT, explodedAarType);
                    reg.artifactTransform(ExtractAarTransform.class);
                });

        boolean sharedLibSupport =
                globalScope
                        .getProjectOptions()
                        .get(BooleanOption.CONSUME_DEPENDENCIES_AS_SHARED_LIBRARIES);
        for (ArtifactType transformTarget : AarTransform.getTransformTargets()) {
            dependencies.registerTransform(
                    reg -> {
                        reg.getFrom().attribute(ARTIFACT_FORMAT, explodedAarType);
                        reg.getTo().attribute(ARTIFACT_FORMAT, transformTarget.getType());
                        reg.artifactTransform(
                                AarTransform.class,
                                config -> config.params(transformTarget, sharedLibSupport));
                    });
        }

        dependencies.registerTransform(
                reg -> {
                    reg.getFrom().attribute(ARTIFACT_FORMAT, explodedAarType);
                    reg.getTo()
                            .attribute(
                                    ARTIFACT_FORMAT,
                                    ArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME.getType());
                    reg.artifactTransform(LibrarySymbolTableTransform.class);
                });

        for (String transformTarget : JarTransform.getTransformTargets()) {
            dependencies.registerTransform(
                    reg -> {
                        reg.getFrom().attribute(ARTIFACT_FORMAT, "jar");
                        reg.getTo().attribute(ARTIFACT_FORMAT, transformTarget);
                        reg.artifactTransform(JarTransform.class);
                    });
        }

        AttributesSchema schema = dependencies.getAttributesSchema();

        // custom strategy for AndroidTypeAttr
        AttributeMatchingStrategy<AndroidTypeAttr> androidTypeAttrStrategy =
                schema.attribute(AndroidTypeAttr.ATTRIBUTE);
        androidTypeAttrStrategy.getCompatibilityRules().add(AndroidTypeAttrCompatRule.class);
        androidTypeAttrStrategy.getDisambiguationRules().add(AndroidTypeAttrDisambRule.class);

        // custom strategy for build-type and product-flavor.
        setBuildTypeStrategy(schema);

        setupFlavorStrategy(schema);
    }

    private static <F, T> List<T> convert(
            @NonNull Collection<F> values,
            @NonNull Function<F, ?> function,
            @NonNull Class<T> convertedType) {
        return values.stream()
                .map(function)
                .filter(convertedType::isInstance)
                .map(convertedType::cast)
                .collect(Collectors.toList());
    }

    private void setBuildTypeStrategy(@NonNull AttributesSchema schema) {
        // this is ugly but because the getter returns a very base class we have no choices.
        // In the case of the experimental plugin, we don't support matching.
        List<BuildType> dslBuildTypes =
                convert(buildTypes.values(), BuildTypeData::getBuildType, BuildType.class);

        if (dslBuildTypes.isEmpty()) {
            return;
        }

        Map<String, List<String>> alternateMap = Maps.newHashMap();

        for (BuildType buildType : dslBuildTypes) {
            if (!buildType.getMatchingFallbacks().isEmpty()) {
                alternateMap.put(buildType.getName(), buildType.getMatchingFallbacks());
            }
        }

        if (!alternateMap.isEmpty()) {
            AttributeMatchingStrategy<BuildTypeAttr> buildTypeStrategy =
                    schema.attribute(BuildTypeAttr.ATTRIBUTE);

            buildTypeStrategy
                    .getCompatibilityRules()
                    .add(
                            AlternateCompatibilityRule.BuildTypeRule.class,
                            config -> config.setParams(alternateMap));
            buildTypeStrategy
                    .getDisambiguationRules()
                    .add(
                            AlternateDisambiguationRule.BuildTypeRule.class,
                            config -> config.setParams(alternateMap));
        }
    }

    private void setupFlavorStrategy(AttributesSchema schema) {
        // this is ugly but because the getter returns a very base class we have no choices.
        // In the case of the experimental plugin, we don't support matching.
        List<com.android.build.gradle.internal.dsl.ProductFlavor> flavors =
                convert(
                        productFlavors.values(),
                        ProductFlavorData::getProductFlavor,
                        com.android.build.gradle.internal.dsl.ProductFlavor.class);

        // first loop through all the flavors and collect for each dimension, and each value, its
        // fallbacks

        // map of (dimension > (requested > fallbacks))
        Map<String, Map<String, List<String>>> alternateMap = Maps.newHashMap();
        for (com.android.build.gradle.internal.dsl.ProductFlavor flavor : flavors) {
            if (!flavor.getMatchingFallbacks().isEmpty()) {
                String name = flavor.getName();
                String dimension = flavor.getDimension();

                Map<String, List<String>> dimensionMap =
                        alternateMap.computeIfAbsent(dimension, s -> Maps.newHashMap());

                dimensionMap.put(name, flavor.getMatchingFallbacks());
            }

            handleMissingDimensions(alternateMap, flavor);
        }

        // also handle missing dimensions on the default config.
        if (defaultConfigData.getProductFlavor() instanceof BaseFlavor) {
            handleMissingDimensions(
                    alternateMap, (BaseFlavor) defaultConfigData.getProductFlavor());
        }

        // now that we know we have all the fallbacks for each dimensions, we can create the
        // rule instances.
        for (Map.Entry<String, Map<String, List<String>>> entry : alternateMap.entrySet()) {
            addFlavorStrategy(schema, entry.getKey(), entry.getValue());
        }
    }

    public static void addFlavorStrategy(
            @NonNull AttributesSchema schema,
            @NonNull String dimension,
            @NonNull Map<String, List<String>> alternateMap) {
        Attribute<ProductFlavorAttr> attr = Attribute.of(dimension, ProductFlavorAttr.class);
        AttributeMatchingStrategy<ProductFlavorAttr> flavorStrategy = schema.attribute(attr);

        flavorStrategy
                .getCompatibilityRules()
                .add(
                        AlternateCompatibilityRule.ProductFlavorRule.class,
                        config -> config.setParams(alternateMap));
        flavorStrategy
                .getDisambiguationRules()
                .add(
                        AlternateDisambiguationRule.ProductFlavorRule.class,
                        config -> config.setParams(alternateMap));
    }

    private static void handleMissingDimensions(
            @NonNull Map<String, Map<String, List<String>>> alternateMap,
            @NonNull BaseFlavor flavor) {
        Map<String, DimensionRequest> missingStrategies = flavor.getMissingDimensionStrategies();
        if (!missingStrategies.isEmpty()) {
            for (Map.Entry<String, DimensionRequest> entry : missingStrategies.entrySet()) {
                String dimension = entry.getKey();

                Map<String, List<String>> dimensionMap =
                        alternateMap.computeIfAbsent(dimension, s -> Maps.newHashMap());

                dimensionMap.put(entry.getValue().getRequested(), entry.getValue().getFallbacks());
            }
        }
    }

    /**
     * Returns a modified name.
     *
     * <p>This name is used to request a missing dimension. It is the same name as the flavor that
     * sets up the request, which means it's not going to be matched, and instead it'll go to a
     * custom fallbacks provided by the flavor.
     *
     * <p>We are just modifying the name to avoid collision in case the same name exists in
     * different dimensions
     */
    public static String getModifiedName(@NonNull String name) {
        return "____" + name;
    }

    /**
     * Create all variants.
     */
    public void populateVariantDataList() {
        List<String> flavorDimensionList = extension.getFlavorDimensionList();

        if (productFlavors.isEmpty()) {
            configureDependencies();
            createVariantDataForProductFlavors(Collections.emptyList());
        } else {
            // ensure that there is always a dimension
            if (flavorDimensionList == null || flavorDimensionList.isEmpty()) {
                androidBuilder
                        .getIssueReporter()
                        .reportError(
                                EvalIssueReporter.Type.UNNAMED_FLAVOR_DIMENSION,
                                "All flavors must now belong to a named flavor dimension. "
                                        + "Learn more at "
                                        + "https://d.android.com/r/tools/flavorDimensions-missing-error-message.html");
            } else if (flavorDimensionList.size() == 1) {
                // if there's only one dimension, auto-assign the dimension to all the flavors.
                String dimensionName = flavorDimensionList.get(0);
                for (ProductFlavorData<CoreProductFlavor> flavorData : productFlavors.values()) {
                    CoreProductFlavor flavor = flavorData.getProductFlavor();
                    if (flavor.getDimension() == null && flavor instanceof DefaultProductFlavor) {
                        ((DefaultProductFlavor) flavor).setDimension(dimensionName);
                    }
                }
            }

            // can only call this after we ensure all flavors have a dimension.
            configureDependencies();

            // Create iterable to get GradleProductFlavor from ProductFlavorData.
            Iterable<CoreProductFlavor> flavorDsl =
                    Iterables.transform(
                            productFlavors.values(),
                            ProductFlavorData::getProductFlavor);

            // Get a list of all combinations of product flavors.
            List<ProductFlavorCombo<CoreProductFlavor>> flavorComboList =
                    ProductFlavorCombo.createCombinations(
                            flavorDimensionList,
                            flavorDsl);

            for (ProductFlavorCombo<CoreProductFlavor>  flavorCombo : flavorComboList) {
                //noinspection unchecked
                createVariantDataForProductFlavors(
                        (List<ProductFlavor>) (List) flavorCombo.getFlavorList());
            }
        }
    }

    private BaseVariantData createVariantDataForVariantType(
            @NonNull com.android.builder.model.BuildType buildType,
            @NonNull List<? extends ProductFlavor> productFlavorList,
            @NonNull VariantType variantType) {
        BuildTypeData buildTypeData = buildTypes.get(buildType.getName());

        final DefaultAndroidSourceSet sourceSet = defaultConfigData.getSourceSet();
        GradleVariantConfiguration variantConfig =
                GradleVariantConfiguration.getBuilderForExtension(extension)
                        .create(
                                globalScope.getProjectOptions(),
                                defaultConfigData.getProductFlavor(),
                                sourceSet,
                                getParser(sourceSet.getManifestFile()),
                                buildTypeData.getBuildType(),
                                buildTypeData.getSourceSet(),
                                variantType,
                                signingOverride);

        // sourceSetContainer in case we are creating variant specific sourceSets.
        NamedDomainObjectContainer<AndroidSourceSet> sourceSetsContainer = extension
                .getSourceSets();

        // We must first add the flavors to the variant config, in order to get the proper
        // variant-specific and multi-flavor name as we add/create the variant providers later.
        for (ProductFlavor productFlavor : productFlavorList) {
            ProductFlavorData<CoreProductFlavor> data = productFlavors.get(
                    productFlavor.getName());

            String dimensionName = productFlavor.getDimension();
            if (dimensionName == null) {
                dimensionName = "";
            }

            variantConfig.addProductFlavor(
                    data.getProductFlavor(),
                    data.getSourceSet(),
                    dimensionName);
        }

        createCompoundSourceSets(productFlavorList, variantConfig, sourceSetManager);

        // Add the container of dependencies.
        // The order of the libraries is important, in descending order:
        // variant-specific, build type, multi-flavor, flavor1, flavor2, ..., defaultConfig.
        // variant-specific if the full combo of flavors+build type. Does not exist if no flavors.
        // multi-flavor is the combination of all flavor dimensions. Does not exist if <2 dimension.
        final List<DefaultAndroidSourceSet> variantSourceSets =
                Lists.newArrayListWithExpectedSize(productFlavorList.size() + 4);

        // 1. add the variant-specific if applicable.
        if (!productFlavorList.isEmpty()) {
            variantSourceSets.add((DefaultAndroidSourceSet) variantConfig.getVariantSourceProvider());
        }

        // 2. the build type.
        variantSourceSets.add(buildTypeData.getSourceSet());

        // 3. the multi-flavor combination
        if (productFlavorList.size() > 1) {
            variantSourceSets.add((DefaultAndroidSourceSet) variantConfig.getMultiFlavorSourceProvider());
        }

        // 4. the flavors.
        for (ProductFlavor productFlavor : productFlavorList) {
            variantSourceSets.add(productFlavors.get(productFlavor.getName()).getSourceSet());
        }

        // 5. The defaultConfig
        variantSourceSets.add(defaultConfigData.getSourceSet());

        // Done. Create the variant and get its internal storage object.
        BaseVariantData variantData =
                variantFactory.createVariantData(variantConfig, taskManager, recorder);

        VariantDependencies.Builder builder =
                VariantDependencies.builder(
                                project,
                                variantData.getScope().getGlobalScope().getErrorHandler(),
                                variantConfig)
                        .setConsumeType(
                                getConsumeType(variantData.getVariantConfiguration().getType()))
                        .setPublishType(
                                getPublishingType(variantData.getVariantConfiguration().getType()))
                        .setFlavorSelection(getFlavorSelection(variantConfig))
                        .addSourceSets(variantSourceSets)
                        .setBaseSplit(
                                variantType == VariantType.FEATURE && extension.getBaseFeature());

        final VariantDependencies variantDep = builder.build();
        variantData.setVariantDependency(variantDep);

        if (variantConfig.isLegacyMultiDexMode()) {
            project.getDependencies().add(
                    variantDep.getCompileClasspath().getName(), COM_ANDROID_SUPPORT_MULTIDEX);
            project.getDependencies().add(
                    variantDep.getRuntimeClasspath().getName(), COM_ANDROID_SUPPORT_MULTIDEX);
        }

        if (variantConfig.getRenderscriptSupportModeEnabled()) {
            File renderScriptSupportJar = androidBuilder.getRenderScriptSupportJar();

            final ConfigurableFileCollection fileCollection = project.files(renderScriptSupportJar);
            project.getDependencies()
                    .add(variantDep.getCompileClasspath().getName(), fileCollection);
            if (variantType == VariantType.DEFAULT || variantType == VariantType.FEATURE) {
                project.getDependencies()
                        .add(variantDep.getRuntimeClasspath().getName(), fileCollection);
            }
        }


        return variantData;
    }

    private static void createCompoundSourceSets(
            @NonNull List<? extends ProductFlavor> productFlavorList,
            @NonNull GradleVariantConfiguration variantConfig,
            @NonNull SourceSetManager sourceSetManager) {
        if (!productFlavorList.isEmpty() /* && !variantConfig.getType().isSingleBuildType()*/) {
            DefaultAndroidSourceSet variantSourceSet =
                    (DefaultAndroidSourceSet)
                            sourceSetManager.setUpSourceSet(
                                    computeSourceSetName(
                                            variantConfig.getFullName(), variantConfig.getType()),
                                    variantConfig.getType().isForTesting());
            variantConfig.setVariantSourceProvider(variantSourceSet);
        }

        if (productFlavorList.size() > 1) {
            DefaultAndroidSourceSet multiFlavorSourceSet =
                    (DefaultAndroidSourceSet)
                            sourceSetManager.setUpSourceSet(
                                    computeSourceSetName(
                                            variantConfig.getFlavorName(), variantConfig.getType()),
                                    variantConfig.getType().isForTesting());
            variantConfig.setMultiFlavorSourceProvider(multiFlavorSourceSet);
        }
    }

    /**
     * Turns a string into a valid source set name for the given {@link VariantType}, e.g.
     * "fooBarUnitTest" becomes "testFooBar".
     */
    @NonNull
    private static String computeSourceSetName(
            @NonNull String name,
            @NonNull VariantType variantType) {
        if (name.endsWith(variantType.getSuffix())) {
            name = name.substring(0, name.length() - variantType.getSuffix().length());
        }

        if (!variantType.getPrefix().isEmpty()) {
            name = StringHelper.appendCapitalized(variantType.getPrefix(), name);
        }

        return name;
    }

    /**
     * Create a TestVariantData for the specified testedVariantData.
     */
    public TestVariantData createTestVariantData(
            BaseVariantData testedVariantData,
            VariantType type) {
        CoreBuildType buildType = testedVariantData.getVariantConfiguration().getBuildType();
        BuildTypeData buildTypeData = buildTypes.get(buildType.getName());

        GradleVariantConfiguration testedConfig = testedVariantData.getVariantConfiguration();
        List<? extends CoreProductFlavor> productFlavorList = testedConfig.getProductFlavors();

        // handle test variant
        // need a suppress warning because ProductFlavor.getTestSourceSet(type) is annotated
        // to return @Nullable and the constructor is @NonNull on this parameter,
        // but it's never the case on defaultConfigData
        // The constructor does a runtime check on the instances so we should be safe.
        final DefaultAndroidSourceSet testSourceSet = defaultConfigData.getTestSourceSet(type);
        @SuppressWarnings("ConstantConditions")
        GradleVariantConfiguration testVariantConfig =
                testedConfig.getMyTestConfig(
                        testSourceSet,
                        testSourceSet != null ? getParser(testSourceSet.getManifestFile()) : null,
                        buildTypeData.getTestSourceSet(type),
                        type);


        for (CoreProductFlavor productFlavor : productFlavorList) {
            ProductFlavorData<CoreProductFlavor> data = productFlavors
                    .get(productFlavor.getName());

            String dimensionName = productFlavor.getDimension();
            if (dimensionName == null) {
                dimensionName = "";
            }
            // same supress warning here.
            //noinspection ConstantConditions
            testVariantConfig.addProductFlavor(
                    data.getProductFlavor(),
                    data.getTestSourceSet(type),
                    dimensionName);
        }

        createCompoundSourceSets(productFlavorList, testVariantConfig, sourceSetManager);

        // create the internal storage for this variant.
        TestVariantData testVariantData =
                new TestVariantData(
                        globalScope,
                        extension,
                        taskManager,
                        testVariantConfig,
                        (TestedVariantData) testedVariantData,
                        recorder);
        // link the testVariant to the tested variant in the other direction
        ((TestedVariantData) testedVariantData).setTestVariantData(testVariantData, type);

        return testVariantData;
    }

    /**
     * Creates VariantData for a specified list of product flavor.
     *
     * This will create VariantData for all build types of the given flavors.
     *
     * @param productFlavorList the flavor(s) to build.
     */
    private void createVariantDataForProductFlavors(
            @NonNull List<ProductFlavor> productFlavorList) {
        for (VariantType variantType : variantFactory.getVariantConfigurationTypes()) {
            createVariantDataForProductFlavorsAndVariantType(productFlavorList, variantType);
        }
    }

    private void createVariantDataForProductFlavorsAndVariantType(
            @NonNull List<ProductFlavor> productFlavorList, @NonNull VariantType variantType) {

        BuildTypeData testBuildTypeData = null;
        if (extension instanceof TestedAndroidConfig) {
            TestedAndroidConfig testedExtension = (TestedAndroidConfig) extension;

            testBuildTypeData = buildTypes.get(testedExtension.getTestBuildType());
            if (testBuildTypeData == null) {
                throw new RuntimeException(String.format(
                        "Test Build Type '%1$s' does not exist.", testedExtension.getTestBuildType()));
            }
        }

        BaseVariantData variantForAndroidTest = null;

        CoreProductFlavor defaultConfig = defaultConfigData.getProductFlavor();

        Action<com.android.build.api.variant.VariantFilter> variantFilterAction =
                extension.getVariantFilter();

        final String restrictedProject =
                projectOptions.get(StringOption.IDE_RESTRICT_VARIANT_PROJECT);
        final boolean restrictVariants = restrictedProject != null;

        // compare the project name if the type is not a lib.
        final boolean projectMatch;
        final String restrictedVariantName;
        if (restrictVariants) {
            projectMatch =
                    variantType != VariantType.LIBRARY
                            && project.getPath().equals(restrictedProject);
            restrictedVariantName = projectOptions.get(StringOption.IDE_RESTRICT_VARIANT_NAME);
        } else {
            projectMatch = false;
            restrictedVariantName = null;
        }

        for (BuildTypeData buildTypeData : buildTypes.values()) {
            boolean ignore = false;

            if (restrictVariants || variantFilterAction != null) {
                variantFilter.reset(
                        defaultConfig,
                        buildTypeData.getBuildType(),
                        variantType,
                        productFlavorList);

                if (restrictVariants) {
                    if (projectMatch) {
                        // get the app project, compare to this one, and if a match only accept
                        // the variant being built.
                        ignore = !variantFilter.getName().equals(restrictedVariantName);
                    }
                } else {
                    // variantFilterAction != null always true here.
                    variantFilterAction.execute(variantFilter);
                    ignore = variantFilter.isIgnore();
                }
            }

            if (!ignore) {
                BaseVariantData variantData =
                        createVariantDataForVariantType(
                                buildTypeData.getBuildType(), productFlavorList, variantType);
                addVariant(variantData);

                GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();
                VariantScope variantScope = variantData.getScope();
                GradleBuildVariant.Builder profileBuilder =
                        ProcessProfileWriter.getOrCreateVariant(
                                        project.getPath(), variantData.getName())
                                .setIsDebug(variantConfig.getBuildType().isDebuggable())
                                .setMinSdkVersion(
                                        AnalyticsUtil.toProto(variantConfig.getMinSdkVersion()))
                                .setMinifyEnabled(variantScope.getCodeShrinker() != null)
                                .setUseMultidex(variantConfig.isMultiDexEnabled())
                                .setUseLegacyMultidex(variantConfig.isLegacyMultiDexMode())
                                .setVariantType(variantData.getType().getAnalyticsVariantType())
                                .setDexBuilder(AnalyticsUtil.toProto(variantScope.getDexer()))
                                .setDexMerger(AnalyticsUtil.toProto(variantScope.getDexMerger()))
                                .setTestExecution(
                                        AnalyticsUtil.toProto(
                                                globalScope
                                                        .getExtension()
                                                        .getTestOptions()
                                                        .getExecutionEnum()));

                if (variantScope.getCodeShrinker() != null) {
                    profileBuilder.setCodeShrinker(
                            AnalyticsUtil.toProto(variantScope.getCodeShrinker()));
                }

                if (variantConfig.getTargetSdkVersion().getApiLevel() > 0) {
                    profileBuilder.setTargetSdkVersion(
                            AnalyticsUtil.toProto(variantConfig.getTargetSdkVersion()));
                }
                if (variantConfig.getMergedFlavor().getMaxSdkVersion() != null) {
                    profileBuilder.setMaxSdkVersion(
                            ApiVersion.newBuilder()
                                    .setApiLevel(
                                            variantConfig.getMergedFlavor().getMaxSdkVersion()));
                }

                VariantScope.Java8LangSupport supportType =
                        variantData.getScope().getJava8LangSupportType();
                if (supportType != VariantScope.Java8LangSupport.INVALID
                        && supportType != VariantScope.Java8LangSupport.UNUSED) {
                    profileBuilder.setJava8LangSupport(AnalyticsUtil.toProto(supportType));
                }

                if (variantFactory.hasTestScope()) {
                    if (buildTypeData == testBuildTypeData) {
                        variantForAndroidTest = variantData;
                    }

                    if (variantType != FEATURE) {
                        // There's nothing special about unit testing the feature variant, so
                        // there's no point creating the duplicate unit testing variant. This only
                        // causes tests to run twice when running "testDebug".
                        TestVariantData unitTestVariantData =
                                createTestVariantData(variantData, UNIT_TEST);
                        addVariant(unitTestVariantData);
                    }
                }
            }
        }

        if (variantForAndroidTest != null) {
            // TODO: b/34624400
            if (variantType != FEATURE) {
                TestVariantData androidTestVariantData =
                        createTestVariantData(variantForAndroidTest, ANDROID_TEST);
                addVariant(androidTestVariantData);
            }
        }
    }

    private static void checkName(@NonNull String name, @NonNull String displayName) {
        checkPrefix(name, displayName, ANDROID_TEST.getPrefix());
        checkPrefix(name, displayName, UNIT_TEST.getPrefix());

        if (LINT.equals(name)) {
            throw new RuntimeException(String.format(
                    "%1$s names cannot be %2$s", displayName, LINT));
        }
    }

    private static void checkPrefix(String name, String displayName, String prefix) {
        if (name.startsWith(prefix)) {
            throw new RuntimeException(String.format(
                    "%1$s names cannot start with '%2$s'", displayName, prefix));
        }
    }

    private CoreSigningConfig createSigningOverride() {
        SigningOptions signingOptions = SigningOptions.readSigningOptions(projectOptions);
        if (signingOptions != null) {
            com.android.build.gradle.internal.dsl.SigningConfig signingConfigDsl =
                    new com.android.build.gradle.internal.dsl.SigningConfig("externalOverride");

            signingConfigDsl.setStoreFile(new File(signingOptions.getStoreFile()));
            signingConfigDsl.setStorePassword(signingOptions.getStorePassword());
            signingConfigDsl.setKeyAlias(signingOptions.getKeyAlias());
            signingConfigDsl.setKeyPassword(signingOptions.getKeyPassword());

            if (signingOptions.getStoreType() != null) {
                signingConfigDsl.setStoreType(signingOptions.getStoreType());
            }

            if (signingOptions.getV1Enabled() != null) {
                signingConfigDsl.setV1SigningEnabled(signingOptions.getV1Enabled());
            }

            if (signingOptions.getV2Enabled() != null) {
                signingConfigDsl.setV2SigningEnabled(signingOptions.getV2Enabled());
            }

            return signingConfigDsl;
        }
        return null;
    }

    @NonNull
    private ManifestAttributeSupplier getParser(@NonNull File file) {
        return manifestParserMap.computeIfAbsent(file, DefaultManifestParser::new);
    }
}
