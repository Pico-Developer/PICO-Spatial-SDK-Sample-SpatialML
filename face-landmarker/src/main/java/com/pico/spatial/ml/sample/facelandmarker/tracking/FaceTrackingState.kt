// Copyright 2026 PICO. All rights reserved.
package com.pico.spatial.ml.sample.facelandmarker

/** A retained measurement, shared by every effect and the independently paced surface uploader. */
data class TrackedFace(
    val frame: FaceFrame,
    val generation: Long,
    val localVertices: FloatArray,
    val expressions: FloatArray,
    val surfaceFrame: FaceFrame? = frame.takeIf { it.outputs.mesh },
    val expressionFrame: FaceFrame? = frame.takeIf { it.outputs.blendshapes },
)

/** Brief misses do not revoke a good pose. Only a fresh, advancing measurement extends its life. */
class FaceTrackingState {
    private var latest: TrackedFace? = null
    private var generation = 0L

    fun offer(frame: FaceFrame?, now: Double): TrackedFace? {
        val readback = frame?.readbackSeconds ?: return null
        if (!now.isFinite() || !readback.isFinite() || now - readback !in 0.0..HOLD_SECONDS)
            return null
        if (!frame.hasFace || !frame.freshCameraFrame) return null
        if (!frame.captureSeconds.isFinite() || !frame.trackingIdentity.isFinite()) return null
        val old = latest
        val changedIdentity =
            old == null ||
                now - checkNotNull(old.frame.readbackSeconds) > HOLD_SECONDS ||
                frame.trackingIdentity != old.frame.trackingIdentity
        val retained = old?.takeUnless { changedIdentity }
        val advances = retained == null || frame.captureSeconds > retained.frame.captureSeconds
        if (
            !advances &&
                (frame.captureSeconds != retained.frame.captureSeconds ||
                    frame.sequence != retained.frame.sequence)
        )
            return null
        if (
            advances && retained != null && readback <= checkNotNull(retained.frame.readbackSeconds)
        )
            return null
        val newSurface = frame.outputs.mesh && (retained?.surfaceFrame?.sequence != frame.sequence)
        val newExpressions =
            frame.outputs.blendshapes && (retained?.expressionFrame?.sequence != frame.sequence)
        if (!advances && !newSurface && !newExpressions) return null
        FacePoseTransform.decompose(frame.pose)
        if (changedIdentity) generation++
        // A later surface/expression packet for this exposure does not extend the pose hold.
        val poseFrame = if (advances) frame else checkNotNull(retained).frame
        val surfaceFrame = if (newSurface) frame else retained?.surfaceFrame
        val retainedExpressions =
            retained?.expressionFrame?.takeIf {
                poseFrame.captureSeconds - it.captureSeconds in 0.0..HOLD_SECONDS
            }
        val expressionsFrame = if (newExpressions) frame else retainedExpressions
        return TrackedFace(
                poseFrame,
                generation,
                if (newSurface) FaceLocalGeometry.localVertices(frame.vertices, frame.pose)
                else retained?.localVertices ?: FloatArray(0),
                if (newExpressions) frame.blendshapes
                else if (retainedExpressions != null) checkNotNull(retained).expressions
                else FloatArray(0),
                surfaceFrame,
                expressionsFrame,
            )
            .also { latest = it }
    }

    fun current(now: Double): TrackedFace? {
        val face = latest ?: return null
        if (
            !now.isFinite() || now - checkNotNull(face.frame.readbackSeconds) !in 0.0..HOLD_SECONDS
        ) {
            clear()
            return null
        }
        return face
    }

    fun clear() {
        latest = null
        generation++
    }

    companion object {
        const val HOLD_SECONDS = .35
    }
}
