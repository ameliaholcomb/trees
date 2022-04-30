package com.trees.common.helpers;

import android.util.Log;

//import com.huawei.hiar.ARImage;
import com.google.ar.core.ArImage;


import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;

public class TofUtil {
    public class TofArrays {
        public short[] xBuffer;
        public short[] yBuffer;
        public float[] dBuffer;
        public float[] percentageBuffer;
        public int length;

        public TofArrays(int size) {
            xBuffer = new short[size];
            yBuffer = new short[size];
            dBuffer = new float[size];
            percentageBuffer = new float[size];
        }
    }

    public TofArrays parseTof(ArImage imgTOF) {

        // Buffers for storing TOF output
        TofArrays arrays = new TofArrays(imgTOF.getWidth() * imgTOF.getHeight());
        ArImage.Plane plane = imgTOF.getPlanes()[0];
        ShortBuffer shortDepthBuffer = plane.getBuffer().asShortBuffer();

        int stride = plane.getRowStride();
        int offset = 0;
//        float sum = 0.0f;
//        float[] output = new float[imgTOF.getWidth() * imgTOF.getHeight()];
        int i = 0;
        for (short y = 0; y < imgTOF.getHeight(); y++) {
            for (short x = 0; x < imgTOF.getWidth(); x++) {
                // Parse the data. Format is [depth|confidence]
                int depthSample = shortDepthBuffer.get((int) (y / 2) * stride + x) & 0xFFFF;
                depthSample = (((depthSample & 0xFF) << 8) & 0xFF00) | (((depthSample & 0xFF00) >> 8) & 0xFF);
                short depthSampleShort = (short) depthSample;
                short depthRange = (short) (depthSampleShort & 0x1FFF);
                short depthConfidence = (short) ((depthSampleShort >> 13) & 0x7);
                float depthPercentage = depthConfidence == 0 ? 1.f : (depthConfidence - 1) / 7.f;

//                output[offset + x] = (float) depthRange / 10000;

//                sum += output[offset + x];

                // Store data in buffer
                arrays.xBuffer[i] = x;
                arrays.yBuffer[i] = y;
                arrays.dBuffer[i] = depthRange / 1000.0f;
                arrays.percentageBuffer[i] = depthPercentage;
                i++;
            }
            offset += imgTOF.getWidth();
        }
        return arrays;
    }

}
