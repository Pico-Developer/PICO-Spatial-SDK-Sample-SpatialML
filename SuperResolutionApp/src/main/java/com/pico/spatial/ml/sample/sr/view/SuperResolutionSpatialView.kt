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
package com.pico.spatial.ml.sample.sr.view

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pico.spatial.core.ecs.AttachmentPanelComponent
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ml.sample.sr.vm.SrViewModel
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.ButtonDefaults.buttonColors
import com.pico.spatial.ui.design.CircularProgressIndicator
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.R
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.windows.Subwindow
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The spatial-view to show 2D mixing 3D content in the super-resolution. This view will only be
 * brought up when the user confirms SecureMR v.s. RelaxMR mode. In RelaxMR mode, when the VQA
 * button is clicked, a subwindow will also be shown to demonstrate the VQA results.
 *
 * @param srViewModel the viewmodel which maintains the app's finite state machine. This view will
 *   behave differently between [SrViewModel.AppMode.RELAX_MR_CONFIRMED] and
 *   [SrViewModel.AppMode.SECURE_MR_CONFIRMED].
 */
@SuppressWarnings("LongMethod", "CyclomaticComplexMethod")
@Composable
fun SuperResolutionSpatialView(srViewModel: SrViewModel = viewModel()) {
    val context = LocalContext.current.applicationContext

    val pitchMax = 140f
    val pitchMin = 40f
    val pitchRatio = 30f

    val currentZoomFactor = remember { mutableStateOf("4x") }

    val queryStatus by srViewModel.queryStatus.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val appMode = remember { srViewModel.appMode.value }

    val launcher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) {
            isGranted: Boolean ->
            hasCameraPermission = isGranted
        }

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission && appMode == SrViewModel.AppMode.RELAX_MR_CONFIRMED) {
            launcher.launch(Manifest.permission.CAMERA)
        } else {
            srViewModel.init(context, 4.0f)
        }
    }

    if (appMode == SrViewModel.AppMode.RELAX_MR_CONFIRMED) {
        ControlBarAugment(srViewModel)
    }
    if (queryStatus != SrViewModel.VQAStatus.NO_ACTION) {
        Subwindow {
            Column(
                Modifier.width(320.dp).wrapContentHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start,
            ) {
                IconButton(
                    onClick = { srViewModel.closeVQAResult() },
                    size = ButtonDefaults.Small,
                    colors = buttonColors(Color.Transparent, Color.Black),
                    modifier =
                        Modifier.padding(start = 10.dp, end = 10.dp, top = 5.dp, bottom = 10.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_sui_close),
                        contentDescription = "Close",
                    )
                }
                Column(
                    Modifier.fillMaxWidth().wrapContentHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        bitmap = srViewModel.latestBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier =
                            Modifier.sizeIn(maxWidth = 240.dp, maxHeight = 240.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .padding(20.dp),
                    )
                    when (queryStatus) {
                        SrViewModel.VQAStatus.ANSWER_OBTAINED ->
                            Text(
                                srViewModel.latestMessage,
                                textAlign = TextAlign.Justify,
                                fontSize = 3.em,
                                softWrap = true,
                                modifier = Modifier.padding(horizontal = 40.dp, vertical = 15.dp),
                            )

                        else -> CircularProgressIndicator()
                    }
                }
            }
        }
    }

    if (appMode != SrViewModel.AppMode.RELAX_MR_CONFIRMED || hasCameraPermission) {
        // render the view only (1) not in relax mr mode, or (2) in relax mr mode, and
        // camera permission is given
        SpatialView(
            modifier =
                Modifier.background(Color.Transparent).pointerInput(Unit) {
                    detectSpatialDragGesture(
                        context,
                        TargetEntity.any { it.getName() == "Handle" },
                    ) { event ->
                        event.targetEntity?.components?.get(TransformComponent::class.java)?.apply {
                            val newPitch =
                                max(
                                    min(
                                        eulerAngles.pitch + event.dragAmount.y / pitchRatio,
                                        pitchMax,
                                    ),
                                    pitchMin,
                                )
                            Log.i(
                                "SpatialML",
                                "Handle is dragged for [${event.dragAmount.x}, " +
                                    "${event.dragAmount.y}, ${event.dragAmount.z}] " +
                                    "=> pitch of the handle becomes $newPitch degrees. " +
                                    "Original euler angles are [yaw: ${eulerAngles.yaw}" +
                                    ", roll: ${eulerAngles.roll}, pitch " +
                                    "${eulerAngles.pitch}]",
                            )
                            setEulerAngles(
                                EulerAngles(
                                    yaw = eulerAngles.yaw,
                                    roll = eulerAngles.roll,
                                    pitch = newPitch,
                                )
                            )
                            val ratio = 16f / 4f.pow((newPitch - 90) / (pitchMax - pitchMin) + 1)
                            if (ratio - ratio.toInt() < 0.1f) {
                                currentZoomFactor.value = "${ratio.toInt()}x"
                            } else {
                                val residual = (10 * (ratio - ratio.toInt())).toInt()
                                currentZoomFactor.value = "${ratio.toInt()}.${residual}x"
                            }
                            srViewModel.resetUpscaleRatio(ratio)
                        }
                    }
                },
            attachments = {
                AttachmentPanel(
                    id = "zoom_factor",
                    alignment = AttachmentPanelComponent.Alignment.TOP_CENTER,
                ) {
                    Box(
                        modifier =
                            Modifier.background(Color(color = 0xB33D8BFF), RoundedCornerShape(6.dp))
                    ) {
                        Text(
                            text = currentZoomFactor.value,
                            textAlign = TextAlign.Center,
                            color = Color(color = 0xF2FFFFFF),
                        )
                    }
                }
            },
            update = { content, _ ->
                if (hasCameraPermission && !srViewModel.textureHasReset) {
                    content.entities
                        .filter { it.getName() == "display512" }
                        .forEach { display ->
                            val newMat = UnlitMaterial.create()
                            (display.components[ModelComponent::class.java]?.materials?.get(0)
                                    as? PhysicallyBasedMaterial)
                                ?.let { oldMat ->
                                    newMat.setOpacity(oldMat.getOpacity())
                                    newMat.setDepthTest(oldMat.getDepthTest())
                                    newMat.setDepthWrite(oldMat.getDepthWrite())
                                    newMat.setBlendingMode(oldMat.getBlendingMode())
                                    newMat.setCullingMode(oldMat.getCullingMode())
                                    newMat.setPolygonFillMode(oldMat.getPolygonFillMode())
                                }
                                ?: Log.e(
                                    "SpatialML",
                                    "fail to set the dynamic-texture tensor storing zoomed images" +
                                        " as the display512's base material",
                                )
                            srViewModel.useZoomedImageAsBaseColor(newMat)
                            display.components[ModelComponent::class.java]
                                ?.materials
                                ?.set(0, newMat)
                            Log.i(
                                "SpatialML",
                                "successfully set the dynamic-texture tensor as display512's base color",
                            )
                        }
                }
            },
        ) { content, attachments ->
            if (srViewModel.appMode.value == SrViewModel.AppMode.RELAX_MR_CONFIRMED) {
                content.addEntity(
                    withContext(Dispatchers.IO) { Entity.load("asset://Display512.glb") }
                        .also { display ->
                            display.setName("display512")
                            display.components[TransformComponent::class.java]!!.apply {
                                setPosition(Vector3(0f, 0f, -0.25f))
                            }
                        }
                )
            } else {
                val controller =
                    withContext(Dispatchers.IO) { Entity.load("asset://Controller.glb") }
                controller.components[TransformComponent::class.java]!!.apply {
                    scaleBy(0.8f)
                    setPosition(Vector3(0f, 0f, -0.25f))
                }

                attachments.entity("zoom_factor")?.let { textEntity ->
                    textEntity.components[TransformComponent::class.java]!!.apply {
                        setPosition(Vector3(0.1f, 0.0f, 0.15f))
                    }
                    controller.addChild(textEntity)
                }
                controller.findEntity("Handle")?.apply {
                    components.set(InteractableComponent())
                    components.set(
                        CollisionComponent(
                            collisionShape =
                                listOf(
                                    ShapeResource.createCapsule(0.15f, 0.1f)
                                        .offsetByTranslation(Vector3(0f, 0.15f, 0f))
                                        .offsetByRotation(EulerAngles(roll = 90f))
                                ),
                            physicsMaterial = PhysicsMaterialResource(),
                        )
                    )
                } ?: Log.e("SpatialML", "cannot find the handle entity")
                content.addEntity(controller)
            }
        }
    }
}
