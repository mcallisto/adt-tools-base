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

package com.android.build.gradle.internal.core;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.builder.core.DefaultBuildType;
import com.android.builder.core.DefaultProductFlavor;
import com.android.builder.core.VariantType;
import com.android.builder.model.SigningConfig;
import com.android.builder.signing.DefaultSigningConfig;
import com.google.common.collect.Iterables;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class VariantConfigurationTest {

    private DefaultProductFlavor mDefaultConfig;
    private DefaultProductFlavor mFlavorConfig;
    private DefaultBuildType mBuildType;

    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private File srcDir;

    @Before
    public void setUp() throws Exception {
        mDefaultConfig = new DefaultProductFlavor("main");
        mFlavorConfig = new DefaultProductFlavor("flavor");
        mBuildType = new DefaultBuildType("debug");
        srcDir = tmp.newFolder("src");
    }

    @Test
    public void testPackageOverrideNone() {
        VariantConfiguration variant = getVariant();

        assertThat(variant.getIdOverride()).isNull();
    }

    @Test
    public void testIdOverrideIdFromFlavor() {
        mFlavorConfig.setApplicationId("foo.bar");

        VariantConfiguration variant = getVariant();

        assertThat(variant.getIdOverride()).isEqualTo("foo.bar");
    }

    @Test
    public void testPackageOverridePackageFromFlavorWithSuffix() {
        mFlavorConfig.setApplicationId("foo.bar");
        mBuildType.setApplicationIdSuffix(".fortytwo");

        VariantConfiguration variant = getVariant();

        assertThat(variant.getIdOverride()).isEqualTo("foo.bar.fortytwo");
    }

    @Test
    public void testPackageOverridePackageFromFlavorWithSuffix2() {
        mFlavorConfig.setApplicationId("foo.bar");
        mBuildType.setApplicationIdSuffix("fortytwo");

        VariantConfiguration variant = getVariant();

        assertThat(variant.getIdOverride()).isEqualTo("foo.bar.fortytwo");
    }

    @Test
    public void testPackageOverridePackageWithSuffixOnly() {

        mBuildType.setApplicationIdSuffix("fortytwo");

        VariantConfiguration variant = getVariantWithManifestPackage();

        assertThat(variant.getIdOverride()).isEqualTo("fake.package.name.fortytwo");
    }

    @Test
    public void testVersionNameFromFlavorWithSuffix() {
        mFlavorConfig.setVersionName("1.0");
        mBuildType.setVersionNameSuffix("-DEBUG");

        VariantConfiguration variant = getVariant();

        assertThat(variant.getVersionName()).isEqualTo("1.0-DEBUG");
    }

    @Test
    public void testVersionNameWithSuffixOnly() {
        mBuildType.setVersionNameSuffix("-DEBUG");

        VariantConfiguration variant = getVariantWithManifestVersion();

        assertThat(variant.getVersionName()).isEqualTo("2.0b1-DEBUG");
    }

    @Test
    public void testSigningBuildTypeOverride() {
        // DefaultSigningConfig doesn't compare the name, so put some content.
        DefaultSigningConfig debugSigning = new DefaultSigningConfig("debug");
        debugSigning.setStorePassword("debug");
        mBuildType.setSigningConfig(debugSigning);

        DefaultSigningConfig override = new DefaultSigningConfig("override");
        override.setStorePassword("override");

        VariantConfiguration variant = getVariant(override);

        assertThat(variant.getSigningConfig()).isEqualTo(override);
    }

    @Test
    public void testSigningProductFlavorOverride() {
        // DefaultSigningConfig doesn't compare the name, so put some content.
        DefaultSigningConfig defaultConfig = new DefaultSigningConfig("defaultConfig");
        defaultConfig.setStorePassword("debug");
        mDefaultConfig.setSigningConfig(defaultConfig);

        DefaultSigningConfig override = new DefaultSigningConfig("override");
        override.setStorePassword("override");

        VariantConfiguration variant = getVariant(override);

        assertThat(variant.getSigningConfig()).isEqualTo(override);
    }

    @Test
    public void testGetNavigationFiles() throws IOException {
        VariantConfiguration<DefaultBuildType, DefaultProductFlavor, DefaultProductFlavor> variant =
                getVariantWithTempFolderSourceProviders();

        File resDir = Iterables.getFirst(variant.getDefaultSourceSet().getResDirectories(), null);
        assertThat(resDir).isNotNull();

        File navigationDir = new File(resDir, "navigation");
        assertThat(navigationDir.mkdirs()).isTrue();

        File navigationFile = new File(navigationDir, "main_nav.xml");
        assertThat(navigationFile.createNewFile()).isTrue();

        List<File> retrievedNavigationFiles = variant.getNavigationFiles();
        assertThat(retrievedNavigationFiles.size()).isEqualTo(1);
        File retrievedNavigationFile = Iterables.getOnlyElement(retrievedNavigationFiles);

        assertThat(retrievedNavigationFile).isEqualTo(navigationFile);
    }

    private VariantConfiguration getVariant() {
        return getVariant(null /*signingOverride*/);
    }

    private VariantConfiguration getVariant(SigningConfig signingOverride) {
        VariantConfiguration<DefaultBuildType, DefaultProductFlavor, DefaultProductFlavor> variant =
                new VariantConfiguration<>(
                        mDefaultConfig,
                        new MockSourceProvider("main"),
                        null,
                        mBuildType,
                        new MockSourceProvider("debug"),
                        VariantType.DEFAULT,
                        signingOverride);

        variant.addProductFlavor(mFlavorConfig, new MockSourceProvider("custom"), "");

        return variant;
    }

    private VariantConfiguration getVariantWithManifestPackage() {
        VariantConfiguration<DefaultBuildType, DefaultProductFlavor, DefaultProductFlavor> variant =
                new VariantConfiguration<
                        DefaultBuildType, DefaultProductFlavor, DefaultProductFlavor>(
                        mDefaultConfig,
                        new MockSourceProvider("main"),
                        null,
                        mBuildType,
                        new MockSourceProvider("debug"),
                        VariantType.DEFAULT,
                        null /*signingConfigOverride*/) {

                    @NonNull
                    @Override
                    public String getPackageFromManifest() {
                        return "fake.package.name";
                    }
                };

        variant.addProductFlavor(mFlavorConfig, new MockSourceProvider("custom"), "");
        return variant;
    }

    private VariantConfiguration getVariantWithManifestVersion() {
        VariantConfiguration<DefaultBuildType, DefaultProductFlavor, DefaultProductFlavor> variant =
                new VariantConfiguration<
                        DefaultBuildType, DefaultProductFlavor, DefaultProductFlavor>(
                        mDefaultConfig,
                        new MockSourceProvider("main"),
                        null,
                        mBuildType,
                        new MockSourceProvider("debug"),
                        VariantType.DEFAULT,
                        null /*signingConfigOverride*/) {

                    @Override
                    public String getVersionNameFromManifest() {
                        return "2.0b1";
                    }
                    // don't do validation.
                };

        variant.addProductFlavor(mFlavorConfig, new MockSourceProvider("custom"), "");
        return variant;
    }

    private VariantConfiguration<DefaultBuildType, DefaultProductFlavor, DefaultProductFlavor>
            getVariantWithTempFolderSourceProviders() {
        VariantConfiguration<DefaultBuildType, DefaultProductFlavor, DefaultProductFlavor> variant =
                new VariantConfiguration<>(
                        mDefaultConfig,
                        new MockSourceProvider(srcDir.getPath() + File.separatorChar + "main"),
                        null,
                        mBuildType,
                        new MockSourceProvider(srcDir.getPath() + File.separatorChar + "debug"),
                        VariantType.DEFAULT,
                        null);

        variant.addProductFlavor(
                mFlavorConfig,
                new MockSourceProvider(srcDir.getPath() + File.separatorChar + "custom"),
                "");
        return variant;
    }
}
