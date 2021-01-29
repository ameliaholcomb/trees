package com.trees.common.jni;

import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.huawei.hiar.ARImage;
import com.trees.common.helpers.ImageUtil;

import java.nio.ByteBuffer;
import java.util.UnknownFormatConversionException;

import static android.graphics.ImageFormat.YUV_420_888;

public class ImageProcessor implements ImageProcessorInterface {
    static {
        System.loadLibrary("image-processor");
    }
    private class ImageInfo {
        int[] info;
        int[] pixelStrides ;
        int[] rowStrides  ;
        ByteBuffer[] buffers  ;

        ImageInfo(Image image) {
            Image.Plane[] planes = image.getPlanes();
            info = new int[]{image.getFormat(), image.getWidth(), image.getHeight()};
            pixelStrides = new int[planes.length];
            rowStrides = new int[planes.length];
            buffers = new ByteBuffer[planes.length];
            for (int i=0; i< planes.length;i++ ){
                pixelStrides[i] = planes[i].getPixelStride();
                rowStrides[i]= planes[i].getRowStride();
                buffers[i] = planes[i].getBuffer();
            }
        }


    }
    /* Native interface to process a paired TOF and RGB image
        Returns errno.
        Callers of the class use the public interface processImage,
        which handles translating Java-friendly classes for parameters and return values
        into efficient JNI-compatible types.
     */
    private native int nativeProcessImage(
            // RGB Image
            int[] rgbInfo,
            int[] rgbPixelStride,
            int[] rgbRowStride,
            ByteBuffer[] rgbBuffers,
            // AR Image
            int[] arInfo,
            int[] arPixelStride,
            int[] arRowStride,
            ByteBuffer[] arBuffers
    );


    public ImageResult processImage(Image imgRGB, ARImage imgTOF) {

        ImageInfo arInfo = new ImageInfo(imgTOF);
        ImageInfo rgbInfo = new ImageInfo(imgRGB);

        int errno = nativeProcessImage(
                rgbInfo.info, rgbInfo.pixelStrides, rgbInfo.rowStrides, rgbInfo.buffers,
                arInfo.info, arInfo.pixelStrides, arInfo.rowStrides, arInfo.buffers
        );
        Log.i("EVAN", String.format("Combined widths of %d vs %d", errno, imgTOF.getWidth() + imgRGB.getWidth()));
        // TODO: Reconsider error convention
        if(errno == 0 ) {
            throw new IllegalArgumentException("I'm obviously of the wrong type");
        }
        // Check return value and raise exception

        // Parse buffers for computation return values

        return new FakeImageProcessor().processImage(imgRGB, imgTOF);
    }
}
