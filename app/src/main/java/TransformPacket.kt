package com.example.android.camerax.video


import edu.wpi.first.math.Matrix
import edu.wpi.first.math.Nat
import edu.wpi.first.math.geometry.Rotation3d
import edu.wpi.first.math.geometry.Transform3d
import edu.wpi.first.math.geometry.Translation3d


public class TransformPacket(
        val x: Double,
        val y: Double,
        val z: Double,
        val r1: Double,
        val r2: Double,
        val r3: Double,
        val r4: Double,
        val r5: Double,
        val r6: Double,
        val r7: Double,
        val r8: Double,
        val r9: Double
) {
        fun getTransform3d(): Transform3d{
            var translation = Translation3d(x, y,z);

            val rotArr: DoubleArray = doubleArrayOf(
                    r1, r2, r3,
                    r4, r5, r6,
                    r7, r8, r9)
            val rotMat = Matrix.mat(Nat.N3(), Nat.N3()).fill(
                    r1, r2, r3,
                    r4, r5, r6,
                    r7, r8, r9
            )
            var rotation = Rotation3d(rotMat);

            return Transform3d(translation, rotation);
        }
    }