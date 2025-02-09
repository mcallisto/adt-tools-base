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

package com.android.tools.lint.checks

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.detector.api.Detector

class ThreadDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector = ThreadDetector()

    fun testThreading() {
        val expected = (""
                +
                "src/test/pkg/ThreadTest.java:15: Error: Method onPreExecute must be called from the main thread, currently inferred thread is worker thread [WrongThread]\n"
                + "                onPreExecute(); // ERROR\n"
                + "                ~~~~~~~~~~~~~~\n"
                +
                "src/test/pkg/ThreadTest.java:16: Error: Method paint must be called from the UI thread, currently inferred thread is worker thread [WrongThread]\n"
                + "                view.paint(); // ERROR\n"
                + "                ~~~~~~~~~~~~\n"
                +
                "src/test/pkg/ThreadTest.java:22: Error: Method publishProgress must be called from the worker thread, currently inferred thread is main thread [WrongThread]\n"
                + "                publishProgress(); // ERROR\n"
                + "                ~~~~~~~~~~~~~~~~~\n"
                + "3 errors, 0 warnings\n")

        lint().files(
                java("src/test/pkg/ThreadTest.java", ""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import android.support.annotation.MainThread;\n"
                        + "import android.support.annotation.UiThread;\n"
                        + "import android.support.annotation.WorkerThread;\n"
                        + "\n"
                        + "public class ThreadTest {\n"
                        + "    public static AsyncTask testTask() {\n"
                        + "\n"
                        + "        return new AsyncTask() {\n"
                        + "            final CustomView view = new CustomView();\n"
                        + "\n"
                        + "            @Override\n"
                        + "            protected void doInBackground(Object... params) {\n"
                        + "                onPreExecute(); // ERROR\n"
                        + "                view.paint(); // ERROR\n"
                        + "                publishProgress(); // OK\n"
                        + "            }\n"
                        + "\n"
                        + "            @Override\n"
                        + "            protected void onPreExecute() {\n"
                        + "                publishProgress(); // ERROR\n"
                        + "                onProgressUpdate(); // OK\n"
                        + "                // Suppressed via older Android Studio inspection id:\n"
                        + "                //noinspection ResourceType\n"
                        + "                publishProgress(); // SUPPRESSED\n"
                        + "                // Suppressed via new lint id:\n"
                        + "                //noinspection WrongThread\n"
                        + "                publishProgress(); // SUPPRESSED\n"
                        + "                // Suppressed via Studio inspection id:\n"
                        + "                //noinspection AndroidLintWrongThread\n"
                        + "                publishProgress(); // SUPPRESSED\n"
                        + "            }\n"
                        + "        };\n"
                        + "    }\n"
                        + "\n"
                        + "    @UiThread\n"
                        + "    public static class View {\n"
                        + "        public void paint() {\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    public static class CustomView extends View {\n"
                        + "        @Override public void paint() {\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    public abstract static class AsyncTask {\n"
                        + "        @WorkerThread\n"
                        + "        protected abstract void doInBackground(Object... params);\n"
                        + "\n"
                        + "        @MainThread\n"
                        + "        protected void onPreExecute() {\n"
                        + "        }\n"
                        + "\n"
                        + "        @MainThread\n"
                        + "        protected void onProgressUpdate(Object... values) {\n"
                        + "        }\n"
                        + "\n"
                        + "        @WorkerThread\n"
                        + "        protected final void publishProgress(Object... values) {\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"),
                SUPPORT_ANNOTATIONS_CLASS_PATH,
                SUPPORT_ANNOTATIONS_JAR)
                .run()
                .expect(expected)
    }

    fun testConstructor() {
        val expected = "" +
                "src/test/pkg/ConstructorTest.java:19: Error: Constructor ConstructorTest must be called from the UI thread, currently inferred thread is worker thread [WrongThread]\n" +
                "        new ConstructorTest(res, range);\n" +
                "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                "1 errors, 0 warnings\n"
        lint().files(
                LintDetectorTest.java("src/test/pkg/ConstructorTest.java", ""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import android.support.annotation.DrawableRes;\n"
                        + "import android.support.annotation.IntRange;\n"
                        + "import android.support.annotation.UiThread;\n"
                        + "import android.support.annotation.WorkerThread;\n"
                        + "\n"
                        + "public class ConstructorTest {\n"
                        + "    @UiThread\n"
                        +
                        "    ConstructorTest(@DrawableRes int iconResId, @IntRange(from = 5) int start) {\n"
                        + "    }\n"
                        + "\n"
                        + "    public void testParameters() {\n"
                        + "        new ConstructorTest(1, 3);\n"
                        + "    }\n"
                        + "\n"
                        + "    @WorkerThread\n"
                        + "    public void testMethod(int res, int range) {\n"
                        + "        new ConstructorTest(res, range);\n"
                        + "    }\n"
                        + "}\n"),
                SUPPORT_ANNOTATIONS_CLASS_PATH,
                SUPPORT_ANNOTATIONS_JAR)
                .run()
                .expect(expected)
    }

    fun testThreadingIssue207313() {
        // Regression test for scenario in
        //  https://code.google.com/p/android/issues/detail?id=207313
        val expected = (""
                + "src/test/pkg/BigClassClient.java:10: Error: Constructor BigClass must be called from the UI thread, currently inferred thread is worker thread [WrongThread]\n"
                + "        BigClass o = new BigClass();\n"
                + "                     ~~~~~~~~~~~~~~\n"
                + "src/test/pkg/BigClassClient.java:11: Error: Method f1 must be called from the UI thread, currently inferred thread is worker thread [WrongThread]\n"
                + "        o.f1();   // correct WrongThread: must be called from the UI thread currently inferred thread is worker\n"
                + "        ~~~~~~\n"
                + "src/test/pkg/BigClassClient.java:12: Error: Method f2 must be called from the UI thread, currently inferred thread is worker thread [WrongThread]\n"
                + "        o.f2();   // correct WrongThread: must be called from the UI thread currently inferred thread is worker\n"
                + "        ~~~~~~\n"
                + "src/test/pkg/BigClassClient.java:13: Error: Method f100 must be called from the UI thread, currently inferred thread is worker thread [WrongThread]\n"
                + "        o.f100(); // correct WrongThread: must be called from the UI thread currently inferred thread is worker\n"
                + "        ~~~~~~~~\n"
                + "src/test/pkg/BigClassClient.java:22: Error: Method g must be called from the worker thread, currently inferred thread is UI thread [WrongThread]\n"
                + "        o.g();    // correct WrongThread: must be called from the worker thread currently inferred thread is UI\n"
                + "        ~~~~~\n"
                + "5 errors, 0 warnings\n")
        lint().files(
                java("src/test/pkg/BigClass.java", ""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import android.support.annotation.UiThread;\n"
                        + "import android.support.annotation.WorkerThread;\n"
                        + "\n"
                        + "@UiThread // it's here to prevent putting it on all 100 methods\n"
                        + "class BigClass {\n"
                        + "    void f1() { }\n"
                        + "    void f2() { }\n"
                        + "    //...\n"
                        + "    void f100() { }\n"
                        + "    @WorkerThread // this single method is not UI, it's something else\n"
                        + "    void g() { }\n"
                        + "    BigClass() { }\n"
                        + "}\n"),
                java("src/test/pkg/BigClassClient.java", ""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import android.support.annotation.UiThread;\n"
                        + "import android.support.annotation.WorkerThread;\n"
                        + "\n"
                        + "@SuppressWarnings(\"unused\")\n"
                        + "public class BigClassClient {\n"
                        + "    @WorkerThread\n"
                        + "    void worker() {\n"
                        + "        BigClass o = new BigClass();\n"
                        + "        o.f1();   // correct WrongThread: must be called from the UI thread currently inferred thread is worker\n"
                        + "        o.f2();   // correct WrongThread: must be called from the UI thread currently inferred thread is worker\n"
                        + "        o.f100(); // correct WrongThread: must be called from the UI thread currently inferred thread is worker\n"
                        + "        o.g();    // unexpected WrongThread: must be called from the UI thread currently inferred thread is worker\n"
                        + "    }\n"
                        + "    @UiThread\n"
                        + "    void ui() {\n"
                        + "        BigClass o = new BigClass();\n"
                        + "        o.f1();   // no problem\n"
                        + "        o.f2();   // no problem\n"
                        + "        o.f100(); // no problem\n"
                        + "        o.g();    // correct WrongThread: must be called from the worker thread currently inferred thread is UI\n"
                        + "    }\n"
                        + "}\n"),
                SUPPORT_ANNOTATIONS_CLASS_PATH,
                SUPPORT_ANNOTATIONS_JAR)
                .run()
                .expect(expected)
    }

    fun testThreadingIssue207302() {
        // Regression test for
        //    https://code.google.com/p/android/issues/detail?id=207302
        lint().files(
                java("src/test/pkg/TestPostRunnable.java", ""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import android.support.annotation.WorkerThread;\n"
                        + "import android.view.View;\n"
                        + "\n"
                        + "public class TestPostRunnable {\n"
                        + "    View view;\n"
                        + "    @WorkerThread\n"
                        + "    void f() {\n"
                        + "        view.post(new Runnable() {\n"
                        + "            @Override public void run() {\n"
                        + "                // stuff on UI thread\n"
                        + "            }\n"
                        + "        });\n"
                        + "    }\n"
                        + "}"),
                SUPPORT_ANNOTATIONS_CLASS_PATH,
                SUPPORT_ANNOTATIONS_JAR)
                .run()
                .expectClean()
    }

    fun testAnyThread() {
        val expected = (""
                + "src/test/pkg/AnyThreadTest.java:11: Error: Method worker must be called from the worker thread, currently inferred thread is any thread [WrongThread]\n"
                + "        worker(); // ERROR\n"
                + "        ~~~~~~~~\n"
                + "1 errors, 0 warnings\n")
        lint().files(
                java("src/test/pkg/AnyThreadTest.java", ""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import android.support.annotation.AnyThread;\n"
                        + "import android.support.annotation.UiThread;\n"
                        + "import android.support.annotation.WorkerThread;\n"
                        + "\n"
                        + "@UiThread\n"
                        + "class AnyThreadTest {\n"
                        + "    @AnyThread\n"
                        + "    static void threadSafe() {\n"
                        + "        worker(); // ERROR\n"
                        + "    }\n"
                        + "    @WorkerThread\n"
                        + "    static void worker() {\n"
                        + "        threadSafe(); // OK\n"
                        + "    }\n"
                        + "}\n"),
                SUPPORT_ANNOTATIONS_CLASS_PATH,
                SUPPORT_ANNOTATIONS_JAR)
                .run()
                .expect(expected)
    }

    fun testMultipleThreads() {
        // Ensure that when multiple threading annotations are specified
        // on methods, this is handled properly: calls can satisfy any one
        // threading annotation on the target, but if multiple threads are
        // found in the context, all of them must be valid for all targets
        val expected = (""
                + "src/test/pkg/MultiThreadTest.java:21: Error: Method calleee must be called from the UI or worker thread, currently inferred thread is binder and worker thread [WrongThread]\n"
                + "        calleee(); // Not ok: thread could be binder thread, not supported by target\n"
                + "        ~~~~~~~~~\n"
                + "src/test/pkg/MultiThreadTest.java:28: Error: Method calleee must be called from the UI or worker thread, currently inferred thread is worker and binder thread [WrongThread]\n"
                + "        calleee(); // Not ok: thread could be binder thread, not supported by target\n"
                + "        ~~~~~~~~~\n"
                + "2 errors, 0 warnings\n")
        lint().files(
                java("src/test/pkg/MultiThreadTest.java", ""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import android.support.annotation.BinderThread;\n"
                        + "import android.support.annotation.UiThread;\n"
                        + "import android.support.annotation.WorkerThread;\n"
                        + "\n"
                        + "class MultiThreadTest {\n"
                        + "    @UiThread\n"
                        + "    @WorkerThread\n"
                        + "    private static void calleee() {\n"
                        + "    }\n"
                        + "\n"
                        + "    @WorkerThread\n"
                        + "    private static void call1() {\n"
                        + "        calleee(); // OK - context is included in target\n"
                        + "    }\n"
                        + "\n"
                        + "    @BinderThread\n"
                        + "    @WorkerThread\n"
                        + "    private static void call2() {\n"
                        + "        calleee(); // Not ok: thread could be binder thread, not supported by target\n"
                        + "    }\n"
                        + "\n"
                        + "    // Same case as call2 but different order to make sure we don't just test the first one:\n"
                        + "    @WorkerThread\n"
                        + "    @BinderThread\n"
                        + "    private static void call3() {\n"
                        + "        calleee(); // Not ok: thread could be binder thread, not supported by target\n"
                        + "    }\n"
                        + "}\n"),
                SUPPORT_ANNOTATIONS_CLASS_PATH,
                SUPPORT_ANNOTATIONS_JAR)
                .run()
                .expect(expected)
    }

    fun testStaticMethod() {
        // Regression test for
        //  https://code.google.com/p/android/issues/detail?id=175397
        lint().files(
                java("src/test/pkg/StaticMethods.java", ""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import android.content.Context;\n"
                        + "import android.os.AsyncTask;\n"
                        + "import android.support.annotation.WorkerThread;\n"
                        + "import android.view.View;\n"
                        + "\n"
                        + "public class StaticMethods extends View {\n"
                        + "    public StaticMethods(Context context) {\n"
                        + "        super(context);\n"
                        + "    }\n"
                        + "\n"
                        + "    class MyAsyncTask extends AsyncTask<Long, Void, Boolean> {\n"
                        + "        @Override\n"
                        + "        protected Boolean doInBackground(Long... sizes) {\n"
                        + "            return workedThreadMethod();\n"
                        + "        }\n"
                        + "\n"
                        + "        @Override\n"
                        + "        protected void onPostExecute(Boolean isEnoughFree) {\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    public static boolean workedThreadMethod() {\n"
                        + "        return true;\n"
                        + "    }\n"
                        + "}"),
                SUPPORT_ANNOTATIONS_CLASS_PATH,
                SUPPORT_ANNOTATIONS_JAR)
                .run()
                .expectClean()
    }

    fun testThreadingWithinLambdas() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=223101
        lint().files(
                java(""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import android.app.Activity;\n"
                        + "import android.os.Bundle;\n"
                        + "import android.support.annotation.WorkerThread;\n"
                        + "\n"
                        + "public class LambdaThreadTest extends Activity {\n"
                        + "    @WorkerThread\n"
                        + "    static void doSomething() {}\n"
                        + "\n"
                        + "    static void doInBackground(Runnable r) {}\n"
                        + "\n"
                        + "    @Override protected void onCreate(Bundle savedInstanceState) {\n"
                        + "        super.onCreate(savedInstanceState);\n"
                        + "        doInBackground(new Runnable() {\n"
                        + "            @Override public void run() {\n"
                        + "                doSomething();\n"
                        + "            }\n"
                        + "        });\n"
                        + "        doInBackground(() -> doSomething());\n"
                        + "        doInBackground(LambdaThreadTest::doSomething);\n"
                        + "    }\n"
                        + "}\n"
                ),
                SUPPORT_ANNOTATIONS_CLASS_PATH,
                SUPPORT_ANNOTATIONS_JAR)
                .run()
                .expectClean()
    }

    fun testThreadsInLambdas() {
        // Regression test for b/38069472
        lint().files(
                LintDetectorTest.manifest().minSdk(1),
                java(""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import android.support.annotation.MainThread;\n"
                        + "import android.support.annotation.WorkerThread;\n"
                        + "\n"
                        + "import java.util.concurrent.Executor;\n"
                        + "\n"
                        + "public abstract class ApiCallInLambda<T> {\n"
                        + "    Executor networkExecutor;\n"
                        + "    @MainThread\n"
                        + "    private void fetchFromNetwork(T data) {\n"
                        + "        networkExecutor.execute(() -> {\n"
                        + "            Call<T> call = createCall();\n"
                        + "        });\n"
                        + "    }\n"
                        + "\n"
                        + "    @WorkerThread\n"
                        + "    protected abstract Call<T> createCall();\n"
                        + "\n"
                        + "    private static class Call<T> {\n"
                        + "    }\n"
                        + "}\n"),
                SUPPORT_ANNOTATIONS_CLASS_PATH,
                SUPPORT_ANNOTATIONS_JAR)
                .run()
                .expectClean()
    }

    fun testWrongThread() {

        lint().files(
                LintDetectorTest.java(""
                        + "package test.pkg;\n"
                        + "import android.support.annotation.MainThread;\n"
                        + "import android.support.annotation.UiThread;\n"
                        + "import android.support.annotation.WorkerThread;\n"
                        + "\n"
                        + "public class X {\n"
                        + "    public AsyncTask testTask() {\n"
                        + "\n"
                        + "        return new AsyncTask() {\n"
                        + "            final CustomView view = new CustomView();\n"
                        + "\n"
                        + "            @Override\n"
                        + "            protected void doInBackground(Object... params) {\n"
                        + "                /*Method onPreExecute must be called from the main thread, currently inferred thread is worker thread*/onPreExecute()/**/; // ERROR\n"
                        + "                /*Method paint must be called from the UI thread, currently inferred thread is worker thread*/view.paint()/**/; // ERROR\n"
                        + "                publishProgress(); // OK\n"
                        + "            }\n"
                        + "\n"
                        + "            @Override\n"
                        + "            protected void onPreExecute() {\n"
                        + "                /*Method publishProgress must be called from the worker thread, currently inferred thread is main thread*/publishProgress()/**/; // ERROR\n"
                        + "                onProgressUpdate(); // OK\n"
                        + "            }\n"
                        + "        };\n"
                        + "    }\n"
                        + "\n"
                        + "    @UiThread\n"
                        + "    public static class View {\n"
                        + "        public void paint() {\n"
                        + "        }\n"
                        + "    }\n"
                        + "    @some.pkg.UnrelatedNameEndsWithThread\n"
                        + "    public static void test1(View view) {\n"
                        + "        view.paint();\n"
                        + "    }\n"
                        + "\n"
                        + "    @UiThread\n"
                        + "    public static void test2(View view) {\n"
                        + "        test1(view);\n"
                        + "    }\n"
                        + "\n"
                        + "    @UiThread\n"
                        + "    public static void test3(View view) {\n"
                        + "        TestClass.test4();\n"
                        + "    }\n"
                        + "\n"
                        + "    @some.pkg.UnrelatedNameEndsWithThread\n"
                        + "    public static class TestClass {\n"
                        + "        public static void test4() {\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    public static class CustomView extends View {\n"
                        + "    }\n"
                        + "\n"
                        + "    public static abstract class AsyncTask {\n"
                        + "        @WorkerThread\n"
                        + "        protected abstract void doInBackground(Object... params);\n"
                        + "\n"
                        + "        @MainThread\n"
                        + "        protected void onPreExecute() {\n"
                        + "        }\n"
                        + "\n"
                        + "        @MainThread\n"
                        + "        protected void onProgressUpdate(Object... values) {\n"
                        + "        }\n"
                        + "\n"
                        + "        @WorkerThread\n"
                        + "        protected final void publishProgress(Object... values) {\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"),
                SUPPORT_ANNOTATIONS_CLASS_PATH,
                SUPPORT_ANNOTATIONS_JAR)
                .allowCompilationErrors()
                .run()
                .expectInlinedMessages()
    }

    /**
     * Test that the parent class annotations are not inherited by the static methods declared in a child class. In the example below,
     * android.view.View is annotated with the UiThread annotation. The test checks that workerThreadMethod does not inherit that annotation.
     */
    fun testStaticWrongThread() {

        lint().files(
                LintDetectorTest.java(""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import android.content.Context;\n"
                        + "import android.os.AsyncTask;\n"
                        + "import android.support.annotation.WorkerThread;\n"
                        + "import android.view.View;\n"
                        + "\n"
                        + "public class X extends View {\n"
                        + "    public X(Context context) {\n"
                        + "        super(context);\n"
                        + "    }\n"
                        + "\n"
                        + "    class MyAsyncTask extends AsyncTask<Long, Void, Boolean> {\n"
                        + "        @Override\n"
                        + "        protected Boolean doInBackground(Long... sizes) {\n"
                        + "            return workedThreadMethod();\n"
                        + "        }\n"
                        + "\n"
                        + "        @Override\n"
                        + "        protected void onPostExecute(Boolean isEnoughFree) {\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    public static boolean workedThreadMethod() {\n"
                        + "        return true;\n"
                        + "    }\n"
                        + "}"),
                SUPPORT_ANNOTATIONS_CLASS_PATH,
                SUPPORT_ANNOTATIONS_JAR)
                .run()
                .expectClean()
    }

    fun testAnyThread2() {
        // Tests support for the @AnyThread annotation as well as fixing bugs
        // suppressing AndroidLintWrongThread and
        // 207313: Class-level threading annotations are not overridable
        // 207302: @WorkerThread cannot call View.post

        lint().files(
                LintDetectorTest.java(""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import android.support.annotation.BinderThread;\n"
                        + "import android.support.annotation.MainThread;\n"
                        + "import android.support.annotation.UiThread;\n"
                        + "import android.support.annotation.WorkerThread;\n"
                        + "\n"
                        + "@SuppressWarnings({\"WeakerAccess\", \"unused\"})\n"
                        + "public class X {\n"
                        + "    @UiThread\n"
                        + "    static class AnyThreadTest {\n"
                        + "        //    @AnyThread\n"
                        + "        static void threadSafe() {\n"
                        + "            /*Method worker must be called from the worker thread, currently inferred thread is UI thread*/worker()/**/; // ERROR\n"
                        + "        }\n"
                        + "\n"
                        + "        @WorkerThread\n"
                        + "        static void worker() {\n"
                        + "            /*Method threadSafe must be called from the UI thread, currently inferred thread is worker thread*/threadSafe()/**/; // OK\n"
                        + "        }\n"
                        + "\n"
                        + "        // Multi thread test\n"
                        + "        @UiThread\n"
                        + "        @WorkerThread\n"
                        + "        private static void calleee() {\n"
                        + "        }\n"
                        + "\n"
                        + "        @WorkerThread\n"
                        + "        private static void call1() {\n"
                        + "            calleee(); // OK - context is included in target\n"
                        + "        }\n"
                        + "\n"
                        + "        @BinderThread\n"
                        + "        @WorkerThread\n"
                        + "        private static void call2() {\n"
                        + "            /*Method calleee must be called from the UI or worker thread, currently inferred thread is binder and worker thread*/calleee()/**/; // Not ok: thread could be binder thread, not supported by target\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    public static AsyncTask testTask() {\n"
                        + "\n"
                        + "        return new AsyncTask() {\n"
                        + "            final CustomView view = new CustomView();\n"
                        + "\n"
                        + "            @Override\n"
                        + "            protected void doInBackground(Object... params) {\n"
                        + "                /*Method onPreExecute must be called from the main thread, currently inferred thread is worker thread*/onPreExecute()/**/; // ERROR\n"
                        + "                /*Method paint must be called from the UI thread, currently inferred thread is worker thread*/view.paint()/**/; // ERROR\n"
                        + "                publishProgress(); // OK\n"
                        + "            }\n"
                        + "\n"
                        + "            @Override\n"
                        + "            protected void onPreExecute() {\n"
                        + "                /*Method publishProgress must be called from the worker thread, currently inferred thread is main thread*/publishProgress()/**/; // ERROR\n"
                        + "                onProgressUpdate(); // OK\n"
                        + "                // Suppressed via older Android Studio inspection id:\n"
                        + "                //noinspection ResourceType\n"
                        + "                publishProgress(); // SUPPRESSED\n"
                        + "                // Suppressed via new lint id:\n"
                        + "                //noinspection WrongThread\n"
                        + "                publishProgress(); // SUPPRESSED\n"
                        + "                // Suppressed via Studio inspection id:\n"
                        + "                //noinspection AndroidLintWrongThread\n"
                        + "                publishProgress(); // SUPPRESSED\n"
                        + "            }\n"
                        + "        };\n"
                        + "    }\n"
                        + "\n"
                        + "    @UiThread\n"
                        + "    public static class View {\n"
                        + "        public void paint() {\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    public static class CustomView extends View {\n"
                        + "        @Override public void paint() {\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    public abstract static class AsyncTask {\n"
                        + "        @WorkerThread\n"
                        + "        protected abstract void doInBackground(Object... params);\n"
                        + "\n"
                        + "        @MainThread\n"
                        + "        protected void onPreExecute() {\n"
                        + "        }\n"
                        + "\n"
                        + "        @MainThread\n"
                        + "        protected void onProgressUpdate(Object... values) {\n"
                        + "        }\n"
                        + "\n"
                        + "        @WorkerThread\n"
                        + "        protected final void publishProgress(Object... values) {\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"),
                SUPPORT_ANNOTATIONS_CLASS_PATH,
                SUPPORT_ANNOTATIONS_JAR)
                .run()
                .expectInlinedMessages()
    }

}
