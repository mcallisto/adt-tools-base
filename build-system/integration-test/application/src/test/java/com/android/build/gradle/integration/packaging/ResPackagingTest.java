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

package com.android.build.gradle.integration.packaging;

import static com.android.build.gradle.integration.common.fixture.TemporaryProjectModification.doTest;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.AbstractAndroidSubject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * test for packaging of android asset files.
 *
 * This only uses raw files. This is not about running aapt tests, this is only about
 * everything around it, so raw files are easier to test in isolation.
 */
public class ResPackagingTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();

    private GradleTestProject appProject;
    private GradleTestProject libProject;
    private GradleTestProject libProject2;
    private GradleTestProject testProject;

    private void execute(String... tasks) throws IOException, InterruptedException {
        project.executor().run(tasks);
    }

    @Before
    public void setUp() throws Exception {
        appProject = project.getSubproject("app");
        libProject = project.getSubproject("library");
        libProject2 = project.getSubproject("library2");
        testProject = project.getSubproject("test");

        // rewrite settings.gradle to remove un-needed modules
        Files.write("include 'app'\n"
                + "include 'library'\n"
                + "include 'library2'\n"
                + "include 'test'\n",
                project.getSettingsFile(),
                Charsets.UTF_8);

        // setup dependencies.
        TestFileUtils.appendToFile(appProject.getBuildFile(),
                "android {\n"
                + "    publishNonDefault true\n"
                + "}\n"
                + "\n"
                + "dependencies {\n"
                + "    compile project(':library')\n"
                + "}\n");

        TestFileUtils.appendToFile(libProject.getBuildFile(),
                "dependencies {\n"
                + "    compile project(':library2')\n"
                + "}\n");

        TestFileUtils.appendToFile(testProject.getBuildFile(),
                "android {\n"
                + "    targetProjectPath ':app'\n"
                + "    targetVariant 'debug'\n"
                + "}\n");

        // put some default files in the 4 projects, to check non incremental packaging as well,
        // and to provide files to change to test incremental support.
        File appDir = appProject.getTestDir();
        createOriginalResFile(appDir,  "main",        "file.txt",         "app:abcd");
        createOriginalResFile(appDir,  "androidTest", "filetest.txt",     "appTest:abcd");

        File testDir = testProject.getTestDir();
        createOriginalResFile(testDir, "main",        "file.txt",         "test:abcd");

        File libDir = libProject.getTestDir();
        createOriginalResFile(libDir,  "main",        "filelib.txt",      "library:abcd");
        createOriginalResFile(libDir,  "androidTest", "filelibtest.txt",  "libraryTest:abcd");

        File lib2Dir = libProject2.getTestDir();
        createOriginalResFile(lib2Dir, "main",        "filelib2.txt",     "library2:abcd");
        createOriginalResFile(lib2Dir, "androidTest", "filelib2test.txt", "library2Test:abcd");
    }

    @After
    public void cleanUp() {
        project = null;
        appProject = null;
        testProject = null;
        libProject = null;
        libProject2 = null;
    }

    private static void createOriginalResFile(
            @NonNull File projectFolder,
            @NonNull String dimension,
            @NonNull String filename,
            @NonNull String content)
            throws Exception {
        File assetFolder = FileUtils.join(projectFolder, "src", dimension, "res", "raw");
        FileUtils.mkdirs(assetFolder);
        Files.write(content, new File(assetFolder, filename), Charsets.UTF_8);
    }

    @Test
    public void testNonIncrementalPackaging() throws Exception {
        execute("clean", "assembleDebug", "assembleAndroidTest");

        // chek the files are there. Start from the bottom of the dependency graph
        checkAar(    libProject2, "filelib2.txt",     "library2:abcd");
        checkTestApk(libProject2, "filelib2.txt",     "library2:abcd");
        checkTestApk(libProject2, "filelib2test.txt", "library2Test:abcd");

        checkAar(    libProject,  "filelib.txt",     "library:abcd");
        // aar does not contain dependency's assets
        checkAar(    libProject, "filelib2.txt",     null);
        // test apk contains both test-ony assets, lib assets, and dependency assets.
        checkTestApk(libProject, "filelib.txt",      "library:abcd");
        checkTestApk(libProject, "filelib2.txt",     "library2:abcd");
        checkTestApk(libProject, "filelibtest.txt",  "libraryTest:abcd");
        // but not the assets of the dependency's own test
        checkTestApk(libProject, "filelib2test.txt", null);

        // app contain own assets + all dependencies' assets.
        checkApk(    appProject, "file.txt",         "app:abcd");
        checkApk(    appProject, "filelib.txt",      "library:abcd");
        checkApk(    appProject, "filelib2.txt",     "library2:abcd");
        checkTestApk(appProject, "filetest.txt",     "appTest:abcd");
        // app test does not contain dependencies' own test assets.
        checkTestApk(appProject, "filelibtest.txt",  null);
        checkTestApk(appProject, "filelib2test.txt", null);
    }

    // ---- APP DEFAULT ---

    @Test
    public void testAppProjectWithNewResFile() throws Exception {
        execute("app:clean", "app:assembleDebug");

        doTest(appProject, project -> {
            project.addFile("src/main/res/raw/newfile.txt", "newfile content");
            execute("app:assembleDebug");

            checkApk(appProject, "newfile.txt", "newfile content");
        });
    }

    @Test
    public void testAppProjectWithRemovedResFile() throws Exception {
        execute("app:clean", "app:assembleDebug");

        doTest(appProject, project -> {
            project.removeFile("src/main/res/raw/file.txt");
            execute("app:assembleDebug");

            checkApk(appProject, "file.txt", null);
        });
    }

    @Test
    public void testAppProjectWithModifiedResFile() throws Exception {
        execute("app:clean", "app:assembleDebug");

        doTest(appProject, project -> {
            project.replaceFile("src/main/res/raw/file.txt", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "file.txt", "new content");
        });
    }

    @Test
    public void testAppProjectWithNewDebugResFileOverridingMain()
            throws Exception {
        execute("app:clean", "app:assembleDebug");

        doTest(appProject, project -> {
            project.addFile("src/debug/res/raw/file.txt", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "file.txt", "new content");
        });

        // file's been removed, checking in the other direction.
        execute("app:assembleDebug");
        checkApk(appProject, "file.txt", "app:abcd");
    }

    @Test
    public void testAppProjectWithnewResFileOverridingDependency() throws Exception {
        execute("app:clean", "app:assembleDebug");

        doTest(appProject, project -> {
            project.addFile("src/main/res/raw/filelib.txt", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "filelib.txt", "new content");
        });

        // file's been removed, checking in the other direction.
        execute("app:assembleDebug");
        checkApk(appProject, "filelib.txt", "library:abcd");
    }

    @Test
    public void testAppProjectWithnewResFileInDebugSourceSet() throws Exception {
        execute("app:clean", "app:assembleDebug");

        doTest(appProject, project -> {
            project.addFile("src/debug/res/raw/file.txt", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "file.txt", "new content");
        });

        // file's been removed, checking in the other direction.
        execute("app:assembleDebug");
        checkApk(appProject, "file.txt", "app:abcd");
    }

    @Test
    public void testAppProjectWithModifiedResInDependency() throws Exception {
        execute("app:clean", "library:clean", "app:assembleDebug");

        doTest(libProject, project -> {
            project.replaceFile("src/main/res/raw/filelib.txt", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "filelib.txt", "new content");
        });
    }

    @Test
    public void testAppProjectWithAddedResInDependency() throws Exception {
        execute("app:clean", "library:clean", "app:assembleDebug");

        doTest(libProject, project -> {
            project.addFile("src/main/res/raw/new_lib_file.txt", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "new_lib_file.txt", "new content");
        });
    }

    @Test
    public void testAppProjectWithRemovedResInDependency() throws Exception {
        execute("app:clean", "library:clean", "app:assembleDebug");

        doTest(libProject, project -> {
            project.removeFile("src/main/res/raw/filelib.txt");
            execute("app:assembleDebug");

            checkApk(appProject, "filelib.txt", null);
        });
    }

    @Test
    public void testAppResourcesAreFilteredByMinSdkFull() throws Exception {
        // Here are which files go into where:
        //  (none)  v14     v16
        //  f1
        //  f2      f2
        //  f3      f3      f3
        //          f4      f4
        //                  f5
        //
        // If we build without minSdk defined, we should get everything exactly as shown.
        //
        // If we build with minSdkVersion = 14 we should end up with:
        // (none)   v14     v16
        //  f1
        //          f2
        //          f3      f3
        //          f4      f4
        //                  f5
        //
        // If we build with minSdkVersion = 16 we should end up with:
        // (none)   v14     v16
        //  f1
        //          f2
        //                  f3
        //                  f4
        //                  f5

        File raw = appProject.file("src/main/res/raw");
        raw.mkdirs();

        File raw14 = appProject.file("src/main/res/raw-v14");
        raw14.mkdirs();

        File raw16 = appProject.file("src/main/res/raw-v16");
        raw16.mkdirs();

        byte[] f1NoneC = new byte[] { 0 };
        byte[] f2NoneC = new byte[] { 1 };
        byte[] f2v14C = new byte[] { 2 };
        byte[] f3NoneC = new byte[] { 3 };
        byte[] f3v14C = new byte[] { 4 };
        byte[] f3v16C = new byte[] { 5 };
        byte[] f4v14C = new byte[] { 6 };
        byte[] f4v16C = new byte[] { 7 };
        byte[] f5v16C = new byte[] { 8 };

        File f1None = new File(raw, "f1");
        Files.write(f1NoneC, f1None);

        File f2None = new File(raw, "f2");
        Files.write(f2NoneC, f2None);

        File f2v14 = new File(raw14, "f2");
        Files.write(f2v14C, f2v14);

        File f3None = new File(raw, "f3");
        Files.write(f3NoneC, f3None);

        File f3v14 = new File(raw14, "f3");
        Files.write(f3v14C, f3v14);

        File f3v16 = new File(raw16, "f3");
        Files.write(f3v16C, f3v16);

        File f4v14 = new File(raw14, "f4");
        Files.write(f4v14C, f4v14);

        File f4v16 = new File(raw16, "f4");
        Files.write(f4v16C, f4v16);

        File f5v16 = new File(raw16, "f5");
        Files.write(f5v16C, f5v16);


        File appGradleFile = appProject.file("build.gradle");
        String appGradleFileContents = Files.toString(appGradleFile, Charset.defaultCharset());

        // Set no min SDK version and generate the APK.
        String newBuild = appGradleFileContents.replaceAll("minSdkVersion 8", "");
        Files.write(newBuild, appGradleFile, Charset.defaultCharset());
        execute("clean", ":app:assembleDebug");

        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw/f1", f1NoneC);
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw/f2", f2NoneC);
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw/f3", f3NoneC);
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw-v14/f2", f2v14C);
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw-v14/f3", f3v14C);
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw-v14/f4", f4v14C);
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw-v16/f3", f3v16C);
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw-v16/f4", f4v16C);
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw-v16/f5", f5v16C);

        // Set min SDK version 14 and generate the APK.
        newBuild = appGradleFileContents.replaceAll("minSdkVersion 8", "minSdkVersion 14");
        Files.write(newBuild, appGradleFile, Charset.defaultCharset());
        execute("clean", ":app:assembleDebug");

        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw/f1", f1NoneC);
        assertThat(appProject.getApk("debug")).doesNotContain("res/raw/f2");
        assertThat(appProject.getApk("debug")).doesNotContain("res/raw/f3");
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw-v14/f2", f2v14C);
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw-v14/f3", f3v14C);
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw-v14/f4", f4v14C);
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw-v16/f3", f3v16C);
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw-v16/f4", f4v16C);
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw-v16/f5", f5v16C);

        // Set min SDK version 16 and generate the APK.
        newBuild = appGradleFileContents.replaceAll("minSdkVersion 8", "minSdkVersion 16");
        Files.write(newBuild, appGradleFile, Charset.defaultCharset());
        execute("clean", ":app:assembleDebug");

        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw/f1", f1NoneC);
        assertThat(appProject.getApk("debug")).doesNotContain("res/raw/f2");
        assertThat(appProject.getApk("debug")).doesNotContain("res/raw/f3");
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw-v14/f2", f2v14C);
        assertThat(appProject.getApk("debug")).doesNotContain("res/raw-v14/f3");
        assertThat(appProject.getApk("debug")).doesNotContain("res/raw-v14/f4");
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw-v16/f3", f3v16C);
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw-v16/f4", f4v16C);
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw-v16/f5", f5v16C);
    }

    @Test
    public void testAppResourcesAreFilteredByMinSdkIncremental() throws Exception {
        // Note: this test is very similar to the previous one but, instead of trying all 3
        // versions independently, we start with min SDK 14, then change to no min SDK and set
        // min SDK to 16. The outputs should be the same as in the previous test.

        File raw = appProject.file("src/main/res/raw");
        raw.mkdirs();

        File raw14 = appProject.file("src/main/res/raw-v14");
        raw14.mkdirs();

        File raw16 = appProject.file("src/main/res/raw-v16");
        raw16.mkdirs();

        byte[] f1NoneC = new byte[] { 0 };
        byte[] f2NoneC = new byte[] { 1 };
        byte[] f2v14C = new byte[] { 2 };
        byte[] f3NoneC = new byte[] { 3 };
        byte[] f3v14C = new byte[] { 4 };
        byte[] f3v16C = new byte[] { 5 };
        byte[] f4v14C = new byte[] { 6 };
        byte[] f4v16C = new byte[] { 7 };
        byte[] f5v16C = new byte[] { 8 };

        File f1None = new File(raw, "f1");
        Files.write(f1NoneC, f1None);

        File f2None = new File(raw, "f2");
        Files.write(f2NoneC, f2None);

        File f2v14 = new File(raw14, "f2");
        Files.write(f2v14C, f2v14);

        File f3None = new File(raw, "f3");
        Files.write(f3NoneC, f3None);

        File f3v14 = new File(raw14, "f3");
        Files.write(f3v14C, f3v14);

        File f3v16 = new File(raw16, "f3");
        Files.write(f3v16C, f3v16);

        File f4v14 = new File(raw14, "f4");
        Files.write(f4v14C, f4v14);

        File f4v16 = new File(raw16, "f4");
        Files.write(f4v16C, f4v16);

        File f5v16 = new File(raw16, "f5");
        Files.write(f5v16C, f5v16);


        File appGradleFile = appProject.file("build.gradle");
        String appGradleFileContents = Files.toString(appGradleFile, Charset.defaultCharset());

        // Set min SDK version 14 and generate the APK.
        String newBuild = appGradleFileContents.replaceAll("minSdkVersion 8", "minSdkVersion 14");
        Files.write(newBuild, appGradleFile, Charset.defaultCharset());
        execute("clean", ":app:assembleDebug");

        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw/f1", f1NoneC);
        assertThat(appProject.getApk("debug")).doesNotContain("res/raw/f2");
        assertThat(appProject.getApk("debug")).doesNotContain("res/raw/f3");
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw-v14/f2", f2v14C);
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw-v14/f3", f3v14C);
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw-v14/f4", f4v14C);
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw-v16/f3", f3v16C);
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw-v16/f4", f4v16C);
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw-v16/f5", f5v16C);

        // Set no min SDK version and generate the APK. Incremental update!
        newBuild = appGradleFileContents.replaceAll("minSdkVersion 8", "");
        Files.write(newBuild, appGradleFile, Charset.defaultCharset());
        execute(":app:assembleDebug");

        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw/f1", f1NoneC);
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw/f2", f2NoneC);
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw/f3", f3NoneC);
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw-v14/f2", f2v14C);
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw-v14/f3", f3v14C);
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw-v14/f4", f4v14C);
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw-v16/f3", f3v16C);
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw-v16/f4", f4v16C);
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw-v16/f5", f5v16C);

        // Set min SDK version 16 and generate the APK. Incremental update!
        newBuild = appGradleFileContents.replaceAll("minSdkVersion 8", "minSdkVersion 16");
        Files.write(newBuild, appGradleFile, Charset.defaultCharset());
        execute(":app:assembleDebug");

        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw/f1", f1NoneC);
        assertThat(appProject.getApk("debug")).doesNotContain("res/raw/f2");
        assertThat(appProject.getApk("debug")).doesNotContain("res/raw/f3");
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw-v14/f2", f2v14C);
        assertThat(appProject.getApk("debug")).doesNotContain("res/raw-v14/f3");
        assertThat(appProject.getApk("debug")).doesNotContain("res/raw-v14/f4");
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw-v16/f3", f3v16C);
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw-v16/f4", f4v16C);
        assertThat(appProject.getApk("debug"))
                .containsFileWithContent("res/raw-v16/f5", f5v16C);
    }

    // ---- APP TEST ---

    @Test
    public void testAppProjectTestWithNewResFile() throws Exception {
        execute("app:clean", "app:assembleAT");

        doTest(appProject, project -> {
            project.addFile("src/androidTest/res/raw/newfile.txt", "new file content");
            execute("app:assembleAT");

            checkTestApk(appProject, "newfile.txt", "new file content");
        });
    }

    @Test
    public void testAppProjectTestWithRemovedResFile() throws Exception {
        execute("app:clean", "app:assembleAT");

        doTest(appProject, project -> {
            project.removeFile("src/androidTest/res/raw/filetest.txt");
            execute("app:assembleAT");

            checkTestApk(appProject, "filetest.txt", null);
        });
    }

    @Test
    public void testAppProjectTestWithModifiedResFile() throws Exception {
        execute("app:clean", "app:assembleAT");

        doTest(appProject, project -> {
            project.replaceFile("src/androidTest/res/raw/filetest.txt", "new content");
            execute("app:assembleAT");

            checkTestApk(appProject, "filetest.txt", "new content");
        });
    }

    // ---- LIB DEFAULT ---

    @Test
    public void testLibProjectWithNewResFile() throws Exception {
        execute("library:clean", "library:assembleDebug");

        doTest(libProject, project -> {
            project.addFile("src/main/res/raw/newfile.txt", "newfile content");
            execute("library:assembleDebug");

            checkAar(libProject, "newfile.txt", "newfile content");
        });
    }

    @Test
    public void testLibProjectWithRemovedResFile() throws Exception {
        execute("library:clean", "library:assembleDebug");

        doTest(libProject, project -> {
            project.removeFile("src/main/res/raw/filelib.txt");
            execute("library:assembleDebug");

            checkAar(libProject, "filelib.txt", null);
        });
    }

    @Test
    public void testLibProjectWithModifiedResFile() throws Exception {
        execute("library:clean", "library:assembleDebug");

        doTest(libProject, project -> {
            project.replaceFile("src/main/res/raw/filelib.txt", "new content");
            execute("library:assembleDebug");

            checkAar(libProject, "filelib.txt", "new content");
        });
    }

    @Test
    public void testLibProjectWithnewResFileInDebugSourceSet() throws Exception {
        execute("library:clean", "library:assembleDebug");

        doTest(libProject, project -> {
            project.addFile("src/debug/res/raw/filelib.txt", "new content");
            execute("library:assembleDebug");

            checkAar(libProject, "filelib.txt", "new content");
        });

        // file's been removed, checking in the other direction.
        execute("library:assembleDebug");
        checkAar(libProject, "filelib.txt", "library:abcd");
    }

    // ---- LIB TEST ---

    @Test
    public void testLibProjectTestWithNewResFile() throws Exception {
        execute("library:clean", "library:assembleAT");

        doTest(libProject, project -> {
            project.addFile("src/androidTest/res/raw/newfile.txt", "new file content");
            execute("library:assembleAT");

            checkTestApk(libProject, "newfile.txt", "new file content");
        });
    }

    @Test
    public void testLibProjectTestWithRemovedResFile() throws Exception {
        execute("library:clean", "library:assembleAT");

        doTest(libProject, project -> {
            project.removeFile("src/androidTest/res/raw/filelibtest.txt");
            execute("library:assembleAT");

            checkTestApk(libProject, "filelibtest.txt", null);
        });
    }

    @Test
    public void testLibProjectTestWithModifiedResFile() throws Exception {
        execute("library:clean", "library:assembleAT");

        doTest(libProject, project -> {
            project.replaceFile("src/androidTest/res/raw/filelibtest.txt", "new content");
            execute("library:assembleAT");

            checkTestApk(libProject, "filelibtest.txt", "new content");
        });
    }

    @Test
    public void testLibProjectTestWithnewResFileOverridingTestedLib() throws Exception {
        execute("library:clean", "library:assembleAT");

        doTest(libProject, project -> {
            project.addFile("src/androidTest/res/raw/filelib.txt", "new content");
            execute("library:assembleAT");

            checkTestApk(libProject, "filelib.txt", "new content");
        });

        // files been removed, checking in the other direction.
        execute("library:assembleAT");
        checkTestApk(libProject, "filelib.txt", "library:abcd");

    }

    @Test
    public void testLibProjectTestWithnewResFileOverridingDependency() throws Exception {
        execute("library:clean", "library:assembleAT");

        doTest(libProject, project -> {
            project.addFile("src/androidTest/res/raw/filelib2.txt", "new content");
            execute("library:assembleAT");

            checkTestApk(libProject, "filelib2.txt", "new content");
        });

        // file's been removed, checking in the other direction.
        execute("library:assembleAT");
        checkTestApk(libProject, "filelib2.txt", "library2:abcd");
    }

    // ---- TEST DEFAULT ---

    @Test
    public void testTestProjectWithNewResFile() throws Exception {
        execute("test:clean", "test:assembleDebug");

        doTest(testProject, project -> {
            project.addFile("src/main/res/raw/newfile.txt", "newfile content");
            execute("test:assembleDebug");

            checkApk(testProject, "newfile.txt", "newfile content");
        });
    }

    @Test
    public void testTestProjectWithRemovedResFile() throws Exception {
        execute("test:clean", "test:assembleDebug");

        doTest(testProject, project -> {
            project.removeFile("src/main/res/raw/file.txt");
            execute("test:assembleDebug");

            checkApk(testProject, "file.txt", null);
        });
    }

    @Test
    public void testTestProjectWithModifiedResFile() throws Exception {
        execute("test:clean", "test:assembleDebug");

        doTest(testProject, project -> {
            project.replaceFile("src/main/res/raw/file.txt", "new content");
            execute("test:assembleDebug");

            checkApk(testProject, "file.txt", "new content");
        });
    }

    // -----------------------

    /**
     * check an apk has (or not) the given res file name.
     *
     * <p>If the content is non-null the file is expected to be there with the same content. If the
     * content is null the file is not expected to be there.
     *
     * @param project the project
     * @param filename the filename
     * @param content the content
     */
    private static void checkApk(
            @NonNull GradleTestProject project, @NonNull String filename, @Nullable String content)
            throws Exception {
        check(assertThat(project.getApk("debug")), filename, content);
    }

    /**
     * check a test apk has (or not) the given res file name.
     *
     * <p>If the content is non-null the file is expected to be there with the same content. If the
     * content is null the file is not expected to be there.
     *
     * @param project the project
     * @param filename the filename
     * @param content the content
     */
    private static void checkTestApk(
            @NonNull GradleTestProject project, @NonNull String filename, @Nullable String content)
            throws Exception {
        check(assertThat(project.getTestApk()), filename, content);
    }

    /**
     * check an aat has (or not) the given res file name.
     *
     * <p>If the content is non-null the file is expected to be there with the same content. If the
     * content is null the file is not expected to be there.
     *
     * @param project the project
     * @param filename the filename
     * @param content the content
     */
    private static void checkAar(
            @NonNull GradleTestProject project, @NonNull String filename, @Nullable String content)
            throws Exception {
        check(assertThat(project.getAar("debug")), filename, content);
    }

    private static void check(
            @NonNull AbstractAndroidSubject subject,
            @NonNull String filename,
            @Nullable String content)
            throws Exception {
        if (content != null) {
            subject.containsFileWithContent("res/raw/" + filename, content);
        } else {
            subject.doesNotContainResource("raw/" + filename);
        }
    }
}
