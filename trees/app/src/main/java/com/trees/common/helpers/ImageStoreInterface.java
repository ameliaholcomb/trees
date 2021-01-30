package com.trees.common.helpers;

import android.graphics.Bitmap;
import android.media.Image;

import java.io.IOException;

public interface ImageStoreInterface {

    void saveToFileTOF(
            Integer sampleNumber, Integer captureNumber, TofUtil.TofArrays arrays) throws IOException;

    void saveToFileRGB(
            Integer sampleNumber, Integer captureNumber, Bitmap image) throws IOException;

    void saveToFileMatrix(
            Integer sampleNumber, Integer captureNumber,
            float[] projectionMatrix, float[] viewMatrix) throws IOException;
}
