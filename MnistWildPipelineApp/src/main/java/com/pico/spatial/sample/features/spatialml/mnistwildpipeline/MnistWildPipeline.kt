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

package com.pico.spatial.sample.features.spatialml.mnistwildpipeline

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.pico.spatial.ml.readback.readbackContentSuspend
import com.pico.spatial.ml.securemr.GlobalTensor
import com.pico.spatial.ml.securemr.Pipeline
import com.pico.spatial.ml.securemr.PipelinePackagePipeline
import com.pico.spatial.ml.securemr.SpatialMLException
import com.pico.spatial.ml.securemr.SpatialMLInstance
import com.pico.spatial.ml.securemr.SpatialMLSession
import com.pico.spatial.ml.securemr.loadPipelinePackageFromAssets
import java.io.IOException
import java.nio.ByteBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Runs the schema-v2 MNIST Wild package and publishes its camera crop and prediction. */
class MnistWildPipeline(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _prediction = MutableStateFlow<Int?>(null)
    val prediction: StateFlow<Int?> = _prediction.asStateFlow()

    private val _confidence = MutableStateFlow<Float?>(null)
    val confidence: StateFlow<Float?> = _confidence.asStateFlow()

    private val _crop = MutableStateFlow<Bitmap?>(null)
    val crop: StateFlow<Bitmap?> = _crop.asStateFlow()

    private val _status = MutableStateFlow("Initializing SpatialML…")
    val status: StateFlow<String> = _status.asStateFlow()

    fun start() {
        if (job?.isActive == true) return
        job =
            scope.launch {
                try {
                    runPipeline()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: SpatialMLException) {
                    reportFailure(error)
                } catch (error: IOException) {
                    reportFailure(error)
                } catch (error: IllegalStateException) {
                    reportFailure(error)
                }
            }
    }

    private suspend fun runPipeline() {
        val instance = SpatialMLInstance.create(appContext)
        while (currentCoroutineContext().isActive && !instance.ready) delay(READY_POLL_MS)
        delay(FULL_SPACE_CAMERA_SETTLE_MS)
        val session =
            checkNotNull(
                instance.createSession(
                    SpatialMLSession.InitInfo(
                        imageWidth = CAMERA_WIDTH,
                        imageHeight = CAMERA_HEIGHT,
                        containerWidth = 0,
                        containerHeight = 0,
                        containerDepth = 0,
                        containerType = SpatialMLSession.ContainerType.DISABLED,
                    )
                )
            )
        val bundle = session.loadPipelinePackageFromAssets(ASSET_ROOT)
        val loaded = checkNotNull(bundle.pipelines[PIPELINE_ID])
        val predictedClass = checkNotNull(bundle.globalTensors[PREDICTED_CLASS])
        val predictedScore = checkNotNull(bundle.globalTensors[PREDICTED_SCORE])
        val croppedImage = checkNotNull(bundle.globalTensors[CROPPED_IMAGE])
        val outputs = OutputTensors(predictedClass, predictedScore, croppedImage)

        Log.i(TAG, "pipeline_loaded package=$ASSET_ROOT backend=npu rendering=readback")
        _status.value = "Point the camera at a handwritten digit"
        runFrames(loaded, outputs)
    }

    private suspend fun runFrames(loaded: PipelinePackagePipeline, outputs: OutputTensors) {
        val intervalMs = 1000L / TARGET_FPS
        var frame = 0L
        var waitFor: Pipeline.RunTask? = null
        while (currentCoroutineContext().isActive) {
            val startedAt = SystemClock.elapsedRealtime()
            val result = submitFrame(loaded, outputs, frame, waitFor)
            waitFor = result.waitFor
            if (result.succeeded) frame += 1
            val elapsedMs = SystemClock.elapsedRealtime() - startedAt
            delay((intervalMs - elapsedMs).coerceAtLeast(1L))
        }
    }

    private suspend fun submitFrame(
        loaded: PipelinePackagePipeline,
        outputs: OutputTensors,
        frame: Long,
        waitFor: Pipeline.RunTask?,
    ): FrameSubmission {
        return try {
            val currentTask = loaded.pipeline.submit(loaded.submitBindings, null, waitFor)
            if (frame % READBACK_INTERVAL_FRAMES == 0L) {
                updateResults(outputs)
                if (frame == 0L) Log.i(TAG, "first_result_ready")
            }
            FrameSubmission(currentTask, succeeded = true)
        } catch (error: SpatialMLException) {
            Log.w(TAG, "submit_failed frame=$frame error=${error.message}")
            delay(FAILURE_BACKOFF_MS)
            FrameSubmission(waitFor, succeeded = false)
        }
    }

    private suspend fun updateResults(outputs: OutputTensors) {
        outputs.predictedClass.readbackContentSuspend().use { content ->
            _prediction.value = content[0].toInt()
        }
        outputs.predictedScore.readbackContentSuspend().use { content ->
            _confidence.value = content[0].toFloat()
        }
        outputs.croppedImage.readbackContentSuspend().use { content ->
            _crop.value = content.toBitmap(CROP_SIZE, CROP_SIZE)
        }
    }

    private fun com.pico.spatial.ml.readback.TensorContent.toBitmap(
        width: Int,
        height: Int,
    ): Bitmap {
        val rgba = ByteBuffer.allocate(width * height * RGBA_CHANNELS)
        buffer.rewind()
        while (buffer.remaining() >= RGB_CHANNELS) {
            rgba.put(buffer.get()).put(buffer.get()).put(buffer.get()).put(0xFF.toByte())
        }
        rgba.rewind()
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            copyPixelsFromBuffer(rgba)
        }
    }

    private fun reportFailure(error: Exception) {
        _status.value = "SpatialML error: ${error.message ?: error.javaClass.simpleName}"
        Log.e(TAG, _status.value, error)
    }

    override fun close() {
        job?.cancel()
        scope.cancel()
    }

    private class FrameSubmission(val waitFor: Pipeline.RunTask?, val succeeded: Boolean)

    private class OutputTensors(
        val predictedClass: GlobalTensor,
        val predictedScore: GlobalTensor,
        val croppedImage: GlobalTensor,
    )

    private companion object {
        const val TAG = "MnistWildPipeline"
        const val ASSET_ROOT = "SpatialML/mnistwild-pipeline"
        const val PIPELINE_ID = "inference"
        const val PREDICTED_CLASS = "predicted_class"
        const val PREDICTED_SCORE = "predicted_score"
        const val CROPPED_IMAGE = "cropped_image"
        const val CAMERA_WIDTH = 3248
        const val CAMERA_HEIGHT = 2464
        const val CROP_SIZE = 224
        const val TARGET_FPS = 20
        const val READBACK_INTERVAL_FRAMES = 1L
        const val READY_POLL_MS = 100L
        const val FULL_SPACE_CAMERA_SETTLE_MS = 1_500L
        const val FAILURE_BACKOFF_MS = 100L
        const val RGB_CHANNELS = 3
        const val RGBA_CHANNELS = 4
    }
}
