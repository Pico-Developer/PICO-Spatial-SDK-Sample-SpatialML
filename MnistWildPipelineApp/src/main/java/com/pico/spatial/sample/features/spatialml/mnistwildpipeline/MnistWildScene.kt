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

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import androidx.annotation.MainThread
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Owns the original three TV meshes and their readback textures in an Automatic Stage. */
@MainThread
internal class MnistWildScene : AutoCloseable {
    val root = Entity()
    private val monitors = mutableListOf<Monitor>()
    private var lastCrop: Bitmap? = null
    private var closed = false

    suspend fun initialize() {
        // Establish the launch pose once. Subsequent head motion never moves the monitors.
        placeAtLaunchPose()
        // These poses and scale are copied from mnistwild_display_pipeline.json.
        loadMonitor("mnist-digit", Vector3(-0.5f, 0f, -1.5f))
        loadMonitor("mnist-confidence", Vector3(0.5f, 0f, -1.5f))
        loadMonitor("mnist-vst", Vector3(0f, 1f, -1.5f))
        monitors[0].setText("-1")
        monitors[1].setText("0")
        monitors[2].setText("")
        Log.i(TAG, "three_monitors_ready placement=once stage=automatic passthrough=system")
    }

    private suspend fun placeAtLaunchPose() {
        val tracking = HMDTrackingProvider()
        try {
            tracking.start()
            val pose =
                withTimeoutOrNull(LAUNCH_POSE_TIMEOUT_MILLIS) { tracking.dataFlow.first() }?.hmdPose
                    ?: tracking.latestData.hmdPose
            val parent = checkNotNull(root.getParent())
            root.components.get<TransformComponent>()?.apply {
                position = parent.convertPositionFrom(pose.position, null)
                val launchYaw = pose.rotation.toEulerAngles().yaw
                quaternion = parent.convertRotationFrom(EulerAngles(yaw = launchYaw).toQuat(), null)
            }
        } finally {
            tracking.stop()
        }
    }

    private suspend fun loadMonitor(name: String, position: Vector3) {
        val model = Entity.loadSuspend(TV_ASSET)
        if (closed) {
            model.destroy()
            return
        }
        root.addChild(model)
        currentCoroutineContext().ensureActive()
        // Preserve the asset's own Cube transform; the source pose applies to its parent.
        val placement = Entity()
        root.addChild(placement)
        placement.setName(name)
        placement.components.set(
            TransformComponent().apply {
                this.position = position
                scaleVector = Vector3(TV_SCALE)
            }
        )
        placement.addChild(model)
        val cube = checkNotNull(model.findEntity("Cube"))
        val component = checkNotNull(cube.components.get<ModelComponent>())
        val material =
            UnlitMaterial.create().apply {
                setBaseColor(Color4.WHITE)
                toGlobal()
            }
        monitors.add(Monitor(material))
        component.materials[0] = material
    }

    fun update(prediction: Int?, confidence: Float?, crop: Bitmap?) {
        if (closed || monitors.size != MONITOR_COUNT) return
        if (prediction != null) monitors[0].setText(prediction.toString())
        // The original renders the raw probability, using the default stream precision (6 digits).
        if (confidence != null)
            monitors[1].setText(
                String.format(Locale.US, "%.6f", confidence).trimEnd('0').trimEnd('.')
            )
        if (crop != null && crop !== lastCrop) {
            monitors[2].setImage(crop)
            lastCrop = crop
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        // SpatialView owns its attachment, so detach it before destroying app-owned entities.
        root
            .getChildren()
            .filter {
                it.components.get<com.pico.spatial.core.ecs.AttachmentPanelComponent>() != null
            }
            .forEach { it.removeFromParent() }
        root.destroy()
        monitors.forEach { it.close() }
        monitors.clear()
        lastCrop = null
    }

    private class Monitor(private val material: UnlitMaterial) : AutoCloseable {
        private var texture: TextureResource? = null
        private var text: String? = null

        fun setText(value: String) {
            if (value == text) return
            val bitmap = Bitmap.createBitmap(TEXT_WIDTH, TEXT_HEIGHT, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.BLACK)
                val paint =
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE
                        textSize = TEXT_SIZE
                        typeface = Typeface.SANS_SERIF
                    }
                canvas.drawText(value, TEXT_WIDTH * TEXT_X, TEXT_HEIGHT * TEXT_Y, paint)
                setImage(bitmap)
                text = value
            } finally {
                bitmap.recycle()
            }
        }

        fun setImage(bitmap: Bitmap) {
            val next = TextureResource.create(bitmap).apply { toGlobal() }
            var adopted = false
            try {
                material.setBaseColorTexture(next)
                adopted = true
            } finally {
                if (!adopted) next.close()
            }
            val previous = texture
            texture = next
            previous?.close()
        }

        override fun close() {
            material.close()
            texture?.close()
            texture = null
        }
    }

    private companion object {
        const val TAG = "MnistWildScene"
        const val TV_ASSET = "asset://SpatialML/mnistwild-pipeline/gltf/tv.gltf"
        const val MONITOR_COUNT = 3
        const val TV_SCALE = 0.5f
        const val LAUNCH_POSE_TIMEOUT_MILLIS = 5000L
        const val TEXT_WIDTH = 1440
        const val TEXT_HEIGHT = 960
        const val TEXT_SIZE = 144f
        const val TEXT_X = 0.1f
        const val TEXT_Y = 0.3f
    }
}
