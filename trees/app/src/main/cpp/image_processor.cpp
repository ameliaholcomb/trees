#include <string.h>
#include <jni.h>

extern "C" JNIEXPORT jint JNICALL
Java_com_trees_common_jni_ImageProcessor_nativeProcessImage(
    JNIEnv* env, jobject thiz) {
  return 4;
}
