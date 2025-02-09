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
package com.android.ide.common.res2;

import com.android.annotations.NonNull;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

final class StringResourceUnescaper {

    private StringResourceUnescaper() {}

    @NonNull
    static String unescapeCharacterData(@NonNull String xml) {
        if (xml.isEmpty()) {
            return "";
        }

        xml = StringResourceEscapeUtils.escapeCharacterReferences(xml);
        xml = unescapeLeadingQuestionMarkOrAtSign(xml);

        StringBuilder builder = new StringBuilder(xml.length());

        try {
            StringResourceEscapeUtils.parse(xml, newContentHandler(builder));
        } catch (SAXException exception) {
            throw new IllegalArgumentException(xml, exception);
        }

        xml = builder.toString();
        xml = StringResourceEscapeUtils.unescapeCharacterReferences(xml);

        return xml;
    }

    @NonNull
    private static String unescapeLeadingQuestionMarkOrAtSign(@NonNull String xml) {
        if (xml.startsWith("\\?") || xml.startsWith("\\@")) {
            return xml.substring(1, xml.length());
        } else {
            return xml;
        }
    }

    @NonNull
    private static ContentHandler newContentHandler(@NonNull StringBuilder builder) {
        CharacterHandler handler = new StringResourceUnescaperCharacterHandler();
        return new StringResourceContentHandler(builder, handler);
    }
}
