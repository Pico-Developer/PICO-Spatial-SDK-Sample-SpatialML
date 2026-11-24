package com.pico.spatial.ml.sample.hello

import android.content.Context
import android.util.Log
import com.pico.spatial.ml.securemr.Pipeline
import com.pico.spatial.ml.securemr.SceneGraphProperty.PBRMaterials
import com.pico.spatial.ml.securemr.SpatialMLInstance
import com.pico.spatial.ml.securemr.SpatialMLSession
import com.pico.spatial.ml.securemr.Tensor.DataType
import com.pico.spatial.ml.securemr.Tensor.MultiDimensionalInitInfo
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Owned by one composition coroutine. SDK handle disposal is cleaner-managed in 6.2.8. */
class CameraPreview(private val context: Context) {
    suspend fun run(report: suspend (String) -> Unit) {
        val instance = SpatialMLInstance.create(context)
        val connected =
            withTimeoutOrNull(10_000L) {
                while (!instance.ready) delay(100)
                true
            }
        check(connected == true) { "SpatialML connection timed out after 10 seconds" }
        val session =
            checkNotNull(
                instance.createSession(SpatialMLSession.InitInfo(512, 512, 1200, 1200, 200))
            ) {
                "SpatialML disconnected before session creation"
            }
        val texture =
            session.newGlobalTensor(
                MultiDimensionalInitInfo(DataType.Image.R8G8B8A8_U_DYNAMIC, intArrayOf(512, 512))
            )
        val scene = session.newSceneFromGLTFSuspend("SpatialML/Display512.glb")
        val setup =
            session.newPipeline().apply {
                updateSceneGraphProperty(scene, "/", PBRMaterials[0].BaseColorTexture, texture)
                switchSceneVisibility(scene, scene)
            }
        val frames =
            session.newPipeline().apply {
                val rgb =
                    newLocalTensor(
                        MultiDimensionalInitInfo(DataType.Image.R8G8B8_U, intArrayOf(512, 512))
                    )
                rectifiedVSTAccess(rgb, null, null, null)
                convertColor(Pipeline.ColorConversion.RGB_TO_RGBA, rgb, texture)
            }
        val hide = session.newPipeline().apply { switchSceneVisibility(scene, newLocalTensor(0)) }
        var latest: Pipeline.RunTask? = null
        try {
            latest = setup.submit(emptyMap(), null, null)
            var first = true
            var submitted = 0L
            while (currentCoroutineContext().isActive) {
                // Same-pipeline runs are ordered by the SDK; only the first crosses from setup.
                latest = frames.submit(emptyMap(), null, if (first) latest else null)
                first = false
                submitted++
                if (submitted == 1L || submitted % 10L == 0L) {
                    report("Submitted $submitted frames; check the live panel for output.")
                }
                // A conservative tutorial rate, not a native completion fence or queue limit.
                delay(100)
            }
        } finally {
            // Cancellation stops submissions. Hide follows the latest accepted task.
            // There is no public RunTask.await() or deterministic session.close() in 6.2.8.
            withContext(NonCancellable) {
                try {
                    hide.submit(emptyMap(), null, latest)
                } catch (error: Exception) {
                    Log.w("SpatialMLHello", "Could not submit scene hide", error)
                }
            }
        }
    }
}
