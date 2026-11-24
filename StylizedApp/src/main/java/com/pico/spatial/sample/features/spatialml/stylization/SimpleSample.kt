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

package com.pico.spatial.sample.features.spatialml.stylization

import android.content.Context
import android.graphics.Bitmap
import android.os.SharedMemory
import android.util.Log
import com.pico.spatial.ml.securemr.GlobalTensor
import com.pico.spatial.ml.securemr.Pipeline
import com.pico.spatial.ml.securemr.PipelineTensor
import com.pico.spatial.ml.securemr.SceneGraphProperty
import com.pico.spatial.ml.securemr.SpatialMLException
import com.pico.spatial.ml.securemr.SpatialMLInstance
import com.pico.spatial.ml.securemr.SpatialMLSession
import com.pico.spatial.ml.securemr.Tensor
import com.pico.spatial.sample.features.spatialml.AsyncPipelineRunner
import com.pico.spatial.sample.features.spatialml.loadAssetToSharedMemory
import java.nio.ByteOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay

class SimpleSample(private val appContext: Context, scope: CoroutineScope) {

    private lateinit var scenegraph: GlobalTensor
    private lateinit var stylizedImage: GlobalTensor
    private lateinit var referenceStyle: GlobalTensor
    private lateinit var predictedStyle: GlobalTensor
    private lateinit var affineMatrix: GlobalTensor
    private lateinit var affineReverseMatrix: GlobalTensor

    private lateinit var cameraClipPoints: PipelineTensor

    private val sessionDeferred =
        scope.async {
            val session =
                SpatialMLInstance.Companion.create(appContext)
                    .also {
                        while (!it.ready) {
                            delay(100)
                        }
                        Log.i("SpatialML", "SpatialMLInstance ready")
                    }
                    .createSession(SpatialMLSession.InitInfo(IMG_SIZE, IMG_SIZE, 1200, 1200, 600))!!
            scenegraph = session.newSceneFromGLTFSuspend("SpatialML/stylization-tv.gltf")
            Log.i("SpatialML", "TV scenegraph loaded")
            stylizedImage =
                session.newGlobalTensor(
                    Tensor.MultiDimensionalInitInfo(
                        Tensor.DataType.Image.R8G8B8_U_DYNAMIC,
                        intArrayOf(IMG_SIZE, IMG_SIZE),
                    )
                )
            Log.i("SpatialML", "stylized created")
            referenceStyle =
                session.newGlobalTensor(
                    Tensor.MultiDimensionalInitInfo(
                        Tensor.DataType.Image.R8G8B8A8_U,
                        intArrayOf(REF_SIZE, REF_SIZE),
                    )
                )
            Log.i("SpatialML", "reference global tensor created")
            predictedStyle =
                session.newGlobalTensor(
                    Tensor.MultiDimensionalInitInfo(
                        Tensor.DataType.FLOAT32,
                        intArrayOf(1, STYLE_CNT),
                    )
                )
            Log.i("SpatialML", "predicted style vector created")
            affineMatrix =
                session.newGlobalTensor(
                    Tensor.MultiDimensionalInitInfo(Tensor.DataType.FLOAT32, intArrayOf(2, 3))
                )
            affineReverseMatrix =
                session.newGlobalTensor(
                    Tensor.MultiDimensionalInitInfo(Tensor.DataType.FLOAT32, intArrayOf(2, 3))
                )
            Log.i("SpatialML", "affine matrix created")
            session
        }

    // initialize the SpatialML container
    val initTask =
        scope.async {
            val session = sessionDeferred.await()
            Log.i("SpatialML", "Async task (session) is done -> init pipeline")

            session.newPipeline().run {
                // The TV geometry is centered and sized in meters in the asset. Material 0
                // covers only the rectangular screen; the frame and stand use material 1.
                updateSceneGraphProperty(
                    scenegraph,
                    "/",
                    SceneGraphProperty.PBRMaterials[0].BaseColorTexture,
                    stylizedImage,
                )
                // Show the TV after binding the screen texture.
                switchSceneVisibility(scenegraph, newLocalTensor(0xF.toByte()))

                Log.i("SpatialML", "submit the init pipeline")
                submit(mapOf(), null, null)
            }
        }

    private val stylePredictPipeline =
        AsyncPipelineRunner(scope, sessionDeferred) { pipeline, _ ->
            Log.i("SpatialML", "ready to create the style predict pipeline")
            val referenceStyleF32 =
                pipeline.newLocalTensor(
                    Tensor.MultiDimensionalInitInfo(
                        Tensor.DataType.Image.RGB_FLOAT,
                        intArrayOf(REF_SIZE, REF_SIZE),
                    )
                )

            pipeline.apply { // assemble pipeline
                copy(referenceStyle[0..REF_SIZE - 1, 0..REF_SIZE - 1][0..2], referenceStyleF32)
                arithmetic(referenceStyleF32) { referenceStyleF32 / 255.0 }
                loadAssetToSharedMemory(
                    appContext,
                    "stylization-pipeline/model/stylepredict.tflite",
                ) {
                    try {
                        runModelInference(
                            modelName = "style_predictor",
                            modelType = Pipeline.ModelInferenceType.LITE_RT_NPU,
                            modelBinary = it,
                            inputs =
                                arrayOf(
                                    Pipeline.ModelNodeEncoding("style_image", referenceStyleF32)
                                ),
                            outputs =
                                arrayOf(
                                    Pipeline.ModelNodeEncoding(
                                        "mobilenet_conv_Conv_BiasAdd",
                                        predictedStyle,
                                    )
                                ),
                        )
                    } catch (e: SpatialMLException) {
                        if (e.message == BACKEND_CONNECTION_LOST) {
                            Log.i("SpatialML", "pipeline creation stopped during shutdown")
                            throw CancellationException(
                                "pipeline creation stopped during shutdown",
                                e,
                            )
                        }
                        throw e
                    }
                } // model loading
            } // end of pipeline assemble
            Log.i("SpatialML", "the style predict pipeline has been created")
        }

    private val affinePipeline =
        AsyncPipelineRunner(scope, sessionDeferred) { pipeline, _ ->
            pipeline.apply {
                Log.i("SpatialML", "ready to create the affine pipeline")

                cameraClipPoints =
                    newLocalTensor(
                        Tensor.MultiDimensionalInitInfo(
                            Tensor.DataType.Image.RG_FLOAT,
                            intArrayOf(3, 1),
                        )
                    )

                val dstPoints =
                    newLocalTensor(cameraClipPoints.config).apply {
                        SharedMemory.create("affined_dst_points", 6 * Float.SIZE_BYTES).use { mem ->
                            val buf = mem.mapReadWrite()
                            buf.order(ByteOrder.nativeOrder())
                            buf.putFloat(0.0f)
                            buf.putFloat(0.0f)

                            buf.putFloat(IMG_SIZE.toFloat() - 1)
                            buf.putFloat(0.0f)

                            buf.putFloat(0.0f)
                            buf.putFloat(IMG_SIZE.toFloat() - 1)

                            SharedMemory.unmap(buf)
                            tensorResource = mem
                        }
                    }

                getAffine(cameraClipPoints, dstPoints, affineMatrix)
                getAffine(dstPoints, cameraClipPoints, affineReverseMatrix)
            }
            Log.i("SpatialML", "the affine pipeline has been created")
        }

    val styleTransferPipeline =
        AsyncPipelineRunner(scope, sessionDeferred) { pipeline, _ ->
            pipeline.apply {
                Log.i("SpatialML", "ready to create the style transfer pipeline")
                val stylizeInput =
                    newLocalTensor(
                        Tensor.MultiDimensionalInitInfo(
                            Tensor.DataType.Image.R8G8B8_U,
                            intArrayOf(IMG_SIZE, IMG_SIZE),
                        )
                    )
                val stylizeInputScaled =
                    newLocalTensor(
                        Tensor.MultiDimensionalInitInfo(
                            Tensor.DataType.Image.RGB_FLOAT,
                            intArrayOf(IMG_SIZE, IMG_SIZE),
                        )
                    )
                val stylizeOutputScaled = newLocalTensor(stylizeInputScaled.config)
                val stylizeOutput = newLocalTensor(stylizeInput.config)
                // NOTE: stylize input/output tensors are R8B8G8 tensor, pixel ranging from 0~255
                //       stylize input/output scaled are float32 tensor, pixel ranging from 0.0~1.0
                val stylizeClip = newLocalTensor(stylizeInput.config)
                val displayOutput = newLocalTensor(stylizeInput.config)

                rectifiedVSTAccess(
                    rightImageResult = null,
                    leftImageResult = stylizeInput,
                    timestampResult = null,
                    cameraMatrixResult = null,
                )
                copy(stylizeInput, stylizeInputScaled)
                arithmetic(stylizeInputScaled) { stylizeInputScaled / 255.0 }
                applyAffine(affineMatrix, stylizeInputScaled, stylizeInputScaled)
                applyAffine(affineMatrix, stylizeInput, stylizeClip)
                loadAssetToSharedMemory(
                    appContext,
                    "stylization-pipeline/model/styletransfer.tflite",
                ) {
                    try {
                        runModelInference(
                            modelName = "style_transferrer",
                            modelType = Pipeline.ModelInferenceType.LITE_RT_NPU,
                            modelBinary = it,
                            inputs =
                                arrayOf(
                                    Pipeline.ModelNodeEncoding("content_image", stylizeInputScaled),
                                    Pipeline.ModelNodeEncoding(
                                        "mobilenet_conv_Conv_BiasAdd",
                                        predictedStyle,
                                    ),
                                ),
                            outputs =
                                arrayOf(
                                    Pipeline.ModelNodeEncoding(
                                        "transformer_expand_conv3_conv_Sigmoid",
                                        stylizeOutputScaled,
                                    )
                                ),
                        )
                    } catch (e: SpatialMLException) {
                        if (e.message == BACKEND_CONNECTION_LOST) {
                            Log.i("SpatialML", "pipeline creation stopped during shutdown")
                            throw CancellationException(
                                "pipeline creation stopped during shutdown",
                                e,
                            )
                        }
                        throw e
                    }
                } // model loading
                arithmetic(stylizeOutputScaled) { stylizeOutputScaled * 255.0 }
                copy(stylizeOutputScaled, stylizeOutput)
                applyAffine(affineReverseMatrix, stylizeOutput, stylizeOutput)
                applyAffine(affineReverseMatrix, stylizeClip, stylizeClip)
                arithmetic(displayOutput) { stylizeInput - stylizeClip + stylizeOutput }
                // On the 6.1.9 runtime, compose into a normal image tensor before copying the
                // complete frame into the dynamic texture used by the TV material.
                copy(displayOutput, stylizedImage)
                Log.i("SpatialML", "the style transfer pipeline has been created")
            }
        }

    fun setReference(
        bitMap: Bitmap,
        prevTask: Deferred<Pipeline.RunTask>? = null,
    ): Deferred<Pipeline.RunTask> {
        Log.i("SpatialML", "re-setting reference image")
        return stylePredictPipeline.runOnceAfterValueReset(prevTask) {
            val resizedBitMap = Bitmap.createScaledBitmap(bitMap, REF_SIZE, REF_SIZE, true)
            Log.i("SpatialML", "set reference image of ${resizedBitMap.byteCount} bytes")
            SharedMemory.create("update_reference_style_image", resizedBitMap.byteCount).use { mem
                ->
                val buffer = mem.mapReadWrite()
                buffer.order(ByteOrder.nativeOrder())
                bitMap.copyPixelsToBuffer(buffer)
                SharedMemory.unmap(buffer)
                referenceStyle.tensorResource = mem
            }
            Log.i("SpatialML", "reference image reset")
        }
    }

    fun setCameraClip(
        xMin: Float,
        xMax: Float,
        yMin: Float,
        yMax: Float,
        prevTask: Deferred<Pipeline.RunTask>? = null,
    ): Deferred<Pipeline.RunTask> {
        Log.i("SpatialML", "re-setting image clipping region to $xMin ~ $xMax, $yMin ~ $yMax")
        return affinePipeline.runOnceAfterValueReset(prevTask) {
            SharedMemory.create("affine_camera_clip_update", 6 * Float.SIZE_BYTES).use { mem ->
                val buf = mem.mapReadWrite()
                buf.order(ByteOrder.nativeOrder())
                buf.putFloat(IMG_SIZE.toFloat() * xMin)
                buf.putFloat(IMG_SIZE.toFloat() * yMin)
                Log.i(
                    "SpatialML",
                    "set image clipping point0 (${IMG_SIZE.toFloat() * xMin}, ${IMG_SIZE.toFloat() * yMin})",
                )

                buf.putFloat(IMG_SIZE.toFloat() * xMax - 1)
                buf.putFloat(IMG_SIZE.toFloat() * yMin)
                Log.i(
                    "SpatialML",
                    "set image clipping point1 (${IMG_SIZE.toFloat() * xMax}, ${IMG_SIZE.toFloat() * yMin})",
                )

                buf.putFloat(IMG_SIZE.toFloat() * xMin)
                buf.putFloat(IMG_SIZE.toFloat() * yMax - 1)
                Log.i(
                    "SpatialML",
                    "set image clipping point2 (${IMG_SIZE.toFloat() * xMin}, ${IMG_SIZE.toFloat() * yMax})",
                )

                SharedMemory.unmap(buf)
                cameraClipPoints.tensorResource = mem
            }
            Log.i("SpatialML", "image clipping region reset")
        }
    }

    companion object {
        private const val BACKEND_CONNECTION_LOST =
            "SpatialML backend connection was lost while creating model inference"

        internal const val IMG_SIZE = 384

        internal const val REF_SIZE = 256

        internal const val STYLE_CNT = 100
    }
}
