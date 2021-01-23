package com.trees.model;


import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.trees.common.helpers.ImageStoreInterface;
import com.trees.common.jni.ImageProcessorInterface;

import java.lang.reflect.InvocationTargetException;

public class ImageViewModelFactory implements ViewModelProvider.Factory {
    private ImageStoreInterface imageStoreInterface;
    private ImageProcessorInterface imageProcessorInterface;


    public ImageViewModelFactory(
            ImageProcessorInterface imageProcessorInterface, ImageStoreInterface imageStoreInterface ) {
        this.imageProcessorInterface = imageProcessorInterface;
        this.imageStoreInterface = imageStoreInterface;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        try {
            return modelClass.getConstructor(
                    ImageProcessorInterface.class, ImageStoreInterface.class).newInstance(
                    imageProcessorInterface, imageStoreInterface);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        return null;
    }
}
