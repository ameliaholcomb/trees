package com.trees.model;

import android.app.Activity;
import android.os.Parcelable;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.trees.common.helpers.ImageStoreInterface;
import com.trees.common.pyi.ImageProcessorInterface;

import java.io.IOException;

public class ImageViewModel extends ViewModel {
    private static String LOG_TAG = "AMELIA";

    private ImageProcessorInterface imageProcessor;
    private ImageStoreInterface imageStore;
    private SavedStateHandle state;

    private Integer nextCapture;
    private MutableLiveData<Integer> sampleNumber;
    private MutableLiveData<ImageProcessorInterface.ImageResult> currentCapture;

    public ImageViewModel(SavedStateHandle savedStateHandle,
            ImageProcessorInterface imageProcessor, ImageStoreInterface imageStore) {
        this.state = savedStateHandle;
        this.imageProcessor = imageProcessor;
        this.imageStore = imageStore;
        this.sampleNumber = new MutableLiveData<>();
        this.currentCapture = new MutableLiveData<>();

        nextCapture = state.contains("nextCapture") ? state.get("nextCapture") : 0;
        Integer s = state.contains("sampleNumber") ? state.get("sampleNumber") : 1;
        sampleNumber.setValue(s);
    }

    public LiveData<Integer> getSampleNumber() {
        return sampleNumber;
    }

    public void incrementSampleNumber() {
        sampleNumber.setValue(sampleNumber.getValue() + 1);
        state.set("sampleNumber", sampleNumber.getValue());
    }

    public void decrementSampleNumber() {
        sampleNumber.setValue(Math.min(0, sampleNumber.getValue() - 1));
        state.set("sampleNumber", sampleNumber.getValue());
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
        state.set("nextCapture", nextCapture);
    }
}
