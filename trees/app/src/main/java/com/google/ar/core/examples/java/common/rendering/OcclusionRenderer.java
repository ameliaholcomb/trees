/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.ar.core.examples.java.common.rendering;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.opengl.GLES20;
import android.util.Size;

import com.google.ar.core.PointCloud;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;

/**
 * Renders a point cloud.
 */
public class OcclusionRenderer {
    private static final String TAG = PointCloud.class.getSimpleName();

    // Shader names.
    private static final String VERTEX_SHADER_NAME = "shaders/occlusion.vert";
    private static final String FRAGMENT_SHADER_NAME = "shaders/occlusion.frag";

    private static final int BYTES_PER_FLOAT = Float.SIZE / 8;
    private static final int FLOATS_PER_POINT = 3; // X,Y,Z.
    private static final int BYTES_PER_POINT = BYTES_PER_FLOAT * FLOATS_PER_POINT;

    private int depthWidth = -1;
    private int depthHeight = -1;
    private int programName;
    private int positionAttribute;
    private int pointSizeUniform;

    private int numPoints = 0;

    private FloatBuffer verticesBuffer = null;

    public OcclusionRenderer() {
    }

    /**
     * Allocates and initializes OpenGL resources needed by the plane renderer.
     *
     * @param context Needed to access shader source.
     */
    public void createOnGlThread(Context context) throws IOException {
        ShaderUtil.checkGLError(TAG, "buffer alloc");

        int vertexShader =
                ShaderUtil.loadGLShader(TAG, context, GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_NAME);
        int passthroughShader =
                ShaderUtil.loadGLShader(TAG, context, GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_NAME);

        programName = GLES20.glCreateProgram();
        GLES20.glAttachShader(programName, vertexShader);
        GLES20.glAttachShader(programName, passthroughShader);
        GLES20.glLinkProgram(programName);
        GLES20.glUseProgram(programName);

        ShaderUtil.checkGLError(TAG, "program");

        positionAttribute = GLES20.glGetAttribLocation(programName, "a_Position");
        pointSizeUniform = GLES20.glGetUniformLocation(programName, "u_PointSize");

        ShaderUtil.checkGLError(TAG, "program  params");
    }

    public ArrayList<String> getResolutions(Context context, String cameraId) {
        ArrayList<String> output = new ArrayList<>();
        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            for (Size s : characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG)) {
                output.add(s.getWidth() + "x" + s.getHeight());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return output;
    }

    /**
     * This method opens the camera at the given index. Expects depth camera.
     */
//  public void initCamera(Context context, String cameraId, int index) {
//    boolean ok = false;
//    try {
//      int current = 0;
//      CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
//      CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
//
//      // Loop through every camera, taking their characteristics, until we find the one we are looking
//      // for. At this point, record the depthWidth and depthHeight.
//      for (Size s : characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.DEPTH16)) {
//        depthWidth = s.getWidth();
//        depthHeight = s.getHeight();
//        ok = true;
//        if (current == index)
//          break;
//        else;
//          current++;
//      }
//    } catch (Exception e) {
//      e.printStackTrace();
//    }
//    if (!ok) {
//      Log.e("ARCoreApp", "Depth sensor not found!");
//      System.exit(1);
//    }
//    Log.i("Connor width", Integer.toString(depthWidth));
//    Log.i("Connor height", Integer.toString(depthHeight));
//
//  }
    public synchronized void update(float[] data) {
        numPoints = 0;
        int index = 0;
        int input = 0;
        float[] array = new float[data.length * FLOATS_PER_POINT];
        for (int y = 0; y < depthHeight; y++) {
            for (int x = 0; x < depthWidth; x++) {
                if (data[input] > 0) {
                    array[index++] = 2.0f * (x + 0.5f) / (float) depthWidth - 1.0f;
                    array[index++] = -2.0f * (y + 0.5f) / (float) depthHeight + 1.0f;
                    array[index++] = data[input];
                    numPoints++;
                }
                input++;
            }
        }
        ByteBuffer buffer = ByteBuffer.allocateDirect(data.length * BYTES_PER_POINT);
        buffer.order(ByteOrder.nativeOrder());
        verticesBuffer = buffer.asFloatBuffer();
        verticesBuffer.put(array);
        verticesBuffer.position(0);
    }

    /**
     * Renders the point cloud. ARCore point cloud is given in world space.
     */
    public synchronized void draw(boolean render) {

        if (verticesBuffer == null)
            return;

        ShaderUtil.checkGLError(TAG, "Before draw");

        if (!render)
            GLES20.glColorMask(false, false, false, false);
        GLES20.glUseProgram(programName);
        GLES20.glEnableVertexAttribArray(positionAttribute);
        GLES20.glVertexAttribPointer(positionAttribute, FLOATS_PER_POINT, GLES20.GL_FLOAT, false, BYTES_PER_POINT, verticesBuffer);
        GLES20.glUniform1f(pointSizeUniform, 125.0f);

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, numPoints);
        GLES20.glDisableVertexAttribArray(positionAttribute);
        GLES20.glColorMask(true, true, true, true);

        ShaderUtil.checkGLError(TAG, "Draw");
    }

    public int getDepthWidth() {
        return depthWidth;
    }

    public void setDepthWidth(int w) {
        depthWidth = w;
    }

    public int getDepthHeight() {
        return depthHeight;
    }

    public void setDepthHeight(int h) {
        depthHeight = h;
    }
}
