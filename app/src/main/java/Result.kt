package com.example.android.camerax.video;

public class Result {
    var id: Int = 0;
    var yaw: Double = 0.0;
    var dist: Double = 0.0;
    var numDetections: Int = 0;
    var camToTargetPacket: TransformPacket? = null;
    var isTagDetected: Boolean = false; // Default value is false

    // Existing constructor
    constructor(id: Int, yaw: Double, dist: Double, numDetections: Int, camToTargetPacket: TransformPacket) {
        this.id = id;
        this.yaw = yaw;
        this.dist = dist;
        this.numDetections = numDetections;
        this.camToTargetPacket = camToTargetPacket;
        this.isTagDetected = true;
    }

    // Additional constructor to set isTagDetected
    constructor(isTagDetected: Boolean) {
        this.isTagDetected = isTagDetected;
    }
}