// Copyright 2026 PICO. All rights reserved.
package com.pico.spatial.ml.sample.facelandmarker

/** The sample publishes pose, pose-plus-mesh and expression-bearing packets. */
enum class FaceOutputs(
    val mask: Int,
    val packetSize: Int,
    val mesh: Boolean,
    val blendshapes: Boolean = false,
) {
    POSE(1, 78, false),
    POSE_MESH(3, 1531, true),
    ALL(7, 1583, true, true),
}

/** Decode only the published model ABI into the small app-facing frame contract. */
object FaceOutputPacket {
    fun decode(packet: FloatArray, outputs: FaceOutputs, readbackSeconds: Double): FaceFrame {
        require(packet.size == outputs.packetSize)
        require(packet[packet.size - 2] == 2f && packet.last() == outputs.mask.toFloat()) {
            "Face output schema does not match the submitted frame"
        }
        require(packet[0].isFinite())
        val valid = packet[4] > 0 && packet.all(Float::isFinite)
        val pose = packet.copyOfRange(60, 76)
        val vertices = if (valid && outputs.mesh) vertices(packet, 76, pose) else FloatArray(0)
        return FaceFrame(
            sequence = packet[0].toInt(),
            hasFace = valid && vertices != null,
            captureSeconds = packet[13].toDouble() + packet[14].toDouble(),
            readbackSeconds = readbackSeconds,
            freshCameraFrame = packet[42] > 0,
            trackingIdentity = packet[19],
            needsDetection =
                if (packet[42] <= 0 && packet[18] == 3f && packet[16] > 0) false
                else !valid || vertices == null || packet[37] > 0,
            captureCameraPose = packet.copyOfRange(44, 60),
            pose = pose,
            vertices = vertices ?: FloatArray(0),
            outputs = outputs,
            blendshapes =
                if (valid && outputs.blendshapes) packet.copyOfRange(1529, 1581) else FloatArray(0),
        )
    }

    private fun vertices(packet: FloatArray, offset: Int, world: FloatArray): FloatArray? {
        val width = FaceModelContract.WIDTH.toDouble()
        val height = FaceModelContract.HEIGHT.toDouble()
        val cx = packet[8].toDouble()
        val cy = packet[9].toDouble()
        val side = packet[10].toDouble()
        val cosine = kotlin.math.cos(packet[11].toDouble())
        val sine = kotlin.math.sin(packet[11].toDouble())
        val fx = packet[24].toDouble()
        val fy = packet[25].toDouble()
        val principalX = packet[26].toDouble()
        val principalY = packet[27].toDouble()
        val fit = offset + 1434
        val scale = packet[fit + 16].toDouble() * packet[fit + 17]
        val depth = packet[fit + 18].toDouble()
        val squaredScale =
            packet[fit].toDouble() * packet[fit] +
                packet[fit + 4].toDouble() * packet[fit + 4] +
                packet[fit + 8].toDouble() * packet[fit + 8]
        if (fx <= 0 || fy <= 0 || scale <= 0 || squaredScale <= 1e-12) return null
        val result = FloatArray(478 * 3)
        val metric = DoubleArray(3)
        val local = DoubleArray(3)
        for (i in 0 until 478) {
            val j = i * 3
            val x = packet[offset + j].toDouble() / 256 - .5
            val y = packet[offset + j + 1].toDouble() / 256 - .5
            val lx = ((cx + side * (cosine * x - sine * y)) / width).toFloat()
            val ly = ((cy + side * (sine * x + cosine * y)) / height).toFloat()
            val lz = (packet[offset + j + 2].toDouble() / 256 * side / width).toFloat()
            // Preserve float32 boundaries of the former service tensors for numerical parity.
            val px = ((lx * width - principalX) / fx).toFloat()
            val py = ((principalY - ly * height) / fy).toFloat()
            val pz = (lz * width / fx).toFloat()
            val z = (pz.toDouble() - depth + 1) / scale
            metric[0] = (px * z).toFloat().toDouble()
            metric[1] = (py * z).toFloat().toDouble()
            metric[2] = (-z).toFloat().toDouble()
            for (a in 0..2) {
                var v = 0.0
                for (b in 0..2) v += packet[fit + b * 4 + a] * (metric[b] - packet[fit + b * 4 + 3])
                local[a] = v * (.01 / squaredScale)
            }
            for (r in 0..2) {
                var v = world[r * 4 + 3].toDouble()
                for (c in 0..2) v += world[r * 4 + c] * local[c]
                result[j + r] = v.toFloat()
            }
        }
        return result
    }
}
