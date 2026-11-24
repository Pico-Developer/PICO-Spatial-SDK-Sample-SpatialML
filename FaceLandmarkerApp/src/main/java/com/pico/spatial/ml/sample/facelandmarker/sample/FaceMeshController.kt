// Copyright 2026 PICO. All rights reserved.
package com.pico.spatial.ml.sample.facelandmarker.sample

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.core.content.ContextCompat
import com.pico.spatial.ml.sample.facelandmarker.FaceCalibrationProfile
import com.pico.spatial.ml.sample.facelandmarker.FaceEffect
import com.pico.spatial.ml.sample.facelandmarker.FaceGeometry
import com.pico.spatial.ml.sample.facelandmarker.FaceHelmetScene
import com.pico.spatial.ml.sample.facelandmarker.FaceLandmarkerRunner
import com.pico.spatial.ml.sample.facelandmarker.FaceTrackingState
import com.pico.spatial.ml.sample.facelandmarker.TrackedFace
import com.pico.spatial.ml.sample.perception.StagePoseHistory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

const val FACE_MESH_WINDOW_ID = "face_controls"
const val FACE_MESH_WINDOW_TAG = "options"

/** Stage and controls have separate compositions. The Stage exclusively owns this controller. */
object FaceMeshSession {
    private val active = MutableStateFlow<FaceMeshController?>(null)
    val controller = active.asStateFlow()

    fun attach(controller: FaceMeshController) {
        active.value = controller
    }

    fun detach(controller: FaceMeshController) {
        active.compareAndSet(controller, null)
    }
}

/** Owns the sample lifecycle: permission -> tracking -> measured frames -> retained mesh. */
class FaceMeshController(context: Context, parentScope: CoroutineScope) : AutoCloseable {
    private val scope =
        CoroutineScope(
            parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job])
        )
    val poses = StagePoseHistory()
    val renderer = FaceHelmetScene(FaceGeometry.load(context))
    val runner =
        FaceLandmarkerRunner(
            context,
            backend = 2,
            poses = poses,
            calibrationProfile = FaceCalibrationProfile.forRom(android.os.Build.FINGERPRINT),
        )
    private val tracking = FaceTrackingState()
    private val surfaces = Channel<TrackedFace>(Channel.CONFLATED)
    private var closed = false
    var effect by mutableStateOf(FaceEffect.MESH)
        private set

    fun selectEffect(next: FaceEffect) {
        if (closed || !initialized) return
        effect = next
        renderer.select(next)
        runner.setOutputs(next.outputs)
    }

    var initialized by mutableStateOf(false)
        private set

    var paused by mutableStateOf(false)
        private set

    var visible by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
    var permission by
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
        private set

    fun onPermissionResult(granted: Boolean) {
        if (closed) return
        permission = granted
        if (granted && initialized) runner.start()
    }

    /** Called only after the SpatialView has finished creating its retained entities. */
    fun onSceneReady() {
        if (closed || initialized) return
        initialized = true
        poses.start()
        scope.launch {
            runner.frame.collect { frame ->
                if (paused) return@collect
                try {
                    tracking.offer(frame, System.nanoTime() * 1e-9)?.let { face ->
                        if (frame?.outputs?.mesh == true) surfaces.trySend(face)
                    }
                } catch (failure: IllegalArgumentException) {
                    Log.w(FaceLandmarkerRunner.TAG, "Rejected invalid face measurement", failure)
                }
            }
        }
        scope.launch {
            for (face in surfaces) {
                if (
                    paused ||
                        tracking.current(System.nanoTime() * 1e-9)?.generation != face.generation
                )
                    continue
                try {
                    renderer.updateSurface(face)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (failure: Exception) {
                    error = "Mesh update failed: ${failure.message}"
                }
            }
        }
        scope.launch {
            while (isActive) withFrameNanos { nanos ->
                try {
                    renderer.render(
                        if (paused) null else tracking.current(System.nanoTime() * 1e-9),
                        nanos,
                    )
                    visible = renderer.visible
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (failure: Exception) {
                    renderer.hide()
                    visible = false
                    error = "Face effect could not render: ${failure.message}"
                }
            }
        }
        if (permission) runner.start()
    }

    fun togglePause() {
        if (closed || !initialized) return
        paused = !paused
        runner.paused = paused
        if (paused) {
            tracking.clear()
            renderer.hide()
            visible = false
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        initialized = false
        scope.cancel()
        FaceMeshSession.detach(this)
        runner.close()
        surfaces.close()
        poses.close()
        renderer.close()
    }
}
