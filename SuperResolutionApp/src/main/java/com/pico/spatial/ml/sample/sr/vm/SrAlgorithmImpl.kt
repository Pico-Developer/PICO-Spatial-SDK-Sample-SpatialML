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
package com.pico.spatial.ml.sample.sr.vm

import android.content.Context
import android.os.SharedMemory
import android.util.Log
import com.pico.spatial.ml.readback.TensorContent
import com.pico.spatial.ml.readback.readbackAsTextureResourceSuspend
import com.pico.spatial.ml.readback.readbackContentSuspend
import com.pico.spatial.ml.sample.sr.helper.AsyncPipelineRunner
import com.pico.spatial.ml.sample.sr.helper.loadAssetToSharedMemory
import com.pico.spatial.ml.securemr.GlobalTensor
import com.pico.spatial.ml.securemr.Pipeline
import com.pico.spatial.ml.securemr.PipelineTensor
import com.pico.spatial.ml.securemr.SceneGraphProperty.PBRMaterials
import com.pico.spatial.ml.securemr.SpatialMLException
import com.pico.spatial.ml.securemr.SpatialMLInstance
import com.pico.spatial.ml.securemr.SpatialMLSession.InitInfo
import com.pico.spatial.ml.securemr.Tensor.DataType
import com.pico.spatial.ml.securemr.Tensor.MultiDimensionalInitInfo
import java.nio.ByteOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay

/**
 * The implementation of the super-resolution (SR) algorithm using SpatialML framework.
 *
 * @param appContext the application context, for getting app assets.
 * @param scope the co-routine scope, for async SpatialML API calls.
 * @param useSecureMr a boolean flag determining whether the super-resolution result is protected.
 *   If `true`, the result will be isolated from the app and rendered to screen directly. Otherwise,
 *   the app is allowed to obtain the algorithm outputs.
 */
class SrAlgorithmImpl(
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val useSecureMr: Boolean = false,
) {
    private lateinit var displaySceneGraph: GlobalTensor

    private lateinit var dynamicTexture: GlobalTensor

    private lateinit var zoomAffine: GlobalTensor

    private lateinit var zoomPoints: PipelineTensor

    private val sessionDeferred =
        scope.async {
            val session =
                SpatialMLInstance.create(appContext)
                    .also {
                        while (!it.ready) {
                            delay(100)
                        }
                        Log.i("SpatialML", "SpatialMLInstance ready")
                    }
                    .createSession(
                        InitInfo(
                            imageWidth = CAMERA_IMG_SIZE,
                            imageHeight = CAMERA_IMG_SIZE,
                            containerWidth = if (useSecureMr) 1200 else 0,
                            containerHeight = if (useSecureMr) 1200 else 0,
                            containerDepth = if (useSecureMr) 200 else 0,
                        )
                    )!!
            if (useSecureMr) {
                displaySceneGraph = session.newSceneFromGLTFSuspend("Display512.glb")
                Log.i("SpatialML", "binocular scenegraph loaded")
            }
            dynamicTexture =
                session.newGlobalTensor(
                    MultiDimensionalInitInfo(
                        DataType.Image.R8G8B8A8_U_DYNAMIC,
                        intArrayOf(ZOOMED_IMG_SIZE, ZOOMED_IMG_SIZE),
                    )
                )
            Log.i("SpatialML", "dynamic texture for binocular created")
            zoomAffine =
                session.newGlobalTensor(
                    MultiDimensionalInitInfo(DataType.FLOAT32, intArrayOf(2, 3))
                )
            Log.i("SpatialML", "global affine matrix created")
            session
        }

    /**
     * The initialize the scene rendered in the SecureMR container, so only non-null for SecureMR
     * mode. Always null in RelaxMR mode.
     */
    val initTask =
        if (useSecureMr) {
            scope.async {
                val session = sessionDeferred.await()
                Log.i("SpatialML", "Async task (session) is done -> init pipeline")

                session.newPipeline().run {
                    // step 1: use the dynamic texture to replace the Panel's color texture
                    updateSceneGraphProperty(
                        displaySceneGraph,
                        "/",
                        PBRMaterials[0].BaseColorTexture,
                        dynamicTexture,
                    )
                    // step 2: set visibility
                    switchSceneVisibility(displaySceneGraph, displaySceneGraph)

                    Log.i("SpatialML", "submit the init pipeline")
                    submit(mapOf(), null, null)
                }
            }
        } else {
            null
        }

    /** The pipeline where the super-resolution logic is run routinely. */
    val mainPipeline =
        AsyncPipelineRunner(scope, sessionDeferred) { pipeline, _ ->
            pipeline.apply {
                val rightEyeImg =
                    newLocalTensor(
                        MultiDimensionalInitInfo(
                            DataType.Image.R8G8B8_U_DYNAMIC,
                            intArrayOf(CAMERA_IMG_SIZE, CAMERA_IMG_SIZE),
                        )
                    )
                rectifiedVSTAccess(
                    rightImageResult = rightEyeImg,
                    leftImageResult = null,
                    timestampResult = null,
                    cameraMatrixResult = null,
                )

                val affinedUint8 =
                    newLocalTensor(
                        MultiDimensionalInitInfo(
                            DataType.Image.R8G8B8_U_DYNAMIC,
                            intArrayOf(AFFINE_IMG_SIZE, AFFINE_IMG_SIZE),
                        )
                    )
                applyAffine(zoomAffine, rightEyeImg, affinedUint8)

                val affinedFloat =
                    newLocalTensor(
                        MultiDimensionalInitInfo(
                            DataType.Image.RGB_FLOAT,
                            intArrayOf(AFFINE_IMG_SIZE, AFFINE_IMG_SIZE)
                        )
                    )
                copy(affinedUint8, affinedFloat)
                arithmetic(affinedFloat) {
                    affinedFloat / 255.0
                }
                val zoomedResult =
                    newLocalTensor(
                        MultiDimensionalInitInfo(
                            DataType.Image.RGB_FLOAT,
                            intArrayOf(ZOOMED_IMG_SIZE, ZOOMED_IMG_SIZE)
                        )
                    )
                val zoomedResultUnit =
                    newLocalTensor(
                        MultiDimensionalInitInfo(
                            DataType.Image.R8G8B8_U,
                            intArrayOf(ZOOMED_IMG_SIZE, ZOOMED_IMG_SIZE)
                        )
                    )
                loadAssetToSharedMemory(appContext, "real_esrgan_x4v3.tflite") {
                    Log.i("SpatialML", "Real ESRGAN x4v3 being deployed")
                    runModelInference(
                        modelName = "real_esrgan_x4v3",
                        modelType = Pipeline.ModelInferenceType.LITE_RT_NPU,
                        modelBinary = it,
                        inputs = arrayOf(Pipeline.ModelNodeEncoding("image", affinedFloat)),
                        outputs =
                            arrayOf(Pipeline.ModelNodeEncoding("upscaled_image", zoomedResult)),
                    )
                }
                arithmetic(zoomedResult) {
                    zoomedResult * 255.0
                }
                copy(zoomedResult, zoomedResultUnit)
                convertColor(Pipeline.ColorConversion.RGB_TO_RGBA, zoomedResultUnit, dynamicTexture)
                Log.i("SpatialML", "the super-resolution (main) pipeline has been created")
            }
        }

    /**
     * An auxiliary pipeline called only when the upscale ratio is updated. It updates the affine
     * matrix according to the upscale ratio to achieve a smooth zoom-in zoom-out effect.
     */
    private val affinePipeline =
        AsyncPipelineRunner(scope, sessionDeferred) { pipeline, _ ->
            pipeline.apply {
                zoomPoints =
                    newLocalTensor(MultiDimensionalInitInfo(DataType.FLOAT32, intArrayOf(3, 1), 2))

                val targetZoomPoints =
                    newLocalTensor(zoomPoints.config).apply {
                        SharedMemory.create("affined_dst_points", 6 * Float.SIZE_BYTES).use { mem ->
                            val buf = mem.mapReadWrite()
                            buf.order(ByteOrder.nativeOrder())
                            buf.putFloat(0.0f)
                            buf.putFloat(0.0f)

                            buf.putFloat(AFFINE_IMG_SIZE.toFloat() - 1)
                            buf.putFloat(0.0f)

                            buf.putFloat(0.0f)
                            buf.putFloat(AFFINE_IMG_SIZE.toFloat() - 1)

                            SharedMemory.unmap(buf)
                            tensorResource = mem
                        }
                    }

                getAffine(zoomPoints, targetZoomPoints, zoomAffine)
            }
            Log.i("SpatialML", "the affine pipeline has been created")
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
    ): Deferred<Pipeline.RunTask> =
        affinePipeline.runOnceAfterValueReset(prevTask) {
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
                zoomPoints.tensorResource = mem
            }
            Log.i(
                "SpatialML",
                "zoomPoints reset to [$beginAfterZoom, $beginAfterZoom], " +
                        "[$endAfterZoom, $beginAfterZoom], [$beginAfterZoom, $endAfterZoom] " +
                        "as zoom factor is $zoomFactor (upscale ratio = $upscaleRatio)",
            )
        }

    /**
     * The callback to read the upscaled image content. Require camera permission to call this.
     *
     * @return a deferred [TensorContent] of the upscaled image, of size ([CAMERA_IMG_SIZE] x
     *   [CAMERA_IMG_SIZE])
     */
    fun imageReadBack(): Deferred<TensorContent> =
        scope.async { dynamicTexture.readbackContentSuspend() }

    /**
     * The callback to read the upscaled image as a dynamic texture so that it can be rendered
     * inside the [com.pico.spatial.ml.sample.sr.view.SuperResolutionSpatialView] container.
     */
    fun imageReadbackAsTexture() =
        scope.async {
            sessionDeferred.await()
            dynamicTexture.readbackAsTextureResourceSuspend()
        }

    companion object {
        internal const val CAMERA_IMG_SIZE = 512
        internal const val AFFINE_IMG_SIZE = 128
        internal const val ZOOMED_IMG_SIZE = 512
        private const val AFFINE_IMAGE_SQUARE = (AFFINE_IMG_SIZE * AFFINE_IMG_SIZE).toFloat()
        private const val UPSCALE_CONSTANT = AFFINE_IMAGE_SQUARE / CAMERA_IMG_SIZE / ZOOMED_IMG_SIZE
    }
}
