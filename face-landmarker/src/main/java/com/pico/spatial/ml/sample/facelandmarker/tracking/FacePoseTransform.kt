// Copyright 2026 PICO. All rights reserved.
package com.pico.spatial.ml.sample.facelandmarker

import kotlin.math.abs
import kotlin.math.sqrt

/** Decomposes the measured row-major face-to-Stage similarity without changing handedness. */
object FacePoseTransform {
    data class Pose(val position: FloatArray, val quaternion: FloatArray, val scale: Float)

    fun decompose(matrix: FloatArray): Pose {
        require(matrix.size == 16 && matrix.all(Float::isFinite))
        require((12..14).all { abs(matrix[it]) < .001f } && abs(matrix[15] - 1f) < .001f)
        val scale = sqrt((0..2).sumOf { matrix[it * 4].toDouble() * matrix[it * 4] })
        require(scale in .05..20.0)
        val r = DoubleArray(9) { matrix[it / 3 * 4 + it % 3] / scale }
        for (a in 0..2) for (b in 0..2) {
            require(
                abs((0..2).sumOf { r[it * 3 + a] * r[it * 3 + b] } - if (a == b) 1.0 else 0.0) <
                    .002
            )
        }
        val determinant =
            r[0] * (r[4] * r[8] - r[5] * r[7]) - r[1] * (r[3] * r[8] - r[5] * r[6]) +
                r[2] * (r[3] * r[7] - r[4] * r[6])
        require(determinant > .99)
        val q = DoubleArray(4)
        val trace = r[0] + r[4] + r[8]
        if (trace > 0) {
            val t = 2 * sqrt(1 + trace)
            q[0] = (r[7] - r[5]) / t
            q[1] = (r[2] - r[6]) / t
            q[2] = (r[3] - r[1]) / t
            q[3] = t / 4
        } else {
            val i = (0..2).maxBy { r[it * 3 + it] }
            val j = (i + 1) % 3
            val k = (j + 1) % 3
            val t = 2 * sqrt(1 + r[i * 3 + i] - r[j * 3 + j] - r[k * 3 + k])
            q[i] = t / 4
            q[j] = (r[i * 3 + j] + r[j * 3 + i]) / t
            q[k] = (r[i * 3 + k] + r[k * 3 + i]) / t
            q[3] = (r[k * 3 + j] - r[j * 3 + k]) / t
        }
        val length = sqrt(q.sumOf { it * it })
        return Pose(
            floatArrayOf(matrix[3], matrix[7], matrix[11]),
            FloatArray(4) { (q[it] / length).toFloat() },
            scale.toFloat(),
        )
    }
}
