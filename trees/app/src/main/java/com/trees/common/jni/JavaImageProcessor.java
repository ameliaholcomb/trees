package com.trees.common.jni;

import android.media.Image;
import android.util.Log;

import com.huawei.hiar.ARImage;
import com.trees.common.helpers.ImageUtil;
import com.trees.common.helpers.TofBuffers;
import com.trees.common.helpers.TofUtil;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.nio.ByteBuffer;

public class JavaImageProcessor implements ImageProcessorInterface {

    @Override
    public ImageResult processImage(Image imgRGB, ARImage imgTOF) {

        System.loadLibrary("opencv_java3");
        Mat rgbmat = ImageUtil.imageToMat(imgRGB);
        TofBuffers tofbuffers = TofUtil.TofToBuffers(imgTOF);
        Mat depthmat = new Mat(imgTOF.getHeight(), imgTOF.getWidth(), CvType.CV_8S, tofbuffers.dByteBuffer);

        Log.i("AMELIA", String.format("%f", tofbuffers.dByteBuffer.get(56)));
        Log.i("AMELIA", String.format("%f", ((short) tofbuffers.dByteBuffer.get(56))/1000.0f));

        return null;
    }
}
