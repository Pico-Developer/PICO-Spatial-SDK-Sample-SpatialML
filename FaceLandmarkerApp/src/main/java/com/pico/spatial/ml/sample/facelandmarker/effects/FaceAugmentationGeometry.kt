// Copyright 2026 PICO. All rights reserved.
package com.pico.spatial.ml.sample.facelandmarker

import kotlin.math.*

data class FaceEffectMesh(
    val name: String,
    val color: FloatArray,
    val vertices: FloatArray,
    val indices: List<Int>,
    val jaw: Boolean = false,
)

/** Pure geometry in face-local metres. Rendering resources are allocated by the scene owner. */
class FaceGeometryBuilder {
    private val positions = mutableListOf<Float>()
    private val indices = mutableListOf<Int>()

    private fun vertex(x: Float, y: Float, z: Float): Int {
        val index = positions.size / 3
        positions.add(x)
        positions.add(y)
        positions.add(z)
        return index
    }

    private fun triangle(a: Int, b: Int, c: Int) {
        indices.add(a)
        indices.add(b)
        indices.add(c)
    }

    fun ellipsoid(
        x: Float,
        y: Float,
        z: Float,
        rx: Float,
        ry: Float,
        rz: Float,
        dome: Boolean = false,
        roundness: Float = 1f,
    ) {
        val start = positions.size / 3
        val rows = 14
        val columns = 24
        for (row in 0..rows) for (column in 0..columns) {
            val latitude = PI * row / rows * if (dome) .5 else 1.0
            val longitude = 2 * PI * column / columns
            fun rounded(value: Double) =
                (sign(value) * abs(value).pow(roundness.toDouble())).toFloat()
            vertex(
                x + rx * rounded(sin(latitude) * cos(longitude)),
                y + ry * rounded(cos(latitude)),
                z + rz * rounded(sin(latitude) * sin(longitude)),
            )
        }
        for (row in 0 until rows) for (column in 0 until columns) {
            val a = start + row * (columns + 1) + column
            triangle(a, a + 1, a + columns + 1)
            triangle(a + 1, a + columns + 2, a + columns + 1)
        }
    }

    fun surface(rows: List<List<FloatArray>>) {
        require(rows.size >= 2 && rows.first().size >= 2)
        val columns = rows.first().size
        require(rows.all { it.size == columns })
        val start = positions.size / 3
        rows.forEach { row -> row.forEach { vertex(it[0], it[1], it[2]) } }
        for (row in 0 until rows.lastIndex) for (column in 0 until columns - 1) {
            val a = start + row * columns + column
            triangle(a, a + columns, a + 1)
            triangle(a + 1, a + columns, a + columns + 1)
        }
    }

    /** Parallel-transported rings avoid twists in sculpted circular lids and sweeping braids. */
    fun sculptedTube(points: List<FloatArray>, radius: Float, endRadius: Float = radius) =
        tube(points, radius, endRadius, smoothFrame = true)

    fun tube(
        points: List<FloatArray>,
        radius: Float,
        endRadius: Float = radius,
        smoothFrame: Boolean = false,
    ) {
        require(points.size >= 2)
        val start = positions.size / 3
        val sides = 10
        val closedLoop =
            smoothFrame && (0..2).all { abs(points.first()[it] - points.last()[it]) < 1e-7f }
        var previousNormal: FloatArray? = null
        for (i in points.indices) {
            val a =
                points[
                    if (closedLoop && (i == 0 || i == points.lastIndex)) points.lastIndex - 1
                    else (i - 1).coerceAtLeast(0)]
            val b =
                points[
                    if (closedLoop && (i == 0 || i == points.lastIndex)) 1
                    else (i + 1).coerceAtMost(points.lastIndex)]
            val tangent = FloatArray(3) { b[it] - a[it] }
            val length = sqrt(tangent.sumOf { (it * it).toDouble() }).toFloat()
            for (axis in 0..2) tangent[axis] /= length
            val reference =
                if (abs(tangent[1]) < .9f) floatArrayOf(0f, 1f, 0f) else floatArrayOf(1f, 0f, 0f)
            fun cross(a: FloatArray, b: FloatArray) =
                floatArrayOf(
                    a[1] * b[2] - a[2] * b[1],
                    a[2] * b[0] - a[0] * b[2],
                    a[0] * b[1] - a[1] * b[0],
                )
            val previous = previousNormal
            val normal =
                if (smoothFrame && previous != null) {
                    val projection =
                        (0..2).sumOf { (previous[it] * tangent[it]).toDouble() }.toFloat()
                    FloatArray(3) { previous[it] - tangent[it] * projection }
                } else cross(tangent, reference)
            val norm = sqrt(normal.sumOf { (it * it).toDouble() }).toFloat()
            for (axis in 0..2) normal[axis] /= norm
            previousNormal = normal
            val binormal = cross(tangent, normal)
            val r = radius + (endRadius - radius) * i / points.lastIndex
            for (side in 0 until sides) {
                val angle = 2 * PI * side / sides
                val p =
                    FloatArray(3) { axis ->
                        points[i][axis] +
                            r * (normal[axis] * cos(angle) + binormal[axis] * sin(angle)).toFloat()
                    }
                vertex(p[0], p[1], p[2])
            }
        }
        for (i in 0 until points.lastIndex) for (side in 0 until sides) {
            val a = start + i * sides + side
            val b = start + i * sides + (side + 1) % sides
            triangle(a, b, a + sides)
            triangle(b, b + sides, a + sides)
        }
        if (closedLoop) return
        val first = vertex(points[0][0], points[0][1], points[0][2])
        val last = vertex(points.last()[0], points.last()[1], points.last()[2])
        for (side in 0 until sides) {
            triangle(first, start + (side + 1) % sides, start + side)
            triangle(
                last,
                start + points.lastIndex * sides + side,
                start + points.lastIndex * sides + (side + 1) % sides,
            )
        }
    }

    fun capPeak() {
        val start = positions.size / 3
        val columns = 28
        val rows = 6
        for (layer in 0..1) for (row in 0..rows) for (column in 0..columns) {
            val theta = -PI / 2 + PI * column / columns
            val t = row.toFloat() / rows
            // The inner arc shares the crown hem, joining the visor to the front half only.
            val x = ((.093 + .017 * t) * sin(theta)).toFloat()
            val z = ((.092 + .075 * t) * cos(theta)).toFloat()
            val y = (-.010 * t * sin(theta).pow(2) - .004 * t).toFloat() - layer * .003f
            vertex(x, y, z)
        }
        val layerSize = (columns + 1) * (rows + 1)
        for (layer in 0..1) for (row in 0 until rows) for (column in 0 until columns) {
            val a = start + layer * layerSize + row * (columns + 1) + column
            if (layer == 0) {
                triangle(a, a + columns + 1, a + 1)
                triangle(a + 1, a + columns + 1, a + columns + 2)
            } else {
                triangle(a, a + 1, a + columns + 1)
                triangle(a + 1, a + columns + 2, a + columns + 1)
            }
        }
        val border =
            (0..columns).toList() +
                (1..rows).map { it * (columns + 1) + columns } +
                (columns - 1 downTo 0).map { rows * (columns + 1) + it } +
                (rows - 1 downTo 1).map { it * (columns + 1) }
        for (i in border.indices) {
            val a = start + border[i]
            val b = start + border[(i + 1) % border.size]
            triangle(a, b, a + layerSize)
            triangle(b, b + layerSize, a + layerSize)
        }
    }

    fun mesh(name: String, color: FloatArray, jaw: Boolean = false) =
        FaceEffectMesh(name, color, positions.toFloatArray(), indices.toList(), jaw)
}

object FaceBaseballCapGeometry {
    fun parts(): List<FaceEffectMesh> {
        val crown =
            FaceGeometryBuilder().apply { ellipsoid(0f, 0f, 0f, .093f, .065f, .092f, dome = true) }
        val peak = FaceGeometryBuilder().apply { capPeak() }
        val seams =
            FaceGeometryBuilder().apply {
                ellipsoid(0f, .065f, 0f, .006f, .003f, .006f)
                for (panel in 0 until 6) {
                    val angle = panel * PI / 3
                    tube(
                        (0..12).map { i ->
                            val a = PI * i / 24
                            floatArrayOf(
                                (.0935 * cos(a) * cos(angle)).toFloat(),
                                (.0655 * sin(a)).toFloat(),
                                (.0925 * cos(a) * sin(angle)).toFloat(),
                            )
                        },
                        .00065f,
                    )
                }
            }
        return listOf(
            crown.mesh("baseball-cap-crown", floatArrayOf(.04f, .18f, .56f, 1f)),
            peak.mesh("baseball-cap-forward-peak", floatArrayOf(.018f, .06f, .2f, 1f)),
            seams.mesh("baseball-cap-stitching", floatArrayOf(.85f, .9f, 1f, 1f)),
        )
    }
}
