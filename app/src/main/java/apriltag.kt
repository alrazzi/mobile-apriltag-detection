package com.example.android.camerax.video

class apriltag {
    companion object{
        init {
            System.loadLibrary("ApriltagTower")
        }
        open external fun stringFromJNI(imageData: ByteArray, width: Int, height: Int): String
    }
}