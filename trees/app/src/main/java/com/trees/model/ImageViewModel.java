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
    private static final String LOG_TAG = "AMELIA";

    private final ImageProcessorInterface imageProcessor;
    private final ImageStoreInterface imageStore;
    private final SavedStateHandle state;

    private Integer nextCapture;
    private final MutableLiveData<Integer> sampleNumber;
    private final MutableLiveData<ImageProcessorInterface.ImageResult> currentCapture;

    public ImageViewModel(SavedStateHandle savedStateHandle,
            ImageProcessorInterface imageProcessor, ImageStoreInterface imageStore) {
        this.state = savedStateHandle;
        this.imageProcessor = imageProcessor;
        this.imageStore = imageStore;
        this.sampleNumber = new MutableLiveData<>();
        this.currentCapture = new MutableLiveData<>();

        Integer s;
        Integer c;
        if (state.contains("nextCapture") && state.contains("sampleNumber")) {
            // Get sample and capture number from saved state
            c = state.get("nextCapture");
            s = state.get("sampleNumber");
        } else {
            // If there is no saved state, get the maximum sample and capture numbers currently saved
            // in the file system. Defaults to s = 1, c = 0 if the target directory is empty
            Integer[] sc = imageStore.getMaxSampleCaptureNums();
            s = sc[0];
            c = sc[1];
        }
        sampleNumber.setValue(s);
        state.set("sampleNumber", sampleNumber.getValue());
        nextCapture = c;
        state.set("nextCapture", c);
    }


    public LiveData<Integer> getSampleNumber() {
        return sampleNumber;
    }

    public void incrementSampleNumber() {
        sampleNumber.setValue(sampleNumber.getValue() + 1);
        state.set("sampleNumber", sampleNumber.getValue());
    }

    public void decrementSampleNumber() {
        sampleNumber.setValue(Math.max(0, sampleNumber.getValue() - 1));
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
