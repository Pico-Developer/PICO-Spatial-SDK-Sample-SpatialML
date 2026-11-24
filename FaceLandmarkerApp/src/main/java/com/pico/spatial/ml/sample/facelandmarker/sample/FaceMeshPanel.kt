// Copyright 2026 PICO. All rights reserved.
package com.pico.spatial.ml.sample.facelandmarker.sample

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pico.spatial.ml.sample.facelandmarker.FaceEffectChoices
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.pico.spatial.ui.platform.containers.closeStage
import kotlinx.coroutines.launch

/**
 * Controls hosted in a movable WindowContainer. The system caption bar provides native
 * drag/reposition, so no manual gesture handling is needed. Reads the shared [FaceMeshController]
 * owned by the Stage composition.
 */
@Composable
fun FaceMeshPanel() {
    val activeController by FaceMeshSession.controller.collectAsStateWithLifecycle()
    val controller = activeController
    val navigator = LocalSpatialNavigator.current
    val scope = rememberCoroutineScope()
    if (controller == null) {
        PicoTheme { Text("Loading face mesh…") }
        return
    }
    val status by controller.runner.status.collectAsStateWithLifecycle()
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            controller.onPermissionResult(it)
        }
    LaunchedEffect(controller) {
        if (!controller.permission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    PicoTheme {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Face Landmarker", style = PicoTheme.typography.titleMedium)
            Text(
                controller.error
                    ?: when {
                        !controller.permission -> "Camera access is needed to find a face."
                        controller.paused -> "Paused"
                        !controller.initialized -> "Loading face mesh…"
                        controller.visible -> "Tracking · ${controller.effect.label}"
                        status.startsWith("SpatialML error") || status.startsWith("Error") -> status
                        else -> "Look toward another person to track their face."
                    }
            )
            Text(controller.effect.description, style = PicoTheme.typography.bodyMedium)
            FaceEffectChoices(controller.effect, controller.initialized, controller::selectEffect)
            if (!controller.permission)
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Allow camera")
                }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = controller::togglePause, enabled = controller.initialized) {
                    Text(if (controller.paused) "Resume" else "Pause")
                }
                Button(
                    onClick = {
                        scope.launch {
                            navigator.closeWindowContainer(
                                FACE_MESH_WINDOW_ID,
                                FACE_MESH_WINDOW_TAG,
                            )
                            closeStage()
                        }
                    }
                ) {
                    Text("Close")
                }
            }
        }
    }
}
