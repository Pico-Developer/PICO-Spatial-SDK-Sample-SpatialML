// Copyright 2026 PICO. All rights reserved.
package com.pico.spatial.ml.sample.facelandmarker

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** The unchanged DamagedHelmet GLB from the earlier Spatial SDK sample. */
class FaceClosedHelmetScene : AutoCloseable {
    private val fitting =
        Entity().apply {
            setName("closed-helmet-face-fit")
            enabled = false
        }
    private var asset: Entity? = null
    private var closed = false

    suspend fun initialize(parent: Entity, ordering: FaceRenderOrder) {
        parent.addChild(fitting)
        var loaded: Entity? = null
        try {
            loaded = Entity.loadSuspend("asset://helmet/damaged-helmet.glb")
            currentCoroutineContext().ensureActive()
            check(!closed)
            fitting.addChild(loaded)
            val bounds = loaded.getVisualBounds(fitting, recursive = true, enabledOnly = false)
            check(!bounds.isEmpty() && bounds.size.x > 0f)
            val scale = .25f * 1.5f / bounds.size.x
            val transform = checkNotNull(fitting.components.get<TransformComponent>())
            transform.scaleVector = Vector3(scale, scale, scale)
            transform.position =
                Vector3(
                    -bounds.center.x * scale,
                    .035f - bounds.center.y * scale,
                    -.02f - bounds.center.z * scale,
                )
            ordering.modelTree(loaded)
            asset = loaded
        } finally {
            if (asset !== loaded) loaded?.destroy(true)
        }
    }

    fun select(selected: Boolean) {
        fitting.enabled = selected && !closed
    }

    override fun close() {
        if (!closed) {
            closed = true
            fitting.destroy(true)
            asset = null
        }
    }
}
