#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_humancues_humancuestaggame_GameActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_humancues_humancuestaggame_GameActivity_initAprilTags(
        JNIEnv *env, jobject) {

}


extern "C" JNIEXPORT jintArray JNICALL
Java_com_humancues_humancuestaggame_GameActivity_searchForAprilTags(
        JNIEnv *env, jobject, jbyteArray argbBytes) {
    return env->NewIntArray(4);
}
