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

package com.google.ar.core.examples.java.sharedcamera;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.SizeF;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.ar.core.Session;
import com.google.ar.core.SharedCamera;
import com.google.ar.core.examples.java.common.helpers.CameraPermissionHelper;
import com.google.ar.core.examples.java.common.helpers.StoragePermissionHelper;
import com.google.ar.core.examples.java.common.helpers.DisplayRotationHelper;
import com.google.ar.core.examples.java.common.rendering.BackgroundRenderer;
import com.google.ar.core.examples.java.common.rendering.OcclusionRenderer;
import com.google.ar.sceneform.ux.ArFragment;
import com.huawei.arengine.demos.java.world.rendering.RenderUtil;
import com.huawei.arengine.demos.java.world.rendering.common.DisplayRotationUtil;
import com.huawei.hiar.AREnginesApk;
import com.huawei.hiar.ARFrame;
import com.huawei.hiar.ARSession;
import com.huawei.hiar.ARWorldTrackingConfig;
import com.huawei.hiar.exceptions.ARCameraNotAvailableException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;


public class SharedCameraActivity extends AppCompatActivity {
    private static final String TAG = SharedCameraActivity.class.getSimpleName();

    // path for storing data
    private String fileSaveDir =
            android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/Tree";

    private static final String SAMPLE_NUM_ID = "sampleNum";
    private static final String DELETE_BUTTON_ID = "deleteButton";

    // Parameters for the ToF camera
//    private static final int TOF_ID = 4;
//    private static final int TOF_HEIGHT = 180;
//    private static final int TOF_WIDTH = 240;

    // Whether to save the next available image to a file
    static boolean CAPTURE_IMAGE = false;

    // AR session
    private ARSession mArSession = null;
    private boolean isRemindInstall = true;
    private RenderUtil mRenderUtil;
    private DisplayRotationUtil mDisplayRotationUtil;


    // Whether the surface texture has been attached to the GL context.
    boolean isGlAttached;

    private static final String LOG_TAG = "Connor";

    // GL Surface used to draw camera preview image.
    private GLSurfaceView surfaceView;
    private GLSurfaceView surfaceView2;

    // ARCore session that supports camera sharing.
    private Session sharedSession;

    // Camera capture session. Used by both non-AR and AR modes.
    private CameraCaptureSession captureSession;

    // A list of CaptureRequest keys that can cause delays when switching between AR and non-AR modes.
    private List<CaptureRequest.Key<?>> keysThatCanCauseCaptureDelaysWhenModified;

    // Camera device. Used by both non-AR and AR modes.
    private CameraDevice cameraDevice;

    // Looper handler thread.
    private HandlerThread backgroundThread;

    // Looper handler.
    private Handler backgroundHandler;

    // ARCore shared camera instance, obtained from ARCore session that supports sharing.
    private SharedCamera sharedCamera;

    // Camera ID for the camera used by ARCore.
    private String cameraId;

    // Ensure GL surface draws only occur when new frames are available.
    private final AtomicBoolean shouldUpdateSurfaceTexture = new AtomicBoolean(false);

    // Whether ARCore is currently active.
    private boolean arcoreActive;

    // Whether the GL surface has been created.
    private boolean surfaceCreated;

    // Camera preview capture request builder
    private CaptureRequest.Builder previewCaptureRequestBuilder;

//    // Image reader that continuously processes CPU images.
//    private ImageReader cpuImageReaderCurrent = null;
//    private ImageReader cpuImageReaderTOF = null;
//    private ImageReader cpuImageReaderRGB = null;

    // Various helper classes, see hello_ar_java sample to learn more.
    private DisplayRotationHelper displayRotationHelper;

    // Renderers, see hello_ar_java sample to learn more.
    private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
    private final OcclusionRenderer occlusionRenderer = new OcclusionRenderer();

    // Required for test run.
    private static final Short AUTOMATOR_DEFAULT = 0;
    private static final String AUTOMATOR_KEY = "automator";
    private final AtomicBoolean automatorRun = new AtomicBoolean(false);

    // Prevent any changes to camera capture session after CameraManager.openCamera() is called, but
    // before camera device becomes active.
    private boolean captureSessionChangesPossible = true;

    // App context, to be assigned in onCreate
    private Context context;

    // Preferences object and associated file
    SharedPreferences preferences;
    private static final String PREFERENCES = "stateInfo";
    private static final String SHARED_NUM_CAPTURES = "numCaptures";
    private static final String SHARED_CURRENT_SAMPLE = "currentSample";

    // A check mechanism to ensure that the camera closed properly so that the app can safely exit.
    private final ConditionVariable safeToExitApp = new ConditionVariable();

    // Globals to keep track of phone location. Rotation is continuously updated and position can
    // be re-initialized to (0,0,0) on button press. Note that rotation is a quaternion.
    private float[] rotation;
    private float[] position;
    private float[] velocity;
    private float[] acceleration;
    private boolean firstPosition = true;
    private long lastMeasure;
    private int accSign[];
    private int prevSign[];
    private int root[];

    //     Conversion factor NS to S
    static final float NS2S = 1.0f / 1000000000.0f;

    SensorEventListener eventListener = new SensorEventListener() {

        // Detect sensor events
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
                // Update the position of the phone from the current.
                updatePositionFromLinearAcceleration(event);
            } else if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                // Update the rotation of the phone from the current.
                updateRotationFromRotationVector(event);
            } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                updatePositionFromLinearAcceleration(event);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // TODO: deal with any accuracy changes in the sensor.
        }
    };

    private void updatePositionFromLinearAcceleration(SensorEvent event) {
        float[] acc = event.values;

        // Round the accelerations to 1 decimal places (qualitatively chosen to reduce noise)
        for (int i = 0; i < 3; i++) {
            acc[i] = Math.round(acc[i] * 10.0f) / 10.0f;
            accSign[i] = (int) Math.signum(acc[i]);
        }

        // Check for a sign change
        for (int i = 0; i < 3; i++) {
            if (accSign[i] == -1 && prevSign[i] == 1) {
                root[i] = -1;
            } else if (accSign[i] == 1 && prevSign[i] == -1) {
                root[i] = 1;
            }
        }

        // If there has been a sign change from positive to negative, and we are at a point with 0 acceleration, set
        // velocity component to 0.
        for (int i = 0; i < 3; i++) {
            if (acc[i] == 0) {
                velocity[i] = 0.0f;
            }
        }

        // If this is not the first position measurement
        if (!firstPosition) {

            float dt = (event.timestamp - lastMeasure) * NS2S;

            // Update the position if the acceleration for this component reaches the threshold
            for (int i = 0; i < 3; i++) {
                velocity[i] += ((acc[i] + acceleration[i]) / 2) * dt;
//                velocity[i] += ((acc[i]) / 2) * dt;
                position[i] += velocity[i] * dt;
            }
        } else {
            firstPosition = false;
        }

        // Shallow copy acceleration to global variable
        System.arraycopy(acc, 0, acceleration, 0, 3);

        // Copy the sign of the current acceleration
        System.arraycopy(accSign, 0, prevSign, 0, 3);

        // Update the time of last update
        lastMeasure = event.timestamp;

    }

    private void updateRotationFromRotationVector(SensorEvent event) {
        rotation = event.values;
//        Log.i(LOG_TAG, "Rotation update!");
//        Log.i(LOG_TAG, Arrays.toString(rotation));
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
//        Log.i(LOG_TAG, "Sensor accuracy is " + accuracy);
    }

    // Helpers for shared memory
    private int getSharedPreferencesVar(String name) {
        return preferences.getInt(name, Integer.MIN_VALUE);
    }

    private void setSharedPreferencesVar(String name, int val) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(name, val);
        editor.commit();
    }

    // Camera device state callback.
//    private final CameraDevice.StateCallback cameraDeviceCallback =
//            new CameraDevice.StateCallback() {
//                @Override
//                public void onOpened(CameraDevice cameraDevice) {
//                    Log.d(TAG, "Camera device ID " + cameraDevice.getId() + " opened.");
//                    SharedCameraActivity.this.cameraDevice = cameraDevice;
//                    createCameraPreviewSession();
//                }
//
//                @Override
//                public void onClosed(CameraDevice cameraDevice) {
//                    Log.d(TAG, "Camera device ID " + cameraDevice.getId() + " closed.");
//                    SharedCameraActivity.this.cameraDevice = null;
//                    safeToExitApp.open();
//                }
//
//                @Override
//                public void onDisconnected(CameraDevice cameraDevice) {
//                    Log.w(TAG, "Camera device ID " + cameraDevice.getId() + " disconnected.");
//                    cameraDevice.close();
//                    SharedCameraActivity.this.cameraDevice = null;
//                }
//
//                @Override
//                public void onError(CameraDevice cameraDevice, int error) {
//                    Log.e(TAG, "Camera device ID " + cameraDevice.getId() + " error " + error);
//                    cameraDevice.close();
//                    SharedCameraActivity.this.cameraDevice = null;
//                    // Fatal error. Quit application.
//                    finish();
//                }
//            };

    // Repeating camera capture session state callback.
//    CameraCaptureSession.StateCallback cameraCaptureCallback =
//            new CameraCaptureSession.StateCallback() {
//
//                // Called when the camera capture session is first configured after the app
//                // is initialized, and again each time the activity is resumed.
//                @Override
//                public void onConfigured(CameraCaptureSession session) {
//                    Log.d(TAG, "Camera capture session configured.");
//                    captureSession = session;
//                    resumeCamera2();
//                }
//
//                @Override
//                public void onSurfacePrepared(
//                        CameraCaptureSession session, Surface surface) {
//                    Log.d(TAG, "Camera capture surface prepared.");
//                }
//
//                @Override
//                public void onReady(CameraCaptureSession session) {
//                    Log.d(TAG, "Camera capture session ready.");
//                }
//
//                @Override
//                public void onActive(CameraCaptureSession session) {
//                    Log.d(TAG, "Camera capture session active.");
//                    synchronized (SharedCameraActivity.this) {
//                        captureSessionChangesPossible = true;
//                        SharedCameraActivity.this.notify();
//                    }
//                }
//
//                @Override
//                public void onCaptureQueueEmpty(CameraCaptureSession session) {
//                    Log.w(TAG, "Camera capture queue empty.");
//                }
//
//                @Override
//                public void onClosed(CameraCaptureSession session) {
//                    Log.d(TAG, "Camera capture session closed.");
//                }
//
//                @Override
//                public void onConfigureFailed(CameraCaptureSession session) {
//                    Log.e(TAG, "Failed to configure camera capture session.");
//                }
//            };

    // Repeating camera capture session capture callback.
//    private final CameraCaptureSession.CaptureCallback captureSessionCallback =
//            new CameraCaptureSession.CaptureCallback() {
//
//                @Override
//                public void onCaptureCompleted(
//                        CameraCaptureSession session,
//                        CaptureRequest request,
//                        TotalCaptureResult result) {
//                    shouldUpdateSurfaceTexture.set(true);
//                }
//
//                @Override
//                public void onCaptureBufferLost(
//                        CameraCaptureSession session,
//                        CaptureRequest request,
//                        Surface target,
//                        long frameNumber) {
//                    Log.e(TAG, "onCaptureBufferLost: " + frameNumber);
//                }
//
//                @Override
//                public void onCaptureFailed(
//                        CameraCaptureSession session,
//                        CaptureRequest request,
//                        CaptureFailure failure) {
//                    Log.e(TAG, "onCaptureFailed: " + failure.getFrameNumber() + " " + failure.getReason());
//                }
//
//                @Override
//                public void onCaptureSequenceAborted(
//                        CameraCaptureSession session, int sequenceId) {
//                    Log.e(TAG, "onCaptureSequenceAborted: " + sequenceId + " " + session);
//                }
//            };

    // Classes for managing the sensors
    private SensorManager sensorManager;
    // Acceleration units m/s^2
    private Sensor linearAccelerationSensor;
    // Rotation sensor
    private Sensor rotationSensor;

    // Threads and handlers for sensor listeners
    HandlerThread accelerationThread;
    Handler accelerationHandler;
    HandlerThread rotationThread;
    Handler rotationHandler;

    ArFragment arFragment;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Bundle extraBundle = getIntent().getExtras();
        if (extraBundle != null && 1 == extraBundle.getShort(AUTOMATOR_KEY, AUTOMATOR_DEFAULT)) {
            automatorRun.set(true);
        }

        // set render
        mDisplayRotationUtil = new DisplayRotationUtil(this);
        mRenderUtil = new RenderUtil(this, this);
        mRenderUtil.setDisplayRotationUtil(mDisplayRotationUtil);

        // GL surface view that renders camera preview image.
        surfaceView = findViewById(R.id.glsurfaceview);
        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setEGLContextClientVersion(2);
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        surfaceView.setRenderer(mRenderUtil);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        // Helpers, see hello_ar_java sample to learn more.
        displayRotationHelper = new DisplayRotationHelper(this);

        // Initialize position and velocity to (0,0,0)
        position = new float[] {0, 0, 0};
        velocity = new float[] {0, 0, 0};
        acceleration = new float[] {0, 0, 0};
        firstPosition = true;
        accSign = new int[] {1, 1, 1};
        prevSign = new int[] {1, 1, 1};
        root = new int[] {0, 0, 0};

        // Create threads and handlers for sensors
        accelerationThread = new HandlerThread("accelerationThread");
        accelerationThread.start();
        accelerationHandler = new Handler(accelerationThread.getLooper());

        rotationThread = new HandlerThread("rotationThread");
        rotationThread.start();
        rotationHandler = new Handler(rotationThread.getLooper());

        // Set the linear acceleration sensor.
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        linearAccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        sensorManager.registerListener(eventListener, linearAccelerationSensor, SensorManager.SENSOR_DELAY_NORMAL, accelerationHandler);

        // Set the rotation vector sensor.
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        sensorManager.registerListener(eventListener, rotationSensor, SensorManager.SENSOR_DELAY_NORMAL, accelerationHandler);


        // TODO: Get the gyroscope
        context = getApplicationContext();
        preferences = context.getSharedPreferences(PREFERENCES, context.MODE_PRIVATE);


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

    private synchronized void waitUntilCameraCaptureSesssionIsActive() {
        while (!captureSessionChangesPossible) {
            try {
                this.wait();
            } catch (InterruptedException e) {
                Log.e(TAG, "Unable to wait for a safe time to make changes to the capture session", e);
            }
        }
    }

    // boolean initialized = false;

    private void requestedInstall() {
        AREnginesApk.ARInstallStatus installStatus = AREnginesApk.requestInstall(this, isRemindInstall);
        switch(installStatus) {
            case INSTALL_REQUESTED:
                isRemindInstall = false;
                break;
            case INSTALLED:
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        waitUntilCameraCaptureSesssionIsActive();
        startBackgroundThread();
        surfaceView.onResume();

        // When the activity starts and resumes for the first time, openCamera() will be called
        // from onSurfaceCreated(). In subsequent resumes we call openCamera() here.
//        if (surfaceCreated) {
//            if (initialized)
//                System.exit(0);
//            openCamera();
//        }

        Exception exception = null;
        if (mArSession == null) {
            try {
                // Request to install arengine server. If it is already installed or
                // the user chooses to install it, it will work normally. Otherwise, set isRemindInstall to false.
                requestedInstall();

                // If the user rejects the installation, isRemindInstall is false.
                if (!isRemindInstall) {
                    return;
                }
                mArSession = new ARSession(this);
                ARWorldTrackingConfig config = new ARWorldTrackingConfig(mArSession);

                int supportedSemanticMode = mArSession.getSupportedSemanticMode();
                Log.d(TAG, "supportedSemanticMode:" + supportedSemanticMode);
                if (supportedSemanticMode != ARWorldTrackingConfig.SEMANTIC_NONE) {
                    Log.d(TAG, "supported mode:" + supportedSemanticMode);
                    config.setSemanticMode(supportedSemanticMode);
                }
                mArSession.configure(config);

                mRenderUtil.setArSession(mArSession);
            } catch (Exception capturedException) {
                Log.e(TAG, "Unable to create AR session", capturedException);
            }
        }
        try {
            mArSession.resume();
        } catch (ARCameraNotAvailableException e) {
            Toast.makeText(this, "Camera open failed, please restart the app", Toast.LENGTH_LONG).show();
            mArSession = null;
            return;
        }
        mDisplayRotationUtil.registerDisplayListener();

        displayRotationHelper.onResume();
    }

    @Override
    public void onPause() {
        surfaceView.onPause();
        waitUntilCameraCaptureSesssionIsActive();
        displayRotationHelper.onPause();
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

//    private void resumeCamera2() {
//        setRepeatingCaptureRequest();
//        sharedCamera.getSurfaceTexture().setOnFrameAvailableListener(this);
//    }

    // Called when starting non-AR mode or switching to non-AR mode.
    // Also called when app starts in AR mode, or resumes in AR mode.
//    private void setRepeatingCaptureRequest() {
//        try {
//            captureSession.setRepeatingRequest(
//                    previewCaptureRequestBuilder.build(), captureSessionCallback, backgroundHandler);
//        } catch (CameraAccessException e) {
//            Log.e(TAG, "Failed to set repeating request", e);
//        }
//    }


//    private void createCameraPreviewSession() {
//        try {
//            // Note that isGlAttached will be set to true in AR mode in onDrawFrame().
//            sharedSession.setCameraTextureName(backgroundRenderer.getTextureId());
//            sharedCamera.getSurfaceTexture().setOnFrameAvailableListener(this);
//
//            // Create an ARCore compatible capture request using `TEMPLATE_RECORD`.
//            previewCaptureRequestBuilder =
//                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
//
//            // Build surfaces list, starting with ARCore provided surfaces.
//            List<Surface> surfaceList = sharedCamera.getArCoreSurfaces();
//
//            // Add a CPU image reader surface. On devices that don't support CPU image access, the image
//            // may arrive significantly later, or not arrive at all.
//            if(cpuImageReaderCurrent!= null){
//                surfaceList.add(cpuImageReaderCurrent.getSurface());
//            }
//
//            // Surface list should now contain three surfaces:
//            // 0. sharedCamera.getSurfaceTexture()
//            // 1. …
//            // 2. cpuImageReaderCurrent.getSurface()
//
//            // Add ARCore surfaces and CPU image surface targets.
//            for (Surface surface : surfaceList) {
//                previewCaptureRequestBuilder.addTarget(surface);
//            }
//
//            // Wrap our callback in a shared camera callback.
//            CameraCaptureSession.StateCallback wrappedCallback =
//                    sharedCamera.createARSessionStateCallback(cameraCaptureCallback, backgroundHandler);
//
//            // Create camera capture session for camera preview using ARCore wrapped callback.
//            cameraDevice.createCaptureSession(surfaceList, wrappedCallback, backgroundHandler);
//        } catch (CameraAccessException e) {
//            Log.e(TAG, "CameraAccessException", e);
//        }
//    }


    // Start background handler thread, used to run callbacks without blocking UI thread.
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("sharedCameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    // Stop background handler thread.
    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while trying to join background handler thread", e);
            }
        }
    }

    // Function to return pixel array physical size for a given cameraID
    private SizeF getCameraResolution(String cameraId, CameraManager cameraManager) {
        SizeF size = new SizeF(0,0);
        try {
            CameraCharacteristics character = cameraManager.getCameraCharacteristics(cameraId);
            size = character.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
        } catch (CameraAccessException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
        }
        return size;
    }

    // Function to return TOF camera intrinsic parameters
    private float[] getIntrinsicParams(String cameraId, CameraManager cameraManager) {
        float[] params = new float[] {-1, -1, -1, -1, -1};
        try {
            CameraCharacteristics character = cameraManager.getCameraCharacteristics(cameraId);
            params = character.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION);
        } catch (CameraAccessException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
        }
        return params;
    }


//    // initialize TOF cpu image reader or switch between TOF and RGB cpu image reader
//    private void updateCpuImageReader(){
//        if(cpuImageReaderRGB == null){
//            cpuImageReaderRGB = ImageReader.newInstance(
//                    occlusionRenderer.getDepthWidth(),
//                    occlusionRenderer.getDepthHeight(),
//                    ImageFormat.JPEG,
//                    2);
//            Log.i(LOG_TAG, "updateCpuImageReader: create JPEG CPU image reader");
//        }
//
//        if(cpuImageReaderTOF == null){
//            cpuImageReaderTOF = ImageReader.newInstance(
//                    occlusionRenderer.getDepthWidth(),
//                    occlusionRenderer.getDepthHeight(),
//                    ImageFormat.DEPTH16,
//                    2);
//            Log.i(LOG_TAG, "updateCpuImageReader: create DEPTH16 CPU image reader");
//        }
//
//        if (cpuImageReaderCurrent == null){
//            cpuImageReaderCurrent = cpuImageReaderTOF;
//        }
//        else if(cpuImageReaderCurrent.getImageFormat() == ImageFormat.JPEG){
//            cpuImageReaderCurrent.setOnImageAvailableListener(null, null);
//            // switch to a new CPU image reader that accepts TOF image
//            cpuImageReaderCurrent = cpuImageReaderTOF;
//            Log.i(LOG_TAG, "updateCpuImageReader: update to DEPTH16 CPU image reader");
//        }
//        else if(cpuImageReaderCurrent.getImageFormat() == ImageFormat.DEPTH16){
//            cpuImageReaderCurrent.setOnImageAvailableListener(null, null);
//            // switch to a new CPU image reader that accepts RGB image
//            cpuImageReaderCurrent = cpuImageReaderRGB;
//            Log.i(LOG_TAG, "updateCpuImageReader: update to JPEG CPU image reader");
//        }
//
//        cpuImageReaderCurrent.setOnImageAvailableListener(this, backgroundHandler);
//        sharedCamera.setAppSurfaces(this.cameraId, Arrays.asList(cpuImageReaderCurrent.getSurface()));
//    }


//    // Perform various checks, then open camera device and create CPU image reader.
//    private void openCameraBoth() {
//        Log.i(LOG_TAG, "In openCameraBoth cameraId: "+cameraId);
//
//        updateCpuImageReader();
//
//        try {
//            // Wrap our callback in a shared camera callback.
//            CameraDevice.StateCallback wrappedCallback =
//                    sharedCamera.createARDeviceStateCallback(cameraDeviceCallback, backgroundHandler);
//
//            // Store a reference to the camera system service.
//            // Reference to the camera system service.
//            CameraManager cameraManager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
//
//            // Get the characteristics for the ARCore camera.
//            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(this.cameraId);
//
//            // Log the camera physical size (units = mm)
//            SizeF cameraSize = this.getCameraResolution("0", cameraManager);
//            Log.i(LOG_TAG, "Size of camera 0 " + cameraSize.toString());
//            int[] capabilities = cameraManager.getCameraCharacteristics("0").get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
//            Log.i(LOG_TAG, "Capabilities of camera 0 " + Arrays.toString(capabilities));
//            float[] translation = cameraManager.getCameraCharacteristics("0").get(CameraCharacteristics.LENS_POSE_TRANSLATION);
//            Log.i(LOG_TAG, "translation of camera 0 " + Arrays.toString(translation));
//            boolean isExlusive = cameraManager.getCameraCharacteristics("0").get(CameraCharacteristics.DEPTH_DEPTH_IS_EXCLUSIVE);
//            Log.i(LOG_TAG, "isexlusive of camera 0 " + Boolean.toString(isExlusive));
//
//            for (int i = 1; i <= 4; i++) {
//                Log.i(LOG_TAG, "Intrinsic params for cameraId = " + Integer.toString(i) + Arrays.toString(this.getIntrinsicParams(Integer.toString(i), cameraManager)));
//            }
//            Log.i(LOG_TAG, "Physical camera size for cameraId = " + Integer.toString(TOF_ID) +  " (mm) = " + cameraSize.toString());
//
//            // On Android P and later, get list of keys that are difficult to apply per-frame and can
//            // result in unexpected delays when modified during the capture session lifetime.
//            if (Build.VERSION.SDK_INT >= 28) {
//                keysThatCanCauseCaptureDelaysWhenModified = characteristics.getAvailableSessionKeys();
//                if (keysThatCanCauseCaptureDelaysWhenModified == null) {
//                    // Initialize the list to an empty list if getAvailableSessionKeys() returns null.
//                    keysThatCanCauseCaptureDelaysWhenModified = new ArrayList<>();
//                }
//            }
//
//            // Prevent app crashes due to quick operations on camera open / close by waiting for the
//            // capture session's onActive() callback to be triggered.
//            captureSessionChangesPossible = false;
//
//            // Open the camera device using the ARCore wrapped callback.
//            cameraManager.openCamera(cameraId, wrappedCallback, backgroundHandler);
//        } catch (CameraAccessException | IllegalArgumentException | SecurityException e) {
//            Log.e(TAG, "Failed to open camera", e);
//        }
//    }


    private <T> boolean checkIfKeyCanCauseDelay(CaptureRequest.Key<T> key) {
        if (Build.VERSION.SDK_INT >= 28) {
            // On Android P and later, return true if key is difficult to apply per-frame.
            return keysThatCanCauseCaptureDelaysWhenModified.contains(key);
        } else {
            // On earlier Android versions, log a warning since there is no API to determine whether
            // the key is difficult to apply per-frame. Certain keys such as CONTROL_AE_TARGET_FPS_RANGE
            // are known to cause a noticeable delay on certain devices.
            // If avoiding unexpected capture delays when switching between non-AR and AR modes is
            // important, verify the runtime behavior on each pre-Android P device on which the app will
            // be distributed. Note that this device-specific runtime behavior may change when the
            // device's operating system is updated.
            Log.w(
                    TAG,
                    "Changing "
                            + key
                            + " may cause a noticeable capture delay. Please verify actual runtime behavior on"
                            + " specific pre-Android P devices that this app will be distributed on.");
            // Allow the change since we're unable to determine whether it can cause unexpected delays.
            return false;
        }
    }


    // Close the camera device.
    private void closeCamera() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            waitUntilCameraCaptureSesssionIsActive();
            safeToExitApp.close();
            cameraDevice.close();
            safeToExitApp.block();
        }
//        if (cpuImageReaderRGB != null) {
//            cpuImageReaderRGB.close();
//            cpuImageReaderRGB = null;
//        }
//        if (cpuImageReaderTOF != null) {
//            cpuImageReaderTOF.close();
//            cpuImageReaderTOF = null;
//        }
        if (mArSession != null) {
            mDisplayRotationUtil.unregisterDisplayListener();
            mArSession.pause();
        }
        // cpuImageReaderCurrent = null;
    }

//    // Surface texture on frame available callback, used only in non-AR mode.
//    @Override
//    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
////         Log.d(TAG, "onFrameAvailable()");
//    }


//    // CPU image reader callback.
//    @Override
//    public void onImageAvailable(ImageReader imageReader) {
//        // If the current image reader is accepting TOF images
//        if(imageReader.getImageFormat() == ImageFormat.DEPTH16){
//
//            Image image = imageReader.acquireLatestImage();
//            if (image == null) {
//                Log.w(TAG, "onImageAvailable: Skipping null image.");
//                return;
//            }
//
//            // Buffers for storing TOF output
//            ArrayList<Short> xBuffer = new ArrayList<>();
//            ArrayList<Short> yBuffer = new ArrayList<>();
//            ArrayList<Float> dBuffer = new ArrayList<>();
//            ArrayList<Float> percentageBuffer = new ArrayList<>();
//
//            Image.Plane plane = image.getPlanes()[0];
//            ShortBuffer shortDepthBuffer = plane.getBuffer().asShortBuffer();
//            ArrayList<Short> pixel = new ArrayList<>();
//            while (shortDepthBuffer.hasRemaining()) {
//                pixel.add(shortDepthBuffer.get());
//            }
//            int stride = plane.getRowStride();
//
//            int offset = 0;
//            float sum = 0.0f;
//            float[] output = new float[image.getWidth() * image.getHeight()];
//            for (short y = 0; y < image.getHeight(); y++) {
//                for (short x = 0; x < image.getWidth(); x++) {
//                    // Parse the data. Format is [depth|confidence]
//                    short depthSample = pixel.get((int) (y / 2) * stride + x);
//                    short depthRange = (short) (depthSample & 0x1FFF);
//                    short depthConfidence = (short) ((depthSample >> 13) & 0x7);
//                    float depthPercentage = depthConfidence == 0 ? 1.f : (depthConfidence - 1) / 7.f;
//
//                    output[offset + x] = (float)depthRange/10000;
//
//                    sum += output[offset+x];
//                    // Store data in buffer
//                    xBuffer.add(x);
//                    yBuffer.add(y);
//                    dBuffer.add((float)depthRange / 1000.0f);
//                    percentageBuffer.add(depthPercentage);
//                }
//                offset += image.getWidth();
//            }
//
////        Log.i(LOG_TAG, "Average depth = " + Float.toString(sum / (image.getHeight()*image.getWidth())));
//            image.close();
//
//            occlusionRenderer.update(output);
//
//            if (CAPTURE_IMAGE) {
//                try {
//                    saveToFileTOF(xBuffer, yBuffer, dBuffer, percentageBuffer);
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//
//                // switch cpu image reader and start with a new session
//                updateCpuImageReader();
//                createCameraPreviewSession();
//            }
//        }
//        else if(imageReader.getImageFormat() == ImageFormat.JPEG){
//            Image image = imageReader.acquireLatestImage();
//            if (image == null) {
//                Log.w(TAG, "onImageAvailable: Skipping null image.");
//                return;
//            }
//
//            if (CAPTURE_IMAGE) {
//                try {
//                    saveToFileRGB(image);
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//                CAPTURE_IMAGE = false;
//
//                // switch cpu image reader and start with a new session
//                updateCpuImageReader();
//                createCameraPreviewSession();
//            }
//            image.close();
//        }
//
//    }


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

//    // GL surface created callback. Will be called on the GL thread.
//    @Override
//    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
//        surfaceCreated = true;
//
//        // Set GL clear color to black.
//        GLES20.glClearColor(0f, 0f, 0.5f, 1.0f);
//
//        // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
//        try {
//            // Create the camera preview image texture. Used in non-AR and AR mode.
//            backgroundRenderer.createOnGlThread(this);
//            occlusionRenderer.createOnGlThread(this);
//
//            openCamera();
//        } catch (IOException e) {
//            Log.e(TAG, "Failed to read an asset file", e);
//        }
//    }

//    private void openCamera() {
//        // Don't open camera if already opened.
//        if (cameraDevice != null) {
//            return;
//        }
//
//        // Verify CAMERA_PERMISSION has been granted.
//        if (!CameraPermissionHelper.hasCameraPermission(this)) {
//            CameraPermissionHelper.requestCameraPermission(this);
//            return;
//        }
//
//        // Make sure that ARCore is installed, up to date, and supported on this device.
//        if (!isARCoreSupportedAndUpToDate()) {
//            return;
//        }
//
//        if (sharedSession == null) {
//            try {
//                // Create ARCore session that supports camera sharing.
//                sharedSession = new Session(this, EnumSet.of(Session.Feature.SHARED_CAMERA));
//                Log.i(LOG_TAG, Session.Feature.SHARED_CAMERA.toString());
//            } catch (UnavailableException e) {
//                Log.e(TAG, "Failed to create ARCore session that supports camera sharing", e);
//                return;
//            }
//
//            // Enable auto focus mode while ARCore is running.
//            Config config = sharedSession.getConfig();
//            config.setFocusMode(Config.FocusMode.AUTO);
//
//            // Enable non-blocking node on update call
//            config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
//            sharedSession.configure(config);
//        }
//
//        // Store the ARCore shared camera reference.
//        sharedCamera = sharedSession.getSharedCamera();
//
//        // Store the ID of the camera used by ARCore.
//        cameraId = sharedSession.getCameraConfig().getCameraId();
//
//        Log.i("Connor cam id", cameraId);
//
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    Thread.sleep(1000);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//                runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//
//                        AlertDialog.Builder builder = new AlertDialog.Builder(SharedCameraActivity.this);
//
//                        // Finds the resolutions for the ToF camera.
//                        String[] res = occlusionRenderer.getResolutions(SharedCameraActivity.this, cameraId).toArray(new String[0]);
//
//                        Log.i(LOG_TAG, Arrays.toString(res));
//
//
//                        if (res.length > 0) {
//                            // TODO: based on used cameras
//                            occlusionRenderer.setDepthWidth(TOF_WIDTH);
//                            occlusionRenderer.setDepthHeight(TOF_HEIGHT);
//
//                            // open the camera
//                            openCameraBoth();
//
//                            // Indicate that the camera is ready
//                            initialized = true;
//                        } else {
//                            builder.setTitle("Camera2 API: ToF not found");
//                            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
//                                @Override
//                                public void onClick(DialogInterface dialog, int which) {
//                                    System.exit(0);
//                                }
//                            });
//                        }
//
//                        AlertDialog dialog = builder.create();
//                        dialog.show();
//                    }
//                });
//            }
//        }).start();
//
//    }

//    // GL surface changed callback. Will be called on the GL thread.
//    @Override
//    public void onSurfaceChanged(GL10 gl, int width, int height) {
//        GLES20.glViewport(0, 0, width, height);
//        displayRotationHelper.onSurfaceChanged(width, height);
//    }

//    // GL draw callback. Will be called each frame on the GL thread.
//    @Override
//    public void onDrawFrame(GL10 gl) {
//        // Use the cGL clear color specified in onSurfaceCreated() to erase the GL surface.
//        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
//
//        if (!shouldUpdateSurfaceTexture.get()) {
//            // Not ready to draw.
//            return;
//        }
//
//        // Handle display rotations.
//        displayRotationHelper.updateSessionIfNeeded(sharedSession);
//
//        try {
//            onDrawFrameCamera2();
//        } catch (Throwable t) {
//            // Avoid crashing the application due to unhandled exceptions.
//            Log.e(TAG, "Exception on the OpenGL thread", t);
//        }
//    }


    // onCaptureImage sets captureImage to true, so that next time onImageAvailable is called,
    // the image will be saved to a file.
    public void onCaptureImage(View view) {
        Log.i(LOG_TAG, "Capturing an image");

        // Verify STORAGE_PERMISSION has been granted.
        if (!StoragePermissionHelper.hasStoragePermission(this)) {
            StoragePermissionHelper.requestStoragePermission(this);
            Log.i(LOG_TAG, "We don't have storage permission!");
            return;
        }

        CAPTURE_IMAGE = true;
    }


    public void captureImage(ARFrame arFrame) {
        if(!CAPTURE_IMAGE || arFrame == null){
            return;
        }

        Image imgRGB = arFrame.acquireCameraImage();
        Image imgTOF = arFrame.acquireDepthImage();

        if(imgTOF != null){
            // Buffers for storing TOF output
            ArrayList<Short> xBuffer = new ArrayList<>();
            ArrayList<Short> yBuffer = new ArrayList<>();
            ArrayList<Float> dBuffer = new ArrayList<>();
            ArrayList<Float> percentageBuffer = new ArrayList<>();

            Image.Plane plane = imgTOF.getPlanes()[0];
            ShortBuffer shortDepthBuffer = plane.getBuffer().asShortBuffer();
            ArrayList<Short> pixel = new ArrayList<>();
            while (shortDepthBuffer.hasRemaining()) {
                pixel.add(shortDepthBuffer.get());
            }
            int stride = plane.getRowStride();

            int offset = 0;
            float sum = 0.0f;
            float[] output = new float[imgTOF.getWidth() * imgTOF.getHeight()];
            for (short y = 0; y < imgTOF.getHeight(); y++) {
                for (short x = 0; x < imgTOF.getWidth(); x++) {
                    // Parse the data. Format is [depth|confidence]
                    short depthSample = pixel.get((int) (y / 2) * stride + x);
                    short depthRange = (short) (depthSample & 0x1FFF);
                    short depthConfidence = (short) ((depthSample >> 13) & 0x7);
                    float depthPercentage = depthConfidence == 0 ? 1.f : (depthConfidence - 1) / 7.f;

                    output[offset + x] = (float)depthRange/10000;

                    sum += output[offset+x];
                    // Store data in buffer
                    xBuffer.add(x);
                    yBuffer.add(y);
                    dBuffer.add((float)depthRange / 1000.0f);
                    percentageBuffer.add(depthPercentage);
                }
                offset += imgTOF.getWidth();
            }

            imgTOF.close();

            try {
                saveToFileTOF(xBuffer, yBuffer, dBuffer, percentageBuffer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if(imgRGB != null){
            try {
                saveToFileRGB(imgRGB);
            } catch (IOException e) {
                e.printStackTrace();
            }

            imgRGB.close();
        }

        CAPTURE_IMAGE = false;
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
            Log.i(LOG_TAG , "Enabling delete");
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

    // Method for deleting data
    public void onDeleteData(View view) {
        // File object for the directory where the data is saved.
        File savedDir = new File(this.fileSaveDir + "/samples");
        if(!savedDir.exists()){
            return;
        }

        // Clean up the directory
        for (File file : savedDir.listFiles()) {
            file.delete();
        }
    }

    public void saveToFileTOF(ArrayList<Short> xBuffer, ArrayList<Short> yBuffer,
                              ArrayList<Float> dBuffer, ArrayList<Float> percentageBuffer) throws IOException {

        // Open the output file for this sample
        // As recommended by:
        // https://stackoverflow.com/questions/44587187/android-how-to-write-a-file-to-internal-storage
        int currentSample = this.getSharedPreferencesVar(SHARED_CURRENT_SAMPLE);
        int numCaptures = this.getSharedPreferencesVar(SHARED_NUM_CAPTURES);
        String sampleFName = "Capture_Sample_" + currentSample + "_" + (numCaptures);

        // Write the TOF data currently in buffers to an output file.
        Log.i(LOG_TAG, "Writing to the file");

        File dir = new File(this.fileSaveDir, "/samples");
        Log.i(LOG_TAG, dir.getAbsolutePath());

        File outFile = new File(dir, sampleFName);
        if(!outFile.getParentFile().exists()) {
            outFile.getParentFile().mkdirs();
        }

        // Update the shared preferences for the number of captures
        this.setSharedPreferencesVar(SHARED_NUM_CAPTURES, numCaptures);

        // Write to the output file
        try (FileWriter writer = new FileWriter(outFile)) {
            StringBuilder str = new StringBuilder();

            for (int i = 0; i < dBuffer.size(); i++) {
                str.append(xBuffer.get(i));
                str.append(',');
                str.append(yBuffer.get(i));
                str.append(',');
                str.append(dBuffer.get(i));
                str.append(',');
                str.append(percentageBuffer.get(i));
                str.append('\n');
            }
            writer.write(str.toString());
            writer.flush();
            Log.i(LOG_TAG, "Successfully wrote the file " + sampleFName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

//    public void saveToFileRGB(Image image) throws IOException {
//
//        int currentSample = this.getSharedPreferencesVar(SHARED_CURRENT_SAMPLE);
//        int numCaptures = this.getSharedPreferencesVar(SHARED_NUM_CAPTURES);
//        String sampleFName = "Capture_Sample_" + currentSample + "_" + (numCaptures++) + ".jpeg";
//
//        // Write the TOF data currently in buffers to an output file.
//        Log.i(LOG_TAG, "Writing to the file");
//
//        File dir = new File(this.fileSaveDir, "/samples");
//        Log.i(LOG_TAG, dir.getAbsolutePath());
//
//        File outFile = new File(dir, sampleFName);
//        if(!outFile.getParentFile().exists()) {
//            outFile.getParentFile().mkdirs();
//        }
//
//        // Update the shared preferences for the number of captures
//        this.setSharedPreferencesVar(SHARED_NUM_CAPTURES, numCaptures);
//
//        // Write to the output file
//        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
//        byte[] imageBytes = new byte[buffer.remaining()];
//        buffer.get(imageBytes);
//        Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
//        try {
//            FileOutputStream out = new FileOutputStream(outFile);
//            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
//            out.flush();
//            out.close();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//
//    }

    public void saveToFileRGB(Image image) throws IOException {

        int currentSample = this.getSharedPreferencesVar(SHARED_CURRENT_SAMPLE);
        int numCaptures = this.getSharedPreferencesVar(SHARED_NUM_CAPTURES);
        String sampleFName = "Capture_Sample_" + currentSample + "_" + (numCaptures++) + ".jpeg";

        // Write the TOF data currently in buffers to an output file.
        Log.i(LOG_TAG, "Writing to the file");

        File dir = new File(this.fileSaveDir, "/samples");
        Log.i(LOG_TAG, dir.getAbsolutePath());

        File outFile = new File(dir, sampleFName);
        if(!outFile.getParentFile().exists()) {
            outFile.getParentFile().mkdirs();
        }

        // Update the shared preferences for the number of captures
        this.setSharedPreferencesVar(SHARED_NUM_CAPTURES, numCaptures);

        byte[] imageBytes = ImageUtil.imageToByteArray(image);

        Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        try {
            FileOutputStream out = new FileOutputStream(outFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    // Draw frame when in non-AR mode. Called on the GL thread.
//    public void onDrawFrameCamera2() {
//        SurfaceTexture texture = sharedCamera.getSurfaceTexture();
//
//        // Ensure the surface is attached to the GL context.
//        if (!isGlAttached) {
//            texture.attachToGLContext(backgroundRenderer.getTextureId());
//            isGlAttached = true;
//        }
//
//        List<Surface> surfaces  = sharedCamera.getArCoreSurfaces();
//
//        // Update the surface.
//        texture.updateTexImage();
//
//        // Account for any difference between camera sensor orientation and display orientation.
//        int rotationDegrees = displayRotationHelper.getCameraSensorToDisplayRotation(cameraId);
//
//        // Determine size of the camera preview image.
//        Size size = sharedSession.getCameraConfig().getTextureSize();
//
//        // Determine aspect ratio of the output GL surface, accounting for the current display rotation
//        // relative to the camera sensor orientation of the device.
//        float displayAspectRatio =
//                displayRotationHelper.getCameraSensorRelativeViewportAspectRatio(cameraId);
//
//        // Render camera preview image to the GL surface.
//        //backgroundRenderer.draw(size.getWidth(), size.getHeight(), displayAspectRatio, rotationDegrees);
//        occlusionRenderer.draw(true);
//    }
//
//    private boolean isARCoreSupportedAndUpToDate() {
//        // Make sure ARCore is installed and supported on this device.
//        ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(this);
//        switch (availability) {
//            case SUPPORTED_INSTALLED:
//                break;
//            case SUPPORTED_APK_TOO_OLD:
//            case SUPPORTED_NOT_INSTALLED:
//                try {
//                    // Request ARCore installation or update if needed.
//                    ArCoreApk.InstallStatus installStatus =
//                            ArCoreApk.getInstance().requestInstall(this, /*userRequestedInstall=*/ true);
//                    switch (installStatus) {
//                        case INSTALL_REQUESTED:
//                            Log.e(TAG, "ARCore installation requested.");
//                            return false;
//                        case INSTALLED:
//                            break;
//                    }
//                } catch (UnavailableException e) {
//                    Log.e(TAG, "ARCore not installed", e);
//                    runOnUiThread(
//                            () ->
//                                    Toast.makeText(
//                                            getApplicationContext(), "ARCore not installed\n" + e, Toast.LENGTH_LONG)
//                                            .show());
//                    finish();
//                    return false;
//                }
//                break;
//            case UNKNOWN_ERROR:
//            case UNKNOWN_CHECKING:
//            case UNKNOWN_TIMED_OUT:
//            case UNSUPPORTED_DEVICE_NOT_CAPABLE:
//                Log.e(
//                        TAG,
//                        "ARCore is not supported on this device, ArCoreApk.checkAvailability() returned "
//                                + availability);
//                runOnUiThread(
//                        () ->
//                                Toast.makeText(
//                                        getApplicationContext(),
//                                        "ARCore is not supported on this device, "
//                                                + "ArCoreApk.checkAvailability() returned "
//                                                + availability,
//                                        Toast.LENGTH_LONG)
//                                        .show());
//                return false;
//        }
//        return true;
//    }
}
