// Copyright 2026 PICO. All rights reserved.
package com.pico.spatial.ml.sample.facelandmarker

import kotlin.math.exp
import kotlin.math.max

/** Expression-only joint motion, independent of pose placement and surface uploads. */
class FaceLionMotion(names: List<String>) {
    private val channels = names.withIndex().associate { it.value to it.index }
    private var generation = -1L
    private var capture = Double.NEGATIVE_INFINITY
    private var measuredAt = Double.NEGATIVE_INFINITY
    private var lastTime = 0L
    private var target = Pose()
    var pose = Pose()
        private set

    data class Pose(
        val jaw: Float = 0f,
        val leftLid: Float = 0f,
        val rightLid: Float = 0f,
        val leftYaw: Float = 0f,
        val rightYaw: Float = 0f,
        val leftPitch: Float = 0f,
        val rightPitch: Float = 0f,
    )

    fun offer(face: TrackedFace) {
        if (generation != face.generation) {
            reset()
            generation = face.generation
        }
        val frame = face.expressionFrame ?: return
        val readback = frame.readbackSeconds ?: return
        if (
            !frame.captureSeconds.isFinite() ||
                !readback.isFinite() ||
                frame.captureSeconds <= capture
        )
            return
        capture = frame.captureSeconds
        measuredAt = readback
        fun coefficient(name: String): Float =
            channels[name]
                ?.let { face.expressions.getOrNull(it) }
                ?.takeIf { it.isFinite() }
                ?.coerceIn(0f, 1f) ?: 0f
        fun lid(side: String) =
            2.60f * max(coefficient("eyeBlink$side"), .45f * coefficient("eyeSquint$side")) -
                .10f * coefficient("eyeWide$side")
        fun yaw(side: String, sign: Float) =
            sign * .22f * (coefficient("eyeLookOut$side") - coefficient("eyeLookIn$side"))
        fun pitch(side: String) =
            .18f * (coefficient("eyeLookDown$side") - coefficient("eyeLookUp$side"))
        target =
            Pose(
                .72f * coefficient("jawOpen") * (1f - coefficient("mouthClose")),
                lid("Left"),
                lid("Right"),
                yaw("Left", 1f),
                yaw("Right", -1f),
                pitch("Left"),
                pitch("Right"),
            )
    }

    fun advance(frameTimeNanos: Long, now: Double): Pose {
        if (!now.isFinite() || now - measuredAt !in 0.0..EXPRESSION_HOLD_SECONDS) target = Pose()
        val dt =
            if (lastTime == 0L) 1.0 / 60
            else ((frameTimeNanos - lastTime) * 1e-9).coerceIn(0.0, .06)
        lastTime = frameTimeNanos
        val jawAlpha = (1 - exp(-22 * dt)).toFloat()
        val eyeAlpha = (1 - exp(-38 * dt)).toFloat()
        fun mix(a: Float, b: Float, alpha: Float) = a + (b - a) * alpha
        pose =
            Pose(
                mix(pose.jaw, target.jaw, jawAlpha),
                mix(pose.leftLid, target.leftLid, eyeAlpha),
                mix(pose.rightLid, target.rightLid, eyeAlpha),
                mix(pose.leftYaw, target.leftYaw, eyeAlpha),
                mix(pose.rightYaw, target.rightYaw, eyeAlpha),
                mix(pose.leftPitch, target.leftPitch, eyeAlpha),
                mix(pose.rightPitch, target.rightPitch, eyeAlpha),
            )
        return pose
    }

    fun reset() {
        capture = Double.NEGATIVE_INFINITY
        measuredAt = Double.NEGATIVE_INFINITY
        lastTime = 0L
        target = Pose()
        pose = Pose()
    }

    companion object {
        const val EXPRESSION_HOLD_SECONDS = .25
    }
}
