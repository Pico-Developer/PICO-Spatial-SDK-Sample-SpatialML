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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Scene-lifetime geometry; head pose, expression animation and uploads have separate owners. */
class FaceAugmentationScene(geometry: FaceGeometry) : AutoCloseable {
    private val root = Entity().apply { setName("face-effect-fit") }
    private val hairMount =
        Entity().apply {
            setName("crown-hair-mount")
            enabled = false
            root.addChild(this)
        }
    private val hairMeshes = mutableListOf<MeshResource>()
    private val hairMaterials = mutableListOf<PhysicallyBasedMaterial>()
    private val buggy = FaceBuggyScene(geometry.blendshapeNames)
    private val closedHelmet = FaceClosedHelmetScene()
    private val lion = FaceLionScene(geometry.blendshapeNames)
    private var closed = false
    private var selected = FaceEffect.MESH
    private var appliedSelection: FaceEffect? = null
    private var appliedFit = Float.NaN

    suspend fun initialize(head: Entity, order: FaceRenderOrder) {
        head.addChild(root)
        val preparation = FaceMeshPreparation()
        for (definition in FacePigtailGeometry.parts()) {
            var created: MeshResource? = null
            try {
                withContext(Dispatchers.IO) {
                    val data =
                        preparation.prepare(
                            definition.vertices,
                            definition.indices,
                            needsNormals = true,
                        )
                    created =
                        MeshResource.createWithMeshModel(data.model, data.bounds, definition.name)
                            .apply { toGlobal() }
                }
                currentCoroutineContext().ensureActive()
                check(!closed)
                val mesh = checkNotNull(created)
                hairMeshes.add(mesh)
                created = null
                val c = definition.color
                val appearance =
                    PhysicallyBasedMaterial.create()
                        .apply {
                            toGlobal()
                            setBlendingMode(BlendingMode.TRANSPARENT)
                            setBaseColor(Color4(c[0], c[1], c[2], 1f))
                            setOpacity(1f)
                            setRoughness(.64f)
                            setMetallic(0f)
                            setDepthTest(true)
                            setDepthWrite(true)
                            setCullingMode(MaterialCullingMode.NONE)
                        }
                        .also { hairMaterials.add(it) }
                val entity =
                    Entity().apply {
                        setName(definition.name)
                        components.set(ModelComponent(mesh, appearance))
                        order.model(this)
                    }
                hairMount.addChild(entity)
            } finally {
                created?.close()
            }
        }
        buggy.initialize(root, order)
        closedHelmet.initialize(root, order)
        lion.initialize(root, order)
    }

    fun select(effect: FaceEffect) {
        selected = effect
    }

    fun render(face: TrackedFace, fit: Float) {
        if (closed) return
        if (selected == FaceEffect.PIGTAILS) {
            val anchors = FaceHeadPlacement.anchors(face.localVertices)
            checkNotNull(hairMount.components.get<TransformComponent>()).position =
                Vector3(anchors.centerX, anchors.crownY - .110f, anchors.centerZ)
        }
        if (appliedFit != fit) {
            checkNotNull(root.components.get<TransformComponent>()).scaleVector =
                Vector3(fit, fit, fit)
            appliedFit = fit
        }
        if (appliedSelection != selected) {
            hairMount.enabled = selected == FaceEffect.PIGTAILS
            buggy.select(selected == FaceEffect.EYES)
            closedHelmet.select(selected == FaceEffect.CLOSED_HELMET)
            lion.select(selected == FaceEffect.LION)
            appliedSelection = selected
        }
        if (selected == FaceEffect.EYES) buggy.update(face)
        if (selected == FaceEffect.LION) lion.update(face)
    }

    fun animate(frameTimeNanos: Long) {
        if (selected == FaceEffect.LION) lion.animate(frameTimeNanos)
    }

    fun hide() {
        lion.reset()
    }

    override fun close() {
        if (closed) return
        closed = true
        buggy.close()
        lion.close()
        closedHelmet.close()
        root.destroy(true)
        hairMeshes.forEach { it.close() }
        hairMaterials.forEach { it.close() }
        hairMeshes.clear()
        hairMaterials.clear()
    }

    companion object {
        private val IDENTITY = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    }
}
