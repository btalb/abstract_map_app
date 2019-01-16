#include <jni.h>
#include <string>

#include <android/log.h>

#include "apriltag.h"
#include "image_u8.h"
#include "pjpeg.h"
#include "tag36h11.h"

// Dirty globals
apriltag_detector *detector;

jclass detectionClass;
jmethodID constructor;
jfieldID fieldId;
jfieldID fieldX1;
jfieldID fieldY1;
jfieldID fieldX2;
jfieldID fieldY2;
jfieldID fieldX3;
jfieldID fieldY3;
jfieldID fieldX4;
jfieldID fieldY4;

const bool DEBUG = 0;

extern "C" double ms(struct timespec *ts) {
    return ts->tv_sec * 1000 + ts->tv_nsec / 1000000.0;
}

extern "C" void tdiff(struct timespec *start, struct timespec *stop,
                      struct timespec *result) {
    if ((stop->tv_nsec - start->tv_nsec) < 0) {
        result->tv_sec = stop->tv_sec - start->tv_sec - 1;
        result->tv_nsec = stop->tv_nsec - start->tv_nsec + 1000000000;
    } else {
        result->tv_sec = stop->tv_sec - start->tv_sec;
        result->tv_nsec = stop->tv_nsec - start->tv_nsec;
    }
    return;
}

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
    detector->quad_decimate = 8.0;

    // Cache all of the Java references
    detectionClass = env->FindClass
            ("com/humancues/humancuestaggame/GameActivity$Detection");
    detectionClass = (jclass) env->NewGlobalRef(detectionClass);
    constructor = env->GetMethodID(detectionClass, "<init>", ""
            "(Lcom/humancues/humancuestaggame/GameActivity;)V");
    fieldId = env->GetFieldID(detectionClass, "id", "I");
    fieldX1 = env->GetFieldID(detectionClass, "x1", "D");
    fieldY1 = env->GetFieldID(detectionClass, "y1", "D");
    fieldX2 = env->GetFieldID(detectionClass, "x2", "D");
    fieldY2 = env->GetFieldID(detectionClass, "y2", "D");
    fieldX3 = env->GetFieldID(detectionClass, "x3", "D");
    fieldY3 = env->GetFieldID(detectionClass, "y3", "D");
    fieldX4 = env->GetFieldID(detectionClass, "x4", "D");
    fieldY4 = env->GetFieldID(detectionClass, "y4", "D");
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
    struct timespec tS, tP, tI, tD, tO, rA, rB, rC, rD, rE;
    if (DEBUG) clock_gettime(CLOCK_MONOTONIC, &tS);

    // Get a pointer to the actual data
    jbyte *dataPtr = env->GetByteArrayElements(bytes, NULL);

    if (DEBUG) clock_gettime(CLOCK_MONOTONIC, &tP);

    // Construct the image structure from the byte array
    int error;
    pjpeg_t *jpg = pjpeg_create_from_buffer((uint8_t *) dataPtr, nbytes, 0, &error);
    image_u8_t *img = pjpeg_to_u8_baseline(jpg);

    if (DEBUG) clock_gettime(CLOCK_MONOTONIC, &tI);

    // Attempt the tag detection (bailing if we don't find anything)
    zarray_t *detections = apriltag_detector_detect(detector, img);
    if (DEBUG) clock_gettime(CLOCK_MONOTONIC, &tD);
    if (zarray_size(detections) == 0) {
        if (DEBUG) {
            tdiff(&tS, &tD, &rA);
            tdiff(&tS, &tP, &rB);
            tdiff(&tP, &tI, &rC);
            tdiff(&tI, &tD, &rD);
            __android_log_print(ANDROID_LOG_WARN, "HuC",
                                "cMethod took: %f (tP:%f,tI:%f,tD:%f)",
                                ms(&rA), ms(&rB), ms(&rC), ms(&rD)
            );
        }
        // Clean up and return
        pjpeg_destroy(jpg);
        image_u8_destroy(img);
        apriltag_detections_destroy(detections);
        return NULL;
    }

    // Process the first detection, and extract all of the required information
    apriltag_detection_t *detection;
    zarray_get(detections, 0, &detection);
    if (DEBUG) {
    __android_log_print(ANDROID_LOG_WARN, "HuC",
                        "c detect %d @ %f,%f,%f,%f,%f,%f,%f,%f", detection->id,
                        detection->p[0][0], detection->p[0][1],
                        detection->p[1][0], detection->p[1][1],
                        detection->p[2][0], detection->p[2][1],
                        detection->p[3][0], detection->p[3][1]);
    }

    // Construct the return object
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
    if (DEBUG) clock_gettime(CLOCK_MONOTONIC, &tO);

    // Clean up and return
    pjpeg_destroy(jpg);
    image_u8_destroy(img);
    apriltag_detections_destroy(detections);
    if (DEBUG) {
        tdiff(&tS, &tO, &rA);
        tdiff(&tS, &tP, &rB);
        tdiff(&tP, &tI, &rC);
        tdiff(&tI, &tD, &rD);
        tdiff(&tD, &tO, &rE);
        __android_log_print(ANDROID_LOG_WARN, "HuC",
                            "cMethod took: %f (tP:%f,tI:%f,tD:%f,tO:%f)",
                            ms(&rA), ms(&rB), ms(&rC), ms(&rD), ms(&rE)
        );
    }
    return obj;
}

