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

    Integer[] getMaxSampleCaptureNums();
}
