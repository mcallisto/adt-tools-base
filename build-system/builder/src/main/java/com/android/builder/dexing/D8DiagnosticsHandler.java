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

package com.android.builder.dexing;

import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.MessageReceiver;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.SourcePosition;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.origin.ArchiveEntryOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.origin.TextRangeOrigin;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

class D8DiagnosticsHandler implements DiagnosticsHandler {
    private final MessageReceiver messageReceiver;
    private final Set<String> pendingHints = new HashSet<>();

    public D8DiagnosticsHandler(MessageReceiver messageReceiver) {
        this.messageReceiver = messageReceiver;
    }

    public static Origin getOrigin(ClassFileEntry entry) {
        Path root = entry.getInput().getPath();
        if (Files.isRegularFile(root)) {
            return new ArchiveEntryOrigin(entry.getRelativePath(), new PathOrigin(root));
        } else {
            return new PathOrigin(root.resolve(entry.getRelativePath()));
        }
    }

    public static Origin getOrigin(DexArchiveEntry entry) {
        Path root = entry.getDexArchive().getRootPath();
        if (Files.isRegularFile(root)) {
            return new ArchiveEntryOrigin(entry.getRelativePathInArchive(), new PathOrigin(root));
        } else {
            return new PathOrigin(root.resolve(entry.getRelativePathInArchive()));
        }
    }

    @Override
    public void error(Diagnostic warning) {
        messageReceiver.receiveMessage(convertToMessage(Message.Kind.ERROR, warning));
    }

    @Override
    public void warning(Diagnostic warning) {
        messageReceiver.receiveMessage(convertToMessage(Message.Kind.WARNING, warning));
    }

    @Override
    public void info(Diagnostic info) {
        messageReceiver.receiveMessage(convertToMessage(Message.Kind.INFO, info));
    }

    public Set<String> getPendingHints() {
        return pendingHints;
    }

    protected void addHint(String hint) {
        synchronized (pendingHints) {
            pendingHints.add(hint);
        }
    }

    protected Message convertToMessage(Message.Kind kind, Diagnostic diagnostic) {
        String textMessage = diagnostic.getDiagnosticMessage();

        Origin origin = diagnostic.getOrigin();
        SourceFilePosition position;
        if (origin instanceof TextRangeOrigin && origin.parent() instanceof PathOrigin) {
            TextRangeOrigin textRange = (TextRangeOrigin) origin;
            position =
                    new SourceFilePosition(
                            ((PathOrigin) origin.parent()).getPath().toFile(),
                            new SourcePosition(
                                    textRange.getStart().getLine(),
                                    textRange.getStart().getColumn(),
                                    -1,
                                    textRange.getEnd().getLine(),
                                    textRange.getEnd().getColumn(),
                                    -1));
        } else if (origin.parent() instanceof PathOrigin) {
            position =
                    new SourceFilePosition(
                            ((PathOrigin) origin.parent()).getPath().toFile(),
                            SourcePosition.UNKNOWN);
        } else {
            position = SourceFilePosition.UNKNOWN;
            if (origin != Origin.unknown()) {
                textMessage = origin.toString() + ": " + textMessage;
            }
        }

        return new Message(kind, textMessage, textMessage, "D8", position);
    }
}
