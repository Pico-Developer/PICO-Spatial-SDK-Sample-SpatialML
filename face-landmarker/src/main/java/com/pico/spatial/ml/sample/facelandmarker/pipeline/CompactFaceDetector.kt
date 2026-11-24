// Copyright 2026 PICO. All rights reserved.
package com.pico.spatial.ml.sample.facelandmarker

/** The compact camera view samples source pixels; ROI state uses calibrated image pixels. */
object CompactFaceDetector {
    const val VST_SIZE = 336
    const val VIEW_HEIGHT = 256
    const val CROP_X = 40f

    fun toImage(imageTransform: FloatArray): FloatArray {
        require(imageTransform.size == 9 && imageTransform.all { it.isFinite() })
        // The supported camera profile is an axis-aligned scale and translation. A rotated
        // mapping needs a full affine detector contract rather than these four scalars.
        require(
            imageTransform[1] == 0f &&
                imageTransform[3] == 0f &&
                imageTransform[6] == 0f &&
                imageTransform[7] == 0f &&
                imageTransform[8] == 1f &&
                imageTransform[0] > 0f &&
                imageTransform[4] > 0f
        )
        val scaleX = FaceModelContract.WIDTH.toFloat() / VST_SIZE
        val scaleY = FaceModelContract.HEIGHT.toFloat() / VIEW_HEIGHT
        return floatArrayOf(
            imageTransform[0] * scaleX,
            imageTransform[4] * scaleY,
            imageTransform[0] * CROP_X * scaleX + imageTransform[2],
            imageTransform[5],
        )
    }
}
