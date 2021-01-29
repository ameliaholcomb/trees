package com.trees.common.helpers;

import java.nio.ByteBuffer;
import java.util.ArrayList;

/* Representation of TOF image as four buffers: x,y,d,confidence
*    For use in saving the TOF image into text readable format
* */

public class TofBuffers {
    public ArrayList<Short> xBuffer = new ArrayList<>();
    public ArrayList<Short> yBuffer = new ArrayList<>();
    public ArrayList<Float> dBuffer = new ArrayList<>();
    public ByteBuffer dByteBuffer;
    public ArrayList<Float> percentageBuffer = new ArrayList<>();
}
