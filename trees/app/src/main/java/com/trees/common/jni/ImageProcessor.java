package com.trees.common.jni;

import android.media.Image;
import android.util.Log;

import com.huawei.hiar.ARImage;
import com.trees.common.helpers.ImageUtil;
import com.trees.common.helpers.TofUtil;

import org.opencv.core.CvType;
import org.opencv.core.Mat;


public class ImageProcessor implements ImageProcessorInterface {

    @Override
    public ImageResult processImage(ImageRaw raw) {

        Log.i("AMELIA", "doing my fake processing over here");

        return new FakeImageProcessor().processImage(raw);
    }
}
