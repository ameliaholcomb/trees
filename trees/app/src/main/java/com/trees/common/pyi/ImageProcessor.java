package com.trees.common.pyi;

import android.app.Activity;
import android.graphics.Bitmap;
import android.util.Log;
import android.widget.Toast;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.trees.activities.R;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;


public class ImageProcessor implements ImageProcessorInterface {

//    TODO how to not hard-code this
    int[] SHAPE = new int[]{480, 640}; /* height x width */

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
        byte[] displayImage;
        try {
            List<PyObject> obj = ImProcModule.callAttrThrows("run", pyDepth, pyRgb,
                    raw.rgbWidth, raw.rgbHeight, raw.tofWidth, raw.tofHeight).asList();
            displayImage = obj.get(0).toJava(byte[].class);
            float estDepth = obj.get(1).toJava(float.class);
            float estDiameter = obj.get(2).toJava(float.class);

            Buffer display = ByteBuffer.wrap(displayImage);
            imageResult.DisplayImage = Bitmap.createBitmap(SHAPE[1], SHAPE[0], Bitmap.Config.ARGB_8888);
            imageResult.DisplayImage.copyPixelsFromBuffer(display);
            imageResult.RGBImage = raw.rgbMat;
            imageResult.DepthImage = raw.tofMat;

            Log.e("SOFIJA", "number of bytes in display image" + imageResult.DisplayImage.getByteCount());
            imageResult.Depth = estDepth;
            imageResult.Diameter = estDiameter;

            if (estDiameter == 0 && estDepth == 0) {
                Toast.makeText(context, R.string.noDepthPoints, Toast.LENGTH_LONG).show();
            }

        } catch (Throwable throwable) {
            if (Objects.requireNonNull(throwable.getMessage()).contains("MissingDepthError")) {
                Toast.makeText(context, R.string.noDepthPoints, Toast.LENGTH_LONG).show();
            } else {
                throwable.printStackTrace();
                Log.e("SOFIJA", "throwable log", throwable);
            }
        }
        return imageResult;
    }
}
