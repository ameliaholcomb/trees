package com.trees.model;

import android.app.Activity;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.trees.common.helpers.ImageStoreInterface;
import com.trees.common.jni.ImageProcessorInterface;

import java.io.IOException;

public class ImageViewModel extends ViewModel {
    private static String LOG_TAG = "AMELIA";

    private ImageProcessorInterface imageProcessor;
    private ImageStoreInterface imageStore;

    private Integer nextCapture;
    private MutableLiveData<Integer> sampleNumber;
    private MutableLiveData<ImageProcessorInterface.ImageResult> currentCapture;

    public ImageViewModel(
            ImageProcessorInterface imageProcessor, ImageStoreInterface imageStore) {
        this.imageProcessor = imageProcessor;
        this.imageStore = imageStore;
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

    public void captureImage(Activity context, ImageProcessorInterface.ImageRaw raw) {
        ImageProcessorInterface.ImageResult imageResult = imageProcessor.processImage(context, raw);
        currentCapture.setValue(imageResult);
    }

    public void storeCapture() {
        ImageProcessorInterface.ImageResult c = currentCapture.getValue();
        Integer s = sampleNumber.getValue();
        try {
            imageStore.saveToFileRGB(s, nextCapture, c.RGBImage);
            imageStore.saveToFileTOF(s, nextCapture, c.DepthImage);
            imageStore.saveToFileResults(s, nextCapture, c.Depth, c.Diameter);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Unable to store the image: ", e);
        }
        nextCapture++;
    }
}
