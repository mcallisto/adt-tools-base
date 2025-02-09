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

package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Detector;

@SuppressWarnings("javadoc")
public class JavaPerformanceDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new JavaPerformanceDetector();
    }

    @Override
    protected boolean allowCompilationErrors() {
        // Some of these unit tests are still relying on source code that references
        // unresolved symbols etc.
        return true;
    }

    @SuppressWarnings("all")
    public void test() throws Exception {
        String expected = ""
                + "src/test/pkg/JavaPerformanceTest.java:31: Warning: Avoid object allocations during draw/layout operations (preallocate and reuse instead) [DrawAllocation]\n"
                + "        new String(\"foo\");\n"
                + "        ~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/JavaPerformanceTest.java:32: Warning: Avoid object allocations during draw/layout operations (preallocate and reuse instead) [DrawAllocation]\n"
                + "        String s = new String(\"bar\");\n"
                + "                   ~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/JavaPerformanceTest.java:106: Warning: Avoid object allocations during draw/layout operations (preallocate and reuse instead) [DrawAllocation]\n"
                + "        new String(\"flag me\");\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/JavaPerformanceTest.java:112: Warning: Avoid object allocations during draw/layout operations (preallocate and reuse instead) [DrawAllocation]\n"
                + "        new String(\"flag me\");\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/JavaPerformanceTest.java:115: Warning: Avoid object allocations during draw/layout operations (preallocate and reuse instead) [DrawAllocation]\n"
                + "        Bitmap.createBitmap(100, 100, null);\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/JavaPerformanceTest.java:116: Warning: Avoid object allocations during draw/layout operations (preallocate and reuse instead) [DrawAllocation]\n"
                + "        android.graphics.Bitmap.createScaledBitmap(null, 100, 100, false);\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/JavaPerformanceTest.java:117: Warning: Avoid object allocations during draw/layout operations (preallocate and reuse instead) [DrawAllocation]\n"
                + "        BitmapFactory.decodeFile(null);\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/JavaPerformanceTest.java:119: Warning: Avoid object allocations during draw operations: Use Canvas.getClipBounds(Rect) instead of Canvas.getClipBounds() which allocates a temporary Rect [DrawAllocation]\n"
                + "        canvas.getClipBounds(); // allocates on your behalf\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/JavaPerformanceTest.java:143: Warning: Avoid object allocations during draw/layout operations (preallocate and reuse instead) [DrawAllocation]\n"
                + "            new String(\"foo\");\n"
                + "            ~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/JavaPerformanceTest.java:73: Warning: Use new SparseArray<String>(...) instead for better performance [UseSparseArrays]\n"
                + "        Map<Integer, String> myMap = new HashMap<Integer, String>();\n"
                + "                                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/JavaPerformanceTest.java:75: Warning: Use new SparseBooleanArray(...) instead for better performance [UseSparseArrays]\n"
                + "        Map<Integer, Boolean> myBoolMap = new HashMap<Integer, Boolean>();\n"
                + "                                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/JavaPerformanceTest.java:77: Warning: Use new SparseIntArray(...) instead for better performance [UseSparseArrays]\n"
                + "        Map<Integer, Integer> myIntMap = new java.util.HashMap<Integer, Integer>();\n"
                + "                                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/JavaPerformanceTest.java:193: Warning: Use new SparseIntArray(...) instead for better performance [UseSparseArrays]\n"
                + "        new SparseArray<Integer>(); // Use SparseIntArray instead\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/JavaPerformanceTest.java:195: Warning: Use new SparseBooleanArray(...) instead for better performance [UseSparseArrays]\n"
                + "        new SparseArray<Boolean>(); // Use SparseBooleanArray instead\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/JavaPerformanceTest.java:204: Warning: Use new SparseArray<String>(...) instead for better performance [UseSparseArrays]\n"
                + "        Map<Byte, String> myByteMap = new HashMap<Byte, String>();\n"
                + "                                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/JavaPerformanceTest.java:36: Warning: Use Integer.valueOf(5) instead [UseValueOf]\n"
                + "        Integer i = new Integer(5);\n"
                + "                    ~~~~~~~~~~~~~~\n"
                + "src/test/pkg/JavaPerformanceTest.java:148: Warning: Use Integer.valueOf(42) instead [UseValueOf]\n"
                + "        Integer i1 = new Integer(42);\n"
                + "                     ~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/JavaPerformanceTest.java:149: Warning: Use Long.valueOf(42L) instead [UseValueOf]\n"
                + "        Long l1 = new Long(42L);\n"
                + "                  ~~~~~~~~~~~~~\n"
                + "src/test/pkg/JavaPerformanceTest.java:150: Warning: Use Boolean.valueOf(true) instead [UseValueOf]\n"
                + "        Boolean b1 = new Boolean(true);\n"
                + "                     ~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/JavaPerformanceTest.java:151: Warning: Use Character.valueOf('c') instead [UseValueOf]\n"
                + "        Character c1 = new Character('c');\n"
                + "                       ~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/JavaPerformanceTest.java:152: Warning: Use Float.valueOf(1.0f) instead [UseValueOf]\n"
                + "        Float f1 = new Float(1.0f);\n"
                + "                   ~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/JavaPerformanceTest.java:153: Warning: Use Double.valueOf(1.0) instead [UseValueOf]\n"
                + "        Double d1 = new Double(1.0);\n"
                + "                    ~~~~~~~~~~~~~~~\n"
                + "0 errors, 22 warnings\n";

        //noinspection all // Sample code
        lint().files(
                java("src/test/pkg/JavaPerformanceTest.java", ""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import android.annotation.SuppressLint;\n"
                        + "import android.content.Context;\n"
                        + "import android.graphics.Bitmap;\n"
                        + "import android.graphics.BitmapFactory;\n"
                        + "import android.graphics.Canvas;\n"
                        + "import android.graphics.LinearGradient;\n"
                        + "import android.graphics.Rect;\n"
                        + "import android.graphics.Shader.TileMode;\n"
                        + "import android.util.AttributeSet;\n"
                        + "import android.util.SparseArray;\n"
                        + "import android.widget.Button;\n"
                        + "import java.util.HashMap;\n"
                        + "import java.util.Map;\n"
                        + "\n"
                        + "/** Some test data for the JavaPerformanceDetector */\n"
                        + "@SuppressWarnings(\"unused\")\n"
                        + "public class JavaPerformanceTest extends Button {\n"
                        + "    public JavaPerformanceTest(Context context, AttributeSet attrs, int defStyle) {\n"
                        + "        super(context, attrs, defStyle);\n"
                        + "    }\n"
                        + "\n"
                        + "    private Rect cachedRect;\n"
                        + "\n"
                        + "    @Override\n"
                        + "    protected void onDraw(android.graphics.Canvas canvas) {\n"
                        + "        super.onDraw(canvas);\n"
                        + "\n"
                        + "        // Various allocations:\n"
                        + "        new String(\"foo\");\n"
                        + "        String s = new String(\"bar\");\n"
                        + "\n"
                        + "        // This one should not be reported:\n"
                        + "        @SuppressLint(\"DrawAllocation\")\n"
                        + "        Integer i = new Integer(5);\n"
                        + "\n"
                        + "        // Cached object initialized lazily: should not complain about these\n"
                        + "        if (cachedRect == null) {\n"
                        + "            cachedRect = new Rect(0, 0, 100, 100);\n"
                        + "        }\n"
                        + "        if (cachedRect == null || cachedRect.width() != 50) {\n"
                        + "            cachedRect = new Rect(0, 0, 50, 100);\n"
                        + "        }\n"
                        + "\n"
                        + "        boolean b = Boolean.valueOf(true); // auto-boxing\n"
                        + "        dummy(1, 2);\n"
                        + "\n"
                        + "        // Non-allocations\n"
                        + "        super.animate();\n"
                        + "        dummy2(1, 2);\n"
                        + "        int x = 4 + '5';\n"
                        + "\n"
                        + "        // This will involve allocations, but we don't track\n"
                        + "        // inter-procedural stuff here\n"
                        + "        someOtherMethod();\n"
                        + "    }\n"
                        + "\n"
                        + "    void dummy(Integer foo, int bar) {\n"
                        + "        dummy2(foo, bar);\n"
                        + "    }\n"
                        + "\n"
                        + "    void dummy2(int foo, int bar) {\n"
                        + "    }\n"
                        + "\n"
                        + "    void someOtherMethod() {\n"
                        + "        // Allocations are okay here\n"
                        + "        new String(\"foo\");\n"
                        + "        String s = new String(\"bar\");\n"
                        + "        boolean b = Boolean.valueOf(true); // auto-boxing\n"
                        + "\n"
                        + "        // Sparse array candidates\n"
                        + "        Map<Integer, String> myMap = new HashMap<Integer, String>();\n"
                        + "        // Should use SparseBooleanArray\n"
                        + "        Map<Integer, Boolean> myBoolMap = new HashMap<Integer, Boolean>();\n"
                        + "        // Should use SparseIntArray\n"
                        + "        Map<Integer, Integer> myIntMap = new java.util.HashMap<Integer, Integer>();\n"
                        + "\n"
                        + "        // This one should not be reported:\n"
                        + "        @SuppressLint(\"UseSparseArrays\")\n"
                        + "        Map<Integer, Object> myOtherMap = new HashMap<Integer, Object>();\n"
                        + "    }\n"
                        + "\n"
                        + "    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec,\n"
                        + "                             boolean x) { // wrong signature\n"
                        + "        new String(\"not an error\");\n"
                        + "    }\n"
                        + "\n"
                        + "    protected void onMeasure(int widthMeasureSpec) { // wrong signature\n"
                        + "        new String(\"not an error\");\n"
                        + "    }\n"
                        + "\n"
                        + "    protected void onLayout(boolean changed, int left, int top, int right,\n"
                        + "                            int bottom, int wrong) { // wrong signature\n"
                        + "        new String(\"not an error\");\n"
                        + "    }\n"
                        + "\n"
                        + "    protected void onLayout(boolean changed, int left, int top, int right) {\n"
                        + "        // wrong signature\n"
                        + "        new String(\"not an error\");\n"
                        + "    }\n"
                        + "\n"
                        + "    @Override\n"
                        + "    protected void onLayout(boolean changed, int left, int top, int right,\n"
                        + "                            int bottom) {\n"
                        + "        new String(\"flag me\");\n"
                        + "    }\n"
                        + "\n"
                        + "    @SuppressWarnings(\"null\") // not real code\n"
                        + "    @Override\n"
                        + "    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {\n"
                        + "        new String(\"flag me\");\n"
                        + "\n"
                        + "        // Forbidden factory methods:\n"
                        + "        Bitmap.createBitmap(100, 100, null);\n"
                        + "        android.graphics.Bitmap.createScaledBitmap(null, 100, 100, false);\n"
                        + "        BitmapFactory.decodeFile(null);\n"
                        + "        Canvas canvas = null;\n"
                        + "        canvas.getClipBounds(); // allocates on your behalf\n"
                        + "        canvas.getClipBounds(null); // NOT an error\n"
                        + "\n"
                        + "        final int layoutWidth = getWidth();\n"
                        + "        final int layoutHeight = getHeight();\n"
                        + "        if (mAllowCrop && (mOverlay == null || mOverlay.getWidth() != layoutWidth ||\n"
                        + "                mOverlay.getHeight() != layoutHeight)) {\n"
                        + "            mOverlay = Bitmap.createBitmap(layoutWidth, layoutHeight, Bitmap.Config.ARGB_8888);\n"
                        + "            mOverlayCanvas = new Canvas(mOverlay);\n"
                        + "        }\n"
                        + "\n"
                        + "        if (widthMeasureSpec == 42) {\n"
                        + "            throw new IllegalStateException(\"Test\"); // NOT an allocation\n"
                        + "        }\n"
                        + "\n"
                        + "        // More lazy init tests\n"
                        + "        boolean initialized = false;\n"
                        + "        if (!initialized) {\n"
                        + "            new String(\"foo\");\n"
                        + "            initialized = true;\n"
                        + "        }\n"
                        + "\n"
                        + "        // NOT lazy initialization\n"
                        + "        if (!initialized || mOverlay == null) {\n"
                        + "            new String(\"foo\");\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    void factories() {\n"
                        + "        Integer i1 = new Integer(42);\n"
                        + "        Long l1 = new Long(42L);\n"
                        + "        Boolean b1 = new Boolean(true);\n"
                        + "        Character c1 = new Character('c');\n"
                        + "        Float f1 = new Float(1.0f);\n"
                        + "        Double d1 = new Double(1.0);\n"
                        + "\n"
                        + "        // The following should not generate errors:\n"
                        + "        Object i2 = new foo.bar.Integer(42);\n"
                        + "        Integer i3 = Integer.valueOf(42);\n"
                        + "    }\n"
                        + "\n"
                        + "    private boolean mAllowCrop;\n"
                        + "    private Canvas mOverlayCanvas;\n"
                        + "    private Bitmap mOverlay;\n"
                        + "private abstract class JavaPerformanceTest1 extends JavaPerformanceTest {\n"
                        + "    @Override\n"
                        + "    public void layout(int l, int t, int r, int b) {\n"
                        + "        // Using \"this.\" to reference fields\n"
                        + "        if (this.shader == null)\n"
                        + "            this.shader = new LinearGradient(0, 0, getWidth(), 0, GRADIENT_COLORS, null,\n"
                        + "                    TileMode.REPEAT);\n"
                        + "    }\n"
                        + "} private abstract class JavaPerformanceTest2 extends JavaPerformanceTest {\n"
                        + "        @Override\n"
                        + "    public void layout(int l, int t, int r, int b) {\n"
                        + "        int width = getWidth();\n"
                        + "        int height = getHeight();\n"
                        + "\n"
                        + "        if ((shader == null) || (lastWidth != width) || (lastHeight != height))\n"
                        + "        {\n"
                        + "            lastWidth = width;\n"
                        + "            lastHeight = height;\n"
                        + "\n"
                        + "            shader = new LinearGradient(0, 0, width, 0, GRADIENT_COLORS, null, TileMode.REPEAT);\n"
                        + "        }\n"
                        + "    }\n"
                        + "} private abstract class JavaPerformanceTest3 extends JavaPerformanceTest {\n"
                        + "    @Override\n"
                        + "    public void layout(int l, int t, int r, int b) {\n"
                        + "        if ((shader == null) || (lastWidth != getWidth()) || (lastHeight != getHeight())) {\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"
                        + "    public void inefficientSparseArray() {\n"
                        + "        new SparseArray<Integer>(); // Use SparseIntArray instead\n"
                        + "        new SparseArray<Long>();    // Use SparseLongArray instead\n"
                        + "        new SparseArray<Boolean>(); // Use SparseBooleanArray instead\n"
                        + "        new SparseArray<Object>();  // OK\n"
                        + "    }\n"
                        + "\n"
                        + "    public void longSparseArray() { // but only minSdkVersion >= 17 or if has v4 support lib\n"
                        + "        Map<Long, String> myStringMap = new HashMap<Long, String>();\n"
                        + "    }\n"
                        + "\n"
                        + "    public void byteSparseArray() { // bytes easily apply to ints\n"
                        + "        Map<Byte, String> myByteMap = new HashMap<Byte, String>();\n"
                        + "    }\n"
                        + "\n"
                        + "    protected LinearGradient shader;\n"
                        + "    protected int lastWidth;\n"
                        + "    protected int lastHeight;\n"
                        + "    protected int[] GRADIENT_COLORS;\n"
                        + "\n"
                        + "    private static class foo {\n"
                        + "        private static class bar {\n"
                        + "            private static class Integer {\n"
                        + "                public Integer(int val) {\n"
                        + "                }\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "    public JavaPerformanceTest() {\n"
                        + "        super(null);\n"
                        + "    }\n"
                        + "}\n"))
                .run()
                .expect(expected)
                .expectFixDiffs(""
                        + "Fix for src/test/pkg/JavaPerformanceTest.java line 35: Replace with valueOf():\n"
                        + "@@ -36 +36\n"
                        + "-         Integer i = new Integer(5);\n"
                        + "+         Integer i = Integer.valueOf(5);\n"
                        + "Fix for src/test/pkg/JavaPerformanceTest.java line 147: Replace with valueOf():\n"
                        + "@@ -148 +148\n"
                        + "-         Integer i1 = new Integer(42);\n"
                        + "+         Integer i1 = Integer.valueOf(42);\n"
                        + "Fix for src/test/pkg/JavaPerformanceTest.java line 148: Replace with valueOf():\n"
                        + "@@ -149 +149\n"
                        + "-         Long l1 = new Long(42L);\n"
                        + "+         Long l1 = Long.valueOf(42L);\n"
                        + "Fix for src/test/pkg/JavaPerformanceTest.java line 149: Replace with valueOf():\n"
                        + "@@ -150 +150\n"
                        + "-         Boolean b1 = new Boolean(true);\n"
                        + "+         Boolean b1 = Boolean.valueOf(true);\n"
                        + "Fix for src/test/pkg/JavaPerformanceTest.java line 150: Replace with valueOf():\n"
                        + "@@ -151 +151\n"
                        + "-         Character c1 = new Character('c');\n"
                        + "+         Character c1 = Character.valueOf('c');\n"
                        + "Fix for src/test/pkg/JavaPerformanceTest.java line 151: Replace with valueOf():\n"
                        + "@@ -152 +152\n"
                        + "-         Float f1 = new Float(1.0f);\n"
                        + "+         Float f1 = Float.valueOf(1.0f);\n"
                        + "Fix for src/test/pkg/JavaPerformanceTest.java line 152: Replace with valueOf():\n"
                        + "@@ -153 +153\n"
                        + "-         Double d1 = new Double(1.0);\n"
                        + "+         Double d1 = Double.valueOf(1.0);\n");
    }

    public void testLongSparseArray() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "src/test/pkg/LongSparseArray.java:10: Warning: Use new LongSparseArray(...) instead for better performance [UseSparseArrays]\n"
                + "        Map<Long, String> myStringMap = new HashMap<Long, String>();\n"
                + "                                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n",

                lintProject(
                    manifest().minSdk(17),
                    mLongSparseArray));
    }

    public void testLongSparseSupportLibArray() throws Exception {
        //noinspection all // Sample code
        assertEquals(""
                + "src/test/pkg/LongSparseArray.java:10: Warning: Use new android.support.v4.util.LongSparseArray(...) instead for better performance [UseSparseArrays]\n"
                + "        Map<Long, String> myStringMap = new HashMap<Long, String>();\n"
                + "                                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n",

                lintProject(
                        mLongSparseArray,
                        jar("libs/android-support-v4.jar") // just a placeholder
                ));
    }

    public void testNoLongSparseArray() throws Exception {
        //noinspection all // Sample code
        assertEquals(
                "No warnings.",

                lintProject(
                    manifest().minSdk(1),
                    mLongSparseArray));
    }

    public void testSparseLongArray1() {
        String expected = ""
                + "src/test/pkg/SparseLongArray.java:10: Warning: Use new SparseLongArray(...) instead for better performance [UseSparseArrays]\n"
                + "        Map<Integer, Long> myStringMap = new HashMap<Integer, Long>();\n"
                + "                                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n";
        lint().files(
                manifest().minSdk(19),
                mSparseLongArray)
                .run()
                .expect(expected);
    }

    public void testSparseLongArray2() {
        // Note -- it's offering a SparseArray, not a SparseLongArray!
        String expected = ""
                + "src/test/pkg/SparseLongArray.java:10: Warning: Use new SparseArray<Long>(...) instead for better performance [UseSparseArrays]\n"
                + "        Map<Integer, Long> myStringMap = new HashMap<Integer, Long>();\n"
                + "                                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n";
        lint().files(
                manifest().minSdk(1),
                mSparseLongArray)
                .run()
                .expect(expected);
    }

    public void testNoSparseArrayOutsideAndroid() {
        //noinspection all // Sample code
        lint().files(
                manifest().minSdk(17),
                mLongSparseArray,
                gradle("apply plugin: 'java'\n"))
                .run()
                .expectClean();
    }

    public void testUseValueOfOnArrays() {
        //noinspection all // Sample code
        lint().files(
                manifest().minSdk(1),
                java(""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import junit.framework.Assert;\n"
                        + "\n"
                        + "import java.util.Arrays;\n"
                        + "import java.util.Calendar;\n"
                        + "import java.util.List;\n"
                        + "\n"
                        + "public class TestValueOf {\n"
                        + "    public Integer[] getAffectedDays(List<Integer> mAffectedDays) {\n"
                        + "        return mAffectedDays.toArray(new Integer[mAffectedDays.size()]);\n"
                        + "    }\n"
                        + "\n"
                        + "    public void test2(Integer[] x) {\n"
                        + "        Assert.assertTrue(Arrays.equals(x, new Integer[]{Calendar.MONDAY}));\n"
                        + "    }\n"
                        + "}\n"))
                .run()
                .expectClean();
    }

    public void testAllocationForArrays() {
        //noinspection all // Sample code
        lint().files(
                java(""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import android.content.Context;\n"
                        + "import android.content.res.TypedArray;\n"
                        + "import android.graphics.Canvas;\n"
                        + "import android.util.AttributeSet;\n"
                        + "import android.widget.Button;\n"
                        + "\n"
                        + "public class MyButton extends Button {\n"
                        + "\n"
                        + "    public MyButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {\n"
                        + "        super(context, attrs, defStyleAttr, defStyleRes);\n"
                        + "    }\n"
                        + "\n"
                        + "    @Override\n"
                        + "    protected void onDraw(Canvas canvas) {\n"
                        + "        super.onDraw(canvas);\n"
                        + "\n"
                        + "        char[] text;\n"
                        + "\n"
                        + "        if (isInEditMode()) {\n"
                        + "            text = new char[0];\n"
                        + "        } else {\n"
                        + "            text = getText().toString().toCharArray();\n"
                        + "        }\n"
                        + "\n"
                        + "        TypedArray array = getContext().obtainStyledAttributes(new int[] { android.R.attr.listPreferredItemHeight });\n"
                        + "        array.recycle();\n"
                        + "    }\n"
                        + "}\n"))
                .run()
                .expect(""
                        + "src/test/pkg/MyButton.java:22: Warning: Avoid object allocations during draw/layout operations (preallocate and reuse instead) [DrawAllocation]\n"
                        + "            text = new char[0];\n"
                        + "                   ~~~~~~~~~~~\n"
                        + "src/test/pkg/MyButton.java:27: Warning: Avoid object allocations during draw/layout operations (preallocate and reuse instead) [DrawAllocation]\n"
                        + "        TypedArray array = getContext().obtainStyledAttributes(new int[] { android.R.attr.listPreferredItemHeight });\n"
                        + "                                                               ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 2 warnings\n");
    }

    public void testWildcards() {
        String expected = ""
                + "src/test/pkg/SparseLongArray.java:10: Warning: Use new SparseArray<Long>(...) instead for better performance [UseSparseArrays]\n"
                + "        Map<Integer, Long> myStringMap = new HashMap<>();\n"
                + "                                         ~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n";
        //noinspection all // Sample code
        lint().files(
                manifest().minSdk(1),
                java(""
                        + "package test.pkg;\n"
                        + "\n"
                        + "import java.util.HashMap;\n"
                        + "import java.util.Map;\n"
                        + "\n"
                        + "import android.content.Context;\n"
                        + "\n"
                        + "public class SparseLongArray {\n"
                        + "    public void test() { // but only minSdkVersion >= 18\n"
                        + "        Map<Integer, Long> myStringMap = new HashMap<>();\n"
                        + "    }\n"
                        + "}\n"))
                .run()
                .expect(expected);
    }

    @SuppressWarnings("all") // Sample code
    private TestFile mLongSparseArray = java(""
            + "package test.pkg;\n"
            + "\n"
            + "import java.util.HashMap;\n"
            + "import java.util.Map;\n"
            + "\n"
            + "import android.content.Context;\n"
            + "\n"
            + "public class LongSparseArray {\n"
            + "    public void test() { // but only minSdkVersion >= 17\n"
            + "        Map<Long, String> myStringMap = new HashMap<Long, String>();\n"
            + "    }\n"
            + "}\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mSparseLongArray = java(""
            + "package test.pkg;\n"
            + "\n"
            + "import java.util.HashMap;\n"
            + "import java.util.Map;\n"
            + "\n"
            + "import android.content.Context;\n"
            + "\n"
            + "public class SparseLongArray {\n"
            + "    public void test() { // but only minSdkVersion >= 18\n"
            + "        Map<Integer, Long> myStringMap = new HashMap<Integer, Long>();\n"
            + "    }\n"
            + "}\n");
}
