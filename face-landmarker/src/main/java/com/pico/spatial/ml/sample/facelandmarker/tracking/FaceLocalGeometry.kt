// Copyright 2026 PICO. All rights reserved.
package com.pico.spatial.ml.sample.facelandmarker

/**
 * Remove the displayed similarity pose once; the tracked head Entity reapplies it to both meshes.
 */
object FaceLocalGeometry {
    fun localVertices(stageVertices: FloatArray, faceToStage: FloatArray): FloatArray {
        require(stageVertices.size == 478 * 3 && stageVertices.all(Float::isFinite))
        val pose = FacePoseTransform.decompose(faceToStage)
        val squaredScale = pose.scale.toDouble() * pose.scale
        return FloatArray(stageVertices.size) { index ->
            val vertex = index / 3 * 3
            val axis = index % 3
            var value = 0.0
            for (row in 0..2) {
                value +=
                    faceToStage[row * 4 + axis] *
                        (stageVertices[vertex + row].toDouble() - faceToStage[row * 4 + 3])
            }
            (value / squaredScale).toFloat()
        }
    }
}
