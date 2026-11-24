// Copyright 2026 PICO. All rights reserved.
package com.pico.spatial.ml.sample.facelandmarker

import com.pico.spatial.ml.sample.perception.StageRegistration

/** One completed exposure. The runner registers spatial data into Stage before publishing it. */
data class FaceFrame(
    val sequence: Int,
    val hasFace: Boolean,
    val captureSeconds: Double,
    val readbackSeconds: Double,
    val freshCameraFrame: Boolean,
    val trackingIdentity: Float,
    val needsDetection: Boolean,
    val captureCameraPose: FloatArray,
    val pose: FloatArray,
    val vertices: FloatArray,
    val outputs: FaceOutputs,
    val blendshapes: FloatArray = FloatArray(0),
) {
    fun inStage(serviceToStage: FloatArray): FaceFrame =
        if (!hasFace) this
        else
            copy(
                pose = StageRegistration.multiply(serviceToStage, pose),
                vertices =
                    if (outputs.mesh) StageRegistration.transformVertices(serviceToStage, vertices)
                    else FloatArray(0),
            )

    fun withPresentationGeometry(filtered: FacePoseMeshStabilizer.Result): FaceFrame =
        copy(
            pose = filtered.pose,
            vertices = if (outputs.mesh) filtered.vertices else FloatArray(0),
        )
}
