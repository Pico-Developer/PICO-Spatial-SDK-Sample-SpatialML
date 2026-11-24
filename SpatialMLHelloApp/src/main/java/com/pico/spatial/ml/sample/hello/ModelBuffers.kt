package com.pico.spatial.ml.sample.hello

import android.content.Context
import android.os.SharedMemory
import com.pico.spatial.ml.securemr.Tensor
import java.nio.ByteOrder

/** Small tutorial assets only; large-model loading should use a bounded streaming policy. */
fun withModelAsset(context: Context, asset: String, build: (SharedMemory) -> Unit) {
    val bytes = context.assets.open(asset).use { it.readBytes() }
    require(bytes.isNotEmpty()) { "Empty model asset: $asset" }
    SharedMemory.create("spatialml-model", bytes.size).use { memory ->
        val buffer = memory.mapReadWrite()
        try {
            buffer.put(bytes)
        } finally {
            SharedMemory.unmap(buffer)
        }
        build(memory)
    }
}

/** Call only while this tensor has no concurrent consumer; shape/type are the caller's contract. */
fun writeFloats(tensor: Tensor, values: FloatArray) {
    require(values.isNotEmpty() && values.size <= Int.MAX_VALUE / Float.SIZE_BYTES)
    SharedMemory.create("spatialml-floats", values.size * Float.SIZE_BYTES).use { memory ->
        val buffer = memory.mapReadWrite().order(ByteOrder.nativeOrder())
        try {
            values.forEach { buffer.putFloat(it) }
        } finally {
            SharedMemory.unmap(buffer)
        }
        tensor.tensorResource = memory
    }
}
