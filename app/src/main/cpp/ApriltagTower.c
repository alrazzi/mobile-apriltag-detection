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
jstring
Java_com_example_android_camerax_video_apriltag_00024Companion_stringFromJNI( JNIEnv* env, jobject thiz, jbyteArray data, jint width, jint height)
{
    if(data == NULL){
        return (*env)->NewStringUTF(env, "Error creating image structure.");
    }
    jbyte* pixelData = (*env)->GetByteArrayElements(env, data, NULL);

    image_u8_t *im = image_u8_create(width, height);
//
//    // Check if the image_u8_t structure was created successfully
    if (im == NULL) {
        // Handle the error and return an error message
        return (*env)->NewStringUTF(env, "Error creating image structure.");
    }
//
//    // Cast the pixelData to an array of RGBA pixels
    uint32_t *rgbaPixels = (uint32_t *)pixelData;
//
//    // Iterate through each pixel in the Bitmap and copy its red component to the image
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            // Extract the red component from the RGBA pixel (assuming 32-bit RGBA format)
            uint8_t red = (rgbaPixels[y * width + x] >> 16) & 0xFF;

            // Set the corresponding pixel in the image_u8_t structure
            im->buf[y * im->stride + x] = red;
        }
    }
    apriltag_detector_t *td = apriltag_detector_create();
    apriltag_family_t *tf = tag36h11_create();
    apriltag_detector_add_family(td, tf);
        zarray_t *detections = apriltag_detector_detect(td, im);
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
    (*env)->ReleaseByteArrayElements(env, data, pixelData, 0);
    if (im) {
        if (im->buf) {
            free(im->buf); // Free the pixel data
        }
        free(im);      // Free the image structure
    }
    // Format the id_found as a string
    char num_det_str[20];  // Assuming a maximum of 20 characters for the integer
    snprintf(num_det_str, sizeof(num_det_str), "%d", num_det_str);
    return (*env)->NewStringUTF(env, num_det_str);

//    // Create the result string
//    char result[50];  // Adjust the size as needed
//    snprintf(result, sizeof(result), "I found id: %s", id_str);
//
//    return (*env)->NewStringUTF(env, result);
//    return (*env)->NewStringUTF(env, "Hello from JNI!");
}