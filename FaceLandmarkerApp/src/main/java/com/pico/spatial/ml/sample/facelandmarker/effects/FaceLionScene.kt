// Copyright 2026 PICO. All rights reserved.
package com.pico.spatial.ml.sample.facelandmarker

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Authored meter-scale lion rig, retained for the Stage lifetime. No per-frame mesh rebuilding. */
class FaceLionScene(names: List<String>) : AutoCloseable {
    private val root =
        Entity().apply {
            setName("lion-dance-face-effect")
            enabled = false
        }
    private val motion = FaceLionMotion(names)

    private data class Joint(val transform: TransformComponent, val rest: Quat)

    private var joints = emptyMap<String, Joint>()
    private var closed = false

    suspend fun initialize(parent: Entity, ordering: FaceRenderOrder) {
        parent.addChild(root)
        var loaded: Entity? = null
        var adopted = false
        try {
            loaded = Entity.loadSuspend("asset://lion/lion-dance.glb")
            currentCoroutineContext().ensureActive()
            check(!closed)
            root.addChild(loaded)
            // Exporter records +Y up, +Z front, meters and the canonical face pose as its origin.
            // Keep the authored pivots and size; the tracked head and shared fit own placement.
            val bounds = loaded.getVisualBounds(root, recursive = true, enabledOnly = false)
            check(
                !bounds.isEmpty() && bounds.size.x in 0.40f..0.50f && bounds.size.y in 0.40f..0.50f
            ) {
                "Lion asset dimensions do not match its authoring contract: ${bounds.size}"
            }
            val head = checkNotNull(loaded.findEntity("LionDanceHead"))
            joints =
                listOf("LionJaw", "LionLidLeft", "LionLidRight", "LionEyeLeft", "LionEyeRight")
                    .associateWith { name ->
                        val entity =
                            checkNotNull(loaded.findEntity(name)) { "Missing lion joint: $name" }
                        check(entity.getParent()?.id == head.id) {
                            "Lion joint hierarchy changed: $name"
                        }
                        val transform = checkNotNull(entity.components.get<TransformComponent>())
                        Joint(transform, transform.quaternion)
                    }
            ordering.modelTree(loaded)
            adopted = true
        } finally {
            if (!adopted) {
                joints = emptyMap()
                loaded?.destroy(true)
            }
        }
    }

    fun select(selected: Boolean) {
        root.enabled = selected && !closed
        if (!selected) reset()
    }

    fun update(face: TrackedFace) {
        if (!closed && root.enabled) motion.offer(face)
    }

    fun animate(frameTimeNanos: Long) {
        if (closed || !root.enabled) return
        val pose = motion.advance(frameTimeNanos, System.nanoTime() * 1e-9)
        rotate("LionJaw", pose.jaw)
        rotate("LionLidLeft", pose.leftLid)
        rotate("LionLidRight", pose.rightLid)
        rotate("LionEyeLeft", pose.leftPitch, pose.leftYaw)
        rotate("LionEyeRight", pose.rightPitch, pose.rightYaw)
    }

    private fun rotate(name: String, pitch: Float, yaw: Float = 0f) {
        val joint = joints.getValue(name)
        joint.transform.quaternion =
            joint.rest * Quat(Vector3(0f, 1f, 0f), yaw) * Quat(Vector3(1f, 0f, 0f), pitch)
    }

    fun reset() {
        motion.reset()
        if (!closed) joints.values.forEach { it.transform.quaternion = it.rest }
    }

    override fun close() {
        if (closed) return
        closed = true
        root.destroy(true)
        joints = emptyMap()
        motion.reset()
    }
}
