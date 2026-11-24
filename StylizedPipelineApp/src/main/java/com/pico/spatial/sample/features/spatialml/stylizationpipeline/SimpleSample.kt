/*
 * Copyright 2025 - 2026 PICO. All rights reserved.
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

package com.pico.spatial.sample.features.spatialml.stylizationpipeline

import android.content.Context
import android.graphics.Bitmap
import android.os.SharedMemory
import com.pico.spatial.ml.securemr.Pipeline
import com.pico.spatial.ml.securemr.SceneGraphProperty
import com.pico.spatial.ml.securemr.SpatialMLInstance
import com.pico.spatial.ml.securemr.SpatialMLSession
import com.pico.spatial.ml.securemr.Tensor
import com.pico.spatial.ml.securemr.loadPipelinePackageFromAssets
import com.pico.spatial.sample.features.spatialml.LoadedPipelineRunner
import java.nio.ByteOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/** The stylized-camera demo, with prediction, cropping and inference defined in JSON assets. */
class SimpleSample(appContext: Context, scope: CoroutineScope) {
    private val sessionDeferred =
        scope.async(Dispatchers.IO) {
            val instance = SpatialMLInstance.create(appContext)
            withTimeout(30_000) { while (!instance.ready) delay(100) }
            checkNotNull(
                instance.createSession(
                    SpatialMLSession.InitInfo(IMG_SIZE, IMG_SIZE, 1200, 1200, 600)
                )
            )
        }

    private val bundleDeferred =
        scope.async(Dispatchers.IO) {
            sessionDeferred.await().loadPipelinePackageFromAssets(ASSET_ROOT)
        }

    // Display integration remains in Kotlin: the JSON loader creates ordinary image tensors.
    val initTask =
        scope.async(Dispatchers.IO) {
            val session = sessionDeferred.await()
            val bundle = bundleDeferred.await()
            val scene = session.newSceneFromGLTFSuspend("SpatialML/stylization-tv.gltf")
            val texture =
                session.newGlobalTensor(
                    Tensor.MultiDimensionalInitInfo(
                        Tensor.DataType.Image.R8G8B8_U_DYNAMIC,
                        intArrayOf(IMG_SIZE, IMG_SIZE),
                    )
                )
            bundle.pipelines
                .getValue("style_transfer")
                .pipeline
                .copy(bundle.globalTensors.getValue("stylized_image"), texture)
            session.newPipeline().run {
                // The shared TV asset is centered and sized in meters. Material 0 covers
                // only the rectangular screen; the frame and stand use material 1.
                updateSceneGraphProperty(
                    scene,
                    "/",
                    SceneGraphProperty.PBRMaterials[0].BaseColorTexture,
                    texture,
                )
                switchSceneVisibility(scene, newLocalTensor(0xF.toByte()))
                submit(emptyMap(), null, null)
            }
        }

    private val stylePredictPipeline = LoadedPipelineRunner(scope, bundleDeferred, "style_predict")
    private val affinePipeline = LoadedPipelineRunner(scope, bundleDeferred, "affine")
    val styleTransferPipeline = LoadedPipelineRunner(scope, bundleDeferred, "style_transfer")

    fun setReference(
        bitMap: Bitmap,
        prevTask: Deferred<Pipeline.RunTask>? = null,
    ): Deferred<Pipeline.RunTask> =
        stylePredictPipeline.runOnceAfterValueReset(prevTask) { bundle ->
            val resized = Bitmap.createScaledBitmap(bitMap, REF_SIZE, REF_SIZE, true)
            val rgba =
                if (resized.config == Bitmap.Config.ARGB_8888) resized
                else resized.copy(Bitmap.Config.ARGB_8888, false)
            try {
                SharedMemory.create("pipeline_reference_style", rgba.byteCount).use { memory ->
                    val buffer = memory.mapReadWrite()
                    try {
                        rgba.copyPixelsToBuffer(buffer)
                    } finally {
                        SharedMemory.unmap(buffer)
                    }
                    bundle.globalTensors.getValue("reference_style").tensorResource = memory
                }
            } finally {
                if (rgba !== resized && rgba !== bitMap) rgba.recycle()
                if (resized !== bitMap) resized.recycle()
            }
        }

    fun setCameraClip(
        xMin: Float,
        xMax: Float,
        yMin: Float,
        yMax: Float,
        prevTask: Deferred<Pipeline.RunTask>? = null,
    ): Deferred<Pipeline.RunTask> =
        affinePipeline.runOnceAfterValueReset(prevTask) { bundle ->
            require(xMin >= 0f && xMax <= 1f && yMin >= 0f && yMax <= 1f)
            require((xMax - xMin) * IMG_SIZE > 1f && (yMax - yMin) * IMG_SIZE > 1f)
            SharedMemory.create("pipeline_camera_clip", 6 * Float.SIZE_BYTES).use { memory ->
                val buffer = memory.mapReadWrite().order(ByteOrder.nativeOrder())
                try {
                    buffer.putFloat(IMG_SIZE * xMin).putFloat(IMG_SIZE * yMin)
                    buffer.putFloat(IMG_SIZE * xMax - 1).putFloat(IMG_SIZE * yMin)
                    buffer.putFloat(IMG_SIZE * xMin).putFloat(IMG_SIZE * yMax - 1)
                } finally {
                    SharedMemory.unmap(buffer)
                }
                bundle.globalTensors.getValue("camera_clip_points").tensorResource = memory
            }
        }

    companion object {
        const val ASSET_ROOT = "SpatialML/stylization-pipeline"
        internal const val IMG_SIZE = 384
        internal const val REF_SIZE = 256
    }
}
