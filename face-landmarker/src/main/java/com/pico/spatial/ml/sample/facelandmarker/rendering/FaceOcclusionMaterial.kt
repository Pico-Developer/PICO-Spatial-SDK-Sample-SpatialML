// Copyright 2026 PICO. All rights reserved.
package com.pico.spatial.ml.sample.facelandmarker

import com.pico.spatial.core.ecs.resource.AssetBundle
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.ShaderGraphMaterial

/** Owns the exported MaterialX Occlusion Surface and its backing bundle for the scene lifetime. */
class FaceOcclusionMaterial
private constructor(private val bundle: AssetBundle, val material: ShaderGraphMaterial) :
    AutoCloseable {
    override fun close() {
        material.close()
        bundle.close()
    }

    companion object {
        const val BUNDLE = "asset://occlusion/face-occlusion.bundle"
        const val MATERIAL = "FaceDepthOcclusion/Root/FaceOcclusionMaterial"

        fun load(): FaceOcclusionMaterial {
            val bundle = AssetBundle.load(BUNDLE)
            var material: ShaderGraphMaterial? = null
            try {
                material = ShaderGraphMaterial.loadFromAssetBundle(bundle, MATERIAL)
                material.toGlobal()
                // Keep the exported surface/blending contract; zero-alpha unlit is not depth-only.
                material.setDepthTest(true)
                material.setDepthWrite(true)
                material.setCullingMode(MaterialCullingMode.NONE)
                check(material.valid && material.getDepthTest() && material.getDepthWrite())
                return FaceOcclusionMaterial(bundle, material)
            } catch (failure: Throwable) {
                material?.close()
                bundle.close()
                throw failure
            }
        }
    }
}
