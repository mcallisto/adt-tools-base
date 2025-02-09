/*
 * Copyright (C) 2008 Google Inc.
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

package com.android.tools.perflib.heap;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.tools.perflib.analyzer.Capture;
import com.android.tools.perflib.captures.DataBuffer;
import com.android.tools.perflib.heap.analysis.*;
import com.android.tools.perflib.heap.ext.NativeRegistryPostProcessor;
import com.android.tools.perflib.heap.ext.SnapshotPostProcessor;
import com.android.tools.proguard.ProguardMap;
import gnu.trove.THashSet;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TLongObjectHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/*
 * A snapshot of all of the heaps, and related meta-data, for the runtime at a given instant.
 *
 * There are three possible heaps: default, app and zygote. GC roots are always reported in the
 * default heap, and they are simply references to objects living in the zygote or the app heap.
 * During parsing of the HPROF file HEAP_DUMP_INFO chunks change which heap is being referenced.
 */
public class Snapshot extends Capture {


    public enum DominatorComputationStage {
        INITIALIZING(new ComputationProgress("Preparing for dominator calculation...", 0), 0.1, 0.0),
        RESOLVING_REFERENCES(new ComputationProgress("Resolving references...", 0), 0.1, 0.2),
        COMPUTING_SHORTEST_DISTANCE(new ComputationProgress("Computing depth to nodes...", 0), 0.3,
                0.03),
        COMPUTING_TOPOLOGICAL_SORT(new ComputationProgress("Performing topological sorting...", 0),
                0.33, 0.30),
        COMPUTING_DOMINATORS(new ComputationProgress("Calculating dominators...", 0), 0.63, 0.35),
        COMPUTING_RETAINED_SIZES(new ComputationProgress("Calculating retained sizes...", 0), 0.98,
                0.02);

        private final ComputationProgress mInitialProgress;

        private final double mOffset;

        private final double mScale;

        DominatorComputationStage(@NonNull ComputationProgress initialProgress, double offset,
                double scale) {
            mInitialProgress = initialProgress;
            mOffset = offset;
            mScale = scale;
        }

        public ComputationProgress getInitialProgress() {
            return mInitialProgress;
        }

        public static double toAbsoluteProgressPercentage(@NonNull DominatorComputationStage baseStage,
                                                          @NonNull ComputationProgress computationProgress) {
            return computationProgress.getProgress() * baseStage.mScale + baseStage.mOffset;
        }
    }

    public static final String TYPE_NAME = "hprof";

    private static final String JAVA_LANG_CLASS = "java.lang.Class";

    //  Special root object used in dominator computation for objects reachable via multiple roots.
    public static final Instance SENTINEL_ROOT = new RootObj(RootType.UNKNOWN);

    private static final int DEFAULT_HEAP_ID = 0;

    @NonNull
    private final DataBuffer mBuffer;

    @NonNull
    ArrayList<Heap> mHeaps = new ArrayList<Heap>();

    @NonNull
    Heap mCurrentHeap;

    //  Root objects such as interned strings, jni locals, etc
    @NonNull
    ArrayList<RootObj> mRoots = new ArrayList<RootObj>();

    //  List stack traces, which are lists of stack frames
    @NonNull
    TIntObjectHashMap<StackTrace> mTraces = new TIntObjectHashMap<StackTrace>();

    //  List of individual stack frames
    @NonNull
    TLongObjectHashMap<StackFrame> mFrames = new TLongObjectHashMap<StackFrame>();

    private List<Instance> mTopSort;

    private DominatorsBase mDominators;

    private volatile DominatorComputationStage mDominatorComputationStage
            = DominatorComputationStage.INITIALIZING;

    //  The set of all classes that are (sub)class(es) of java.lang.ref.Reference.
    private THashSet<ClassObj> mReferenceClasses = new THashSet<ClassObj>();

    private int[] mTypeSizes;

    private long mIdSizeMask = 0x00000000ffffffffL;

    @NonNull
    public static Snapshot createSnapshot(@NonNull DataBuffer buffer) {
        return createSnapshot(buffer, new ProguardMap());
    }

    @NonNull
    public static Snapshot createSnapshot(@NonNull DataBuffer buffer, @NonNull ProguardMap map) {
        return createSnapshot(buffer, map, Arrays.asList(new NativeRegistryPostProcessor()));
    }

    @NonNull
    public static Snapshot createSnapshot(
            @NonNull DataBuffer buffer,
            @NonNull ProguardMap map,
            @NonNull List<SnapshotPostProcessor> postProcessors) {
        try {
            Snapshot snapshot = new Snapshot(buffer);
            HprofParser.parseBuffer(snapshot, buffer, map);
            for (SnapshotPostProcessor processor : postProcessors) {
                processor.postProcess(snapshot);
            }

            return snapshot;
        } catch (RuntimeException e) {
            buffer.dispose();
            throw e;
        }
    }

    @VisibleForTesting
    public Snapshot(@NonNull DataBuffer buffer) {
        mBuffer = buffer;
        setToDefaultHeap();
    }

    public void dispose() {
        mBuffer.dispose();
    }

    @NonNull
    DataBuffer getBuffer() {
        return mBuffer;
    }

    @NonNull
    public Heap setToDefaultHeap() {
        return setHeapTo(DEFAULT_HEAP_ID, "default");
    }

    @NonNull
    public Heap setHeapTo(int id, @NonNull String name) {
        Heap heap = getHeap(id);

        if (heap == null) {
            heap = new Heap(id, name);
            heap.mSnapshot = this;
            mHeaps.add(heap);
        }

        mCurrentHeap = heap;

        return mCurrentHeap;
    }

    public int getHeapIndex(@NonNull Heap heap) {
        return mHeaps.indexOf(heap);
    }

    @Nullable
    public Heap getHeap(int id) {
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < mHeaps.size(); i++) {
            if (mHeaps.get(i).getId() == id) {
                return mHeaps.get(i);
            }
        }
        return null;
    }

    @Nullable
    public Heap getHeap(@NonNull String name) {
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < mHeaps.size(); i++) {
            if (name.equals(mHeaps.get(i).getName())) {
                return mHeaps.get(i);
            }
        }
        return null;
    }

    @NonNull
    public Collection<Heap> getHeaps() {
        return mHeaps;
    }

    @NonNull
    public Collection<RootObj> getGCRoots() {
        return mRoots;
    }

    public final void addStackFrame(@NonNull StackFrame theFrame) {
        mFrames.put(theFrame.mId, theFrame);
    }

    public final StackFrame getStackFrame(long id) {
        return mFrames.get(id);
    }

    public final void addStackTrace(@NonNull StackTrace theTrace) {
        mTraces.put(theTrace.mSerialNumber, theTrace);
    }

    public final StackTrace getStackTrace(int traceSerialNumber) {
        return mTraces.get(traceSerialNumber);
    }

    public final StackTrace getStackTraceAtDepth(int traceSerialNumber, int depth) {
        StackTrace trace = mTraces.get(traceSerialNumber);

        if (trace != null) {
            trace = trace.fromDepth(depth);
        }

        return trace;
    }

    public final void addRoot(@NonNull RootObj root) {
        mRoots.add(root);
        root.setHeap(mCurrentHeap);
    }

    public final void addThread(ThreadObj thread, int serialNumber) {
        mCurrentHeap.addThread(thread, serialNumber);
    }

    public final ThreadObj getThread(int serialNumber) {
        return mCurrentHeap.getThread(serialNumber);
    }

    public final void setIdSize(int size) {
        int maxId = -1;
        for (int i = 0; i < Type.values().length; ++i) {
            maxId = Math.max(Type.values()[i].getTypeId(), maxId);
        }
        assert (maxId > 0) && (maxId <= Type.LONG
                .getTypeId()); // Update this if hprof format ever changes its supported types.
        mTypeSizes = new int[maxId + 1];
        Arrays.fill(mTypeSizes, -1);

        for (int i = 0; i < Type.values().length; ++i) {
            mTypeSizes[Type.values()[i].getTypeId()] = Type.values()[i].getSize();
        }
        mTypeSizes[Type.OBJECT.getTypeId()] = size;
        mIdSizeMask = 0xffffffffffffffffL >>> ((8 - size) * 8);
    }

    public final int getTypeSize(Type type) {
        return mTypeSizes[type.getTypeId()];
    }

    public final long getIdSizeMask() {
        return mIdSizeMask;
    }

    public final void addInstance(long id, @NonNull Instance instance) {
        mCurrentHeap.addInstance(id, instance);
        instance.setHeap(mCurrentHeap);
    }

    public final void addClass(long id, @NonNull ClassObj theClass) {
        mCurrentHeap.addClass(id, theClass);
        theClass.setHeap(mCurrentHeap);
    }

    @Nullable
    public final Instance findInstance(long id) {
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < mHeaps.size(); i++) {
            Instance instance = mHeaps.get(i).getInstance(id);

            if (instance != null) {
                return instance;
            }
        }

        //  Couldn't find an instance of a class, look for a class object
        return findClass(id);
    }

    @Nullable
    public final ClassObj findClass(long id) {
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < mHeaps.size(); i++) {
            ClassObj theClass = mHeaps.get(i).getClass(id);

            if (theClass != null) {
                return theClass;
            }
        }

        return null;
    }

    /**
     * Finds the first ClassObj with a class name that matches <code>name</code>.
     *
     * @param name of the class to find
     * @return the found <code>ClassObj</code>, or null if not found
     */
    @Nullable
    public final ClassObj findClass(String name) {
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < mHeaps.size(); i++) {
            ClassObj theClass = mHeaps.get(i).getClass(name);

            if (theClass != null) {
                return theClass;
            }
        }

        return null;
    }

    /**
     * Finds all <code>ClassObj</code>s with class name that match the given <code>name</code>.
     *
     * @param name of the class to find
     * @return a collection of the found <code>ClassObj</code>s, or empty collection if not found
     */
    @NonNull
    public final Collection<ClassObj> findClasses(String name) {
        ArrayList<ClassObj> classObjs = new ArrayList<ClassObj>();

        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < mHeaps.size(); i++) {
            classObjs.addAll(mHeaps.get(i).getClasses(name));
        }

        return classObjs;
    }

    public void resolveClasses() {
        ClassObj clazz = findClass(JAVA_LANG_CLASS);
        int javaLangClassSize = clazz != null ? clazz.getInstanceSize() : 0;

        for (Heap heap : mHeaps) {
            for (ClassObj classObj : heap.getClasses()) {
                ClassObj superClass = classObj.getSuperClassObj();
                if (superClass != null) {
                    superClass.addSubclass(classObj);
                }
                // We under-approximate the size of the class by including the size of Class.class
                // and the size of static fields, and omitting padding, vtable and imtable sizes.
                int classSize = javaLangClassSize;

                for (Field f : classObj.mStaticFields) {
                    classSize += getTypeSize(f.getType());
                }
                classObj.setSize(classSize);
            }

            final int heapId = heap.getId();
            heap.forEachInstance(instance -> {
                ClassObj classObj = instance.getClassObj();
                if (classObj != null) {
                    classObj.addInstance(heapId, instance);
                }
                return true;
            });
        }
    }

    public void identifySoftReferences() {
        List<ClassObj> referenceDescendants = findAllDescendantClasses(
                ClassObj.getReferenceClassName());
        for (ClassObj classObj : referenceDescendants) {
            classObj.setIsSoftReference();
            mReferenceClasses.add(classObj);
        }
    }

    public void resolveReferences() {
        for (Heap heap : getHeaps()) {
            for (ClassObj clazz : heap.getClasses()) {
                clazz.resolveReferences();
            }
            heap.forEachInstance(instance -> {
                instance.resolveReferences();
                return true;
            });
        }
    }

    public void compactMemory() {
        for (Heap heap : getHeaps()) {
            heap.forEachInstance(instance -> {
                instance.compactMemory();
                return true;
            });
        }
    }

    @NonNull
    public List<ClassObj> findAllDescendantClasses(@NonNull String className) {
        Collection<ClassObj> ancestorClasses = findClasses(className);
        List<ClassObj> descendants = new ArrayList<ClassObj>();
        for (ClassObj ancestor : ancestorClasses) {
            descendants.addAll(ancestor.getDescendantClasses());
        }
        return descendants;
    }

    public void computeDominators() {
        prepareDominatorComputation();
        doComputeDominators(new LinkEvalDominators(this));
    }

    @VisibleForTesting
    public void prepareDominatorComputation() {
        if (mDominators != null) {
            return;
        }

        mDominatorComputationStage = DominatorComputationStage.RESOLVING_REFERENCES;
        resolveReferences();
        compactMemory();

        mDominatorComputationStage = DominatorComputationStage.COMPUTING_SHORTEST_DISTANCE;
        ShortestDistanceVisitor shortestDistanceVisitor = new ShortestDistanceVisitor();
        shortestDistanceVisitor.doVisit(getGCRoots());

        mDominatorComputationStage = DominatorComputationStage.COMPUTING_TOPOLOGICAL_SORT;
        mTopSort = TopologicalSort.compute(getGCRoots());
        for (Instance instance : mTopSort) {
            instance.dedupeReferences();
        }
    }

    @VisibleForTesting
    public void doComputeDominators(@NonNull DominatorsBase computable) {
        if (mDominators != null) {
            return;
        }

        mDominators = computable;
        mDominatorComputationStage = DominatorComputationStage.COMPUTING_DOMINATORS;
        mDominators.computeDominators();

        mDominatorComputationStage = DominatorComputationStage.COMPUTING_RETAINED_SIZES;
        mDominators.computeRetainedSizes();
    }

    @NonNull
    public ComputationProgress getComputationProgress() {
        if (mDominatorComputationStage == DominatorComputationStage.COMPUTING_DOMINATORS) {
            return mDominators.getComputationProgress();
        } else {
            return mDominatorComputationStage.getInitialProgress();
        }
    }

    public DominatorComputationStage getDominatorComputationStage() {
        return mDominatorComputationStage;
    }

    @NonNull
    public List<Instance> getReachableInstances() {
        List<Instance> result = new ArrayList<Instance>(mTopSort.size());
        for (Instance node : mTopSort) {
            if (node.getImmediateDominator() != null) {
                result.add(node);
            }
        }
        return result;
    }

    public List<Instance> getTopologicalOrdering() {
        return mTopSort;
    }

    public final void dumpInstanceCounts() {
        for (Heap heap : mHeaps) {
            System.out.println("+------------------ instance counts for heap: " + heap.getName());
            heap.dumpInstanceCounts();
        }
    }

    public final void dumpSizes() {
        for (Heap heap : mHeaps) {
            System.out.println("+------------------ sizes for heap: " + heap.getName());
            heap.dumpSizes();
        }
    }

    public final void dumpSubclasses() {
        for (Heap heap : mHeaps) {
            System.out.println("+------------------ subclasses for heap: " + heap.getName());
            heap.dumpSubclasses();
        }
    }

    @Nullable
    @Override
    public <T> T getRepresentation(Class<T> asClass) {
        if (asClass.isAssignableFrom(getClass())) {
            return asClass.cast(this);
        }
        return null;
    }

    @NonNull
    @Override
    public String getTypeName() {
        return TYPE_NAME;
    }
}
