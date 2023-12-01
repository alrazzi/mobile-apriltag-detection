#ifndef POSE_MATH_H
#define POSE_MATH_H

#include "include/apriltag_pose.h"

// Structure to represent a pose of a tag
typedef struct {
    double tagX;
    double tagY;
    double tagZ;
    double tagYaw;
    double tagPitch;
    double tagRoll;
} TagPose;

// Function to create a rotation matrix from Euler angles
static matd_t* eulerToRotation(double yaw, double pitch, double roll);

// Function to create a translation matrix
static matd_t* createTranslation(double x, double y, double z);

// Function to multiply two matrices
static matd_t* multiplyMatrices_Custom(const matd_t* A, const matd_t* B);

// Function to add two matrices
static matd_t* addMatrices_Custom(const matd_t* A, const matd_t* B);

// Function to calculate camera position based on tag pose
static void calculateCameraPosition(const apriltag_pose_t* pose, const TagPose* tagPose);

#endif // POSE_MATH_H