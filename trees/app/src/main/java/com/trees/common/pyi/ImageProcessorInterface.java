package com.trees.common.pyi;

import android.app.Activity;
import android.graphics.Bitmap;

import com.trees.common.helpers.TofUtil;

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
        public byte[] RGBImage;

        /* Raw Depth image, in a saveable float array format */
        public TofUtil.TofArrays DepthImage;

        /* Trunk diameter estimate, as computed by algorithm */
        public float Diameter;

        /* Estimate trunk depth, as computed by algorithm */
        public float Depth;

        // log info
        public String LogInfo;

        // rgb_disp_plot
        public byte[] RGBDispPlot;

        // center_depth_plot
        public byte[] CenterDepthPlot;
    }

    class ImageRaw {
        public byte[] rgbMat;
        public int rgbWidth;
        public int rgbHeight;
        public TofUtil.TofArrays tofMat;
        public int tofWidth;
        public int tofHeight;
    }

    ImageResult processImage(Activity context, ImageRaw raw);
}
