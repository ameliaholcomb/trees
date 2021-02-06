/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2020-2020. All rights reserved.
 */

package com.huawei.arengine.demos.java.world.rendering;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.Image;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.huawei.hiar.ARImage;
import com.trees.activities.ImageCaptureActivity;
import com.huawei.arengine.demos.java.world.rendering.common.DisplayRotationUtil;
import com.huawei.arengine.demos.java.world.rendering.common.TextDisplayUtil;
import com.huawei.arengine.demos.java.world.rendering.common.TextureRenderUtil;
import com.huawei.hiar.ARCamera;
import com.huawei.hiar.ARFrame;
import com.huawei.hiar.ARLightEstimate;
import com.huawei.hiar.ARPlane;
import com.huawei.hiar.ARSession;
import com.huawei.hiar.ARTrackable;
import com.trees.common.helpers.ImageUtil;
import com.trees.common.helpers.TofUtil;
import com.trees.common.jni.ImageProcessorInterface;


import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


/**
 * This class shows how to render the data obtained through AREngine.
 *
 * @author HW
 * @since 2020-03-21
 */
public class RenderUtil implements GLSurfaceView.Renderer {

    private static final String TAG = RenderUtil.class.getSimpleName();


    private static final float MATRIX_SCALE_SX = -1.0f;

    private static final float MATRIX_SCALE_SY = -1.0f;

    private ARSession mSession;

    private ImageCaptureActivity mActivity;

    private Context mContext;

    private TextView mSearchingTextView;

    private int frames = 0;

    private long lastInterval;

    private float fps;

    private TextureRenderUtil mTextureRenderUtil = new TextureRenderUtil();

    private TextDisplayUtil mTextDisplayUtil = new TextDisplayUtil();


    private DisplayRotationUtil mDisplayRotationUtil;

    private CompletableFuture<ImageProcessorInterface.ImageRaw> captureFuture = null;


    /**
     * Constructor, passing in context and activity.
     * This method will be called by {@link Activity#onCreate}
     *
     * @param activity Activity
     * @param context Context
     */
    public RenderUtil(ImageCaptureActivity activity, Context context) {
        mActivity = activity;
        mContext = context;
    }

    /**
     * Set AR session for updating in onDrawFrame to get the latest data.
     *
     * @param arSession ARSession.
     */
    public void setArSession(ARSession arSession) {
        if (arSession == null) {
            Log.e(TAG, "setSession error, arSession is null!");
            return;
        }
        mSession = arSession;
    }


    /**
     * Set displayRotationUtil, this object will be used in onSurfaceChanged and onDrawFrame.
     *
     * @param displayRotationUtil DisplayRotationUtil.
     */
    public void setDisplayRotationUtil(DisplayRotationUtil displayRotationUtil) {
        if (displayRotationUtil == null) {
            Log.e(TAG, "setDisplayRotationUtil error, displayRotationUtil is null!");
            return;
        }
        mDisplayRotationUtil = displayRotationUtil;
    }


    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        // Clear color, set window color.
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

        mTextureRenderUtil.init();
        mTextDisplayUtil.setListener(new TextDisplayUtil.OnTextInfoChangeListener() {
            @Override
            public boolean textInfoChanged(String text, float positionX, float positionY) {
                return true;
            }
        });
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        mTextureRenderUtil.onSurfaceChanged(width, height);
        GLES20.glViewport(0, 0, width, height);
        mDisplayRotationUtil.updateViewportRotation(width, height);
    }

    public Future<ImageProcessorInterface.ImageRaw> captureNextFrame() {
        captureFuture = new CompletableFuture<>();
        return captureFuture;
    }

    private void maybeCaptureImage(ARFrame frame) {
        if (captureFuture == null) {
            return;
        }
        try (
                Image imgRGB = frame.acquireCameraImage();
                ARImage imgTOF = (ARImage) frame.acquireDepthImage();
            ) {
            ImageProcessorInterface.ImageRaw ret = new ImageProcessorInterface.ImageRaw();
            ret.rgbMat = ImageUtil.imageToByteArray(imgRGB);
            TofUtil.TofArrays tofArrays = new TofUtil().parseTof(imgTOF);
            ret.tofMat = tofArrays;
            captureFuture.complete(ret);
        } catch (Throwable t) {
            captureFuture.completeExceptionally(t);
        } finally {
            captureFuture= null;
        }
    }

    @Override
    public void onDrawFrame(GL10 unused) {

        // Clear screen to notify driver it should not load any pixels from previous frame.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (mSession == null) {
            return;
        }
        if (mDisplayRotationUtil.getDeviceRotation()) {
            mDisplayRotationUtil.updateArSessionDisplayGeometry(mSession);
        }

        try {
            mSession.setCameraTextureName(mTextureRenderUtil.getExternalTextureId());
            ARFrame arFrame = mSession.update();
            ARCamera arCamera = arFrame.getCamera();
            maybeCaptureImage(arFrame);


            // ARCameraConfig arCameraConfig = mSession.getCameraConfig();
            mTextureRenderUtil.onDrawFrame(arFrame);

            StringBuilder sb = new StringBuilder();
            updateMessageData(sb);
            mTextDisplayUtil.onDrawFrame(sb);


            for (ARPlane plane : mSession.getAllTrackables(ARPlane.class)) {
                if (plane.getType() != ARPlane.PlaneType.UNKNOWN_FACING
                        && plane.getTrackingState() == ARTrackable.TrackingState.TRACKING) {
                    hideLoadingMessage();
                    break;
                }
            }
            float lightPixelIntensity = 1;
            ARLightEstimate lightEstimate = arFrame.getLightEstimate();
            if (lightEstimate.getState() != ARLightEstimate.State.NOT_VALID) {
                lightPixelIntensity = lightEstimate.getPixelIntensity();
            };

        } catch (Throwable t) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(TAG, "Exception on the OpenGL thread", t);
        }
    }

    private Bitmap getPlaneBitmap(int id) {
        TextView view = mActivity.findViewById(id);
        view.setDrawingCacheEnabled(true);
        view.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        Bitmap bitmap = view.getDrawingCache();
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.setScale(MATRIX_SCALE_SX, MATRIX_SCALE_SY);
        if (bitmap == null) {
            return null;
        }
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        return bitmap;
    }

    /**
     * Update gesture related data for display.
     *
     * @param sb string buffer.
     */
    private void updateMessageData(StringBuilder sb) {
        float fpsResult = doFpsCalculate();
        sb.append("FPS=" + fpsResult + System.lineSeparator());
    }

    private float doFpsCalculate() {
        ++frames;
        long timeNow = System.currentTimeMillis();

        // Convert milliseconds to seconds.
        if (((timeNow - lastInterval) / 1000.0f) > 0.5f) {
            fps = frames / ((timeNow - lastInterval) / 1000.0f);
            frames = 0;
            lastInterval = timeNow;
        }
        return fps;
    }

    private void hideLoadingMessage() {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mSearchingTextView != null) {
                    mSearchingTextView.setVisibility(View.GONE);
                    mSearchingTextView = null;
                }
            }
        });
    }
}