package com.trees.common.jni;


import android.graphics.Bitmap;
import android.media.Image;

import com.huawei.hiar.ARImage;

public class ImageProcessor implements ImageProcessorInterface {
    static {
        System.loadLibrary("image-processor");
    }
    /* Native interface to process a paired TOF and RGB image
        Returns errno.
        Callers of the class use the public interface processImage,
        which handles translating Java-friendly classes for parameters and return values
        into efficient JNI-compatible types.
     */
    private native int nativeProcessImage();

    public ImageResult processImage(Image imgRGB, ARImage imgTOF) {


        int errno = nativeProcessImage();
        // Check return value and raise exception

        // Parse buffers for computation return values
        ImageResult res = new ImageResult();

        return res;
    }
}
