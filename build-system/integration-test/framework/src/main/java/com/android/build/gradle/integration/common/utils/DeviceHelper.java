/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.integration.common.utils;


import com.android.annotations.NonNull;
import com.android.builder.testing.ConnectedDeviceProvider;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.DeviceException;
import com.android.ddmlib.DdmPreferences;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import com.google.common.collect.Sets;
import java.util.Set;

/**
 * Helper for performing connected device related tasks.
 */
public class DeviceHelper {

    /**
     * This hardcoded timeout only impacts the gradle plugin integration tests, i.e. not anything
     * that is externally published.
     */
    public static final int DEFAULT_ADB_TIMEOUT_MSEC = DdmPreferences.DEFAULT_TIMEOUT * 3;

    /**
     * Return the set of all ABIs supported by any of the connected devices.
     */
    @NonNull
    public static Set<String> getDeviceAbis() throws DeviceException {
        ILogger logger = new StdLogger(StdLogger.Level.VERBOSE);
        ConnectedDeviceProvider deviceProvider =
                new ConnectedDeviceProvider(SdkHelper.getAdb(), DEFAULT_ADB_TIMEOUT_MSEC, logger);
        deviceProvider.init();
        Set<String> abis = Sets.newHashSet();
        for(DeviceConnector deviceConnector : deviceProvider.getDevices()) {
            abis.addAll(deviceConnector.getAbis());
        }
        deviceProvider.terminate();
        return abis;
    }

    public static IDevice getIDevice() throws DeviceException {
        return Iterables.getOnlyElement(getIDevices());
    }
    public static List<IDevice> getIDevices() throws DeviceException {
        AndroidDebugBridge.initIfNeeded(false /*clientSupport*/);

        AndroidDebugBridge bridge = AndroidDebugBridge.createBridge(
                SdkHelper.getAdb().getAbsolutePath(), false /*forceNewBridge*/);

        assertNotNull("Debug bridge", bridge);

        long timeOut = DEFAULT_ADB_TIMEOUT_MSEC;
        int sleepTime = 1000;
        while (!bridge.hasInitialDeviceList() && timeOut > 0) {
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                throw new DeviceException(e);
            }
            timeOut -= sleepTime;
        }

        if (timeOut <= 0 && !bridge.hasInitialDeviceList()) {
            throw new DeviceException("Timeout getting device list.");
        }

        return ImmutableList.copyOf(bridge.getDevices());
    }
}
