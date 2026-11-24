// Copyright 2026 PICO. All rights reserved.
package com.pico.spatial.ml.sample.facelandmarker

import android.content.Context
import org.json.JSONObject

class FaceGeometry(
    val canonical: FloatArray,
    val triangles: List<Int>,
    val blendshapeNames: List<String>,
) {
    companion object {
        fun load(context: Context): FaceGeometry {
            val json =
                JSONObject(
                    context.assets
                        .open("${FaceModelContract.ASSETS}/data/face_geometry.json")
                        .bufferedReader()
                        .use { it.readText() }
                )
            fun ints(key: String): List<Int> =
                json.getJSONArray(key).let { array -> List(array.length()) { array.getInt(it) } }
            val canonical =
                json.getJSONArray("canonical").let { array ->
                    FloatArray(array.length()) { array.getDouble(it).toFloat() }
                }
            val names =
                json.getJSONArray("blendshapeNames").let { array ->
                    List(array.length()) { array.getString(it) }
                }
            require(names.size == 52)
            return FaceGeometry(canonical, ints("triangles"), names).also {
                require(canonical.size >= 468 * 4 && canonical.all(Float::isFinite))
                require(
                    it.triangles.size % 3 == 0 && it.triangles.all { index -> index in 0 until 478 }
                )
            }
        }
    }
}
