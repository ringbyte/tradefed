/*
 * Copyright (C) 2010 The Android Open Source Project
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

import com.android.ddmlib.testrunner.ITestRunListener;

import junit.framework.Test;

/**
 * A specialization of JUnit Test that reports results to a {@link ITestRunListener}.
 *
 * This is desirable so the results of a remote test don't need to be unnecessarily marshalled and
 * unmarshalled from {@link Test} objects.
 */
public interface IRemoteTest extends Test {

    /**
     * Runs the tests, and reports results to the listener.
     *
     * @param listener the {@link ITestRunListener}
     */
    public void run(ITestRunListener listener);
}
