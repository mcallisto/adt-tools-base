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

package com.android.build.gradle.internal.cxx.json;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.File;
import java.io.IOException;

/**
 * GSon TypeAdapter that will convert between File and String.
 */
public class PlainFileGsonTypeAdaptor extends TypeAdapter<File> {
    @Override
    public void write(JsonWriter jsonWriter, File file) throws IOException {
        if (file == null || file.getPath() == null) {
            jsonWriter.nullValue();
            return;
        }
        jsonWriter.value(file.getPath());
    }

    @Override
    public File read(JsonReader jsonReader) throws IOException {
        String path = jsonReader.nextString();
        return new File(path);
    }
}
