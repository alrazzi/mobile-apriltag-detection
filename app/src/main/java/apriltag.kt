package com.example.android.camerax.video

import edu.wpi.first.math.geometry.Pose3d
import edu.wpi.first.math.geometry.Rotation3d
import edu.wpi.first.math.geometry.Translation3d

class apriltag {
    companion object{
        init {
            System.loadLibrary("ApriltagTower")

        }
        open external fun getApriltagResult(imageData: Array<IntArray>, width: Int, height: Int, ids: IntArray): Result

        var apriltagMap: HashMap<Int, Pose3d> = hashMapOf(
                1 to Pose3d(Translation3d(0.0,0.0,0.0), Rotation3d(0.0,0.0,0.0)),
                2 to Pose3d(Translation3d(0.0,0.0,0.0), Rotation3d(0.0,0.0,0.0)),
                3 to Pose3d(Translation3d(0.0,0.0,0.0), Rotation3d(0.0,0.0,0.0)),
        )
    }
}