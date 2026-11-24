// Copyright 2026 PICO. All rights reserved.
package com.pico.spatial.ml.sample.facelandmarker

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** One retained mesh per feature, shared by the two eyes; frame updates change only transforms. */
class FaceBuggyScene(names: List<String>) : AutoCloseable {
    private val root =
        Entity().apply {
            setName("buggy-eyes-grin")
            enabled = false
        }
    private val meshes = mutableListOf<MeshResource>()
    private val materials = mutableMapOf<FaceBuggyGeometry.Finish, PhysicallyBasedMaterial>()

    private data class Node(val entity: Entity, val mount: FaceBuggyGeometry.Mount, val eye: Int)

    private val nodes = mutableListOf<Node>()
    private val channels = names.withIndex().associate { it.value to it.index }
    private var closed = false

    suspend fun initialize(parent: Entity, order: FaceRenderOrder) {
        parent.addChild(root)
        val preparation = FaceMeshPreparation()
        for (part in FaceBuggyGeometry.parts()) {
            var created: MeshResource? = null
            try {
                withContext(Dispatchers.IO) {
                    val data =
                        preparation.prepare(
                            part.mesh.vertices,
                            part.mesh.indices,
                            needsNormals = true,
                        )
                    created =
                        MeshResource.createWithMeshModel(data.model, data.bounds, part.mesh.name)
                            .apply { toGlobal() }
                }
                currentCoroutineContext().ensureActive()
                check(!closed)
                val mesh = checkNotNull(created)
                meshes.add(mesh)
                created = null
                val material =
                    materials.getOrPut(part.finish) {
                        val c = part.finish.color
                        PhysicallyBasedMaterial.create().apply {
                            toGlobal()
                            setBlendingMode(BlendingMode.TRANSPARENT)
                            setBaseColor(Color4(c[0], c[1], c[2], 1f))
                            setRoughness(part.finish.roughness)
                            setMetallic(0f)
                            setOpacity(1f)
                            setDepthTest(true)
                            setDepthWrite(true)
                            setCullingMode(MaterialCullingMode.NONE)
                        }
                    }
                val eyes =
                    if (part.mount == FaceBuggyGeometry.Mount.MOUTH) listOf(-1) else listOf(0, 1)
                for (eye in eyes) {
                    val entity =
                        Entity().apply {
                            setName("${part.mesh.name}-$eye")
                            components.set(ModelComponent(mesh, material))
                            order.model(this)
                        }
                    root.addChild(entity)
                    nodes.add(Node(entity, part.mount, eye))
                }
            } finally {
                created?.close()
            }
        }
    }

    fun select(selected: Boolean) {
        root.enabled = selected && !closed
    }

    fun update(face: TrackedFace) {
        if (closed || !root.enabled) return
        val v = face.localVertices
        fun coefficient(name: String): Float =
            channels[name]
                ?.let { face.expressions.getOrNull(it) }
                ?.takeIf { it.isFinite() }
                ?.coerceIn(0f, 1f) ?: 0f
        val mouthX = (v[61 * 3] + v[291 * 3]) * .5f
        val mouthY = (v[0 * 3 + 1] + v[17 * 3 + 1]) * .5f
        val mouthZ = maxOf(v[0 * 3 + 2], v[17 * 3 + 2]) + .002f
        val mouthScale = abs(v[291 * 3] - v[61 * 3]) / .0491f
        val jaw = coefficient("jawOpen") * (1 - coefficient("mouthClose"))
        for (node in nodes) {
            val transform = checkNotNull(node.entity.components.get<TransformComponent>())
            if (node.eye < 0) {
                transform.position = Vector3(mouthX, mouthY, mouthZ)
                transform.scaleVector =
                    Vector3(mouthScale, mouthScale * (1 + .32f * jaw), mouthScale)
            } else {
                val a = if (node.eye == 0) 33 else 263
                val b = if (node.eye == 0) 133 else 362
                val side = if (node.eye == 0) "Right" else "Left"
                val eyeScale = abs(v[a * 3] - v[b * 3]) / .026f
                val opening =
                    (1 - .92f * coefficient("eyeBlink$side") + .15f * coefficient("eyeWide$side"))
                        .coerceIn(.08f, 1.15f)
                val gaze =
                    if (node.mount == FaceBuggyGeometry.Mount.GAZE)
                        FaceBuggyGeometry.gaze(
                            (coefficient("eyeLookOut$side") - coefficient("eyeLookIn$side")) *
                                if (node.eye == 0) -1 else 1,
                            coefficient("eyeLookUp$side") - coefficient("eyeLookDown$side"),
                        )
                    else floatArrayOf(0f, 0f, 0f)
                transform.position =
                    Vector3(
                        (v[a * 3] + v[b * 3]) * .5f + gaze[0] * eyeScale,
                        (v[a * 3 + 1] + v[b * 3 + 1]) * .5f + gaze[1] * eyeScale * opening,
                        (v[a * 3 + 2] + v[b * 3 + 2]) * .5f + gaze[2] * eyeScale,
                    )
                transform.scaleVector =
                    Vector3(
                        eyeScale,
                        eyeScale * if (node.mount == FaceBuggyGeometry.Mount.BROW) 1f else opening,
                        eyeScale,
                    )
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        root.destroy(true)
        meshes.forEach { it.close() }
        meshes.clear()
        materials.values.forEach { it.close() }
        materials.clear()
        nodes.clear()
    }

    companion object {
        private val IDENTITY = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    }
}
