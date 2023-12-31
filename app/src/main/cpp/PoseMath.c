#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <string.h>
#include <jni.h>
#include "apriltag.h"
#include "tag36h11.h"
#include "apriltag_pose.h"
#include <math.h>
#include <stdbool.h>
#include <stdlib.h>
#include "PoseMath.h"

// Function to create a rotation matrix from Euler angles
static matd_t* eulerToRotation(double yaw, double pitch, double roll) {
    matd_t* rotation = malloc(sizeof(matd_t) + 9 * sizeof(double));
    rotation->nrows = 3;
    rotation->ncols = 3;

    // Fill in the rotation matrix elements based on yaw, pitch, and roll
    double cy = cos(yaw);
    double sy = sin(yaw);
    double cp = cos(pitch);
    double sp = sin(pitch);
    double cr = cos(roll);
    double sr = sin(roll);

    rotation->data[0] = cy * cp;
    rotation->data[1] = cy * sp * sr - sy * cr;
    rotation->data[2] = cy * sp * cr + sy * sr;

    rotation->data[3] = sy * cp;
    rotation->data[4] = sy * sp * sr + cy * cr;
    rotation->data[5] = sy * sp * cr - cy * sr;

    rotation->data[6] = -sp;
    rotation->data[7] = cp * sr;
    rotation->data[8] = cp * cr;

    return rotation;
}

// Function to create a translation matrix
static matd_t* createTranslation(double x, double y, double z) {
    matd_t* translation = malloc(sizeof(matd_t) + 3 * sizeof(double));
    translation->nrows = 3;
    translation->ncols = 1;

    translation->data[0] = x;
    translation->data[1] = y;
    translation->data[2] = z;

    return translation;
}

// Function to multiply two matrices
static matd_t* multiplyMatrices_Custom(const matd_t* A, const matd_t* B) {
    if (A->ncols != B->nrows) {
        fprintf(stderr, "Matrix dimensions do not match for multiplication\n");
        return NULL;
    }

    matd_t* result = malloc(sizeof(matd_t) + A->nrows * B->ncols * sizeof(double));
    result->nrows = A->nrows;
    result->ncols = B->ncols;

    for (unsigned int i = 0; i < A->nrows; ++i) {
        for (unsigned int j = 0; j < B->ncols; ++j) {
            double sum = 0.0;
            for (unsigned int k = 0; k < A->ncols; ++k) {
                sum += A->data[i * A->ncols + k] * B->data[k * B->ncols + j];
            }
            result->data[i * B->ncols + j] = sum;
        }
    }

    return result;
}

// Function to add two matrices
static matd_t* addMatrices_Custom(const matd_t* A, const matd_t* B) {
    if (A->nrows != B->nrows || A->ncols != B->ncols) {
        fprintf(stderr, "Matrix dimensions do not match for addition\n");
        return NULL;
    }

    matd_t* result = malloc(sizeof(matd_t) + A->nrows * A->ncols * sizeof(double));
    result->nrows = A->nrows;
    result->ncols = A->ncols;

    for (unsigned int i = 0; i < A->nrows; ++i) {
        for (unsigned int j = 0; j < A->ncols; ++j) {
            result->data[i * A->ncols + j] = A->data[i * A->ncols + j] + B->data[i * A->ncols + j];
        }
    }

    return result;
}
// Function to transpose a matrix
static matd_t* transpose(const matd_t* A) {
    matd_t* result = malloc(sizeof(matd_t) + A->nrows * A->ncols * sizeof(double));
    result->nrows = A->ncols;
    result->ncols = A->nrows;

    for (unsigned int i = 0; i < A->nrows; ++i) {
        for (unsigned int j = 0; j < A->ncols; ++j) {
            result->data[j * A->nrows + i] = A->data[i * A->ncols + j];
        }
    }

    return result;
}

// Function to subtract two matrices
static matd_t* subtractMatrices_Custom(const matd_t* A, const matd_t* B) {
    if (A->nrows != B->nrows || A->ncols != B->ncols) {
        fprintf(stderr, "Matrix dimensions do not match for subtraction\n");
        return NULL;
    }

    matd_t* result = malloc(sizeof(matd_t) + A->nrows * A->ncols * sizeof(double));
    result->nrows = A->nrows;
    result->ncols = A->ncols;

    for (unsigned int i = 0; i < A->nrows; ++i) {
        for (unsigned int j = 0; j < A->ncols; ++j) {
            result->data[i * A->ncols + j] = A->data[i * A->ncols + j] - B->data[i * A->ncols + j];
        }
    }

    return result;
}

// Function to negate a matrix
static matd_t* negateMatrix(const matd_t* A) {
    matd_t* result = matd_create(A->nrows, A->ncols);

    for (unsigned int i = 0; i < A->nrows; ++i) {
        for (unsigned int j = 0; j < A->ncols; ++j) {
            MATD_EL(result, i, j) = -MATD_EL(A, i, j);
        }
    }

    return result;
}

static apriltag_pose_t* calculateCameraPosition(apriltag_pose_t pose, TagPose* tagPose) {
    // Convert Euler angles to radians
    double yaw = tagPose->tagYaw * M_PI / 180.0;
    double pitch = tagPose->tagPitch * M_PI / 180.0;
    double roll = tagPose->tagRoll * M_PI / 180.0;

    // Create rotation matrix for the tag
    matd_t* tagRotation = eulerToRotation(yaw, pitch, roll);

    // Create translation matrix for the tag
    matd_t* tagTranslation = createTranslation(tagPose->tagX, tagPose->tagY, tagPose->tagZ);

    // Combine rotation and translation matrices for the tag
    matd_t* tagTransform = addMatrices_Custom(tagRotation, tagTranslation);

    // Invert the pose transformation matrix (since pose represents the transformation from camera to tag)
    matd_t* inversePoseRotation = transpose(pose.R);
    matd_t* inversePoseTranslation = negateMatrix(pose.t);
    inversePoseTranslation = multiplyMatrices_Custom(inversePoseRotation, inversePoseTranslation);
    matd_t* rotatedInversePoseTranslation = multiplyMatrices_Custom(tagRotation, inversePoseTranslation);

    // Combine inverted camera pose and tag pose
    matd_t* cameraRotation = multiplyMatrices_Custom(inversePoseRotation, tagRotation);
    matd_t* cameraTranslation = addMatrices_Custom(tagTranslation, rotatedInversePoseTranslation);


    pose.R = cameraRotation;
    pose.t = cameraTranslation;

    // Cleanup: Free allocated matrices
    free(tagRotation);
    free(tagTranslation);
    free(tagTransform);
    free(cameraRotation);
    free(cameraTranslation);

    return &pose;
}