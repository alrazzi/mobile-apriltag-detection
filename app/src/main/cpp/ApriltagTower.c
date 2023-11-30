/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#include <string.h>
#include <jni.h>
#include "apriltag.h"
#include "tag36h11.h"
#include "apriltag_pose.h"
#include <math.h>
#include <stdbool.h>
#include <stdlib.h>
#include <stdio.h>


/* This is a trivial JNI example where we use a native method
 * to return a new VM String. See the corresponding Java source
 * file located at:
 *
 *   apps/samples/hello-jni/project/src/com/example/hellojni/HelloJni.java
 */
apriltag_pose_t* set_apriltag_pose(double R_data[9], double t_data[3]) {
    apriltag_pose_t* pose = malloc(sizeof(apriltag_pose_t));

    // Assuming matd_create_data initializes a matrix with the provided data
    pose->R = matd_create_data(3, 3, R_data);
    pose->t = matd_create_data(3, 1, t_data);

    return pose;
}
static apriltag_pose_t tag_field[4];
void initialize_tag_field() {
    // Set the values for 0th element (North)
    double R_data0[9] = {-1.0, 0.0, 0.0,
                         0.0, -1.0, 0.0,
                         0.0, 0.0, 1.0};
    double t_data0[3] = {0, 0, 1};
    tag_field[0] = *set_apriltag_pose(R_data0, t_data0);

    // Set the values for 1th element (East)
    double R_data1[9] = {0.0, 1.0, 0.0,
                         -1.0, 0.0, 0.0,
                         0.0, 0.0, 1.0};
    double t_data1[3] = {1, 0, 0};
    tag_field[1] = *set_apriltag_pose(R_data1, t_data1);

    // Set the values for 2th element (South)
    double R_data2[9] = {1.0, 0.0, 0.0,
                         0.0, 1.0, 0.0,
                         0.0, 0.0, 1.0};
    double t_data2[3] = {0, 0, -1};
    tag_field[2] = *set_apriltag_pose(R_data2, t_data2);

    // Set the values for 3th element (West)
    double R_data3[9] = {0.0, -1.0, 0.0,
                         1.0,  0.0, 0.0,
                         0.0,  0.0, 1.0};
    double t_data3[3] = {-1, 0, 0};
    tag_field[3] = *set_apriltag_pose(R_data3, t_data3);
}

bool is_tag_in_field(int id) {
    return sizeof(tag_field) > id;
}
jlong pixel_array_to_uint_8_img(JNIEnv *env, jobject instance, jobjectArray pixelArray, jint width, jint height) {
    // Create an instance of image_u8_t
    image_u8_t *image = image_u8_create_stride((int) width, (int) height, (int) width);

    if (image == NULL) {
        // Handle error, unable to create image structure
        return 0;
    }

    int length = (*env)->GetArrayLength(env, pixelArray);

    for (int i = 0; i < length; i++) {
        jobject pixel = (*env)->GetObjectArrayElement(env, pixelArray, i);

        if (pixel != NULL) {
            jintArray intArray = (jintArray) pixel;
            jint *pixels = (*env)->GetIntArrayElements(env, intArray, 0);

            if (pixels != NULL) {
                jint r = pixels[0];
                jint g = pixels[1];
                jint b = pixels[2];
                jint a = pixels[3];

                // Now you can use the values of r, g, b, and a in your C code
                uint8_t gray = (r+g+g+b)/4;

                // Store the grayscale value in the image structure
                image->buf[i] = gray;
                // Release the jint array
                (*env)->ReleaseIntArrayElements(env, intArray, pixels, 0);
            }
        }
    }

    return (jlong)image;
}

char* test_img(JNIEnv *env, jobject instance, jobjectArray imageData, jint width, jint height, jlong imagePointer) {
    // Cast the image pointer back to image_u8_t
    image_u8_t *image = (image_u8_t*)imagePointer;

    if (image == NULL) {
        // Handle an error if the image pointer is invalid
        return strdup("Image pointer is invalid");
    }

    // Verify dimensions
    if (image->width != width || image->height != height) {
        // Handle dimension mismatch error
        char error[100];
        snprintf(error, sizeof(error), "Height/width mismatch. Image: Height=%d, Width=%d, Expected: Height=%d, Width=%d", image->height, image->width, height, width);
        return strdup(error);
    }

    // Iterate through the ArrayList to validate pixel data
    for (int y = 0; y < height; y++) {
        jintArray intArray = (jintArray)(*env)->GetObjectArrayElement(env, imageData, y);
        jint* pixelData = (*env)->GetIntArrayElements(env, intArray, 0);

        for (int x = 0; x < width; x++) {
            int index = y * width + x;
            uint8_t expectedPixelValue = (uint8_t)pixelData[x];

            if (image->buf[index] != expectedPixelValue) {
                // Handle pixel value mismatch error
                (*env)->ReleaseIntArrayElements(env, intArray, pixelData, JNI_ABORT);
                char problem[50];
                snprintf(problem, sizeof(problem), "Pixel value mismatch at index %d: Expected=%d, Actual=%d", index, expectedPixelValue, image->buf[index]);
                return strdup(problem);
            }
        }

        // Release the IntArray elements
        (*env)->ReleaseIntArrayElements(env, intArray, pixelData, 0);
    }

    // All checks passed; the image is valid
    return strdup("everything passed!");
}
apriltag_pose_t get_pose(apriltag_detection_t *detection){
    apriltag_detection_info_t info;
    info.det = detection;
    info.tagsize = 0.05; // in meters
    info.fx = 657;
    info.fy = 657.26;
    info.cx = 312.18;
    info.cy = 241.739;

    // Then call estimate_tag_pose.
    apriltag_pose_t pose;
    double err = estimate_tag_pose(&info, &pose);

    return pose;
}

double get_pose_error(apriltag_detection_t detection){
    apriltag_detection_info_t info;
    info.det = &detection;
    info.tagsize = 0.02; // in meters
    info.fx = 657;
    info.fy = 657.26;
    info.cx = 312.18;
    info.cy = 241.739;

    // Then call estimate_tag_pose.
    apriltag_pose_t pose;
    double err = estimate_tag_pose(&info, &pose);

    return err;
}

apriltag_detection_t* get_best_detection(zarray_t *detections){
    apriltag_detection_t* best_detection = NULL;
    double least_error = MAXFLOAT;
    apriltag_pose_t pose;
    for (int i = 0; i < zarray_size(detections); i++) {
        apriltag_detection_t *det;
        zarray_get(detections, i, &det);

//        if(!is_tag_in_field(det->id)){
//            continue;
//        }

        double err = get_pose_error(*det);
        if(err < least_error){
            least_error=err;
            best_detection=det;
        }
    }
    return best_detection;
}
void cleanup(apriltag_detector_t *td ,apriltag_family_t *tf,image_u8_t *img){
    tag36h11_destroy(tf);
    apriltag_detector_destroy(td);
    if (img) {
        if (img->buf) {
            free(img->buf); // Free the pixel data
        }
        free(img);      // Free the image structure
    }
}

double get_yaw(apriltag_pose_t pose){
    double x = pose.t->data[0];
    double y = pose.t->data[1];
    double z = pose.t->data[2];

    double yaw = atan2(x, z) * 180.0 / M_PI;
    return yaw;
}

double get_dist(apriltag_pose_t pose){
    double x = pose.t->data[0];
    double y = pose.t->data[1];
    double z = pose.t->data[2];

    double dist = sqrt(pow(x,2) + pow(y,2) + pow(z,2));
    return dist;
}

apriltag_pose_t get_abs_pos(int id, apriltag_pose_t pose){
    apriltag_pose_t tag_pos = tag_field[id];

}
//MY TRY
void convert1DTo2D(double array1D[9], double array2D[3][3]) {
    int k = 0;
    for (int i = 0; i < 3; ++i) {
        for (int j = 0; j < 3; ++j) {
            array2D[i][j] = array1D[k++];
        }
    }
}

void rotateTByR(double t[3], double R[9], double result[3]){
    // Matrix-vector multiplication
    double R2D[3][3];
    convert1DTo2D(R, R2D);

    for (int i = 0; i < 3; ++i) {
        for (int j = 0; j < 3; ++j) {
            result[i] += R2D[i][j] * t[j];
        }
    }
}
void flattenMatrix(double matrix[3][3], double result[9]) {
    for (int i = 0; i < 3; ++i) {
        for (int j = 0; j < 3; ++j) {
            result[i * 3 + j] = matrix[i][j];
        }
    }
}
void rotateRByR(double R1[3][3], double R2[3][3], double flattened_result[9]) {
    double R1_2D[3][3];
    convert1DTo2D(R1, R1_2D);
    double R2_2D[3][3];
    convert1DTo2D(R2, R2_2D);

    double result[3][3];
    for (int i = 0; i < 3; ++i) {
        for (int j = 0; j < 3; ++j) {
            result[i][j] = 0;
            for (int k = 0; k < 3; ++k) {
                result[i][j] += R1_2D[i][k] * R2_2D[k][j];
            }
        }
    }
    flattenMatrix(result, flattened_result);
}
void addTtoT(double T1[3], double T2[3], double result[3]) {
    for (int i = 0; i < 3; ++i) {
        result[i] = T1[i] + T2[i];
    }
}
apriltag_pose_t TransformBy(apriltag_pose_t pose, apriltag_pose_t transform){
    double added_T[3];
    rotateTByR(transform.t->data, pose.R->data, added_T);
    double new_T[3];
    addTtoT(pose.t->data, added_T, new_T);

    double new_R[9];
    rotateRByR(pose.R->data, transform.R->data, new_R);

    apriltag_pose_t new_pos;
    new_pos=*set_apriltag_pose(new_R, new_T);

    return new_pos;
}
//MY TRY END

//DONT DELETE:
jstring
Java_com_example_android_camerax_video_apriltag_00024Companion_stringFromJNI( JNIEnv* env, jobject thiz, jbyteArray pixelArray, jint width, jint height)
{
//    initialize_tag_field();
//    // Example absolute pose
//    double R_absolute_data[9] = {1, 0, 0, 0, 1, 0, 0, 0, 1};
//    double t_absolute_data[3] = {1, 0, 0};
//    apriltag_pose_t* absolute_pose = &tag_field[0];
//
//    // Example relative pose (transformation)
//    double R_relative_data[9] = {0, -1, 0, 1, 0, 0, 0, 0, 1};
//    double t_relative_data[3] = {1, 0, 0};
//    apriltag_pose_t* relative_pose = &tag_field[1];
//
//    // Transform the absolute pose using the relative pose
//    apriltag_pose_t transformed_pose = TransformBy(*absolute_pose, *relative_pose);
//
//    // Print the transformed pose
//    double x[3];
//    for (int i = 0; i < 3; ++i) {
//        x[i] = transformed_pose.t->data[i];
//    }
//
//    return 0;

//    initialize_tag_field();
    image_u8_t *img = (image_u8_t *) pixel_array_to_uint_8_img(env, thiz, pixelArray, width, height);
//    char* result = test_img(env, thiz, pixelArray, width, height, (jlong) img);

    apriltag_detector_t *td = apriltag_detector_create();
    apriltag_family_t *tf = tag36h11_create();
    apriltag_detector_add_family(td, tf);
    zarray_t *detections = apriltag_detector_detect(td, img);

    apriltag_detection_t* best_detection = get_best_detection(detections);
    if(best_detection==NULL){
        cleanup(td, tf, img);
        return (*env)->NewStringUTF(env, "NO apriltags detected");
    }
    apriltag_pose_t pose = get_pose(best_detection);
    int num_detections = zarray_size(detections);
    int id = best_detection->id;
    double yaw = get_yaw(pose);
    double dist = get_dist(pose);

    cleanup(td, tf, img);

    char num_det_str[60];  // Assuming a maximum of 20 characters for the integer
    snprintf(num_det_str, sizeof(num_det_str), "Id detected: %d; yaw: %.1f; dist: %.2f; # detections: %d", id, yaw, dist, num_detections);
    return (*env)->NewStringUTF(env, num_det_str);
}