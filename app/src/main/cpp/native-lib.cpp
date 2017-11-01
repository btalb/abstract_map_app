#include <jni.h>
#include <string>

#include <android/log.h>

#include "apriltag.h"
#include "image_u8.h"
#include "pjpeg.h"
#include "tag36h11.h"

// Dirty globals
apriltag_detector *detector;

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
    // Create a detector
    detector = apriltag_detector_create();

    // Configure the detector
    apriltag_detector_add_family(detector, tag36h11_create());
}

extern "C" JNIEXPORT void JNICALL
Java_com_humancues_humancuestaggame_GameActivity_cleanupAprilTags(
        JNIEnv *env, jobject) {
    // Destroy the detector
    apriltag_detector_destroy(detector);
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_humancues_humancuestaggame_GameActivity_searchForAprilTags(
        JNIEnv *env, jobject, jbyteArray bytes, jint nbytes) {
    // Get a pointer to the actual data
    jbyte *dataPtr = env->GetByteArrayElements(bytes, NULL);

    // Construct the image structure from the byte array
    int error;
    pjpeg_t *jpg = pjpeg_create_from_buffer((uint8_t *) dataPtr, nbytes, 0, &error);
    image_u8_t *img = pjpeg_to_u8_baseline(jpg);

    // Attempt the tag detection (bailing if we don't find anything)
    zarray_t *detections = apriltag_detector_detect(detector, img);
    if (zarray_size(detections) == 0)
        return NULL;

    // Process the first detection, and extract all of the required information
    apriltag_detection_t *detection;
    zarray_get(detections, 0, &detection);

//    __android_log_print(ANDROID_LOG_ERROR, "HuC",
//                        "c detect %d @ %f,%f,%f,%f,%f,%f,%f,%f", detection->id,
//                        detection->p[0][0], detection->p[0][1],
//                        detection->p[1][0], detection->p[1][1],
//                        detection->p[2][0], detection->p[2][1],
//                        detection->p[3][0], detection->p[3][1]);

    // Construct the return object
    jclass detectionClass = env->FindClass
            ("com/humancues/humancuestaggame/GameActivity$Detection");
    jmethodID constructor = env->GetMethodID(detectionClass, "<init>", ""
            "(Lcom/humancues/humancuestaggame/GameActivity;)V");
    jfieldID fieldId = env->GetFieldID(detectionClass, "id", "I");
    jfieldID fieldX1 = env->GetFieldID(detectionClass, "x1", "D");
    jfieldID fieldY1 = env->GetFieldID(detectionClass, "y1", "D");
    jfieldID fieldX2 = env->GetFieldID(detectionClass, "x2", "D");
    jfieldID fieldY2 = env->GetFieldID(detectionClass, "y2", "D");
    jfieldID fieldX3 = env->GetFieldID(detectionClass, "x3", "D");
    jfieldID fieldY3 = env->GetFieldID(detectionClass, "y3", "D");
    jfieldID fieldX4 = env->GetFieldID(detectionClass, "x4", "D");
    jfieldID fieldY4 = env->GetFieldID(detectionClass, "y4", "D");
    jobject obj = env->NewObject(detectionClass, constructor, NULL);
    env->SetIntField(obj, fieldId, detection->id);
    env->SetDoubleField(obj, fieldX1, detection->p[0][0]);
    env->SetDoubleField(obj, fieldY1, detection->p[0][1]);
    env->SetDoubleField(obj, fieldX2, detection->p[1][0]);
    env->SetDoubleField(obj, fieldY2, detection->p[1][1]);
    env->SetDoubleField(obj, fieldX3, detection->p[2][0]);
    env->SetDoubleField(obj, fieldY3, detection->p[2][1]);
    env->SetDoubleField(obj, fieldX4, detection->p[3][0]);
    env->SetDoubleField(obj, fieldY4, detection->p[3][1]);

    // Clean up and return
    apriltag_detections_destroy(detections);
    return obj;
}
