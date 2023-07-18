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
    private static final String PROCESS_DATA_LOG_TAG = "IntermediateData";

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

            // get log info
            String logInfo = obj.get(3).toJava(String.class);
            System.out.println("getdata");
            // get plot
            /*byte[] rgb_disp_plot = obj.get(4).toJava(byte[].class);
            byte[] center_depth_plot = obj.get(5).toJava(byte[].class);*/

            System.out.println("finished conversion");

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

            // log the info
            Log.i(PROCESS_DATA_LOG_TAG, logInfo);
            imageResult.LogInfo = logInfo;
            // imageResult.RGBDispPlot = rgb_disp_plot;
            // imageResult.CenterDepthPlot = center_depth_plot;


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
