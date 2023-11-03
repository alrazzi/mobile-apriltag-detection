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
/* This is a trivial JNI example where we use a native method
 * to return a new VM String. See the corresponding Java source
 * file located at:
 *
 *   apps/samples/hello-jni/project/src/com/example/hellojni/HelloJni.java
 */

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

    // You now have 'image' populated with pixel data.

    // Optionally perform additional processing on 'image', e.g., decimation or rotation.

    // Return a pointer to the 'image' structure (cast to jlong)
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
jstring
Java_com_example_android_camerax_video_apriltag_00024Companion_stringFromJNI( JNIEnv* env, jobject thiz, jbyteArray pixelArray, jint width, jint height)
{
    image_u8_t *img = (image_u8_t *) pixel_array_to_uint_8_img(env, thiz, pixelArray, width, height);
//    char* result = test_img(env, thiz, pixelArray, width, height, (jlong) img);

    apriltag_detector_t *td = apriltag_detector_create();
    apriltag_family_t *tf = tag36h11_create();
    apriltag_detector_add_family(td, tf);
        zarray_t *detections = apriltag_detector_detect(td, img);
    int num_detections = zarray_size(detections);
    int id_found = -1;
    for (int i = 0; i < zarray_size(detections); i++) {
        apriltag_detection_t *det;
        zarray_get(detections, i, &det);
        id_found = det->id;

        // Do stuff with detections here.
    }
    // Cleanup.
    tag36h11_destroy(tf);
    apriltag_detector_destroy(td);
    if (img) {
        if (img->buf) {
            free(img->buf); // Free the pixel data
        }
        free(img);      // Free the image structure
    }
        char num_det_str[20];  // Assuming a maximum of 20 characters for the integer
    snprintf(num_det_str, sizeof(num_det_str), "Id detected: %d", id_found);
    return (*env)->NewStringUTF(env, num_det_str);
}

//    return (*env)->NewStringUTF(env, result);
//    }else{
//        return (*env)->NewStringUTF(env, "The test failed! :(");
//    }
//    return (*env)->NewStringUTF(env, "Hello from C!");
//    if(data == NULL){
//        return (*env)->NewStringUTF(env, "Error creating image structure.");
//    }
//    jbyte* pixelData = (*env)->GetByteArrayElements(env, data, NULL);
//
//    image_u8_t *im = image_u8_create(width, height);
////
////    // Check if the image_u8_t structure was created successfully
//    if (im == NULL) {
//        // Handle the error and return an error message
//        return (*env)->NewStringUTF(env, "Error creating image structure.");
//    }
////
////    // Cast the pixelData to an array of RGBA pixels
//    uint32_t *rgbaPixels = (uint32_t *)pixelData;
////
////    // Iterate through each pixel in the Bitmap and copy its red component to the image
//    for (int y = 0; y < height; y++) {
//        for (int x = 0; x < width; x++) {
//            // Extract the red component from the RGBA pixel (assuming 32-bit RGBA format)
//            uint8_t red = (rgbaPixels[y * width + x] >> 16) & 0xFF;
//
//            // Set the corresponding pixel in the image_u8_t structure
//            im->buf[y * im->stride + x] = red;
//        }
//    }
//    apriltag_detector_t *td = apriltag_detector_create();
//    apriltag_family_t *tf = tag36h11_create();
//    apriltag_detector_add_family(td, tf);
//        zarray_t *detections = apriltag_detector_detect(td, im);
//    int num_detections = zarray_size(detections);
//    int id_found = -1;
//    for (int i = 0; i < zarray_size(detections); i++) {
//        apriltag_detection_t *det;
//        zarray_get(detections, i, &det);
//        id_found = det->id;
//
//        // Do stuff with detections here.
//    }
//    // Cleanup.
//    tag36h11_destroy(tf);
//    apriltag_detector_destroy(td);
//    (*env)->ReleaseByteArrayElements(env, data, pixelData, 0);
//    if (im) {
//        if (im->buf) {
//            free(im->buf); // Free the pixel data
//        }
//        free(im);      // Free the image structure
//    }
//    // Format the id_found as a string
//    char num_det_str[20];  // Assuming a maximum of 20 characters for the integer
//    snprintf(num_det_str, sizeof(num_det_str), "%d", num_det_str);
//    return (*env)->NewStringUTF(env, num_det_str);

//    // Create the result string
//    char result[50];  // Adjust the size as needed
//    snprintf(result, sizeof(result), "I found id: %s", id_str);
//
//    return (*env)->NewStringUTF(env, result);