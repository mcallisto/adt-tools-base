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

import com.android.annotations.NonNull;
import com.android.builder.model.SourceProvider;
import com.android.ide.common.gradle.model.stubs.SourceProviderStub;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link IdeSourceProvider}. */
public class IdeSourceProviderTest {
    private ModelCache myModelCache;

    @Before
    public void setUp() throws Exception {
        myModelCache = new ModelCache();
    }

    @Test
    public void serializable() {
        assertThat(IdeSourceProvider.class).isAssignableTo(Serializable.class);
    }

    @Test
    public void serialization() throws Exception {
        IdeSourceProvider sourceProvider =
                new IdeSourceProvider(new SourceProviderStub(), myModelCache);
        byte[] bytes = Serialization.serialize(sourceProvider);
        Object o = Serialization.deserialize(bytes);
        assertEquals(sourceProvider, o);
    }

    @Test
    public void model1_dot_5() {
        SourceProvider original =
                new SourceProviderStub() {
                    @Override
                    @NonNull
                    public Collection<File> getShadersDirectories() {
                        throw new UnsupportedOperationException("getShadersDirectories()");
                    }

                    @Override
                    public int hashCode() {
                        return Objects.hash(
                                getName(),
                                getManifestFile(),
                                getJavaDirectories(),
                                getResourcesDirectories(),
                                getAidlDirectories(),
                                getRenderscriptDirectories(),
                                getCDirectories(),
                                getCppDirectories(),
                                getResDirectories(),
                                getAssetsDirectories(),
                                getJniLibsDirectories());
                    }
                };
        IdeSourceProvider sourceProvider = new IdeSourceProvider(original, myModelCache);
        assertThat(sourceProvider.getShadersDirectories()).isEmpty();
    }

    @Test
    public void constructor() throws Throwable {
        SourceProvider original = new SourceProviderStub();
        IdeSourceProvider copy = new IdeSourceProvider(original, myModelCache);
        assertEqualsOrSimilar(original, copy);
        verifyUsageOfImmutableCollections(copy);
    }

    @Test
    public void equalsAndHashCode() {
        createEqualsVerifier(IdeSourceProvider.class).verify();
    }
}
