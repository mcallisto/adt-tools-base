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

package com.android.build.gradle.integration.databinding;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.options.BooleanOption;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(FilterableParameterized.class)
public class DataBindingIntegrationTestAppsConnectedTest {
    @Rule public GradleTestProject project;
    @Rule public Adb adb = new Adb();

    public DataBindingIntegrationTestAppsConnectedTest(String projectName, boolean useV2) {
        project =
                GradleTestProject.builder()
                        .fromDataBindingIntegrationTest(projectName)
                        .addGradleProperties(
                                BooleanOption.ENABLE_DATA_BINDING_V2.getPropertyName()
                                        + "="
                                        + useV2)
                        .create();
    }

    @Parameterized.Parameters(name = "app_{0}_useV2_{1}")
    public static Iterable<Object[]> classNames() {
        // "App With Spaces", not supported by bazel :/
        List<Object[]> params = new ArrayList<>();
        for (boolean useV2 : new boolean[] {true, false}) {
            params.add(new Object[] {"IndependentLibrary", useV2});
            params.add(new Object[] {"TestApp", useV2});
            params.add(new Object[] {"ProguardedAppWithTest", useV2});
            params.add(new Object[] {"AppWithDataBindingInTests", useV2});
        }
        return params;
    }

    @Before
    public void clean() throws IOException, InterruptedException {
        project.execute("clean");
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedCheck() throws Exception {
        project.executeConnectedCheck();
    }
}
