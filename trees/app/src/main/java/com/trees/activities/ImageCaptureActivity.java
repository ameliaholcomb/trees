/*
 * Copyright 2018 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.trees.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Image;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.HandlerThread;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.huawei.hiar.ARCamera;
import com.trees.common.helpers.CameraPermissionHelper;
import com.trees.common.helpers.ImageStore;
import com.trees.common.helpers.ImageUtil;
import com.huawei.arengine.demos.java.world.rendering.RenderUtil;
import com.huawei.arengine.demos.java.world.rendering.common.DisplayRotationUtil;
import com.huawei.hiar.AREnginesApk;
import com.huawei.hiar.ARFrame;
import com.huawei.hiar.ARImage;
import com.huawei.hiar.ARSession;
import com.huawei.hiar.ARWorldTrackingConfig;
import com.huawei.hiar.exceptions.ARCameraNotAvailableException;
import com.trees.common.helpers.TofUtil;
import com.trees.common.jni.DiameterComputation;
import com.trees.common.jni.ImageProcessor;
import com.trees.common.rendering.DrawingView;

import java.util.concurrent.atomic.AtomicBoolean;


public class ImageCaptureActivity extends AppCompatActivity {
    private static final String TAG = ImageCaptureActivity.class.getSimpleName();
    private static final String SAMPLE_NUM_ID = "sampleNum";
    private static final String DELETE_BUTTON_ID = "deleteButton";
    private static final String LOG_TAG = "AMELIA";
    // Required for test run.
    private static final Short AUTOMATOR_DEFAULT = 0;
    private static final String AUTOMATOR_KEY = "automator";
    private static final String PREFERENCES = "stateInfo";
    private static final String SHARED_NUM_CAPTURES = "numCaptures";
    private static final String SHARED_CURRENT_SAMPLE = "currentSample";
    // Projection matrix constants
    private static final int PROJ_MATRIX_OFFSET = 0;
    private static final float PROJ_MATRIX_NEAR = 0.1f;
    private static final float PROJ_MATRIX_FAR = 100.0f;
    private final AtomicBoolean automatorRun = new AtomicBoolean(false);
    // A check mechanism to ensure that the camera closed properly so that the app can safely exit.
    private final ConditionVariable safeToExitApp = new ConditionVariable();
    // Preferences object and associated file
    SharedPreferences preferences;
    // AR session
    private ARSession arSession = null;
    private ARFrame arFrame = null;
    private int imagePreviewResult;
    private float[] projectionMatrix;
    private float[] viewMatrix;
    private Image imgRGB;
    private ARImage imgTOF;
    // RenderUtil for rendering
    private RenderUtil renderUtil = null;
    private boolean isRemindInstall = true;
    // DisplayRotationUtil as a rotation helper
    private DisplayRotationUtil displayRotationUtil = null;
    // GL Surface used to draw camera preview image.
    private GLSurfaceView surfaceView;
    // SurfaceView to draw simpler objects
    private DrawingView drawingView;
    // Looper handler thread.
    private HandlerThread backgroundThread;
    // App context, to be assigned in onCreate
    private Context context;

    // Helpers for shared memory
    private int getSharedPreferencesVar(String name) {
        return preferences.getInt(name, Integer.MIN_VALUE);
    }

    private void setSharedPreferencesVar(String name, int val) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(name, val);
        editor.commit();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get permission to access the camera
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            CameraPermissionHelper.requestCameraPermission(this);
        }

        Bundle extraBundle = getIntent().getExtras();
        if (extraBundle != null && 1 == extraBundle.getShort(AUTOMATOR_KEY, AUTOMATOR_DEFAULT)) {
            automatorRun.set(true);
        }

        // set render
        displayRotationUtil = new DisplayRotationUtil(this);
        renderUtil = new RenderUtil(this, this);
        renderUtil.setDisplayRotationUtil(displayRotationUtil);

        // GL surface view that renders camera preview image.
        surfaceView = findViewById(R.id.glsurfaceview);
        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setEGLContextClientVersion(2);
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        surfaceView.setRenderer(renderUtil);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        // Transparent overlay for drawing on the GL camera preview
        drawingView = (DrawingView) findViewById(R.id.drawingsurface);
        drawingView.setZOrderOnTop(true);

        context = getApplicationContext();
        preferences = context.getSharedPreferences(PREFERENCES, MODE_PRIVATE);

        // Ensure that our persistent variables are set properly
        final int DEF = Integer.MIN_VALUE;
        int savedNumCaptures = this.getSharedPreferencesVar(SHARED_NUM_CAPTURES);
        int savedCurrentSample = this.getSharedPreferencesVar(SHARED_CURRENT_SAMPLE);

        if (savedNumCaptures == DEF) {
            savedNumCaptures = 0;
            this.setSharedPreferencesVar(SHARED_NUM_CAPTURES, savedNumCaptures);
        }

        if (savedCurrentSample == DEF) {
            savedCurrentSample = 1;
            this.setSharedPreferencesVar(SHARED_CURRENT_SAMPLE, savedCurrentSample);
        } else if (savedCurrentSample == 0) {
            this.enableDeleteButton();
        }

        // Set the current sample on screen according to savedCurrentSample.
        int sampleNumId = getResources().getIdentifier(SAMPLE_NUM_ID, "id", context.getPackageName());
        EditText sampleNum = (EditText) findViewById(sampleNumId);

        sampleNum.setText(Integer.toString(savedCurrentSample));
    }


    @Override
    protected void onResume() {
        super.onResume();
        surfaceView.onResume();

        if (arSession == null) {
            try {
                // Request to install arengine server. If it is already installed or
                // the user chooses to install it, it will work normally. Otherwise, set isRemindInstall to false.
                requestedInstall();

                // If the user rejects the installation, isRemindInstall is false.
                if (!isRemindInstall) {
                    return;
                }
                arSession = new ARSession(this);
                ARWorldTrackingConfig config = new ARWorldTrackingConfig(arSession);

                int supportedSemanticMode = arSession.getSupportedSemanticMode();
                Log.d(TAG, "supportedSemanticMode:" + supportedSemanticMode);
                if (supportedSemanticMode != ARWorldTrackingConfig.SEMANTIC_NONE) {
                    Log.d(TAG, "supported mode:" + supportedSemanticMode);
                    config.setSemanticMode(supportedSemanticMode);
                }
                arSession.configure(config);

                renderUtil.setArSession(arSession);
            } catch (Exception capturedException) {
                Log.e(TAG, "Unable to create AR session", capturedException);
            }
        }
        try {
            arSession.resume();
        } catch (ARCameraNotAvailableException e) {
            Toast.makeText(this, "Camera open failed, please restart the app", Toast.LENGTH_LONG).show();
            arSession = null;
            return;
        }
        displayRotationUtil.registerDisplayListener();
    }


    @Override
    public void onPause() {
        super.onPause();
        surfaceView.onPause();
        if (arSession != null) {
            displayRotationUtil.unregisterDisplayListener();
            arSession.pause();
        }
    }


    // Android permission request callback.
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(
                    getApplicationContext(),
                    "Camera permission is needed to run this application",
                    Toast.LENGTH_LONG)
                    .show();
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this);
            }
            finish();
        }
    }


    // Android focus change callback.
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }


    private void requestedInstall() {
        AREnginesApk.ARInstallStatus installStatus = AREnginesApk.requestInstall(this, isRemindInstall);
        switch (installStatus) {
            case INSTALL_REQUESTED:
                isRemindInstall = false;
                break;
            case INSTALLED:
                break;
        }
    }


    public void onCaptureImage(View view) {
        Log.i(LOG_TAG, "Capturing an image");
        if (arFrame == null) {
            return;
        }

        Integer sampleNumber = this.getSharedPreferencesVar(SHARED_CURRENT_SAMPLE);
        Integer captureNumber = this.getSharedPreferencesVar(SHARED_NUM_CAPTURES);

        // Acquire ToF, RGB, and projection matrices
        ARFrame arFrame = arSession.update();
        imgRGB = arFrame.acquireCameraImage();
        imgTOF = (ARImage) arFrame.acquireDepthImage();

//        float[] projectionMatrix = new float[16];  /* 4 x 4 */
//        float[] viewMatrix = new float[16];        /* 4 x 4 */
//        ARCamera arCamera = arFrame.getCamera();
//        arCamera.getProjectionMatrix(projectionMatrix, PROJ_MATRIX_OFFSET, PROJ_MATRIX_NEAR, PROJ_MATRIX_FAR);
//        arCamera.getViewMatrix(viewMatrix, 0);
//
//        // Convert TOF and RGB images to format passable through intents and JNI layer
//        byte[] rgbBytes = ImageUtil.imageToByteArray(imgRGB);
//        float[] depthArray = TofUtil.TofToFloatArray(imgTOF);
//
//        // Perform native diameter computation and save Parcelable results
//        // TODO: Implement native computation
//        DiameterComputation comp = new ImageProcessor().JavaComputeDiameter(rgbBytes, depthArray, confArray);
//
//        // Pass images, matrices, and diameter computation through to the ImagePreviewActivity
//        Intent intent = new Intent(this, ImagePreviewActivity.class);
//        intent.putExtra("SAMPLE_NUM", sampleNumber);
//        intent.putExtra("CAPTURE_NUM", captureNumber);
//        intent.putExtra("IMAGE_DEPTH", depthArray);
//        intent.putExtra("IMAGE_RGB", rgbBytes);
//        intent.putExtra("IMAGE_DEPTH", depthArray);
//        intent.putExtra("IMAGE_CONF", confArray);
//        intent.putExtra("PROJ_MATRIX", projectionMatrix);
//        intent.putExtra("VIEW_MATRIX", viewMatrix);
//        intent.putExtra("DIAMETER_COMP", comp);
//        startActivityForResult(intent, imagePreviewResult);

        // Update capture number
        this.setSharedPreferencesVar(SHARED_NUM_CAPTURES, this.getSharedPreferencesVar(SHARED_NUM_CAPTURES) + 1);
    }


    // Plus button to change the sample being read
    public void onSamplePlus(View view) {
        Log.i(LOG_TAG, "adding to the sample num");

        // If enabled, disable the delete button
        this.disableDeleteButton();

        // Get the text object which displays the current sample
        int sampleNumId = getResources().getIdentifier(SAMPLE_NUM_ID, "id", context.getPackageName());
        EditText sampleNum = (EditText) findViewById(sampleNumId);

        int currentSample = this.getSharedPreferencesVar(SHARED_CURRENT_SAMPLE);

        // Don't allow the sample number to overflow
        if (currentSample + 1 == Integer.MAX_VALUE) {
            return;
        } else {
            currentSample++;
        }

        // Write the change to the shared preferences file
        this.setSharedPreferencesVar(SHARED_CURRENT_SAMPLE, currentSample);

        // Change the on-screen number
        sampleNum.setText(Integer.toString(currentSample));
    }


    // Minus button for the sample being read
    public void onSampleMinus(View view) {
        Log.i(LOG_TAG, "decrementing the sample num");
        // Get the text object which displays the current sample
        int sampleNumId = getResources().getIdentifier(SAMPLE_NUM_ID, "id", context.getPackageName());
        EditText sampleNum = (EditText) findViewById(sampleNumId);

        int currentSample = this.getSharedPreferencesVar(SHARED_CURRENT_SAMPLE);

        // Don't allow the sample number get less than 0
        if (currentSample - 1 == 0) {
            currentSample--;
            Log.i(LOG_TAG, "Enabling delete");
            this.enableDeleteButton();
        } else if (currentSample - 1 > 0) {
            currentSample--;
        } else {
            return;
        }

        // Write the change to the shared preferences file
        this.setSharedPreferencesVar(SHARED_CURRENT_SAMPLE, currentSample);

        // Change the on-screen number
        sampleNum.setText(Integer.toString(currentSample));
    }

    // Enable the button to delete all files
    private void enableDeleteButton() {
        int deleteId = getResources().getIdentifier(DELETE_BUTTON_ID, "id", context.getPackageName());
        Log.i("delete id", Integer.toString(deleteId));
        View view = findViewById(deleteId);
        view.setVisibility(View.VISIBLE);
        view.setEnabled(true);
    }


    private void disableDeleteButton() {
        int deleteId = getResources().getIdentifier(DELETE_BUTTON_ID, "id", context.getPackageName());
        View view = findViewById(deleteId);

        if (view.getVisibility() == View.VISIBLE) {
            view.setVisibility(View.GONE);
            view.setEnabled(false);
        }
    }

    // Method for deleting data
    public void onDeleteData(View view) {
        ImageStore imageStore = new ImageStore();
        imageStore.deleteFiles();
    }

}
