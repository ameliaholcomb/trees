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
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Session;
import com.google.ar.core.CameraConfig;
import com.google.ar.core.CameraConfigFilter;
import com.google.ar.core.Config;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import com.trees.common.helpers.CameraPermissionHelper;
import com.trees.common.helpers.ImageStore;
import com.huawei.arengine.demos.java.world.rendering.RenderUtil;
import com.huawei.arengine.demos.java.world.rendering.common.DisplayRotationUtil;


// TODO: figure out why this doesn't work
//import com.google.ar.core.examples.java.common.helpers.DepthSettings;


//import com.huawei.hiar.AREnginesApk;
//import com.huawei.hiar.ARSession;
//import com.huawei.hiar.ARWorldTrackingConfig; //this one is missing in ARCore
//import com.huawei.hiar.exceptions.ARCameraNotAvailableException;

import com.trees.common.helpers.ImageStoreInterface;
import com.trees.common.helpers.StoragePermissionHelper;
import com.trees.common.pyi.ImageProcessorInterface;
import com.trees.common.pyi.ImageProcessor;
import com.trees.model.ImageViewModel;
import com.trees.model.ImageViewModelFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import java.util.EnumSet;

public class ImageCaptureActivity extends AppCompatActivity {
    private static final String TAG = ImageCaptureActivity.class.getSimpleName();
    private static final String LOG_TAG = "AMELIA";
    // Required for test run.
    private static final Short AUTOMATOR_DEFAULT = 0;
    private static final String AUTOMATOR_KEY = "automator";
    private final AtomicBoolean automatorRun = new AtomicBoolean(false);
    // Projection matrix constants
//    private static final int PROJ_MATRIX_OFFSET = 0;
//    private static final float PROJ_MATRIX_NEAR = 0.1f;
//    private static final float PROJ_MATRIX_FAR = 100.0f;

    private ImageViewModel imageModel;

//    trying to copy arcore example
    private boolean installRequested;

    // AR session
    private Session arSession = null;
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

        // Verify STORAGE_PERMISSION has been granted.
        if (!StoragePermissionHelper.hasStoragePermission(this)) {
            StoragePermissionHelper.requestStoragePermission(this);
        }

        // Get permission to access the camera
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            CameraPermissionHelper.requestCameraPermission(this);
        }

        // TODO: Deal with this in a way Evan approves of
        ImageProcessorInterface imageProcessor = new ImageProcessor();
        ImageStoreInterface imageStore = new ImageStore();
        ImageViewModelFactory imageViewModelFactory = new ImageViewModelFactory(
                imageStore, imageProcessor, this, savedInstanceState);
        imageModel = new ViewModelProvider(this, imageViewModelFactory).get(ImageViewModel.class);
        imageModel.getSampleNumber().observe(this, sample -> {
                    EditText sampleNum = (EditText) findViewById(R.id.sampleNum);
                    sampleNum.setText(Integer.toString(sample));

                    if (sample == 0) {
                        this.enableDeleteButton();
                    } else {
                        this.disableDeleteButton();
                    }
                }
        );

        setContentView(R.layout.activity_main);


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
            Exception exception = null;
            String message = null;
            try {
//                // Request to install arcore server. If it is already installed or
//                // the user chooses to install it, it will work normally. Otherwise, set isRemindInstall to false.
//                requestedInstall();
//
//                // If the user rejects the installation, isRemindInstall is false.
//                if (!isRemindInstall) {
//                    return;
//                }

                switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    case INSTALL_REQUESTED:
                        installRequested = true;
                        return;
                    case INSTALLED:
                        break;
                }

                // Create the session.
                arSession = new Session(/* context= */ this);
                configureSession();
                renderUtil.setArSession(arSession);
            } catch (UnavailableArcoreNotInstalledException
                    | UnavailableUserDeclinedInstallationException e) {
                message = "Please install ARCore";
                exception = e;
            } catch (UnavailableApkTooOldException e) {
                message = "Please update ARCore";
                exception = e;
            } catch (UnavailableSdkTooOldException e) {
                message = "Please update this app";
                exception = e;
            } catch (UnavailableDeviceNotCompatibleException e) {
                message = "This device does not support AR";
                exception = e;
            } catch (Exception e) {
                message = "Failed to create AR session";
                exception = e;
            }

//            if (message != null) {
//                messageSnackbarHelper.showError(this, message);
//                Log.e(TAG, "Exception creating session", exception);
//                return;
//            }
        }

        // Note that order matters - see the note in onPause(), the reverse applies here.
        try {
            arSession.resume();
        }
            // To record a live camera session for later playback, call
            // `session.startRecording(recordingConfig)` at anytime. To playback a previously recorded AR
            // session instead of using the live camera feed, call
            // `session.setPlaybackDatasetUri(Uri)` before calling `session.resume()`. To
            // learn more about recording and playback, see:
            // https://developers.google.com/ar/develop/java/recording-and-playback
             catch(CameraNotAvailableException e){
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

//    private void requestedInstall() {
//        ArCoreApk.InstallStatus installStatus = ArCoreApk.getInstance().requestInstall(this, isRemindInstall);
//        switch (installStatus) {
//            case INSTALL_REQUESTED:
//                isRemindInstall = false;
//                break;
//            case INSTALLED:
//                break;
//        }
//    }

    public void onCaptureImage(View view) {
        Future<ImageProcessorInterface.ImageRaw> future = renderUtil.captureNextFrame();
        try {
            ImageProcessorInterface.ImageRaw raw = future.get();//replace
            imageModel.captureImage(this, raw);
            getSupportFragmentManager().beginTransaction()
                    .setReorderingAllowed(true)
                    .add(R.id.fragment_container_view, CaptureConfirmationFragment.class, null)
                    .commit();
        } catch (ExecutionException e) {
            Toast.makeText(this, R.string.captureError, Toast.LENGTH_LONG).show();
            e.printStackTrace();
        } catch (InterruptedException e) {
            Toast.makeText(this, R.string.captureInterrupted, Toast.LENGTH_LONG).show();
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
        View view = findViewById(R.id.deleteButton);
        view.setVisibility(View.VISIBLE);
        view.setEnabled(true);
    }

    private void disableDeleteButton() {
        View view = findViewById(R.id.deleteButton);

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

    /** Configures the session with feature settings. */
//    TODO: figure out what the correct depth settings are
    private void configureSession() {
        Config config = arSession.getConfig();
        config.setLightEstimationMode(Config.LightEstimationMode.ENVIRONMENTAL_HDR);

        if (arSession.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            config.setDepthMode(Config.DepthMode.AUTOMATIC);
        } else {
            config.setDepthMode(Config.DepthMode.DISABLED);
        }

        // Create a camera config filter for the session.
        CameraConfigFilter filter = new CameraConfigFilter(arSession);
        // Return only camera configs that will not use the depth sensor.
        filter.setDepthSensorUsage(EnumSet.of(CameraConfig.DepthSensorUsage.DO_NOT_USE));
        List<CameraConfig> cameraConfigList = arSession.getSupportedCameraConfigs(filter);

        // element 0 contains the camera config that best matches the specified filter
        // settings.
        arSession.setCameraConfig(cameraConfigList.get(0));
        arSession.configure(config);
    }
}
