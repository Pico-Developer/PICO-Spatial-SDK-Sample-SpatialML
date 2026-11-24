package com.pico.spatial.ml.sample.hello

import com.pico.spatial.ml.readback.readbackContentSuspend
import com.pico.spatial.ml.securemr.GlobalTensor
import java.nio.ByteOrder

/** Caller checks permission, FLOAT32 schema and coherent publication before consuming this copy. */
suspend fun readFloatPacket(output: GlobalTensor, count: Int): FloatArray {
    require(count > 0 && count <= Int.MAX_VALUE / Float.SIZE_BYTES)
    return output.readbackContentSuspend().use { content ->
        val bytes = content.buffer.duplicate().order(ByteOrder.nativeOrder())
        bytes.rewind()
        require(bytes.remaining() == count * Float.SIZE_BYTES) { "Unexpected packet byte count" }
        FloatArray(count) { bytes.float }
    }
}
