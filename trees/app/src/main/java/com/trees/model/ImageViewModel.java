package com.trees.model;

import android.media.Image;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.huawei.hiar.ARImage;
import com.trees.common.helpers.ImageStoreInterface;
import com.trees.common.jni.ImageProcessorInterface;

import java.io.IOException;

public class ImageViewModel extends ViewModel {
    private static String LOG_TAG = "AMELIA";

    private ImageProcessorInterface imageProcessorInterface;
    private ImageStoreInterface imageStoreInterface;

    private Integer nextCapture;
    private MutableLiveData<Integer> sampleNumber;
    private MutableLiveData<ImageProcessorInterface.ImageResult> currentCapture;

    public ImageViewModel(
            ImageProcessorInterface imageProcessorInterface, ImageStoreInterface imageStoreInterface) {
        this.imageProcessorInterface = imageProcessorInterface;
        this.imageStoreInterface = imageStoreInterface;
        this.sampleNumber = new MutableLiveData<>();
        this.currentCapture = new MutableLiveData<>();

        // TODO: Persist sample and capture number
        this.nextCapture = 0;
        this.sampleNumber.setValue(1);
    }

    public LiveData<Integer> getSampleNumber() {
        return sampleNumber;
    }

    public void incrementSampleNumber() {
        sampleNumber.setValue(sampleNumber.getValue() + 1);
    }

    public void decrementSampleNumber() {
        sampleNumber.setValue(Math.min(0, sampleNumber.getValue() - 1));
    }

    public LiveData<ImageProcessorInterface.ImageResult> getCurrentCapture() {
        return currentCapture;
    }

    public void captureImage(Image rgbImage, ARImage tofImage) {
        // Pass to JNI for image processing.
        // The Java wrapper will handle parsing the Image and ARImage into JNI-compatible objects
        ImageProcessorInterface.ImageResult imageResult = imageProcessorInterface.processImage(
                rgbImage, tofImage);
        currentCapture.setValue(imageResult);
    }

    public void storeCapture() {
        ImageProcessorInterface.ImageResult c = currentCapture.getValue();
        Integer s = sampleNumber.getValue();
        try {
            imageStoreInterface.saveToFileRGB(s, nextCapture, c.RGBImage);
            //        imageStoreInterface.saveToFileTOF(sampleNumber, nextCapture, c.DepthImage, c.ConfImage);
            //        imageStoreInterface.saveToFileMatrix(sampleNumber, nextCapture, c.matrices);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Unable to store the image: ", e);
        }
        nextCapture++;
    }
}
