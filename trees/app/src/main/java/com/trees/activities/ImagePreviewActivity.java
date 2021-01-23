package com.trees.activities;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.huawei.hiar.ARImage;
import com.trees.common.helpers.ImageStore;
import com.trees.common.helpers.StoragePermissionHelper;
import com.trees.common.helpers.TofBuffers;
import com.trees.common.helpers.TofUtil;

import java.io.IOException;

public class ImagePreviewActivity extends AppCompatActivity {
    private String LOG_TAG = "AMELIA";
    private Integer captureNumber;
    private Integer sampleNumber;
    private ARImage imgTOF;
    private Image imgRGB;
    private float[] projectionMatrix;
    private float[] viewMatrix;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_preview);
        if (getIntent().hasExtra("IMAGE_RGB")) {
            ImageView _imv = (ImageView) findViewById(R.id.imageView);
            Bitmap _bitmap = BitmapFactory.decodeByteArray(
                    getIntent().getByteArrayExtra("IMAGE_RGB"), 0, getIntent().getByteArrayExtra("IMAGE_RGB").length);
            _imv.setImageBitmap(_bitmap);
        }
    }

    public void onApprove(View view) {
        // Verify STORAGE_PERMISSION has been granted.
        if (!StoragePermissionHelper.hasStoragePermission(this)) {
            StoragePermissionHelper.requestStoragePermission(this);
            Log.i(LOG_TAG, "We don't have storage permission!");
            return;
        }

        // Extract and save image
        TofBuffers buffers = TofUtil.TofToBuffers(imgTOF);
        try {
            ImageStore imageStore = new ImageStore();
            imageStore.saveToFileTOF(sampleNumber, captureNumber, buffers);
            imageStore.saveToFileRGB(sampleNumber, captureNumber, imgRGB);
            imageStore.saveToFileMatrix(sampleNumber, captureNumber, projectionMatrix, viewMatrix);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Unable to save images: ", e);
            Intent resultIntent = new Intent();
            setResult(Activity.RESULT_CANCELED, resultIntent);
            finish();
        }

        Intent resultIntent = new Intent();
        setResult(Activity.RESULT_OK, resultIntent);
        finish();
    }


    public void onReject(View view) {
        Intent resultIntent = new Intent();
        setResult(Activity.RESULT_CANCELED, resultIntent);
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (!StoragePermissionHelper.hasStoragePermission(this)) {
            Toast.makeText(
                    getApplicationContext(),
                    "Storage permission is needed to save this image",
                    Toast.LENGTH_LONG)
                    .show();
            if (!StoragePermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                StoragePermissionHelper.launchPermissionSettings(this);
            }
            finish();
        }
    }
}