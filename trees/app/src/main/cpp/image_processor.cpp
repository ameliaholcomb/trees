#include <string.h>
#include <jni.h>
#include <android/hardware_buffer_jni.h>
#include <vector>
//#include <opencv2/core/mat.hpp>



const int YUV_420_888 = 35;


struct Plane {
    int64_t pixelStride;
    int64_t rowStride;
    // unowned
    void* buffer;
    int64_t buffer_size;
};

struct Image {
    int64_t height;
    int64_t width;
    int64_t format;
    std::vector<Plane> planes;

    static Image FromJNI(jintArray info, jintArray pixel_stride, jintArray row_stride, jobjectArray buffers, JNIEnv* env) {
        jint* tmp = env->GetIntArrayElements(info, nullptr);
        Image image {
            .format = tmp[0],
            .width = tmp[1],
            .height = tmp[2]
        };
        jint* pixel = env->GetIntArrayElements(pixel_stride, nullptr);
        jint* row = env->GetIntArrayElements(pixel_stride, nullptr);
        for(int i=0; i<env->GetArrayLength(pixel_stride); i++) {
            jobject buf = env->GetObjectArrayElement(buffers, i);
            image.planes.push_back({
                .pixelStride = pixel[i],
                .rowStride = row[i],
                .buffer = env->GetDirectBufferAddress(buf),
                .buffer_size = env->GetDirectBufferCapacity(buf)
            });
        }
        return image;
    }
};

extern "C" JNIEXPORT jint JNICALL
Java_com_trees_common_jni_ImageProcessor_nativeProcessImage(
    JNIEnv* env, jobject thiz, jintArray rgb_info, jintArray rgb_pixel_stride,
    jintArray rgb_row_stride, jobjectArray rgb_buffers, jintArray ar_info,
    jintArray ar_pixel_stride, jintArray ar_row_stride, jobjectArray ar_buffers) {

    Image rgbImage = Image::FromJNI(rgb_info, rgb_pixel_stride, rgb_row_stride, rgb_buffers, env);
    Image tofImage = Image::FromJNI(ar_info, ar_pixel_stride, ar_row_stride, ar_buffers, env);

//    cv::Mat
    return 1;

}


//  // Buffers for storing TOF output
//  TofBuffers buffers = new TofBuffers();
//
//  ARImage.Plane plane = imgTOF.getPlanes()[0];
//  ShortBuffer shortDepthBuffer = plane.getBuffer().asShortBuffer();
//
//  int stride = plane.getRowStride();
//  int offset = 0;
//  float sum = 0.0f;
//  float[] output = new float[imgTOF.getWidth() * imgTOF.getHeight()];
//  for (short y = 0; y < imgTOF.getHeight(); y++) {
//    for (short x = 0; x < imgTOF.getWidth(); x++) {
//      // Parse the data. Format is [depth|confidence]
//      int depthSample = shortDepthBuffer.get((int) (y / 2) * stride + x) & 0xFFFF;
//      depthSample = (((depthSample & 0xFF) << 8) & 0xFF00) | (((depthSample & 0xFF00) >> 8) & 0xFF);
//      short depthSampleShort = (short) depthSample;
//      short depthRange = (short) (depthSampleShort & 0x1FFF);
//      short depthConfidence = (short) ((depthSampleShort >> 13) & 0x7);
//      float depthPercentage = depthConfidence == 0 ? 1.f : (depthConfidence - 1) / 7.f;
//
//      output[offset + x] = (float) depthRange / 10000;
//
//      sum += output[offset + x];
//      // Store data in buffer
//      buffers.xBuffer.add(x);
//      buffers.yBuffer.add(y);
//      buffers.dBuffer.add(depthRange / 1000.0f);
//      buffers.percentageBuffer.add(depthPercentage);
//    }
//    offset += imgTOF.getWidth();
//  }
//  return buffers;

