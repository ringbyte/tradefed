/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.tradefed.testtype;

import com.android.ddmlib.FileListingService;
import com.android.ddmlib.Log;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IFileEntry;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Test that runs a native benchmark test executable on given device.
 * <p/>
 * It uses {@link NativeBenchmarkTestParser} to parse out the average operation time vs delay
 * between operations those results to the {@link ITestInvocationListener}s.
 */
public class NativeBenchmarkTest extends AbstractRemoteTest implements IDeviceTest, IRemoteTest {

    private static final String LOG_TAG = "NativeStressTest";
    static final String DEFAULT_TEST_PATH = "data/nativebenchmark";

    // The metrics key names to report to listeners
    static final String AVG_OP_TIME_KEY_PREFIX = "avg-operation-time";
    static final String ITERATION_KEY = "iterations";

    private ITestDevice mDevice = null;

    @Option(name = "native-benchmark-device-path",
      description="The path on the device where native stress tests are located.")
    private String mDeviceTestPath = DEFAULT_TEST_PATH;

    @Option(name = "benchmark-module-name",
            description="The name of the native benchmark test module to run. " +
            "If not specified all tests in --native-benchmark-device-path will be run")
    private String mTestModule = null;

    @Option(name = "benchmark-run-name",
            description="Optional name to pass to test reporters. If unspecified, will use" +
            "--benchmark-module-name")
    private String mReportRunName = null;

    @Option(name = "iterations",
            description="The number of benchmark test iterations per run.")
    private int mNumIterations = 1000;

    @Option(name = "delay-per-run",
            description="The delay between each benchmark iteration, in micro seconds." +
                "Multiple values may be given to specify multiple runs with different delay values.")
    // TODO: change units to seconds for consistency with native benchmark module input
    private Collection<Integer> mDelays = new ArrayList<Integer>();

    @Option(name = "max-run-time", description =
         "The maximum time to allow for one benchmark run in ms. Default is 5 min.")
    private int mMaxRunTime = 5 * 60 * 1000;

    @Option(name = "server-cpu",
            description="Optionally specify a server cpu. Default 1")
    private int mServerCpu = 1;

    @Option(name = "client-cpu",
            description="Optionally specify a client cpu. Default 1")
    private int mClientCpu = 1;


    // TODO: consider sharing code with {@link GTest} and {@link NativeStressTest}

    /**
     * {@inheritDoc}
     */
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * Set the Android native benchmark test module to run.
     *
     * @param moduleName The name of the native test module to run
     */
    public void setModuleName(String moduleName) {
        mTestModule = moduleName;
    }

    /**
     * Get the Android native benchmark test module to run.
     *
     * @return the name of the native test module to run, or null if not set
     */
    public String getModuleName(String moduleName) {
        return mTestModule;
    }

    /**
     * Set the number of iterations to execute per run
     */
    void setNumIterations(int iterations) {
        mNumIterations = iterations;
    }

    /**
     * Set the delay values per run
     */
    void addDelaysPerRun(Collection<Integer> delays) {
        mDelays.addAll(delays);
    }

    /**
     * Gets the path where native benchmark tests live on the device.
     *
     * @return The path on the device where the native tests live.
     */
    private String getTestPath() {
        StringBuilder testPath = new StringBuilder(mDeviceTestPath);
        if (mTestModule != null) {
            testPath.append(FileListingService.FILE_SEPARATOR);
            testPath.append(mTestModule);
        }
        return testPath.toString();
    }

    /**
     * Executes all native benchmark tests in a folder as well as in all subfolders recursively.
     *
     * @param rootEntry The root folder to begin searching for native tests
     * @param testDevice The device to run tests on
     * @param listeners the run listeners
     * @throws DeviceNotAvailableException
     */
    private void doRunAllTestsInSubdirectory(IFileEntry rootEntry, ITestDevice testDevice,
            Collection<ITestRunListener> listeners) throws DeviceNotAvailableException {

        if (rootEntry.isDirectory()) {
            // recursively run tests in all subdirectories
            for (IFileEntry childEntry : rootEntry.getChildren(true)) {
                doRunAllTestsInSubdirectory(childEntry, testDevice, listeners);
            }
        } else {
            // assume every file is a valid benchmark test binary.
            // use name of file as run name
            String runName = (mReportRunName == null ? rootEntry.getName() : mReportRunName);
            String fullPath = rootEntry.getFullEscapedPath();
            if (mDelays.size() == 0) {
                // default to one run with no delay
                mDelays.add(0);
            }

            // force file to be executable
            testDevice.executeShellCommand(String.format("chmod 755 %s", fullPath));
            long startTime = System.currentTimeMillis();

            for (ITestRunListener listener : listeners) {
                listener.testRunStarted(runName, 0);
            }
            Map<String, String> metricMap = new HashMap<String, String>();
            metricMap.put(ITERATION_KEY, Integer.toString(mNumIterations));
            try {
                for (Integer delay : mDelays) {
                    NativeBenchmarkTestParser resultParser = createResultParser(runName);
                    // convert delay to seconds
                    double delayFloat = ((double)delay)/1000000;
                    String cmd = String.format("%s -n %d -d %f -c %d -s %d", fullPath,
                            mNumIterations, delayFloat, mClientCpu, mServerCpu);
                    Log.i(LOG_TAG, String.format("Running native benchmark test on %s: %s",
                            mDevice.getSerialNumber(), cmd));
                    testDevice.executeShellCommand(cmd, resultParser, mMaxRunTime, 1);
                    addMetric(metricMap, resultParser, delay);
                }
                // TODO: is catching exceptions, and reporting testRunFailed necessary?
            } finally {
                final long elapsedTime = System.currentTimeMillis() - startTime;
                for (ITestRunListener listener : listeners) {
                    listener.testRunEnded(elapsedTime, metricMap);
                }
            }
        }
    }

    /**
     * Adds the operation time metric for a run with given delay
     *
     * @param metricMap
     * @param resultParser
     * @param delay
     */
    private void addMetric(Map<String, String> metricMap, NativeBenchmarkTestParser resultParser,
            Integer delay) {
        String metricKey = String.format("%s-delay%d", AVG_OP_TIME_KEY_PREFIX, delay);
        // temporarily convert seconds to microseconds, as some reporters cannot handle small values
        metricMap.put(metricKey, Double.toString(resultParser.getAvgOperationTime()*1000000));
    }

    /**
     * Factory method for creating a {@link NativeBenchmarkTestParser} that parses test output
     * <p/>
     * Exposed so unit tests can mock.
     *
     * @param runName
     * @return a {@link NativeBenchmarkTestParser}
     */
    NativeBenchmarkTestParser createResultParser(String runName) {
        return new NativeBenchmarkTestParser(runName);
    }

    /**
     * {@inheritDoc}
     */
    public void run(List<ITestInvocationListener> listeners) throws DeviceNotAvailableException {
        if (mDevice == null) {
            throw new IllegalArgumentException("Device has not been set");
        }

        String testPath = getTestPath();
        IFileEntry nativeTestDirectory = mDevice.getFileEntry(testPath);
        if (nativeTestDirectory == null) {
            Log.w(LOG_TAG, String.format("Could not find native benchmark test directory %s in %s!",
                    testPath, mDevice.getSerialNumber()));
            return;
        }
        doRunAllTestsInSubdirectory(nativeTestDirectory, mDevice, convertListeners(listeners));
    }

    /**
     * Convert a list of {@link ITestInvocationListener} to a collection of {@link ITestRunListener}
     */
    private Collection<ITestRunListener> convertListeners(List<ITestInvocationListener> listeners) {
        ArrayList<ITestRunListener> copy = new ArrayList<ITestRunListener>(listeners.size());
        copy.addAll(listeners);
        return copy;
    }
}
