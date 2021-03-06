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
package com.android.tradefed.build;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import java.io.File;
import java.io.IOException;

/**
 * Implementation of a {@link ISdkBuildInfo}
 */
public class SdkBuildInfo extends BuildInfo implements ISdkBuildInfo {

    private File mAdtDir = null;
    private File mSdkDir = null;
    private boolean mDeleteSdkDirParent;

    private static final int ANDROID_TIMEOUT_MS = 15*1000;

    /**
     * Creates a {@link SdkBuildInfo} using default attribute values.
     */
    public SdkBuildInfo() {
    }

    /**
     * Creates a {@link SdkBuildInfo}
     *
     * @param buildId the build id
     * @param testTarget the test target name
     * @param buildName the build name
     */
    public SdkBuildInfo(String buildId, String testTarget, String buildName) {
        super(buildId, testTarget, buildName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getSdkDir() {
        return mSdkDir;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getAdtDir() {
        return mAdtDir;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAdtDir(File adtDir) {
        mAdtDir  = adtDir;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSdkDir(File sdkDir) {
        setSdkDir(sdkDir, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSdkDir(File sdkDir, boolean deleteParent) {
        mSdkDir = sdkDir;
        mDeleteSdkDirParent = deleteParent;
    }

    @Override
    public void cleanUp() {
        if (mSdkDir != null) {
            if (mDeleteSdkDirParent) {
                FileUtil.recursiveDelete(mSdkDir.getParentFile());
            } else {
                FileUtil.recursiveDelete(mSdkDir);
            }
        }
        if (mAdtDir != null) {
            FileUtil.recursiveDelete(mAdtDir);
        }
        mSdkDir = null;
        mAdtDir = null;
    }

    @Override
    public IBuildInfo clone() {
        SdkBuildInfo cloneBuild = new SdkBuildInfo(getBuildId(), getTestTag(), getBuildTargetName());
        cloneBuild.addAllBuildAttributes(this);
        try {
            File cloneAdtDir = null;
            if (getAdtDir() != null) {
                cloneAdtDir = FileUtil.createTempDir("cloneAdt");
                FileUtil.recursiveCopy(getAdtDir(), cloneAdtDir);
                cloneBuild.setAdtDir(cloneAdtDir);
            }
            File cloneSdkDir = null;
            if (getSdkDir() != null) {
                cloneSdkDir = FileUtil.createTempDir("cloneSdk");
                FileUtil.recursiveCopy(getSdkDir(), cloneSdkDir);
                cloneBuild.setSdkDir(cloneSdkDir);
            }
            return cloneBuild;
        } catch (IOException e) {
            throw new RuntimeException("Could not clone sdk build", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAndroidToolPath() {
        if (getSdkDir() == null) {
            throw new IllegalStateException("sdk dir is not set");
        }
        return FileUtil.getPath(getSdkDir().getAbsolutePath(), "tools", "android");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String[] getSdkTargets() {
        CommandResult result = getRunUtil().runTimedCmd(ANDROID_TIMEOUT_MS,
                getAndroidToolPath(), "list", "targets", "--compact");
        if (!result.getStatus().equals(CommandStatus.SUCCESS)) {
            CLog.e(String.format(
                    "Unable to get list of SDK targets using %s. Result %s, err %s",
                    getAndroidToolPath(), result.getStatus(), result.getStderr()));
            return null;
        }
        return result.getStdout().split("\n");
    }

    /**
     * Gets the {@link IRunUtil} instance to use.
     * <p/>
     * Exposed for unit testing
     */
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getEmulatorToolPath() {
        if (getSdkDir() == null) {
            throw new IllegalStateException("sdk dir is not set");
        }
        return FileUtil.getPath(getSdkDir().getAbsolutePath(), "tools", "emulator");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void makeToolsExecutable() {
        File toolsDir = FileUtil.getFileForPath(getSdkDir(), "tools");
        makeExecutable(toolsDir.listFiles());
        File platformToolsDir = FileUtil.getFileForPath(getSdkDir(), "platform-tools");
        makeExecutable(platformToolsDir.listFiles());
    }

    /**
     * Helper method to make a array of files executable
     *
     * @param files the files to make executable
     */
    private void makeExecutable(File[] files) {
        if (files != null) {
            for (File file : files) {
                file.setExecutable(true, true);
            }
        }
    }
 }
