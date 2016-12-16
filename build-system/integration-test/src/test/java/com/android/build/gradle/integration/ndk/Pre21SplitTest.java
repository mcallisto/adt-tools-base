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

package com.android.build.gradle.integration.ndk;

import com.android.build.gradle.integration.common.category.SmokeTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.testutils.apk.Apk;
import com.android.testutils.truth.MoreTruth;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Test split DSL with API level < 21.
 */
@Category(SmokeTests.class)
public class Pre21SplitTest {
    @ClassRule public static GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(new HelloWorldJniApp())
            .addGradleProperties("android.useDeprecatedNdk=true")
            .create();

    @BeforeClass
    public static void setUp() throws IOException {
        TestFileUtils.appendToFile(project.getBuildFile(),
                "apply plugin: 'com.android.application'\n"
                + "\n"
                + "android {\n"
                + "    compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n"
                + "    buildToolsVersion \"" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "\"\n"
                + "\n"
                + "    generatePureSplits false\n"
                + "\n"
                + "    defaultConfig {\n"
                + "        minSdkVersion 15\n"
                + "        ndk {\n"
                + "            moduleName \"hello-jni\"\n"
                + "        }\n"
                + "    }\n"
                + "\n"
                + "    splits {\n"
                + "        abi {\n"
                + "            enable true\n"
                + "            reset()\n"
                + "            include 'x86', 'armeabi-v7a', 'mips'\n"
                + "        }\n"
                + "    }\n"
                + "}\n");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkSplitsDslWorksWithApiLevelLessThan21() throws IOException {
        project.execute("assembleX86Debug");

        // Verify .so are built for all platform.
        Apk apk = project.getApk("x86", "debug");
        MoreTruth.assertThat(apk).doesNotContain("lib/armeabi-v7a/libhello-jni.so");
        MoreTruth.assertThat(apk).doesNotContain("lib/mips/libhello-jni.so");
        MoreTruth.assertThat(apk).contains("lib/x86/libhello-jni.so");
    }
}
