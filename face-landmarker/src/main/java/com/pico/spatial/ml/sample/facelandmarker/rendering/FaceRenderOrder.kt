// Copyright 2026 PICO. All rights reserved.
package com.pico.spatial.ml.sample.facelandmarker

import com.pico.spatial.core.ecs.DrawOrderGroup
import com.pico.spatial.core.ecs.DrawOrderGroupComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.UnlitMaterial

/** One transparent pass. Per the rendering guide, larger indices draw before smaller indices. */
class FaceRenderOrder {
    private val group = DrawOrderGroup.create()
    private var nextModel = 1

    fun depthFace(entity: Entity) {
        entity.components.set(DrawOrderGroupComponent(group, 10000))
    }

    fun paint(entity: Entity) {
        entity.components.set(DrawOrderGroupComponent(group, 0))
    }

    fun model(entity: Entity) {
        check(nextModel < 10000)
        entity.components.set(DrawOrderGroupComponent(group, nextModel++))
    }

    fun modelTree(root: Entity): Int {
        var count = 0
        fun visit(node: Entity) {
            node.components.get<ModelComponent>()?.let { component ->
                // Preserve imported textures and PBR values, while entering the face's same pass.
                component.materials.forEach { material ->
                    when (material) {
                        is PhysicallyBasedMaterial ->
                            material.apply {
                                setBlendingMode(BlendingMode.TRANSPARENT)
                                setOpacity(1f)
                                setDepthTest(true)
                                setDepthWrite(true)
                            }
                        is UnlitMaterial ->
                            material.apply {
                                setBlendingMode(BlendingMode.TRANSPARENT)
                                setOpacity(1f)
                                setDepthTest(true)
                                setDepthWrite(true)
                            }
                        else ->
                            error(
                                "Unsupported face-effect material: ${material.javaClass.simpleName}"
                            )
                    }
                }
                model(node)
                count++
            }
            node.getChildren().forEach(::visit)
        }
        visit(root)
        check(count > 0) { "Face effect has no renderable model" }
        return count
    }
}
