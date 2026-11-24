// Copyright 2026 PICO. All rights reserved.
package com.pico.spatial.ml.sample.facelandmarker

import android.graphics.Bitmap
import android.util.Log
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PolygonFillMode
import com.pico.spatial.core.ecs.resource.ShaderGraphMaterial
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector2
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Shared MaterialX facial occluder for helmets, the cap and pigtails, with optional surface paint.
 */
class FaceOcclusionScene(private val geometry: FaceGeometry) : AutoCloseable {
    private val entity = Entity().apply { setName("face-depth-occluder") }
    val ordering = FaceRenderOrder()
    private val decoration =
        Entity().apply {
            setName("tracked-face-paint")
            enabled = false
        }
    private val paintAtlas = FacePaintAtlas(geometry.canonical)
    // Fill the oral aperture so the grin covers the actual mouth. The same depth surface
    // also prevents the helmet's rear shell showing through an open mouth.
    private val surfaceTriangles = geometry.triangles + FacePaintAtlas.MOUTH_FILL
    private val uv = List(478) { Vector2(paintAtlas.uv[it * 2], paintAtlas.uv[it * 2 + 1]) }
    private val paints = mutableMapOf<FaceEffect, UnlitMaterial>()
    private val textures = mutableListOf<TextureResource>()
    private val preparation = FaceMeshPreparation()
    private val uploadMutex = Mutex()
    private var material: ShaderGraphMaterial? = null
    private var materialOwner: FaceOcclusionMaterial? = null
    private var mesh: MeshResource? = null
    private var uploadingResource: MeshResource? = null
    private var closed = false

    suspend fun initialize(head: Entity, helmet: Entity) {
        check(material == null && !closed)
        // SpatialEngine recognizes the exported Occlusion Surface. A zero-alpha unlit
        // color material does not provide the same depth/occlusion pass contract.
        materialOwner = FaceOcclusionMaterial.load()
        material = checkNotNull(materialOwner).material
        checkNotNull(decoration.components.get<TransformComponent>()).position =
            Vector3(0f, 0f, .001f)
        paints[FaceEffect.MESH] =
            UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
                toGlobal()
                setBaseColor(Color4(0f, 1f, 1f, .65f))
                setOpacity(.65f)
                setDepthTest(true)
                setDepthWrite(false)
                setCullingMode(MaterialCullingMode.NONE)
                setPolygonFillMode(PolygonFillMode.LINE)
            }
        for (effect in listOf(FaceEffect.EYES, FaceEffect.FLAG, FaceEffect.TEAM)) {
            val bitmap =
                Bitmap.createBitmap(
                    paintAtlas.pixels(effect),
                    FacePaintAtlas.SIZE,
                    FacePaintAtlas.SIZE,
                    Bitmap.Config.ARGB_8888,
                )
            val texture =
                try {
                    TextureResource.create(bitmap).apply { toGlobal() }
                } finally {
                    bitmap.recycle()
                }
            textures.add(texture)
            paints[effect] =
                UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
                    toGlobal()
                    setBaseColor(Color4(1f, 1f, 1f, 1f))
                    setBaseColorTexture(texture)
                    setDepthTest(true)
                    setDepthWrite(false)
                    setCullingMode(MaterialCullingMode.NONE)
                }
        }
        val models = ordering.modelTree(helmet)
        // Allocate and bind a hidden neutral surface once, avoiding first-face resource creation.
        // The head remains disabled until a measured mesh and its matching pose are applied.
        var created: MeshResource? = null
        var adopted = false
        try {
            withContext(Dispatchers.IO) {
                val canonical =
                    FloatArray(478 * 3) { index ->
                        if (index / 3 < 468) geometry.canonical[index / 3 * 4 + index % 3] * .01f
                        else 0f
                    }
                val data =
                    preparation.prepare(canonical, surfaceTriangles, needsNormals = false, uv = uv)

                currentCoroutineContext().ensureActive()
                created =
                    MeshResource.createWithMeshModel(data.model, data.bounds, "FaceDepthOccluder")
                        .apply { toGlobal() }
            }
            currentCoroutineContext().ensureActive()
            check(!closed)
            val next = checkNotNull(created)
            entity.components.set(ModelComponent(next, checkNotNull(material)))
            ordering.depthFace(entity)
            mesh = next
            adopted = true
        } finally {
            if (!adopted) created?.close()
        }
        head.addChild(entity)
        head.addChild(decoration)
        Log.i(
            FaceLandmarkerRunner.TAG,
            "face_occlusion_initialized materialx_surface=ND__occlusion_surfaceshader material=${checkNotNull(material).getName()} blending=${checkNotNull(material).getBlendingMode()} depth_write=true depth_test=true culling=none scope=helmet,cap,pigtails helmet_models=$models normals=false mesh_vertices=478 mesh_created=true hidden=true",
        )
    }

    fun setEffect(effect: FaceEffect) {
        val paint = paints[effect]
        val current = mesh
        decoration.enabled = !closed && paint != null && current != null
        if (paint != null && current != null) {
            decoration.components.set(ModelComponent(current, paint))
            ordering.paint(decoration)
        }
    }

    suspend fun update(
        frame: FaceFrame,
        localVertices: FloatArray = FaceLocalGeometry.localVertices(frame.vertices, frame.pose),
    ) =
        uploadMutex.withLock {
            check(!closed && frame.outputs.mesh)
            val current = mesh
            uploadingResource = current
            var created: MeshResource? = null
            var adopted = false
            try {
                withContext(Dispatchers.IO) {
                    val vertices = localVertices
                    val data =
                        preparation.prepare(
                            vertices,
                            surfaceTriangles,
                            needsNormals = false,
                            uv = uv,
                        )
                    currentCoroutineContext().ensureActive()
                    if (current == null) {
                        created =
                            MeshResource.createWithMeshModel(
                                    data.model,
                                    data.bounds,
                                    "FaceDepthOccluder",
                                )
                                .apply { toGlobal() }
                    } else {
                        check(current.replaceWithMeshModel(data.model, data.bounds)) {
                            "Face occluder mesh upload was rejected"
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                check(!closed)
                created?.let { next ->
                    entity.components.set(ModelComponent(next, checkNotNull(material)))
                    ordering.depthFace(entity)
                    mesh = next
                    adopted = true
                }
            } finally {
                uploadingResource = null
                if (!adopted) created?.close()
                // close() must not release a retained mesh during its noncancellable SDK upload.
                if (closed) {
                    current?.close()
                    mesh = null
                }
            }
        }

    override fun close() {
        if (closed) return
        closed = true
        entity.destroy(true)
        decoration.destroy(true)
        mesh?.takeUnless { it === uploadingResource }?.close()
        paints.values.forEach { it.close() }
        paints.clear()
        textures.forEach { it.close() }
        textures.clear()
        materialOwner?.close()
        materialOwner = null
        material = null
    }

    companion object {
        private val IDENTITY_BASIS = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    }
}
