// Copyright 2026 PICO. All rights reserved.
package com.pico.spatial.ml.sample.facelandmarker

import com.pico.spatial.ml.sample.perception.StageRegistration
import kotlin.math.*

/** Filters a Stage similarity pose and face-local shape; input measurements stay untouched. */
class FacePoseMeshStabilizer {
    data class Result(val vertices: FloatArray, val pose: FloatArray)

    private data class Fit(val p: DoubleArray, val q: DoubleArray, val s: Double)

    private var rawFit: Fit? = null
    private var filteredFit: Fit? = null
    private var local: FloatArray? = null
    private var rawLocal: FloatArray? = null
    private var velocity = DoubleArray(3)
    private var angularVelocity = DoubleArray(3)
    private var scaleVelocity = 0.0
    private var time = Double.NaN
    private var shapeTime = Double.NaN
    private var identity = Float.NaN
    private var context: Any? = null
    private var pending: Fit? = null
    private var pendingSince = 0.0
    private var pendingCaptureSeconds = Double.NaN

    fun reset() {
        rawFit = null
        filteredFit = null
        local = null
        rawLocal = null
        pending = null
        time = Double.NaN
        shapeTime = Double.NaN
        pendingCaptureSeconds = Double.NaN
        velocity.fill(0.0)
        angularVelocity.fill(0.0)
        scaleVelocity = 0.0
    }

    fun update(
        vertices: FloatArray,
        pose: FloatArray,
        captureSeconds: Double,
        trackingIdentity: Float,
        configuration: Any,
    ): Result {
        require(vertices.size % 3 == 0 && vertices.all(Float::isFinite))
        require(captureSeconds.isFinite() && trackingIdentity.isFinite())
        val measured = fit(pose)
        val hasShape = vertices.isNotEmpty()
        val shape = if (hasShape) inverse(measured, vertices) else null
        val old = filteredFit
        val previous = rawFit
        val dt = captureSeconds - time
        val resetReason =
            when {
                old == null || previous == null -> "first_frame"
                context != configuration -> "configuration"
                identity != trackingIdentity -> "identity"
                hasShape && local != null && local?.size != shape?.size -> "topology"
                dt <= 0 || dt > .25 -> "capture_gap"
                else -> null
            }
        fun accept(): Result {
            rawFit = measured
            filteredFit = measured
            local = shape?.copyOf()
            rawLocal = shape?.copyOf()
            shapeTime = if (hasShape) captureSeconds else Double.NaN
            velocity.fill(0.0)
            angularVelocity.fill(0.0)
            scaleVelocity = 0.0
            pending = null
            time = captureSeconds
            identity = trackingIdentity
            context = configuration
            return Result(vertices.copyOf(), pose.copyOf())
        }
        // The optional surface packet can follow an already published pose from the same capture.
        // Update only shape; one exposure must never advance the pose filter twice.
        if (
            old != null &&
                previous != null &&
                dt == 0.0 &&
                context == configuration &&
                identity == trackingIdentity &&
                distance(measured.p, previous.p) < 1e-7 &&
                angle(measured.q, previous.q) < 1e-6 &&
                abs(ln(measured.s / previous.s)) < 1e-7
        ) {
            val (smoothed, _) = updateShape(shape, captureSeconds)
            val output = transform(old, smoothed)
            return Result(output, matrix(old))
        }
        if (resetReason != null) return accept()
        val last = checkNotNull(previous)
        val before = checkNotNull(old)
        // A same-track isolated bad fit should not snap the face to another depth. Confirm
        // two agreeing fits, or stop holding after 120 ms. Lost tracking still resets upstream.
        val jump =
            distance(measured.p, last.p) > .08 ||
                angle(measured.q, last.q) > .44 ||
                abs(ln(measured.s / last.s)) > .12
        if (jump) {
            val candidate = pending
            if (
                candidate != null &&
                    captureSeconds > pendingCaptureSeconds &&
                    (distance(candidate.p, measured.p) < .035 &&
                        angle(candidate.q, measured.q) < .15 &&
                        abs(ln(candidate.s / measured.s)) < .05)
            )
                return accept()
            if (candidate != null && captureSeconds - pendingSince >= .12) return accept()
            if (candidate == null) pendingSince = captureSeconds
            pending = measured
            pendingCaptureSeconds = captureSeconds
            val held =
                if (hasShape) transform(before, local ?: checkNotNull(shape)) else FloatArray(0)
            return Result(held, matrix(before))
        }
        pending = null
        val derivative = alpha(DERIVATIVE_CUTOFF_HZ, dt)
        for (i in 0..2) velocity[i] += derivative * ((measured.p[i] - last.p[i]) / dt - velocity[i])
        val speed = max(0.0, norm(velocity) - .025)
        var pa = alpha(3.0 + TRANSLATION_SPEED_COEFFICIENT * speed, dt)
        val error = distance(before.p, measured.p)
        if (error > .03) pa = max(pa, 1.0 - .03 / error)
        val p = DoubleArray(3) { before.p[it] + pa * (measured.p[it] - before.p[it]) }
        // Use a signed quaternion log derivative: alternating orientation noise cancels rather
        // than being interpreted as consistently fast angular movement.
        val relative = multiply(conjugate(last.q), align(measured.q, last.q))
        val vectorLength = norm(relative.copyOfRange(0, 3))
        val signedAngle = 2.0 * atan2(vectorLength, relative[3])
        for (i in 0..2) {
            val measuredVelocity =
                if (vectorLength > 1e-8) relative[i] * signedAngle / vectorLength / dt else 0.0
            angularVelocity[i] += derivative * (measuredVelocity - angularVelocity[i])
        }
        var qa = alpha(3.0 + ANGULAR_SPEED_COEFFICIENT * max(0.0, norm(angularVelocity) - .05), dt)
        val rotationError = angle(before.q, measured.q)
        if (rotationError > .0873) qa = max(qa, 1.0 - .0873 / rotationError)
        val q = slerp(before.q, measured.q, qa)
        scaleVelocity += derivative * (ln(measured.s / last.s) / dt - scaleVelocity)
        var sa = alpha(.7 + 3.0 * max(0.0, abs(scaleVelocity) - .02), dt)
        val scaleError = abs(ln(measured.s / before.s))
        if (scaleError > .03) sa = max(sa, 1.0 - .03 / scaleError)
        val scale = exp(ln(before.s) + sa * ln(measured.s / before.s))
        val (smoothed, _) = updateShape(shape, captureSeconds)
        val filtered = Fit(p, q, scale)
        val output = transform(filtered, smoothed)
        rawFit = measured
        filteredFit = filtered
        time = captureSeconds
        return Result(output, matrix(filtered))
    }

    private fun updateShape(shape: FloatArray?, captureSeconds: Double): Pair<FloatArray, Double> {
        if (shape == null) return FloatArray(0) to 0.0
        val oldShape = local
        val previous = rawLocal
        val dt = captureSeconds - shapeTime
        if (
            oldShape == null ||
                previous == null ||
                oldShape.size != shape.size ||
                dt <= 0 ||
                dt > .25
        ) {
            local = shape.copyOf()
            rawLocal = shape.copyOf()
            shapeTime = captureSeconds
            return shape.copyOf() to 1.0
        }
        // Face-local shape has its own capture interval, independent of intervening pose packets.
        val shapeSpeed = rms(shape, previous) / dt
        val shapeAlpha = alpha(2.0 + 6.0 * max(0.0, shapeSpeed - .02), dt)
        val smoothed = FloatArray(shape.size)
        for (point in 0 until shape.size / 3) {
            val j = point * 3
            val delta = sqrt((0..2).sumOf { (shape[j + it].toDouble() - oldShape[j + it]).pow(2) })
            val a = max(shapeAlpha, if (delta > .008) 1.0 - .008 / delta else 0.0)
            for (axis in 0..2) smoothed[j + axis] =
                (oldShape[j + axis] + a * (shape[j + axis] - oldShape[j + axis])).toFloat()
        }
        rawLocal = shape.copyOf()
        local = smoothed
        shapeTime = captureSeconds
        return smoothed to shapeAlpha
    }

    private fun fit(m: FloatArray): Fit {
        require(m.size == 16 && m.all(Float::isFinite))
        val s = sqrt((0..2).sumOf { m[it * 4].toDouble().pow(2) })
        require(s in .05..20.0)
        val r = DoubleArray(9) { m[(it / 3) * 4 + it % 3] / s }
        require(
            (0..2).all { a ->
                (0..2).all { b ->
                    abs((0..2).sumOf { r[it * 3 + a] * r[it * 3 + b] } - if (a == b) 1.0 else 0.0) <
                        .002
                }
            }
        )
        val det =
            r[0] * (r[4] * r[8] - r[5] * r[7]) - r[1] * (r[3] * r[8] - r[5] * r[6]) +
                r[2] * (r[3] * r[7] - r[4] * r[6])
        require(det > .99 && abs(m[15] - 1) < .001 && (12..14).all { abs(m[it]) < .001 })
        val trace = r[0] + r[4] + r[8]
        val q: DoubleArray
        if (trace > 0) {
            val t = 2 * sqrt(trace + 1)
            q = doubleArrayOf((r[7] - r[5]) / t, (r[2] - r[6]) / t, (r[3] - r[1]) / t, t / 4)
        } else {
            val i = (0..2).maxBy { r[it * 3 + it] }
            val j = (i + 1) % 3
            val k = (j + 1) % 3
            val t = 2 * sqrt(1 + r[i * 3 + i] - r[j * 3 + j] - r[k * 3 + k])
            q = DoubleArray(4)
            q[i] = t / 4
            q[j] = (r[i * 3 + j] + r[j * 3 + i]) / t
            q[k] = (r[i * 3 + k] + r[k * 3 + i]) / t
            q[3] = (r[k * 3 + j] - r[j * 3 + k]) / t
        }
        val n = norm(q)
        return Fit(
            doubleArrayOf(m[3].toDouble(), m[7].toDouble(), m[11].toDouble()),
            q.map { it / n }.toDoubleArray(),
            s,
        )
    }

    private fun matrix(f: Fit): FloatArray {
        val (x, y, z, w) = f.q
        return doubleArrayOf(
                1 - 2 * (y * y + z * z),
                2 * (x * y - z * w),
                2 * (x * z + y * w),
                0.0,
                2 * (x * y + z * w),
                1 - 2 * (x * x + z * z),
                2 * (y * z - x * w),
                0.0,
                2 * (x * z - y * w),
                2 * (y * z + x * w),
                1 - 2 * (x * x + y * y),
                0.0,
                0.0,
                0.0,
                0.0,
                1.0,
            )
            .mapIndexed { i, v ->
                if (i < 12) (if (i % 4 == 3) f.p[i / 4] else v * f.s).toFloat() else v.toFloat()
            }
            .toFloatArray()
    }

    private fun inverse(f: Fit, v: FloatArray): FloatArray {
        val m = matrix(f)
        return FloatArray(v.size) { i ->
            val axis = i % 3
            val j = i / 3 * 3
            ((0..2).sumOf { m[it * 4 + axis] * (v[j + it] - f.p[it]) } / (f.s * f.s)).toFloat()
        }
    }

    private fun transform(f: Fit, v: FloatArray) = StageRegistration.transformVertices(matrix(f), v)

    private fun alpha(hz: Double, dt: Double) = 1 - exp(-2 * PI * hz * dt)

    private fun norm(v: DoubleArray) = sqrt(v.sumOf { it * it })

    private fun distance(a: DoubleArray, b: DoubleArray) =
        norm(DoubleArray(a.size) { a[it] - b[it] })

    private fun dot(a: DoubleArray, b: DoubleArray) = a.indices.sumOf { a[it] * b[it] }

    private fun align(a: DoubleArray, b: DoubleArray) =
        if (dot(a, b) < 0) a.map { -it }.toDoubleArray() else a

    private fun angle(a: DoubleArray, b: DoubleArray) = 2 * acos(abs(dot(a, b)).coerceIn(0.0, 1.0))

    private fun conjugate(q: DoubleArray) = doubleArrayOf(-q[0], -q[1], -q[2], q[3])

    private fun multiply(a: DoubleArray, b: DoubleArray) =
        doubleArrayOf(
            a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
            a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
            a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
            a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2],
        )

    private fun slerp(a: DoubleArray, b: DoubleArray, t: Double): DoubleArray {
        val q = align(b, a)
        val d = dot(a, q).coerceIn(-1.0, 1.0)
        val theta = acos(d)
        val result =
            if (theta < .001) DoubleArray(4) { a[it] + t * (q[it] - a[it]) }
            else
                DoubleArray(4) {
                    (sin((1 - t) * theta) * a[it] + sin(t * theta) * q[it]) / sin(theta)
                }
        val n = norm(result)
        return result.map { it / n }.toDoubleArray()
    }

    private fun rms(a: FloatArray, b: FloatArray) =
        if (a.isEmpty()) 0.0
        else sqrt(a.indices.sumOf { (a[it].toDouble() - b[it]).pow(2) } / (a.size / 3))

    companion object {
        const val DERIVATIVE_CUTOFF_HZ = 1.5
        const val TRANSLATION_SPEED_COEFFICIENT = 12.0
        const val ANGULAR_SPEED_COEFFICIENT = 2.5
    }
}
