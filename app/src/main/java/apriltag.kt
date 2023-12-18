package com.example.android.camerax.video

class apriltag {
    companion object{
        init {
            System.loadLibrary("ApriltagTower")
        }
        open external fun getApriltagResult(imageData: Array<IntArray>, width: Int, height: Int): Result
    }
}