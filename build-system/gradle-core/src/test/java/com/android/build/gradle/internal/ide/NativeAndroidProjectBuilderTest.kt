/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.ide

import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsonStreamingParser
import com.google.common.truth.Truth.assertThat
import com.google.gson.stream.JsonReader
import java.io.StringReader
import org.junit.Test

class NativeAndroidProjectBuilderTest {
    @Test
    fun testHeaderFileThatWasPassedAsSource() {
        val builder = NativeAndroidProjectBuilder("project")
        val visitor = NativeAndroidProjectBuilder.JsonStreamingVisitor(builder, "variant")
        val reader = JsonReader(StringReader("{\n" +
                "  \"buildFiles\": [\n" +
                "    \"/usr/local/google/home/jomof/Projects/GradleTest/native_lib/../../../Android/Sdk/ndk-bundle/build/cmake/android.toolchain.cmake\",\n" +
                "    \"/usr/local/google/home/jomof/Projects/GradleTest/native_lib/CMakeLists.txt\"\n" +
                "  ],\n" +
                "  \"cleanCommands\": [\n" +
                "    \"/usr/local/google/home/jomof/Android/Sdk/cmake/3.10.4604376/bin/cmake --build /usr/local/google/home/jomof/Projects/GradleTest/native_lib/.externalNativeBuild/cmake/release/x86_64 --target clean\"\n" +
                "  ],\n" +
                "  \"libraries\": {\n" +
                "    \"native-lib-Release-x86_64\": {\n" +
                "      \"buildCommand\": \"/usr/local/google/home/jomof/Android/Sdk/cmake/3.10.4604376/bin/cmake --build /usr/local/google/home/jomof/Projects/GradleTest/native_lib/.externalNativeBuild/cmake/release/x86_64 --target native-lib\",\n" +
                "      \"buildType\": \"release\",\n" +
                "      \"toolchain\": \"1351519597\",\n" +
                "      \"abi\": \"x86_64\",\n" +
                "      \"artifactName\": \"native-lib\",\n" +
                "      \"files\": [\n" +
                "        {\n" +
                "          \"src\": \"/usr/local/google/home/jomof/Projects/GradleTest/native_lib/src/main/cpp/native-lib.hpp\",\n" +
                "          \"workingDirectory\": \"/usr/local/google/home/jomof/Projects/GradleTest/native_lib/.externalNativeBuild/cmake/release/x86_64\"\n" +
                "        },\n" +
                "        {\n" +
                "          \"src\": \"/usr/local/google/home/jomof/Projects/GradleTest/native_lib/src/main/cpp/native-lib.cpp\",\n" +
                "          \"flags\": \"-isystem /usr/local/google/home/jomof/Android/Sdk/ndk-bundle/sysroot/usr/include/x86_64-linux-android -D__ANDROID_API__=21 -g -DANDROID -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -Wa,--noexecstack -Wformat -Werror=format-security   -O2 -DNDEBUG  -fPIC  \",\n" +
                "          \"workingDirectory\": \"/usr/local/google/home/jomof/Projects/GradleTest/native_lib/.externalNativeBuild/cmake/release/x86_64\"\n" +
                "        }\n" +
                "      ],\n" +
                "      \"output\": \"/usr/local/google/home/jomof/Projects/GradleTest/native_lib/build/intermediates/cmake/release/obj/x86_64/libnative-lib.so\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"toolchains\": {\n" +
                "    \"1351519597\": {\n" +
                "      \"cppCompilerExecutable\": \"/usr/local/google/home/jomof/Android/Sdk/ndk-bundle/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"cFileExtensions\": [],\n" +
                "  \"cppFileExtensions\": [\n" +
                "    \"cpp\"\n" +
                "  ]\n" +
                "}"))
        AndroidBuildGradleJsonStreamingParser(reader, visitor).parse()
        val result = builder.buildOrNull()!!
        val sourceFiles = result.artifacts.toTypedArray()[0].sourceFiles
        assertThat(sourceFiles.size).isEqualTo(1)
        assertThat(sourceFiles.toTypedArray()[0].filePath.toString()
            .endsWith("cpp/native-lib.cpp"))
            .isTrue()
    }
}