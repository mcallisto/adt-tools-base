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

package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.incremental.InstantRunPatchingPolicy;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.tasks.DefaultAndroidTask;
import com.android.builder.model.OptionalCompilationStep;
import com.android.build.gradle.internal.scope.VariantScope;

import org.gradle.api.Task;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;

/**
 * Kicker task to force execution of the InstantRun slicer and dexer even if the files have not
 * changed (since we delay and batch restart artifacts creation).
 */
public class ColdswapArtifactsKickerTask extends DefaultAndroidTask {

    private VariantScope variantScope;
    private InstantRunBuildContext instantRunContext;

    @TaskAction
    public void doFullTaskAction() throws IOException {
        // if the restart flag is set, we should allow for downstream tasks to execute.
        boolean restartDexRequested =
                variantScope.getGlobalScope().isActive(OptionalCompilationStep.RESTART_ONLY);
        boolean changesAreCompatible =
                variantScope.getInstantRunBuildContext().hasPassedVerification();

        // so if the restart artifacts are requested or the changes are incompatible
        // we should always run, otherwise it can wait.
        if (!restartDexRequested && changesAreCompatible) {
            // We can hot swap, don't produce the apk
            if (instantRunContext.getPatchingPolicy() == InstantRunPatchingPolicy.PRE_LOLLIPOP) {
                // TODO: disable multidex too
                disableTask(variantScope.getPackageApplicationTask());
            } else {
                disableTask(variantScope.getInstantRunSlicerTask());
            }
        } else { //Cold swap
            if (instantRunContext.getPatchingPolicy() == InstantRunPatchingPolicy.PRE_LOLLIPOP) {
                instantRunContext.abort();
            }
        }
    }

    private <T extends Task> void disableTask(AndroidTask<T> task) {
        variantScope.getGlobalScope().getProject().getTasks().getByName(task.getName())
                .setEnabled(false);
    }

    public static class ConfigAction implements TaskConfigAction<ColdswapArtifactsKickerTask> {

        @NonNull
        protected final VariantScope scope;
        @NonNull
        protected final String name;

        public ConfigAction(@NonNull String name, @NonNull VariantScope scope) {
            this.scope = scope;
            this.name = name;
        }

        @Override
        @NonNull
        public String getName() {
            return scope.getTaskName(name);
        }

        @NonNull
        @Override
        public Class<ColdswapArtifactsKickerTask> getType() {
            return ColdswapArtifactsKickerTask.class;
        }

        @Override
        public void execute(@NonNull ColdswapArtifactsKickerTask task) {
            task.setVariantName(scope.getVariantConfiguration().getFullName());
            task.variantScope = scope;
            task.instantRunContext = scope.getInstantRunBuildContext();
        }
    }
}
