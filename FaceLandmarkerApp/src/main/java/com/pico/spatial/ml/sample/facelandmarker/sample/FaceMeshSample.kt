// Copyright 2026 PICO. All rights reserved.
package com.pico.spatial.ml.sample.facelandmarker.sample

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.ml.sample.facelandmarker.FaceLandmarkerRunner
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.pico.spatial.ui.platform.containers.closeStage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Full Space Stage content: only the retained 3D face scene lives here. The controls run in a
 * separate movable WindowContainer (see [FaceMeshPanel]) so the system caption bar handles dragging
 * instead of a manual attachment-panel gesture.
 */
@Composable
fun FaceMeshSample() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val navigator = LocalSpatialNavigator.current
    val controller = remember(context) { FaceMeshController(context, scope) }
    BackHandler { scope.launch { closeStage() } }
    DisposableEffect(controller, navigator) {
        FaceMeshSession.attach(controller)
        onDispose {
            controller.close()
            runCatching {
                navigator.closeWindowContainer(FACE_MESH_WINDOW_ID, FACE_MESH_WINDOW_TAG)
            }
        }
    }
    LaunchedEffect(controller) {
        navigator.openWindowContainer(FACE_MESH_WINDOW_ID, FACE_MESH_WINDOW_TAG)
    }
    PicoTheme {
        SpatialView(
            modifier = Modifier.fillMaxSize(),
            initial = { content, attachments ->
                try {
                    controller.renderer.initialize(content, attachments.entity("options_launcher"))
                    controller.onSceneReady()
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (failure: Exception) {
                    controller.error = "Face mesh could not load: ${failure.message}"
                    Log.e(FaceLandmarkerRunner.TAG, "face_mesh_initialization_error", failure)
                }
            },
            attachments = {
                AttachmentPanel(id = "options_launcher") {
                    Button(
                        onClick = {
                            navigator.openWindowContainer(FACE_MESH_WINDOW_ID, FACE_MESH_WINDOW_TAG)
                        }
                    ) {
                        Text("Options")
                    }
                }
            },
        )
    }
}
