package com.trees.common.jni;

import android.app.Activity;
import android.graphics.Bitmap;

@FunctionalInterface
public interface ImageProcessorInterface {

    /* Java class containing results of image computation.
        Includes images for display and storage, as well as computed depth and diameter estimates.
     */
    class ImageResult {
        /* Image to display to the user.
        Includes artifacts of computation, such as trunk boundary lines. */
        public Bitmap DisplayImage;

        /* Raw RGB image, in a saveable format */
        public Bitmap RGBImage;

        /* Raw Depth image, in a saveable float array format */
        public float[] DepthImage;

        /* Raw Confidence array, in a saveable float array format */
        public float[] ConfImage;

        /* Trunk diameter estimate, as computed by algorithm */
        public float Diameter;

        /* Estimate trunk depth, as computed by algorithm */
        public float Depth;
    }

    class ImageRaw {
        public byte[] rgbMat;
        public float[] depthMat;
    }

    ImageResult processImage(Activity context, ImageRaw raw);
}
