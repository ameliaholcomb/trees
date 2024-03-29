package com.trees.common.pyi;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;

public class FakeImageProcessor implements ImageProcessorInterface {

    @Override
    public ImageResult processImage(Activity context, ImageRaw raw) {
        ImageResult imageResult = new ImageResult();
        Bitmap bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
        // Ironically, turns the bitmap white
        bitmap.eraseColor(Color.WHITE);
        imageResult.DisplayImage = bitmap;
        return imageResult;
    }
}

