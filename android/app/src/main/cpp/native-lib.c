#include <string.h>
#include <jni.h>

JNIEXPORT jstring JNICALL Java_com_yourcompany_humancuestaggame_MainActivity_helloJNI(JNIEnv* env, jobject thiz) {
  return (*env)->NewStringUTF(env, "c++ says hello!");
}
