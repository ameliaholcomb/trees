package com.trees.common.jni;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.provider.ContactsContract;
import android.util.Log;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.trees.common.helpers.TofUtil;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.List;


public class ImageProcessor implements ImageProcessorInterface {

    int[] SHAPE = new int[]{360, 480}; /* height x width */

    @Override
    public ImageResult processImage(Activity context, ImageRaw raw) {

        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(context));
        }
        Python py = Python.getInstance();
        PyObject ImProcModule = py.getModule("improc");

        PyObject pyDepth = PyObject.fromJava(raw.tofMat.dBuffer);
        PyObject pyRgb = PyObject.fromJava(raw.rgbMat);

        ImageResult imageResult = new ImageResult();
        byte[] displayImage = new byte[SHAPE[0] * SHAPE[1] * 4];
        try {
            List<PyObject> obj = ImProcModule.callAttrThrows("run", pyDepth, pyRgb).asList();
            displayImage = obj.get(0).toJava(byte[].class);
            float estDepth = obj.get(1).toJava(float.class);
            float estDiameter = obj.get(2).toJava(float.class);

//            displayImage = ImProcModule.callAttrThrows(
//                    "run", pyDepth, pyRgb).toJava(byte[].class);
            Buffer display = ByteBuffer.wrap(displayImage);
            imageResult.DisplayImage = Bitmap.createBitmap(SHAPE[1], SHAPE[0], Bitmap.Config.ARGB_8888);
            imageResult.DisplayImage.copyPixelsFromBuffer(display);
            imageResult.RGBImage = raw.rgbMat;
            imageResult.DepthImage = raw.tofMat;
            imageResult.Depth = estDepth;
            imageResult.Diameter = estDiameter;

        } catch (Throwable throwable) {
            if (throwable.getMessage().contains("MissingDepthError")) {
                // TODO: This works, just need to show the information to the user
                Log.i("AMELIA", "no depthtthhs");
            } else {
                Log.i("AMELIA", "CAUGHT ASOMETHING");
                throwable.printStackTrace();
            }
        }
        return imageResult;
    }
}
