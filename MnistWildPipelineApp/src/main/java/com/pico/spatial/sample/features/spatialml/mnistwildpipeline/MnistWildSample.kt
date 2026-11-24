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

@file:JvmName("MnistWildSample")

package com.pico.spatial.sample.features.spatialml.mnistwildpipeline

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.ResourceLoadingException
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.platform.containers.closeStage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Three fixed monitors matching the XR sample, rendered by the app from tensor readback. */
@Composable
fun MnistWildSample() {
    val context = LocalContext.current
    val pipeline = remember { MnistWildPipeline(context) }
    val scene = remember { MnistWildScene() }
    val scope = rememberCoroutineScope()
    var sceneStatus by remember { mutableStateOf("Loading monitors…") }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasCameraPermission = granted
        }
    val prediction by pipeline.prediction.collectAsStateWithLifecycle()
    val confidence by pipeline.confidence.collectAsStateWithLifecycle()
    val crop by pipeline.crop.collectAsStateWithLifecycle()
    val status by pipeline.status.collectAsStateWithLifecycle()
    val exit: () -> Unit = {
        scope.launch { closeStage() }
        Unit
    }
    BackHandler(onBack = exit)

    DisposableEffect(scene, pipeline) {
        onDispose {
            pipeline.close()
            scene.close()
        }
    }
    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) pipeline.start()
        else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    PicoTheme {
        SpatialView(
            modifier = Modifier.fillMaxSize(),
            update = { _, _ -> scene.update(prediction, confidence, crop) },
            attachments = {
                AttachmentPanel("mnist-controls") {
                    PicoTheme {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("MNIST Wild — Pipeline", style = PicoTheme.typography.titleSmall)
                            Text(sceneStatus, style = PicoTheme.typography.bodySmall)
                            if (!hasCameraPermission) {
                                Button(
                                    onClick = {
                                        permissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                ) {
                                    Text("Grant camera access")
                                }
                            } else if (status.startsWith("SpatialML error")) {
                                Text(status, style = PicoTheme.typography.bodySmall)
                            }
                            Button(onClick = exit) { Text("Close MNIST") }
                        }
                    }
                }
            },
            initial = { content, attachments ->
                val controls = checkNotNull(attachments.entity("mnist-controls"))
                content.addEntity(scene.root)
                controls.components.get<TransformComponent>()?.position = Vector3(0f, -0.65f, -2f)
                scene.root.addChild(controls)
                try {
                    scene.initialize()
                    sceneStatus = "3 monitors ready"
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: ResourceLoadingException) {
                    sceneStatus = "Unable to load monitors: ${error.message}"
                } catch (error: IllegalStateException) {
                    sceneStatus = "Unable to load monitors: ${error.message}"
                }
            },
        )
    }
}
