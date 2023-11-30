#ifndef POSE_MATH_H
#define POSE_MATH_H

// Include necessary headers if needed

// Define the TagPose struct
typedef struct {
    double tagX;
    double tagY;
    double tagZ;
    double tagYaw;
    double tagPitch;
    double tagRoll;
} TagPose;

// Function declarations
TagPose createTagPose(double x, double y, double z, double yaw, double pitch, double roll);
double getTagX(const TagPose* pose);
double getTagY(const TagPose* pose);
double getTagZ(const TagPose* pose);
double getTagYaw(const TagPose* pose);
double getTagPitch(const TagPose* pose);
double getTagRoll(const TagPose* pose);

#endif //POSE_MATH_H