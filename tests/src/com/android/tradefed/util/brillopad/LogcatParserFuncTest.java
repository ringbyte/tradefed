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
package com.android.tradefed.util.brillopad;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.SnapshotInputStreamSource;
import com.android.tradefed.util.brillopad.item.LogcatItem;

import junit.framework.TestCase;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Functional tests for {@link LogcatParser}
 */
public class LogcatParserFuncTest extends TestCase {
    // FIXME: Make logcat file configurable.
    private static final String LOGCAT_PATH = "/tmp/logcat.txt";

    /**
     * A test that is intended to force Brillopad to parse a logcat. The purpose of this is to
     * assist a developer in checking why a given logcat file might not be parsed correctly by
     * Brillopad.
     */
    public void testParse() {
        InputStreamSource logcatSource = null;
        try {
            logcatSource = new SnapshotInputStreamSource(new FileInputStream(
                    new File(LOGCAT_PATH)));
        } catch (FileNotFoundException e) {
            fail(String.format("File not found at %s", LOGCAT_PATH));
        }
        LogcatItem logcat = null;
        try {
            long start = System.currentTimeMillis();
            logcat = new LogcatParser().parse(logcatSource);
            long stop = System.currentTimeMillis();
            CLog.e("Logcat took %d ms to parse.", stop - start);
        } catch (IOException e) {
            fail(String.format("IOException: %s", e.toString()));
        } finally {
            logcatSource.cancel();
        }

        assertNotNull(logcat);
        assertNotNull(logcat.getStartTime());
        assertNotNull(logcat.getStopTime());

        CLog.e("Stats for logcat:\n" +
                "  Start time: %s\n" +
                "  Stop time: %s\n" +
                "  %d ANR(s), %d Java Crash(es), %d Native Crash(es)",
                logcat.getStartTime().toString(),
                logcat.getStopTime().toString(),
                logcat.getAnrs().size(),
                logcat.getJavaCrashes().size(),
                logcat.getNativeCrashes().size());
    }
}

