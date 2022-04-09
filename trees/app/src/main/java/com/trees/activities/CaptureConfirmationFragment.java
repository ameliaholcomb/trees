package com.trees.activities;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.trees.common.helpers.StoragePermissionHelper;
import com.trees.model.ImageViewModel;


public class CaptureConfirmationFragment extends Fragment implements View.OnClickListener {
    private final String LOG_TAG = "AMELIA";
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
                    Bitmap displayImage = capture.DisplayImage;
                    ImageView imageView = view.findViewById(R.id.imageView);
                    imageView.setImageBitmap(displayImage);
                    if (capture.Diameter < 0.01) {
                        TextView textView = view.findViewById(R.id.imageWarning);
                        textView.setText(R.string.zeroDiameterWarning);
                    }
                });
        return view;
    }

    public void onApprove(View view) {

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