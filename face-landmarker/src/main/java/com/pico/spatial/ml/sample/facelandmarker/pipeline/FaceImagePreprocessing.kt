// Copyright 2026 PICO. All rights reserved.
package com.pico.spatial.ml.sample.facelandmarker

import com.bytedance.pico.openmr.spatial.pack.utils.ModelEncoding
import com.pico.spatial.ml.sample.perception.SpatialMLGraphSession.GraphPipeline
import com.pico.spatial.ml.sample.perception.SpatialMLGraphSession.GraphTensor

/** Inverse-affine RGB sampling and /255 normalization to float32 [1,256,256,3]. */
fun GraphPipeline.preprocess(image: GraphTensor, affine: GraphTensor): GraphTensor =
    image(256, float = true).also { result ->
        model(
            "face_landmark_image_preprocessing",
            mapOf("image" to image, "affine" to affine),
            mapOf("model_input" to result),
            FaceModelContract.PREPROCESSING_BACKEND,
            mapOf("image" to ModelEncoding.EncodingType.UNSIGNED_FIXED_POINT8),
        )
    }

/** Restore the logical 336x256 view from square VST output, then center-crop to 256 square. */
fun GraphPipeline.compactDetectorInput(image: GraphTensor): GraphTensor {
    require(
        image.shape.contentEquals(
            intArrayOf(CompactFaceDetector.VST_SIZE, CompactFaceDetector.VST_SIZE)
        )
    )
    return image(256, float = true).also { result ->
        model(
            "face_detector_image_preprocessing",
            mapOf("image" to image),
            mapOf("model_input" to result),
            FaceModelContract.PREPROCESSING_BACKEND,
            mapOf("image" to ModelEncoding.EncodingType.UNSIGNED_FIXED_POINT8),
        )
    }
}
