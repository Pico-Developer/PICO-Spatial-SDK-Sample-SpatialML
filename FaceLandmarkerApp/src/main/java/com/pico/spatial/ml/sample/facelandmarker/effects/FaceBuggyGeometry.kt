// Copyright 2026 PICO. All rights reserved.
package com.pico.spatial.ml.sample.facelandmarker

import kotlin.math.*

/** Retained, sculpted features inspired by the user's bulging-eye and toothy-grin reference. */
object FaceBuggyGeometry {
    const val EYE_RADIUS = .020f
    const val IRIS_RADIUS = .0027f
    const val HALF_GRIN = .048f

    enum class Mount {
        EYE,
        GAZE,
        BROW,
        MOUTH,
    }

    enum class Finish(val color: FloatArray, val roughness: Float) {
        WHITE(floatArrayOf(.97f, .95f, .91f, 1f), .20f),
        IRIS(floatArrayOf(.14f, .045f, .085f, 1f), .24f),
        PUPIL(floatArrayOf(.012f, .006f, .012f, 1f), .16f),
        GLINT(floatArrayOf(1f, 1f, 1f, 1f), .12f),
        SKIN(floatArrayOf(.64f, .44f, .37f, 1f), .63f),
        LIP(floatArrayOf(.53f, .31f, .28f, 1f), .43f),
        CREASE(floatArrayOf(.22f, .10f, .085f, 1f), .72f),
        BROW(floatArrayOf(.09f, .065f, .053f, 1f), .86f),
        MOUTH(floatArrayOf(.045f, .010f, .016f, 1f), .88f),
        GUM(floatArrayOf(.30f, .10f, .115f, 1f), .48f),
        ENAMEL(floatArrayOf(.95f, .91f, .79f, 1f), .26f),
    }

    data class Part(val mesh: FaceEffectMesh, val mount: Mount, val finish: Finish)

    data class Tooth(
        val x: Float,
        val y: Float,
        val z: Float,
        val rx: Float,
        val ry: Float,
        val rz: Float,
        val upper: Boolean,
    )

    fun top(u: Float) = .012f + .010f * u * u

    fun bottom(u: Float) = -.025f + .047f * u * u

    fun depth(u: Float) = .010f - .014f * u * u

    fun teeth(): List<Tooth> = buildList {
        for (side in listOf(-1f, 1f)) {
            val centers = floatArrayOf(.0051f, .0150f, .0239f, .0317f, .0385f)
            for (i in centers.indices) {
                val x = centers[i] * side
                val ry =
                    minOf(.0107f - i * .00105f, (top(x / HALF_GRIN) - bottom(x / HALF_GRIN)) * .31f)
                add(
                    Tooth(
                        x,
                        top(x / HALF_GRIN) - ry * .86f,
                        depth(x / HALF_GRIN),
                        .0048f - i * .00047f,
                        ry,
                        .0045f,
                        true,
                    )
                )
            }
            for (i in 0..5) {
                val x = (i + .5f) * .0067f * side
                val ry =
                    minOf(.0067f - i * .00045f, (top(x / HALF_GRIN) - bottom(x / HALF_GRIN)) * .22f)
                add(
                    Tooth(
                        x,
                        bottom(x / HALF_GRIN) + ry * .85f,
                        depth(x / HALF_GRIN) - .001f,
                        .0031f - i * .00010f,
                        ry,
                        .0036f,
                        false,
                    )
                )
            }
        }
    }

    fun parts(): List<Part> = buildList {
        fun part(
            name: String,
            mount: Mount,
            finish: Finish,
            build: FaceGeometryBuilder.() -> Unit,
        ) {
            add(
                Part(
                    FaceGeometryBuilder().apply(build).mesh("buggy-$name", finish.color),
                    mount,
                    finish,
                )
            )
        }
        part("sclera", Mount.EYE, Finish.WHITE) {
            ellipsoid(0f, 0f, .012f, EYE_RADIUS, EYE_RADIUS, EYE_RADIUS)
        }
        part("iris", Mount.GAZE, Finish.IRIS) {
            ellipsoid(0f, 0f, 0f, IRIS_RADIUS, IRIS_RADIUS, .0007f)
        }
        part("pupil", Mount.GAZE, Finish.PUPIL) {
            ellipsoid(0f, 0f, .0006f, .00155f, .00155f, .0003f)
        }
        part("pupil-glint", Mount.GAZE, Finish.GLINT) {
            ellipsoid(-.00065f, .0010f, .0009f, .00060f, .00060f, .00020f)
        }
        part("eye-glints", Mount.EYE, Finish.GLINT) {
            ellipsoid(-.0065f, .0105f, .0277f, .0020f, .0025f, .0005f)
            ellipsoid(.006f, -.008f, .0297f, .0007f, .0011f, .0003f)
        }
        part("lid-crease", Mount.EYE, Finish.CREASE) {
            sculptedTube(
                (0..64).map { i ->
                    val a = i * 2 * PI / 64
                    floatArrayOf(.0218f * cos(a).toFloat(), .0216f * sin(a).toFloat(), .012f)
                },
                .0034f,
            )
        }
        part("eyelids", Mount.EYE, Finish.SKIN) {
            sculptedTube(
                (0..64).map { i ->
                    val a = i * 2 * PI / 64
                    floatArrayOf(.0220f * cos(a).toFloat(), .0233f * sin(a).toFloat(), .013f)
                },
                .0028f,
            )
        }
        part("brow", Mount.BROW, Finish.BROW) {
            for (strand in 0..3) sculptedTube(
                (0..20).map { i ->
                    val u = -1f + i / 10f
                    floatArrayOf(u * .024f, .028f + .009f * (1 - u * u) + strand * .00065f, .008f)
                },
                .0015f,
                .0004f,
            )
        }
        part("mouth-cavity", Mount.MOUTH, Finish.MOUTH) {
            surface(
                (0..12).map { row ->
                    (0..64).map { column ->
                        val u = -1f + column / 32f
                        val v = row / 12f
                        floatArrayOf(
                            u * HALF_GRIN,
                            top(u) * (1 - v) + bottom(u) * v,
                            depth(u) - .008f,
                        )
                    }
                }
            )
        }
        for ((name, edge) in listOf("upper" to ::top, "lower" to ::bottom)) {
            part("$name-gum", Mount.MOUTH, Finish.GUM) {
                sculptedTube(
                    (0..64).map { i ->
                        val u = -1f + i / 32f
                        floatArrayOf(u * HALF_GRIN, edge(u), depth(u) - .0025f)
                    },
                    .0034f,
                )
            }
            part("$name-lip", Mount.MOUTH, Finish.LIP) {
                sculptedTube(
                    (0..64).map { i ->
                        val u = -1f + i / 32f
                        floatArrayOf(
                            u * HALF_GRIN,
                            edge(u) + if (name == "upper") .0021f else -.0021f,
                            depth(u) + .003f,
                        )
                    },
                    .0032f,
                )
            }
            part("$name-lip-rim", Mount.MOUTH, Finish.SKIN) {
                sculptedTube(
                    (0..64).map { i ->
                        val u = -1f + i / 32f
                        floatArrayOf(
                            u * HALF_GRIN,
                            edge(u) + if (name == "upper") .0042f else -.0042f,
                            depth(u) + .0005f,
                        )
                    },
                    .0015f,
                )
            }
        }
        // Combine the teeth into one mesh and material, retaining separate rounded silhouettes.
        part("teeth", Mount.MOUTH, Finish.ENAMEL) {
            for (t in teeth()) ellipsoid(t.x, t.y, t.z, t.rx, t.ry, t.rz, roundness = .53f)
        }
    }

    /** Gaze stays on the eyeball surface instead of sliding a pupil across a flat plane. */
    fun gaze(x: Float, y: Float): FloatArray {
        val dx = x.coerceIn(-1f, 1f) * .006f
        val dy = y.coerceIn(-1f, 1f) * .005f
        return floatArrayOf(
            dx,
            dy,
            .012f + sqrt(EYE_RADIUS * EYE_RADIUS - dx * dx - dy * dy) + .00035f,
        )
    }
}
