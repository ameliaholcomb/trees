package com.trees.activities;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.trees.common.helpers.StoragePermissionHelper;
import com.trees.model.ImageViewModel;


public class CaptureConfirmationFragment extends Fragment implements View.OnClickListener {
    private String LOG_TAG = "AMELIA";
    private View view;
    private ImageViewModel imageModel;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Set up fragment buttons
        view = inflater.inflate(R.layout.capture_confirmation_fragment_view, container, false);
        view.findViewById(R.id.Redo).setOnClickListener(this);
        view.findViewById(R.id.Save).setOnClickListener(this);

        // Subscribe to display image, updating with the latest capture
        imageModel = new ViewModelProvider(requireActivity()).get(ImageViewModel.class);
        imageModel.getCurrentCapture().observe(getViewLifecycleOwner(), capture -> {
                    Log.i("AMELIA", "I believe there is a new image to display");
                    Bitmap displayImage = capture.DisplayImage;
                    ImageView imageView = view.findViewById(R.id.imageView);
                    imageView.setImageBitmap(displayImage);
                    }
                );

        return view;
    }

    public void onApprove(View view) {
        // Verify STORAGE_PERMISSION has been granted.
        if (!StoragePermissionHelper.hasStoragePermission(requireActivity())) {
            StoragePermissionHelper.requestStoragePermission(requireActivity());
            Log.i(LOG_TAG, "We don't have storage permission!");
            return;
        }
        imageModel.storeCapture();
    }


    public void onReject(View view) { }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (!StoragePermissionHelper.hasStoragePermission(requireActivity())) {
            Toast.makeText(
                    requireContext(),
                    "Storage permission is needed to save this image",
                    Toast.LENGTH_LONG)
                    .show();
            if (!StoragePermissionHelper.shouldShowRequestPermissionRationale(requireActivity())) {
                // Permission denied with checking "Do not ask again".
                StoragePermissionHelper.launchPermissionSettings(requireActivity());
            }
        }
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()) {
            case R.id.Redo:
                onReject(v);
                break;
            case R.id.Save:
                onApprove(v);
                break;
            default:
                Log.e(LOG_TAG,"Wtf did you click");
        }
        getParentFragmentManager().beginTransaction().remove(this).commit();
    }
}