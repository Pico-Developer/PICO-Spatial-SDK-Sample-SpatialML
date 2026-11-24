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

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pico.spatial.core.ecs.AnchorComponent
import com.pico.spatial.core.ecs.AnchorEntity
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.anchor.AnchorTarget
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.content.SpatialView
import kotlin.math.PI
import kotlin.math.acos

/** Automatic-stage pose sample with SpatialML inference and SpatialSDK-owned rendering. */
@Composable
fun PoseSample() {
    val context = LocalContext.current
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

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (hasCameraPermission) {
        PoseStageContent()
    }
}

@Composable
private fun PoseStageContent() {
    val context = LocalContext.current.applicationContext
    val pipeline = remember { PosePipeline(context) }
    val scene = remember { PoseScene() }
    val frame by pipeline.frame.collectAsStateWithLifecycle()

    DisposableEffect(pipeline, scene) {
        pipeline.start()
        onDispose {
            pipeline.close()
            scene.close()
        }
    }
    PicoTheme {
        SpatialView(
            modifier = Modifier.size(1.dp),
            update = { _, _ -> scene.update(frame) },
            initial = { content, _ -> scene.initialize(content) },
        )
    }
}

private class PoseScene : AutoCloseable {
    private val markerAnchor = AnchorEntity(AnchorTarget.createCameraTarget())
    private var markerNodes = emptyList<Entity>()
    private var limbNodes = emptyList<Entity>()
    private var jointMaterial: UnlitMaterial? = null
    private var limbMaterial: UnlitMaterial? = null
    private var appliedSequence = -1L
    private var hasAppliedTransforms = false

    suspend fun initialize(content: com.pico.spatial.core.container.SpatialViewContent) {
        markerAnchor.components.get<AnchorComponent>()?.positionOffset = MARKER_POSITION
        val markerTemplate = Entity.loadSuspend(INDICATOR_ASSET_URI)
        val limbTemplate = checkNotNull(markerTemplate.clone(Entity.CloneOptions(recursive = true)))
        jointMaterial =
            UnlitMaterial.create().apply {
                setBaseColor(Color4.BLUE)
                toGlobal()
            }
        limbMaterial =
            UnlitMaterial.create().apply {
                setBaseColor(Color4.WHITE)
                toGlobal()
            }
        checkNotNull(markerTemplate.components.get<ModelComponent>()).materials[0] =
            checkNotNull(jointMaterial)
        checkNotNull(limbTemplate.components.get<ModelComponent>()).materials[0] =
            checkNotNull(limbMaterial)
        markerNodes =
            List(MARKER_COUNT) { index ->
                val marker =
                    if (index == 0) markerTemplate
                    else
                        checkNotNull(
                            markerTemplate.clone(
                                Entity.CloneOptions(
                                    recursive = true,
                                    shouldShareMaterialInstance = true,
                                )
                            )
                        )
                marker.apply {
                    setName("pose-landmark-$index")
                    components.get<TransformComponent>()?.scaleVector = Vector3(JOINT_SCALE)
                    enabled = false
                    markerAnchor.addChild(this)
                }
            }
        limbNodes =
            List(LIMB_PAIRS.size) { index ->
                val limb =
                    if (index == 0) limbTemplate
                    else
                        checkNotNull(
                            limbTemplate.clone(
                                Entity.CloneOptions(
                                    recursive = true,
                                    shouldShareMaterialInstance = true,
                                )
                            )
                        )
                limb.apply {
                    setName("pose-limb-$index")
                    enabled = false
                    markerAnchor.addChild(this)
                }
            }
        content.addEntity(markerAnchor)
        Log.i(
            POSE_LOG_TAG,
            "spatial_scene_ready marker_nodes=${markerNodes.size} limbs=${limbNodes.size} " +
                "renderer=stick_figure_6_1_9 stage=automatic view_locked=true visible=true",
        )
    }

    fun update(frame: PosePipeline.PoseFrame?) {
        if (frame == null || frame.sequence == appliedSequence || markerNodes.isEmpty()) return
        if (frame.transforms.size >= markerNodes.size) {
            var accepted = true
            markerNodes.forEachIndexed { index, markerNode ->
                val transform = frame.transforms[index]
                val component = markerNode.components.get<TransformComponent>()
                if (component == null) {
                    accepted = false
                } else {
                    component.position = transform.position
                    markerNode.enabled = true
                }
            }
            LIMB_PAIRS.forEachIndexed { index, (startIndex, endIndex) ->
                val start = frame.transforms[startIndex].position
                val end = frame.transforms[endIndex].position
                val direction = end - start
                val length = direction.length()
                val limbNode = limbNodes[index]
                val component = limbNode.components.get<TransformComponent>()
                if (component == null || length <= MIN_LIMB_LENGTH) {
                    accepted = false
                    limbNode.enabled = false
                } else {
                    component.position = (start + end) * 0.5f
                    component.quaternion = rotationFromUp(direction)
                    component.scaleVector =
                        Vector3(LIMB_RADIUS_SCALE, length * LIMB_LENGTH_SCALE, LIMB_RADIUS_SCALE)
                    limbNode.enabled = true
                }
            }
            if (!hasAppliedTransforms) {
                hasAppliedTransforms = accepted
                Log.i(POSE_LOG_TAG, "first_marker_pose_applied accepted=$accepted")
            }
            if (frame.sequence % MARKER_LOG_INTERVAL_FRAMES == 0L) {
                val nose = frame.transforms[NOSE_INDEX].position
                val rightHip = frame.transforms[RIGHT_HIP_INDEX].position
                Log.i(
                    POSE_LOG_TAG,
                    "marker_pose frame=${frame.sequence} " +
                        "nose=(${nose.x},${nose.y},${nose.z}) " +
                        "right_hip=(${rightHip.x},${rightHip.y},${rightHip.z})",
                )
            }
        }
        appliedSequence = frame.sequence
    }

    override fun close() {
        markerAnchor.destroy(true)
        markerNodes = emptyList()
        limbNodes = emptyList()
        jointMaterial?.close()
        jointMaterial = null
        limbMaterial?.close()
        limbMaterial = null
        hasAppliedTransforms = false
    }

    private fun rotationFromUp(direction: Vector3): Quat {
        val normalized = direction.normalize()
        val dot = normalized.y.coerceIn(-1f, 1f)
        val axis = Vector3(normalized.z, 0f, -normalized.x)
        val axisLength = axis.length()
        return when {
            axisLength > ROTATION_EPSILON -> Quat(axis / axisLength, acos(dot))
            dot >= 0f -> Quat()
            else -> Quat(Vector3(1f, 0f, 0f), PI.toFloat())
        }
    }

    private companion object {
        const val INDICATOR_ASSET_URI =
            "asset://SpatialML/pose-pipeline/gltf/pose_indicator_sphere.glb"
        const val MARKER_COUNT = 13
        const val JOINT_SCALE = 0.0007f
        const val LIMB_RADIUS_SCALE = 0.00018f
        const val LIMB_LENGTH_SCALE = 0.01f
        const val MIN_LIMB_LENGTH = 0.0001f
        const val ROTATION_EPSILON = 0.0001f
        const val MARKER_LOG_INTERVAL_FRAMES = 32L
        const val NOSE_INDEX = 10
        const val RIGHT_HIP_INDEX = 11
        val MARKER_POSITION = Vector3(0.66f, -0.5f, -1.5f)
        val LIMB_PAIRS =
            listOf(
                0 to 2,
                1 to 3,
                11 to 0,
                12 to 1,
                11 to 12,
                4 to 5,
                4 to 6,
                5 to 7,
                6 to 8,
                7 to 9,
                4 to 11,
                5 to 12,
                10 to 4,
                10 to 5,
            )
    }
}

private const val POSE_LOG_TAG = "PosePipeline"
