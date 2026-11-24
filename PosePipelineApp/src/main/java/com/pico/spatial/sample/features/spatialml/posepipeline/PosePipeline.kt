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

package com.pico.spatial.sample.features.spatialml.posepipeline

import android.content.Context
import android.util.Log
import com.pico.spatial.core.math.Matrix4
import com.pico.spatial.core.math.Transform
import com.pico.spatial.ml.readback.readbackContentSuspend
import com.pico.spatial.ml.securemr.SpatialMLException
import com.pico.spatial.ml.securemr.SpatialMLInstance
import com.pico.spatial.ml.securemr.SpatialMLSession
import com.pico.spatial.ml.securemr.loadPipelinePackageFromAssets
import java.io.IOException
import kotlin.math.abs
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

/** Runs the schema-v2 pose graph and exposes CPU-readable transforms for SpatialSDK rendering. */
internal class PosePipeline(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _status = MutableStateFlow("Initializing SpatialML…")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _frame = MutableStateFlow<PoseFrame?>(null)
    val frame: StateFlow<PoseFrame?> = _frame.asStateFlow()

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
                        imageWidth = CAMERA_SIZE,
                        imageHeight = CAMERA_SIZE,
                        containerWidth = 0,
                        containerHeight = 0,
                        containerDepth = 0,
                        containerType = SpatialMLSession.ContainerType.DISABLED,
                    )
                )
            )
        val bundle = session.loadPipelinePackageFromAssets(ASSET_ROOT)
        val inference = checkNotNull(bundle.pipelines[PIPELINE_ID])
        val bodyTransforms = checkNotNull(bundle.globalTensors[BODY_TRANSFORMS])
        val poseScore = checkNotNull(bundle.globalTensors[POSE_SCORE])
        Log.i(TAG, "pipeline_loaded package=$ASSET_ROOT backend=npu rendering=readback")
        _status.value = "Move into view to drive the pose markers"
        val intervalMs = 1000L / TARGET_FPS
        var frame = 0L
        while (currentCoroutineContext().isActive) {
            try {
                // Runs from the same pipeline are ordered by SpatialML. Match the SpatialML sample
                // by starting each inference cycle without an explicit cross-pipeline dependency.
                inference.pipeline.submit(inference.submitBindings, null, null)
                _frame.value = readFrame(poseScore, bodyTransforms, frame)
                if (frame == 0L) Log.i(TAG, "first_readback_ready")
                frame += 1
            } catch (error: SpatialMLException) {
                Log.w(TAG, "submit_failed frame=$frame error=${error.message}")
                delay(FAILURE_BACKOFF_MS)
            }
            delay(intervalMs)
        }
    }

    private suspend fun readFrame(
        poseScore: com.pico.spatial.ml.securemr.GlobalTensor,
        bodyTransforms: com.pico.spatial.ml.securemr.GlobalTensor,
        frame: Long,
    ): PoseFrame {
        val score = poseScore.readbackContentSuspend().use { it[0].toFloat() }
        // Match pose_display_pipeline.json: body transforms drive the visible, view-locked GLTF
        // every frame. pose_score is diagnostic metadata and does not control rendering.
        val transforms = readTransforms(bodyTransforms)
        if (frame == 0L || frame % POSE_LOG_INTERVAL_FRAMES == 0L) {
            Log.i(
                TAG,
                "pose_readback frame=$frame score=$score valid_transforms=${transforms.size}",
            )
        }
        return PoseFrame(frame, score, transforms)
    }

    private suspend fun readTransforms(
        bodyTransforms: com.pico.spatial.ml.securemr.GlobalTensor
    ): List<Transform> {
        val values =
            bodyTransforms.readbackContentSuspend().use { content ->
                content[0 until MARKER_COUNT * MATRIX_SIZE].asFloatArray()
            }
        if (values.any { !it.isFinite() }) return emptyList()
        val matrices =
            List(MARKER_COUNT) { index ->
                val start = index * MATRIX_SIZE
                Matrix4(
                    values[start],
                    values[start + 1],
                    values[start + 2],
                    values[start + 3],
                    values[start + 4],
                    values[start + 5],
                    values[start + 6],
                    values[start + 7],
                    values[start + 8],
                    values[start + 9],
                    values[start + 10],
                    values[start + 11],
                    values[start + 12],
                    values[start + 13],
                    values[start + 14],
                    values[start + 15],
                )
            }
        return if (
            matrices.all { it.isUsablePoseMatrix() } && matrices.any { it.position.hasPoseExtent() }
        ) {
            matrices.map(Matrix4::toTransform)
        } else {
            emptyList()
        }
    }

    private fun Matrix4.isUsablePoseMatrix(): Boolean =
        position.isBounded() &&
            right.isBoundedBasis() &&
            up.isBoundedBasis() &&
            forward.isBoundedBasis()

    private fun com.pico.spatial.core.math.Vector3.isBounded(): Boolean =
        isFinite() &&
            abs(x) <= MAX_MATRIX_COMPONENT &&
            abs(y) <= MAX_MATRIX_COMPONENT &&
            abs(z) <= MAX_MATRIX_COMPONENT

    private fun com.pico.spatial.core.math.Vector3.isBoundedBasis(): Boolean =
        isBounded() &&
            (abs(x) > MIN_BASIS_COMPONENT ||
                abs(y) > MIN_BASIS_COMPONENT ||
                abs(z) > MIN_BASIS_COMPONENT)

    private fun com.pico.spatial.core.math.Vector3.hasPoseExtent(): Boolean =
        abs(x) > MIN_POSE_EXTENT || abs(y) > MIN_POSE_EXTENT || abs(z) > MIN_POSE_EXTENT

    internal class PoseFrame(val sequence: Long, val score: Float, val transforms: List<Transform>)

    private fun reportFailure(error: Exception) {
        _status.value = "SpatialML error: ${error.message ?: error.javaClass.simpleName}"
        Log.e(TAG, _status.value, error)
    }

    override fun close() {
        job?.cancel()
        scope.cancel()
    }

    private companion object {
        const val TAG = "PosePipeline"
        const val ASSET_ROOT = "SpatialML/pose-pipeline"
        const val PIPELINE_ID = "inference"
        const val BODY_TRANSFORMS = "body_transforms"
        const val POSE_SCORE = "pose_score"
        const val CAMERA_SIZE = 512
        const val TARGET_FPS = 20
        const val MARKER_COUNT = 13
        const val MATRIX_SIZE = 16
        const val POSE_LOG_INTERVAL_FRAMES = 32L
        const val MIN_BASIS_COMPONENT = 0.0001f
        const val MIN_POSE_EXTENT = 0.0001f
        const val MAX_MATRIX_COMPONENT = 1000f
        const val READY_POLL_MS = 100L
        const val FULL_SPACE_CAMERA_SETTLE_MS = 1_500L
        const val FAILURE_BACKOFF_MS = 100L
    }
}
