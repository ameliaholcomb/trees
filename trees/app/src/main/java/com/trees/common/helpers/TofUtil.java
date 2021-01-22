package com.trees.common.helpers;

import com.huawei.hiar.ARImage;

import java.nio.ShortBuffer;
import java.util.ArrayList;

public class TofUtil {

    public static TofBuffers TofToBuffers(ARImage imgTOF) {

        // Buffers for storing TOF output
        TofBuffers buffers = new TofBuffers();

        ARImage.Plane plane = imgTOF.getPlanes()[0];
        ShortBuffer shortDepthBuffer = plane.getBuffer().asShortBuffer();

        int stride = plane.getRowStride();
        int offset = 0;
        float sum = 0.0f;
        float[] output = new float[imgTOF.getWidth() * imgTOF.getHeight()];
        for (short y = 0; y < imgTOF.getHeight(); y++) {
            for (short x = 0; x < imgTOF.getWidth(); x++) {
                // Parse the data. Format is [depth|confidence]
                int depthSample = shortDepthBuffer.get((int) (y / 2) * stride + x) & 0xFFFF;
                depthSample = (((depthSample & 0xFF) << 8) & 0xFF00) | (((depthSample & 0xFF00) >> 8) & 0xFF);
                short depthSampleShort = (short) depthSample;
                short depthRange = (short) (depthSampleShort & 0x1FFF);
                short depthConfidence = (short) ((depthSampleShort >> 13) & 0x7);
                float depthPercentage = depthConfidence == 0 ? 1.f : (depthConfidence - 1) / 7.f;

                output[offset + x] = (float) depthRange / 10000;

                sum += output[offset + x];
                // Store data in buffer
                buffers.xBuffer.add(x);
                buffers.yBuffer.add(y);
                buffers.dBuffer.add(depthRange / 1000.0f);
                buffers.percentageBuffer.add(depthPercentage);
            }
            offset += imgTOF.getWidth();
        }
        return buffers;
    }
}
