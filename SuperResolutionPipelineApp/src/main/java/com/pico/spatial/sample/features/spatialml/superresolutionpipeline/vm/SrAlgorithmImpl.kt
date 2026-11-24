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

package com.pico.spatial.sample.features.spatialml.superresolutionpipeline.vm

import android.content.Context
import android.os.SharedMemory
import android.util.Log
import com.pico.spatial.ml.readback.TensorContent
import com.pico.spatial.ml.readback.readbackAsTextureResourceSuspend
import com.pico.spatial.ml.readback.readbackContentSuspend
import com.pico.spatial.ml.securemr.GlobalTensor
import com.pico.spatial.ml.securemr.Pipeline
import com.pico.spatial.ml.securemr.SceneGraphProperty.PBRMaterials
import com.pico.spatial.ml.securemr.SpatialMLException
import com.pico.spatial.ml.securemr.SpatialMLInstance
import com.pico.spatial.ml.securemr.SpatialMLSession.InitInfo
import com.pico.spatial.ml.securemr.Tensor.DataType
import com.pico.spatial.ml.securemr.Tensor.MultiDimensionalInitInfo
import com.pico.spatial.ml.securemr.loadPipelinePackageFromAssets
import com.pico.spatial.sample.features.spatialml.LoadedPipelineRunner
import java.nio.ByteOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/**
 * Loads the zoom and Real-ESRGAN graphs from JSON, preserving SpatialML and RelaxMR display modes.
 */
class SrAlgorithmImpl(
    appContext: Context,
    private val scope: CoroutineScope,
    useSecureMr: Boolean = false,
) {
    private lateinit var dynamicTexture: GlobalTensor

    private val sessionDeferred =
        scope.async(Dispatchers.IO) {
            val instance = SpatialMLInstance.create(appContext)
            withTimeout(30_000) { while (!instance.ready) delay(100) }
            checkNotNull(
                instance.createSession(
                    InitInfo(
                        imageWidth = CAMERA_IMG_SIZE,
                        imageHeight = CAMERA_IMG_SIZE,
                        containerWidth = if (useSecureMr) 1200 else 0,
                        containerHeight = if (useSecureMr) 1200 else 0,
                        containerDepth = if (useSecureMr) 200 else 0,
                    )
                )
            )
        }

    private val bundleDeferred =
        scope.async(Dispatchers.IO) {
            sessionDeferred.await().loadPipelinePackageFromAssets(ASSET_ROOT)
        }

    val initTask =
        scope.async(Dispatchers.IO) {
            val session = sessionDeferred.await()
            val bundle = bundleDeferred.await()
            dynamicTexture =
                session.newGlobalTensor(
                    MultiDimensionalInitInfo(
                        DataType.Image.R8G8B8A8_U_DYNAMIC,
                        intArrayOf(ZOOMED_IMG_SIZE, ZOOMED_IMG_SIZE),
                    )
                )
            // Bridge the loader's output to a displayable/readable dynamic texture after inference.
            bundle.pipelines
                .getValue("super_resolution")
                .pipeline
                .copy(bundle.globalTensors.getValue("zoomed_image"), dynamicTexture)
            session.newPipeline().run {
                if (useSecureMr) {
                    val scene = session.newSceneFromGLTFSuspend("SpatialML/Display512.glb")
                    updateSceneGraphProperty(
                        scene,
                        "/",
                        PBRMaterials[0].BaseColorTexture,
                        dynamicTexture,
                    )
                    switchSceneVisibility(scene, newLocalTensor(0xF.toByte()))
                } else {
                    copy(bundle.globalTensors.getValue("zoomed_image"), dynamicTexture)
                }
                submit(emptyMap(), null, null)
            }
        }

    val mainPipeline = LoadedPipelineRunner(scope, bundleDeferred, "super_resolution")
    private val affinePipeline = LoadedPipelineRunner(scope, bundleDeferred, "affine")

    /** Loads and submits the JSON pipelines once without creating an app-side ECS view. */
    fun runPipelineSmokeTest(upscaleRatio: Float = 4.0f): Deferred<Pipeline.RunTask> {
        val affineTask = setUpscaleFactor(upscaleRatio, initTask)
        return mainPipeline.runOnceAfterValueReset(affineTask)
    }

    /**
     * Callback when the upscale ratio is changed.
     *
     * @param upscaleRatio a value between 1.0 to 16.0. 16.0 means 16x upscale. 1.0 means no
     *   upscale.
     */
    fun setUpscaleFactor(
        upscaleRatio: Float,
        prevTask: Deferred<Pipeline.RunTask>? = null,
    ): Deferred<Pipeline.RunTask> {
        return affinePipeline.runOnceAfterValueReset(prevTask) { bundle ->
            val zoomFactor = 1f - 16 * UPSCALE_CONSTANT / upscaleRatio
            if (zoomFactor !in 0.0f..<1.0f) {
                Log.e("SpatialML", "wrong zoom factor $zoomFactor (upscale ratio = $upscaleRatio)")
                throw SpatialMLException("zoom factor must be [0.0, 1.0), got $zoomFactor")
            }
            val beginAfterZoom = CAMERA_IMG_SIZE * zoomFactor / 2
            val endAfterZoom = CAMERA_IMG_SIZE * (1.0f - zoomFactor / 2.0f) - 1.0f

            SharedMemory.create("update_affine_points", 6 * Float.SIZE_BYTES).use { mem ->
                val buffer = mem.mapReadWrite()
                buffer.order(ByteOrder.nativeOrder())
                buffer.putFloat(beginAfterZoom)
                buffer.putFloat(beginAfterZoom)

                buffer.putFloat(endAfterZoom)
                buffer.putFloat(beginAfterZoom)

                buffer.putFloat(beginAfterZoom)
                buffer.putFloat(endAfterZoom)

                SharedMemory.unmap(buffer)
                bundle.globalTensors.getValue("zoom_points").tensorResource = mem
            }
            Log.i(
                "SpatialML",
                "zoomPoints reset to [$beginAfterZoom, $beginAfterZoom], " +
                    "[$endAfterZoom, $beginAfterZoom], [$beginAfterZoom, $endAfterZoom] " +
                    "as zoom factor is $zoomFactor (upscale ratio = $upscaleRatio)",
            )
        }
    }

    /**
     * The callback to read the upscaled image content. Require camera permission to call this.
     *
     * @return a deferred [TensorContent] of the upscaled image, of size ([CAMERA_IMG_SIZE] x
     *   [CAMERA_IMG_SIZE])
     */
    fun imageReadBack(): Deferred<TensorContent> =
        scope.async {
            initTask.await()
            dynamicTexture.readbackContentSuspend()
        }

    /**
     * The callback to read the upscaled image as a dynamic texture so that it can be rendered
     * inside the
     * [com.pico.spatial.sample.features.spatialml.superresolutionpipeline.view.SuperResolutionSpatialView]
     * container.
     */
    fun imageReadbackAsTexture() =
        scope.async {
            initTask.await()
            dynamicTexture.readbackAsTextureResourceSuspend()
        }

    companion object {
        const val ASSET_ROOT = "SpatialML/superresolution-pipeline"
        internal const val CAMERA_IMG_SIZE = 512
        internal const val AFFINE_IMG_SIZE = 128
        internal const val ZOOMED_IMG_SIZE = 512
        private const val AFFINE_IMAGE_SQUARE = (AFFINE_IMG_SIZE * AFFINE_IMG_SIZE).toFloat()
        private const val UPSCALE_CONSTANT = AFFINE_IMAGE_SQUARE / CAMERA_IMG_SIZE / ZOOMED_IMG_SIZE
    }
}
