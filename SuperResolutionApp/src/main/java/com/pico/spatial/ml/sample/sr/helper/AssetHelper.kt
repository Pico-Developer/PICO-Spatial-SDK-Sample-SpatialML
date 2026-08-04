/*
 * Copyright 2026 PICO. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pico.spatial.ml.sample.sr.helper

import android.content.Context
import android.os.SharedMemory
import android.util.Log
import java.io.IOException
import java.nio.channels.Channels

/**
 * Helper function: load asset file to shared memory for SpatialML usage.
 *
 * @param appContext the app context.
 * @param assetName the path of the asset in the app's asset directory.
 * @param loader the callback after the loading is completed.
 */
@SuppressWarnings("NestedBlockDepth", "EmptyWhileBlock")
fun loadAssetToSharedMemory(
    appContext: Context,
    assetName: String,
    loader: (SharedMemory) -> Unit,
) {
    Log.i("SpatialML", "loading asset from $assetName")
    appContext.assets.open(assetName).use { inputStream ->
        val size = inputStream.available()
        SharedMemory.create("gltf_scene_from$assetName", size).use { mem ->
            val buffer = mem.mapReadWrite()
            try {
                Channels.newChannel(inputStream).use { fileChannel ->
                    while (fileChannel.read(buffer) > 0) {}
                }
            } catch (e: IOException) {
                mem.close()
                throw e
            } finally {
                buffer.rewind()
                SharedMemory.unmap(buffer)
            }
            Log.i("SpatialML", "asset $assetName has been loaded of $size bytes")
            loader(mem)
        }
    }
}
