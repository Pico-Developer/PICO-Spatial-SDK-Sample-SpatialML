// Copyright 2026 PICO. All rights reserved.
package com.pico.spatial.ml.sample.facelandmarker

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.pico.spatial.ml.sample.perception.StagePoseHistory
import com.pico.spatial.ml.securemr.SpatialMLInstance
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns one inference coroutine and one terminally completed frame at a time. Camera images stay in
 * the service. The app reads pose/mesh packets and registers them at exposure time.
 */
class FaceLandmarkerRunner(
    context: Context,
    private val backend: Int,
    private val poses: StagePoseHistory,
    private val calibrationProfile: FaceCalibrationProfile = FaceCalibrationProfile.SERVICE,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableFrame = MutableStateFlow<FaceFrame?>(null)
    val frame = mutableFrame.asStateFlow()
    private val mutableStatus = MutableStateFlow("Initializing SpatialML…")
    val status = mutableStatus.asStateFlow()

    @Volatile private var selection = FaceOutputs.POSE_MESH

    fun setOutputs(outputs: FaceOutputs) {
        selection = outputs
    }

    @Volatile var paused = false
    private var started = false
    // Keep the connection strongly reachable until all native graph handles have been released.
    private var instance: SpatialMLInstance? = null

    fun start() {
        if (started) return
        started = true
        scope.launch {
            var graph: FaceLandmarkerGraph? = null
            try {
                mutableStatus.value = "Waiting for Full Space head tracking…"
                while (!poses.ready) delay(50)
                mutableStatus.value = "Waiting for camera startup…"
                delay(1_500)
                val connection = SpatialMLInstance.create(appContext)
                instance = connection
                withTimeout(15_000) { while (!connection.ready) delay(50) }
                graph = FaceLandmarkerGraph.create(appContext, backend, calibrationProfile)
                val optical = initializeCamera(graph)
                check(connection.ready)
                Log.i(
                    TAG,
                    "pipeline_ready profile=$calibrationProfile backend=$backend camera_calibration_mode=initialize",
                )
                track(graph, optical)
            } catch (cancel: CancellationException) {
                currentCoroutineContext().ensureActive()
                // A timeout cancels only its child scope; Stage disposal cancels this scope.
                fail("Timed out waiting for the service or a complete face frame", cancel)
            } catch (error: Exception) {
                fail(error.message ?: error.javaClass.simpleName, error)
            } finally {
                withContext(NonCancellable + Dispatchers.Default) { graph?.close() }
                instance = null
                mutableFrame.value = null
            }
        }
    }

    private suspend fun initializeCamera(graph: FaceLandmarkerGraph): FloatArray {
        mutableStatus.value = "Initializing camera calibration…"
        graph.submitCameraInitialization()
        withTimeout(15_000) { while (!graph.cameraInitializationComplete()) delay(2) }
        return graph.readInitializedCameraToHead().also {
            Log.i(
                TAG,
                "camera_to_head_calibration source=initialize matrix=${it.contentToString()}",
            )
        }
    }

    private suspend fun track(graph: FaceLandmarkerGraph, optical: FloatArray) {
        val origin = SystemClock.elapsedRealtime()
        var sequence = 0
        var acquire = true
        var lastCapture = Double.NEGATIVE_INFINITY
        var lastFreshAt = origin
        val stabilizer = FacePoseMeshStabilizer()
        while (currentCoroutineContext().isActive) {
            if (paused) {
                mutableFrame.value = null
                mutableStatus.value = "Paused"
                acquire = true
                stabilizer.reset()
                lastFreshAt = SystemClock.elapsedRealtime()
                delay(50)
                continue
            }
            sequence++
            val selected = selection
            graph.submit(
                sequence,
                (SystemClock.elapsedRealtime() - origin) / 1000f,
                acquire,
                selected,
            )
            // Read early pose independently, but keep this capture's tensors owned until terminal
            // completion.
            if (graph.submittedEarlyPose) {
                val early =
                    readFrame(
                        sequence,
                        FaceOutputs.POSE,
                        { graph.poseComplete(sequence) },
                        graph::readPoseResult,
                    )
                if (early.hasFace && early.freshCameraFrame && early.captureSeconds > lastCapture)
                    register(early, optical, stabilizer)?.let {
                        if (!paused && selected == selection) mutableFrame.value = it
                    }
            }
            val raw =
                readFrame(sequence, selected, { graph.resultComplete(sequence) }, graph::readResult)
            val age = System.nanoTime() * 1e-9 - raw.captureSeconds
            if (raw.captureSeconds > lastCapture && age in -.1..2.0) {
                if (lastCapture == Double.NEGATIVE_INFINITY)
                    Log.i(TAG, "camera_stream_ready seq=$sequence capture=${raw.captureSeconds}")
                lastCapture = raw.captureSeconds
                lastFreshAt = SystemClock.elapsedRealtime()
            }
            check(SystemClock.elapsedRealtime() - lastFreshAt < 3_000) {
                "Camera stream stopped supplying fresh frames. Retry camera capture."
            }
            val presented = register(raw, optical, stabilizer)
            mutableFrame.value = if (paused || selected != selection) null else presented
            mutableStatus.value =
                when {
                    paused -> "Paused"
                    raw.hasFace && presented == null -> "Waiting for the matching head pose…"
                    presented?.hasFace == true -> "Tracking face mesh"
                    else -> "Looking for a face…"
                }
            acquire = raw.needsDetection
            if (sequence == 1 || sequence % 100 == 0)
                Log.i(
                    TAG,
                    "face_frame seq=$sequence detected=${raw.hasFace} registered=${presented != null} capture_fresh=${raw.freshCameraFrame} capture=${raw.captureSeconds}",
                )
            yield()
        }
    }

    private suspend fun readFrame(
        sequence: Int,
        outputs: FaceOutputs,
        complete: () -> Boolean,
        read: () -> FloatArray,
    ): FaceFrame {
        val packet =
            withTimeout(15_000) { readCompletedFaceResult(sequence, complete, read = read) }
        return FaceOutputPacket.decode(packet, outputs, System.nanoTime() * 1e-9)
    }

    private fun register(
        raw: FaceFrame,
        optical: FloatArray,
        stabilizer: FacePoseMeshStabilizer,
    ): FaceFrame? {
        // A missing historical pose withholds presentation; never substitute the current pose.
        val head = poses.atCapture(raw.captureSeconds) ?: return null
        val stage =
            raw.inStage(calibrationProfile.serviceToStage(head, optical, raw.captureCameraPose))
        return if (stage.hasFace && stage.freshCameraFrame)
            stage.withPresentationGeometry(
                stabilizer.update(
                    stage.vertices,
                    stage.pose,
                    stage.captureSeconds,
                    stage.trackingIdentity,
                    calibrationProfile,
                )
            )
        else stage
    }

    private fun fail(message: String, cause: Throwable) {
        mutableFrame.value = null
        mutableStatus.value = "SpatialML error: $message"
        Log.e(TAG, mutableStatus.value, cause)
    }

    override fun close() {
        scope.cancel()
    }

    companion object {
        const val TAG = "FaceLandmarker"
    }
}

/** Completion and packet sequence must both match before permitting another capture. */
suspend fun readCompletedFaceResult(
    sequence: Int,
    complete: () -> Boolean,
    pollMillis: Long = 2,
    read: () -> FloatArray,
): FloatArray {
    require(pollMillis in 1..10)
    while (!complete()) delay(pollMillis)
    var snapshot = read()
    while (snapshot[0] != sequence.toFloat()) {
        delay(pollMillis)
        snapshot = read()
    }
    return snapshot
}
