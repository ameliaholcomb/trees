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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.trees.common.helpers.CameraPermissionHelper;
import com.trees.common.helpers.ImageStore;
import com.huawei.arengine.demos.java.world.rendering.RenderUtil;
import com.huawei.arengine.demos.java.world.rendering.common.DisplayRotationUtil;
import com.huawei.hiar.AREnginesApk;
import com.huawei.hiar.ARSession;
import com.huawei.hiar.ARWorldTrackingConfig;
import com.huawei.hiar.exceptions.ARCameraNotAvailableException;
import com.trees.common.helpers.ImageStoreInterface;
import com.trees.common.jni.ImageProcessorInterface;
import com.trees.common.jni.ImageProcessor;
import com.trees.model.ImageViewModel;
import com.trees.model.ImageViewModelFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;


public class ImageCaptureActivity extends AppCompatActivity {
    private static final String TAG = ImageCaptureActivity.class.getSimpleName();
    private static final String SAMPLE_NUM_ID = "sampleNum";
    private static final String DELETE_BUTTON_ID = "deleteButton";
    private static final String LOG_TAG = "AMELIA";
    // Required for test run.
    private static final Short AUTOMATOR_DEFAULT = 0;
    private static final String AUTOMATOR_KEY = "automator";
    // Projection matrix constants
    private static final int PROJ_MATRIX_OFFSET = 0;
    private static final float PROJ_MATRIX_NEAR = 0.1f;
    private static final float PROJ_MATRIX_FAR = 100.0f;
    private final AtomicBoolean automatorRun = new AtomicBoolean(false);

    private ImageViewModel imageModel;

    // AR session
    private ARSession arSession = null;
    // RenderUtil for rendering
    private RenderUtil renderUtil = null;
    private boolean isRemindInstall = true;
    // DisplayRotationUtil as a rotation helper
    private DisplayRotationUtil displayRotationUtil = null;
    // GL Surface used to draw camera preview image.
    private GLSurfaceView surfaceView;
    // App context, to be assigned in onCreate
    private Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // TODO: Deal with this in a way Evan approves of
        ImageProcessorInterface imageProcessor = new ImageProcessor();
        ImageStoreInterface imageStore = new ImageStore();
        ImageViewModelFactory imageViewModelFactory = new ImageViewModelFactory(imageProcessor, imageStore);
        imageModel = new ViewModelProvider(this, imageViewModelFactory).get(ImageViewModel.class);
        imageModel.getSampleNumber().observe(this, sample -> {
                    int sampleNumId = getResources().getIdentifier(
                            SAMPLE_NUM_ID, "id", context.getPackageName());
                    EditText sampleNum = (EditText) findViewById(sampleNumId);
                    sampleNum.setText(Integer.toString(sample));

                    if (sample == 0) {
                        this.enableDeleteButton();
                    } else {
                        this.disableDeleteButton();
                    }
                }
        );

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

        context = getApplicationContext();

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
        Future<ImageProcessorInterface.ImageRaw> future = renderUtil.captureNextFrame();
        try {
            ImageProcessorInterface.ImageRaw raw = future.get();
            imageModel.captureImage(this, raw);
            getSupportFragmentManager().beginTransaction()
                    .setReorderingAllowed(true)
                    .add(R.id.fragment_container_view, CaptureConfirmationFragment.class, null)
                    .commit();
        // TODO: Display to user that capture failed and remove fragment
        } catch (ExecutionException e) {
            Log.i("AMELIA", "errrrr");
            e.printStackTrace();
        } catch (InterruptedException e) {
            Log.i("AMELIA", "errrrr int");
            e.printStackTrace();
        }
    }


    // Plus button to change the sample being read
    public void onSamplePlus(View view) {
        imageModel.incrementSampleNumber();
    }


    // Minus button for the sample being read
    public void onSampleMinus(View view) {
        imageModel.decrementSampleNumber();
    }

    // TODO: Move this to a separate activity showing all saved images
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
