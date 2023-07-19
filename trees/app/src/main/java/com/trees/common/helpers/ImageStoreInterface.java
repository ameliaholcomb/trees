package com.trees.common.helpers;

import android.graphics.Bitmap;
import android.media.Image;

import java.io.IOException;

public interface ImageStoreInterface {

    void saveToFileTOF(
            Integer sampleNumber, Integer captureNumber, TofUtil.TofArrays arrays) throws IOException;

    void saveToFileRGB(
            Integer sampleNumber, Integer captureNumber, byte[] image) throws IOException;

    void saveToFileProjectionMatrix(
            Integer sampleNumber, Integer captureNumber,
            float[] projectionMatrix, float[] viewMatrix) throws IOException;

    void saveToFileResults(
            Integer sampleNumber, Integer nextCapture, float depth, float diameter);

    void saveToFileLogInfo(Integer sampleNumber, Integer nextCapture, String logInfo);

    void saveToFileDispPlot(Integer sampleNumber, Integer captureNumber, byte[] rgb_disp_plot);

    void saveToFileCenterDepthPlot(Integer sampleNumber, Integer captureNumber, byte[] center_depth_plot);

    Integer[] getMaxSampleCaptureNums();
}
