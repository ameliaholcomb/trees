package com.trees.common.jni;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.nio.Buffer;
import java.nio.ByteBuffer;


public class ImageProcessor implements ImageProcessorInterface {

    int[] SHAPE = new int[]{360, 480}; /* height x width */

    @Override
    public ImageResult processImage(Activity context, ImageRaw raw) {

        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(context));
        }
        Python py = Python.getInstance();
        PyObject ImProcModule = py.getModule("improc");

        PyObject pyDepth = PyObject.fromJava(raw.depthMat);
        PyObject pyRgb = PyObject.fromJava(raw.rgbMat);

        byte[] displayImage = new byte[SHAPE[0] * SHAPE[1] * 4];
        try {
            displayImage = ImProcModule.callAttrThrows(
                    "run", pyDepth, pyRgb).toJava(byte[].class);
        } catch (Throwable throwable) {
            if (throwable.getMessage().contains("MissingDepthError")) {
                // TODO: This works, just need to show the information to the user
                Log.i("AMELIA", "no depthtthhs");
            } else {
                Log.i("AMELIA", "CAUGHT ASOMETHING");
                throwable.printStackTrace();
            }
        }
        Buffer display = ByteBuffer.wrap(displayImage);
        Log.i("AMELIA", String.format("size: %d", displayImage.length));
        Log.i("AMELIA", String.format("capacity: %d", display.capacity()));
        Log.i("AMELIA", String.format("position: %d", display.position()));

        ImageResult imageResult = new ImageResult();
        imageResult.DisplayImage = Bitmap.createBitmap(SHAPE[0], SHAPE[1], Bitmap.Config.ARGB_8888);
        imageResult.DisplayImage.copyPixelsFromBuffer(display);

        return imageResult;
    }
}
