// Copyright 2026 PICO. All rights reserved.
package com.pico.spatial.ml.sample.facelandmarker

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Sideways, upward-swept braids in crown-local metres, with a tapered curl at each tip. */
object FacePigtailGeometry {
    fun center(side: Int, t: Float): FloatArray =
        floatArrayOf(
            side * (.080f + .122f * t),
            .043f + .056f * sin(t * PI * .75).toFloat(),
            -.012f + .018f * t,
        )

    fun parts(): List<FaceEffectMesh> = buildList {
        val hair = floatArrayOf(.66f, .115f, .025f, 1f)
        val bright = floatArrayOf(.86f, .22f, .045f, 1f)
        val blue = floatArrayOf(.035f, .30f, .77f, 1f)
        add(
            FaceGeometryBuilder()
                .apply {
                    ellipsoid(0f, .065f, -.020f, .09f, .045f, .072f, dome = true)
                    // Parted front locks meet at the crown and sweep towards the ties.
                    for (side in listOf(-1, 1)) for (strand in 0..7) sculptedTube(
                        (0..24).map { i ->
                            val t = i / 24f
                            floatArrayOf(
                                side * (.004f + .082f * t),
                                .101f - .057f * t * t,
                                .023f + strand * .005f - .013f * t,
                            )
                        },
                        .005f,
                        .003f,
                    )
                }
                .mesh("pigtail-crown", hair)
        )
        for (side in listOf(-1, 1)) {
            for (strand in 0..2) add(
                FaceGeometryBuilder()
                    .apply {
                        sculptedTube(
                            (0..64).map { i ->
                                val t = i / 64f
                                val p = center(side, t)
                                val phase = 2 * PI * (2.5 * t + strand / 3.0)
                                val radius = .012f * (1 - .82f * t)
                                floatArrayOf(
                                    p[0],
                                    p[1] + radius * cos(phase).toFloat(),
                                    p[2] + radius * sin(phase).toFloat(),
                                )
                            },
                            .010f,
                            .0018f,
                        )
                    }
                    .mesh("pigtail-braid-$side-$strand", if (strand == 1) bright else hair)
            )
            add(
                FaceGeometryBuilder()
                    .apply {
                        val p = center(side, .14f)
                        for (lobe in listOf(-1, 1)) ellipsoid(
                            p[0] + lobe * .011f,
                            p[1] + lobe * .008f,
                            p[2] + .019f,
                            .015f,
                            .011f,
                            .005f,
                        )
                        ellipsoid(p[0], p[1], p[2] + .024f, .006f, .007f, .005f)
                    }
                    .mesh("pigtail-bow-$side", blue)
            )
        }
    }
}
