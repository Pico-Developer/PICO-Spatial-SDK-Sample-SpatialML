// Copyright 2026 PICO. All rights reserved.
package com.pico.spatial.ml.sample.facelandmarker

import kotlin.math.abs

/**
 * Crown estimate extends above the forehead landmark; the face-pose origin is not the skull top.
 */
object FaceHeadPlacement {
    data class Anchors(
        val crownY: Float,
        val centerX: Float,
        val centerZ: Float,
        val height: Float,
    )

    fun anchors(local: FloatArray): Anchors {
        require(local.size >= 468 * 3 && local.all(Float::isFinite))
        val foreheadY = local[10 * 3 + 1]
        val chinY = local[152 * 3 + 1]
        val faceHeight = foreheadY - chinY
        require(faceHeight in 0.05f..0.5f)
        val skullWidth = abs(local[356 * 3] - local[127 * 3])
        require(skullWidth in 0.05f..0.4f)
        return Anchors(
            // Upper temples stay with the cranium; an opening jaw must not lift the cap.
            foreheadY + skullWidth * .55f,
            (local[127 * 3] + local[356 * 3]) * .5f,
            (local[127 * 3 + 2] + local[356 * 3 + 2]) * .5f,
            faceHeight,
        )
    }
}
