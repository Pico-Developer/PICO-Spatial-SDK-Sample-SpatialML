// Copyright 2026 PICO. All rights reserved.
package com.pico.spatial.ml.sample.facelandmarker

import kotlin.math.PI

/** Absolute-time motion: ten-second closed/open phases, with eased boundary transitions. */
object FaceHelmetMotion {
    // The head-only opening helmet retains the established lift angle and transition duration.
    const val PHASE_SECONDS = 10.0
    const val TRANSITION_SECONDS = 1.15
    const val OPEN_DEGREES = -110.0

    fun angleRadians(elapsedSeconds: Double): Float {
        require(elapsedSeconds.isFinite() && elapsedSeconds >= 0.0)
        if (elapsedSeconds < PHASE_SECONDS) return 0f
        val phase = elapsedSeconds % (2 * PHASE_SECONDS)
        val opening = phase >= PHASE_SECONDS
        val progress =
            ((if (opening) phase - PHASE_SECONDS else phase) / TRANSITION_SECONDS).coerceIn(
                0.0,
                1.0,
            )
        val eased = progress * progress * (3 - 2 * progress)
        return (OPEN_DEGREES * PI / 180 * if (opening) eased else 1 - eased).toFloat()
    }
}
