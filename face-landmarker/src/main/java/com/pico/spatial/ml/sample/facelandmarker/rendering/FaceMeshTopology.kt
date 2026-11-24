// Copyright 2026 PICO. All rights reserved.
package com.pico.spatial.ml.sample.facelandmarker

/** Closes the mouth aperture in the depth surface used by the existing face renderer. */
object FaceMeshTopology {
    val mouthFill: List<Int> = buildList {
        val upper = intArrayOf(78, 191, 80, 81, 82, 13, 312, 311, 310, 415, 308)
        val lower = intArrayOf(78, 95, 88, 178, 87, 14, 317, 402, 318, 324, 308)
        for (i in 0 until upper.lastIndex) {
            if (upper[i] != lower[i]) addAll(listOf(upper[i], lower[i], upper[i + 1]))
            if (upper[i + 1] != lower[i + 1]) addAll(listOf(upper[i + 1], lower[i], lower[i + 1]))
        }
    }
}
