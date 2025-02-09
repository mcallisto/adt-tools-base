/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.aapt;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.internal.aapt.Aapt;
import com.android.builder.internal.aapt.v1.AaptV1;
import com.android.builder.internal.aapt.v2.QueueableAapt2;
import com.android.builder.sdk.TargetInfo;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.sdklib.BuildToolInfo;
import com.android.utils.ILogger;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Factory that creates instances of {@link com.android.builder.internal.aapt.Aapt} by looking
 * at project configuration.
 */
public final class AaptGradleFactory {

    private AaptGradleFactory() {}

    /**
     * Creates a new {@link Aapt} instance based on project configuration.
     *
     * @param aaptGeneration which aapt to use
     * @param builder the android builder project model
     * @param outputHandler the output handler to use
     * @param crunchPng should PNGs be crunched?
     * @param intermediateDir intermediate directory for aapt to use
     * @param cruncherProcesses the number of cruncher processes to use, if cruncher processes are
     *     used
     * @return the newly-created instance
     */
    @NonNull
    public static Aapt make(
            @NonNull AaptGeneration aaptGeneration,
            @NonNull AndroidBuilder builder,
            @Nullable ProcessOutputHandler outputHandler,
            boolean crunchPng,
            @NonNull File intermediateDir,
            int cruncherProcesses) {
        TargetInfo target = builder.getTargetInfo();
        Preconditions.checkNotNull(target, "target == null");
        BuildToolInfo buildTools = target.getBuildTools();

        if (outputHandler == null) {
            // If we weren't passed any output handlers, use a logged process output handler.
            outputHandler =
                    new LoggedProcessOutputHandler(new FilteringLogger(builder.getLogger()));
        }

        switch (aaptGeneration) {
            case AAPT_V1:
                return new AaptV1(
                        builder.getProcessExecutor(),
                        outputHandler,
                        buildTools,
                        new FilteringLogger(builder.getLogger()),
                        crunchPng ? AaptV1.PngProcessMode.ALL : AaptV1.PngProcessMode.NO_CRUNCH,
                        cruncherProcesses);
            case AAPT_V2_DAEMON_MODE:
                return new QueueableAapt2(
                        outputHandler,
                        buildTools,
                        new FilteringLogger(builder.getLogger()),
                        0 /* use default */);
            default:
                throw new IllegalArgumentException("unknown aapt generation" + aaptGeneration);
        }
    }

    /** Logger that downgrades some warnings to info level. */
    public static class FilteringLogger implements ILogger {

        /**
         * Ignored warnings in the aapt messages.
         */
        private static final List<Pattern> IGNORED_WARNINGS =
                Lists.newArrayList(
                        Pattern.compile("Not recognizing known sRGB profile that has been edited"));

        /**
         * The logger to delegate to.
         */
        @NonNull
        private final ILogger mDelegate;

        /**
         * Creates a new logger.
         *
         * @param delegate the logger ot delegate messages to
         */
        public FilteringLogger(@NonNull ILogger delegate) {
            mDelegate = delegate;
        }

        @Override
        public void error(@Nullable Throwable t, @Nullable String msgFormat, Object... args) {
            if (msgFormat != null && shouldDowngrade(msgFormat, args)) {
                mDelegate.info(Strings.nullToEmpty(msgFormat), args);
            } else {
                mDelegate.error(t, msgFormat, args);
            }
        }

        @Override
        public void warning(@NonNull String msgFormat, Object... args) {
            if (shouldDowngrade(msgFormat, args)) {
                mDelegate.info(msgFormat, args);
            } else {
                mDelegate.warning(msgFormat, args);
            }
        }

        @Override
        public void info(@NonNull String msgFormat, Object... args) {
            mDelegate.info(msgFormat, args);
        }

        @Override
        public void verbose(@NonNull String msgFormat, Object... args) {
            mDelegate.verbose(msgFormat, args);
        }

        /**
         * Checks whether a log message should be downgraded or not.
         *
         * @param msgFormat the message format
         * @param args the message arguments
         * @return should this message be downgraded to {@code INFO} level?
         */
        private static boolean shouldDowngrade(String msgFormat, Object... args) {
            String message = String.format(msgFormat, args);
            for (Pattern pattern : IGNORED_WARNINGS) {
                if (pattern.matcher(message).find()) {
                    return true;
                }
            }

            return false;
        }
    }
}
