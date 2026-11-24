// Copyright 2026 PICO. All rights reserved.
package com.pico.spatial.ml.sample.facelandmarker

import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.math.Vector2
import com.pico.spatial.core.math.Vector3
import kotlin.math.sqrt

/** Converts the local measured surface to SDK vectors and its current bounds. */
class FaceMeshPreparation {
    private var normalScratch = FloatArray(0)

    data class Prepared(val model: MeshModel, val bounds: BoundingBox)

    fun prepare(
        vertices: FloatArray,
        indices: List<Int>,
        needsNormals: Boolean = false,
        uv: List<Vector2>? = null,
    ): Prepared {
        require(vertices.isNotEmpty() && vertices.size % 3 == 0 && vertices.all(Float::isFinite))
        require(indices.size % 3 == 0 && indices.all { it in 0 until vertices.size / 3 })
        require(uv == null || uv.size == vertices.size / 3)
        // SDK resources consume these lists asynchronously; do not recycle their vectors.
        val positions =
            List(vertices.size / 3) { i ->
                Vector3(vertices[i * 3], vertices[i * 3 + 1], vertices[i * 3 + 2])
            }
        return Prepared(
            MeshModel(
                positions,
                indices,
                if (needsNormals) normals(positions, indices) else null,
                uv0 = uv,
            ),
            bounds(positions),
        )
    }

    private fun normals(vertices: List<Vector3>, indices: List<Int>): List<Vector3> {
        val size = vertices.size * 3
        if (normalScratch.size < size) normalScratch = FloatArray(size)
        val values = normalScratch
        values.fill(0f, 0, size)
        for (i in indices.indices step 3) {
            val a = vertices[indices[i]]
            val b = vertices[indices[i + 1]]
            val c = vertices[indices[i + 2]]
            val bx = b.x - a.x
            val by = b.y - a.y
            val bz = b.z - a.z
            val cx = c.x - a.x
            val cy = c.y - a.y
            val cz = c.z - a.z
            val nx = by * cz - bz * cy
            val ny = bz * cx - bx * cz
            val nz = bx * cy - by * cx
            for (index in i..i + 2) {
                val offset = indices[index] * 3
                values[offset] += nx
                values[offset + 1] += ny
                values[offset + 2] += nz
            }
        }
        return List(vertices.size) { i ->
            val j = i * 3
            val length =
                sqrt(
                    values[j] * values[j] +
                        values[j + 1] * values[j + 1] +
                        values[j + 2] * values[j + 2]
                )
            if (length > 1e-12f)
                Vector3(values[j] / length, values[j + 1] / length, values[j + 2] / length)
            else Vector3(0f, 0f, 1f)
        }
    }

    private fun bounds(vertices: List<Vector3>): BoundingBox {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        for (v in vertices) {
            minX = minOf(minX, v.x)
            minY = minOf(minY, v.y)
            minZ = minOf(minZ, v.z)
            maxX = maxOf(maxX, v.x)
            maxY = maxOf(maxY, v.y)
            maxZ = maxOf(maxZ, v.z)
        }
        return BoundingBox(
            Vector3((minX + maxX) * .5f, (minY + maxY) * .5f, (minZ + maxZ) * .5f),
            Vector3((maxX - minX) * .5f, (maxY - minY) * .5f, (maxZ - minZ) * .5f),
        )
    }
}
