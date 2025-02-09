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
package com.android.ide.common.gradle.model;

import static com.android.ide.common.gradle.model.IdeModelTestUtils.*;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.builder.model.NativeFolder;
import com.android.ide.common.gradle.model.stubs.NativeFolderStub;
import java.io.Serializable;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link IdeNativeFolder}. */
public class IdeNativeFolderTest {
    private ModelCache myModelCache;

    @Before
    public void setUp() throws Exception {
        myModelCache = new ModelCache();
    }

    @Test
    public void serializable() {
        assertThat(IdeNativeFolder.class).isAssignableTo(Serializable.class);
    }

    @Test
    public void serialization() throws Exception {
        IdeNativeFolder nativeFolder = new IdeNativeFolder(new NativeFolderStub(), myModelCache);
        byte[] bytes = Serialization.serialize(nativeFolder);
        Object o = Serialization.deserialize(bytes);
        assertEquals(nativeFolder, o);
    }

    @Test
    public void constructor() throws Throwable {
        NativeFolder original = new NativeFolderStub();
        IdeNativeFolder copy = new IdeNativeFolder(original, myModelCache);
        assertEqualsOrSimilar(original, copy);
        verifyUsageOfImmutableCollections(copy);
    }

    @Test
    public void equalsAndHashCode() {
        createEqualsVerifier(IdeNativeFolder.class).verify();
    }
}
