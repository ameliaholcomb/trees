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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

import com.google.ar.core.examples.java.common.helpers.CameraPermissionHelper;
import com.google.ar.core.examples.java.common.helpers.StoragePermissionHelper;
import com.huawei.arengine.demos.java.world.rendering.RenderUtil;
import com.huawei.arengine.demos.java.world.rendering.common.DisplayRotationUtil;
import com.huawei.hiar.AREnginesApk;
import com.huawei.hiar.ARFrame;
import com.huawei.hiar.ARImage;
import com.huawei.hiar.ARSession;
import com.huawei.hiar.ARWorldTrackingConfig;
import com.huawei.hiar.exceptions.ARCameraNotAvailableException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.RoundingMode;
import java.nio.ShortBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;


public class SharedCameraActivity extends AppCompatActivity {
    private static final String TAG = SharedCameraActivity.class.getSimpleName();
    private static final String SAMPLE_NUM_ID = "sampleNum";
    private static final String DELETE_BUTTON_ID = "deleteButton";
    private static final String LOG_TAG = "AMELIA";
    // Required for test run.
    private static final Short AUTOMATOR_DEFAULT = 0;
    private static final String AUTOMATOR_KEY = "automator";
    private static final String PREFERENCES = "stateInfo";
    private static final String SHARED_NUM_CAPTURES = "numCaptures";
    private static final String SHARED_CURRENT_SAMPLE = "currentSample";
    // Whether to save the next available image to a file
    static boolean CAPTURE_IMAGE = false;
    private final AtomicBoolean automatorRun = new AtomicBoolean(false);
    // A check mechanism to ensure that the camera closed properly so that the app can safely exit.
    private final ConditionVariable safeToExitApp = new ConditionVariable();
    // Preferences object and associated file
    SharedPreferences preferences;
    // path for storing data
    private String fileSaveDir =
            android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/Tree";
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
        if (resultCode == Activity.RESULT_OK) {
            CAPTURE_IMAGE = true;
        }
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


    // onCaptureImage sets captureImage to true, so that next time extractImage is called,
    // the image will be saved to a file.
    public void onCaptureImage(View view) {
        Log.i(LOG_TAG, "Capturing an image");
        if (arFrame == null) {
            return;
        }

        // Verify STORAGE_PERMISSION has been granted.
        if (!StoragePermissionHelper.hasStoragePermission(this)) {
            StoragePermissionHelper.requestStoragePermission(this);
            Log.i(LOG_TAG, "We don't have storage permission!");
            return;
        }

        imgRGB = arFrame.acquireCameraImage();
        imgTOF = (ARImage) arFrame.acquireDepthImage();

        Intent intent = new Intent(this, ImagePreviewActivity.class);
        byte[] imageBytes = ImageUtil.imageToByteArray(imgRGB);
        intent.putExtra("IMAGE_RGB", imageBytes);
        startActivityForResult(intent, imagePreviewResult);
    }


    // extract RGB and TOF image from the given arFrame
    public void extractImageData(ARFrame arFrame, float[] projectionMatrix, float[] viewMatrix) {
        if (arFrame == null) {
            return;
        }
        if (!CAPTURE_IMAGE) {
            this.arFrame = arFrame;
            this.projectionMatrix = projectionMatrix;
            this.viewMatrix = viewMatrix;
        } else {
            Log.i(LOG_TAG, "Saving the image");
            if (imgTOF != null) {
                // Buffers for storing TOF output
                ArrayList<Short> xBuffer = new ArrayList<>();
                ArrayList<Short> yBuffer = new ArrayList<>();
                ArrayList<Float> dBuffer = new ArrayList<>();
                ArrayList<Float> percentageBuffer = new ArrayList<>();

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
                        xBuffer.add(x);
                        yBuffer.add(y);
                        dBuffer.add(depthRange / 1000.0f);
                        percentageBuffer.add(depthPercentage);
                    }
                    offset += imgTOF.getWidth();
                }

                if (CAPTURE_IMAGE) {
                    try {
                        saveToFileTOF(xBuffer, yBuffer, dBuffer, percentageBuffer);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                imgTOF.close();
            }

            if (imgRGB != null) {
                if (CAPTURE_IMAGE) {
                    try {
                        saveToFileRGB(imgRGB);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                imgRGB.close();
            }

            if (this.projectionMatrix != null && this.viewMatrix != null) {
                if (CAPTURE_IMAGE) {
                    try {
                        saveToFileMatrix(this.projectionMatrix, this.viewMatrix);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            if (CAPTURE_IMAGE) {
                // Update the shared preferences for the number of captures
                this.setSharedPreferencesVar(SHARED_NUM_CAPTURES, this.getSharedPreferencesVar(SHARED_NUM_CAPTURES) + 1);
                CAPTURE_IMAGE = false;
            }
        }
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


    // Method for deleting data
    public void onDeleteData(View view) {
        // File object for the directory where the data is saved.
        File savedDir = new File(this.fileSaveDir + "/samples");
        if (!savedDir.exists()) {
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
        if (!outFile.getParentFile().exists()) {
            outFile.getParentFile().mkdirs();
        }

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

    public void saveToFileRGB(Image image) throws IOException {
        int currentSample = this.getSharedPreferencesVar(SHARED_CURRENT_SAMPLE);
        int numCaptures = this.getSharedPreferencesVar(SHARED_NUM_CAPTURES);
        String sampleFName = "Capture_Sample_" + currentSample + "_" + (numCaptures) + ".jpeg";

        // Write the TOF data currently in buffers to an output file.
        Log.i(LOG_TAG, "Writing to the file");

        File dir = new File(this.fileSaveDir, "/samples");
        Log.i(LOG_TAG, dir.getAbsolutePath());

        File outFile = new File(dir, sampleFName);
        if (!outFile.getParentFile().exists()) {
            outFile.getParentFile().mkdirs();
        }

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


    public void saveToFileMatrix(float[] projectionMatrix, float[] viewMatrix) throws IOException {
        int currentSample = this.getSharedPreferencesVar(SHARED_CURRENT_SAMPLE);
        int numCaptures = this.getSharedPreferencesVar(SHARED_NUM_CAPTURES);
        String sampleFName = "Capture_Sample_" + currentSample + "_" + (numCaptures) + ".txt";

        // Write the TOF data currently in buffers to an output file.
        Log.i(LOG_TAG, "Writing to the file");

        File dir = new File(this.fileSaveDir, "/samples");
        Log.i(LOG_TAG, dir.getAbsolutePath());

        File outFile = new File(dir, sampleFName);
        if (!outFile.getParentFile().exists()) {
            outFile.getParentFile().mkdirs();
        }

        try {
            DecimalFormat df = new DecimalFormat("#.##########");
            df.setRoundingMode(RoundingMode.CEILING);
            PrintWriter out = new PrintWriter(outFile);
            for (int i = 0; i < projectionMatrix.length; i++) {
                if (i != 0) {
                    out.printf(", ");
                }
                out.printf(df.format(projectionMatrix[i]));
            }
            out.printf("\n");

            for (int i = 0; i < viewMatrix.length; i++) {
                if (i != 0) {
                    out.printf(", ");
                }
                out.printf(df.format(viewMatrix[i]));
            }
            out.printf("\n");
            out.close();
            out.flush();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
