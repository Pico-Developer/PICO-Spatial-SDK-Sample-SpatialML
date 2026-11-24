/*
 * Copyright 2025 - 2026 PICO. All rights reserved.
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

package com.pico.spatial.sample.features.spatialml.stylizationpipeline

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.gesture.detectDragGestures
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

@Composable
private fun BoundingBoxSquare(
    bitmap: Bitmap,
    size: Dp = 600.dp,
    onBoxDrawn: (x1: Float, y1: Float, x2: Float, y2: Float) -> Unit = { _, _, _, _ -> },
) {
    val context = LocalContext.current.applicationContext
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    var start by remember { mutableStateOf<Offset?>(null) }
    var end by remember { mutableStateOf<Offset?>(null) }

    Box(
        modifier =
            Modifier.size(size).background(Color.LightGray).pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        start = offset
                        end = offset
                    },
                    onDrag = { change, _ -> end = change.position },
                    onDragEnd = {
                        val s = start
                        val e = end
                        if (s != null && e != null) {
                            val sizePx = size.toPx()
                            Log.i("SpatialML", "cavas draw: (${s.x}, ${s.y}) -> (${e.x}, ${e.y})")
                            val x1 = min(s.x, e.x) / sizePx
                            val y1 = min(s.y, e.y) / sizePx
                            val x2 = max(s.x, e.x) / sizePx
                            val y2 = max(s.y, e.y) / sizePx
                            val left = x1.coerceIn(0f, 1f)
                            val right = x2.coerceIn(0f, 1f)
                            val top = y1.coerceIn(0f, 1f)
                            val bottom = y2.coerceIn(0f, 1f)
                            if (
                                (right - left) * SimpleSample.IMG_SIZE > 1 &&
                                    (bottom - top) * SimpleSample.IMG_SIZE > 1
                            ) {
                                onBoxDrawn(left, top, right, bottom)
                            }
                        }
                    },
                    onDragCancel = {},
                    context = context,
                    targetedToEntity = null,
                )
            }
    ) {
        Image(
            bitmap = imageBitmap,
            contentDescription = "Selected style",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = start
            val e = end
            if (s != null && e != null) {
                val left = min(s.x, e.x)
                val top = min(s.y, e.y)
                val right = max(s.x, e.x)
                val bottom = max(s.y, e.y)

                drawRect(
                    color = Color.Red.copy(alpha = 0.3f),
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                )
            }
        }
    }
}

@Preview
@Composable
@Suppress("LongMethod")
fun SimpleDemoWindow() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope() + Dispatchers.IO
    val demoFramework = remember { SimpleSample(context, scope) }

    val assets =
        listOf(
            "SpatialML/vangogh.png",
            "SpatialML/kandinsky.png",
            "SpatialML/geometricum.jpg",
            "SpatialML/blechermosaic.jpg",
            "SpatialML/altruria.jpg",
        )

    val bitmaps: List<Bitmap> = remember {
        assets.map { name -> context.assets.open(name).use { BitmapFactory.decodeStream(it) } }
    }
    var selectedStyle by remember { mutableStateOf(bitmaps.first()) }

    LaunchedEffect(Unit) {
        val referenceTask = demoFramework.setReference(bitmaps.first(), demoFramework.initTask)
        val affineTask = demoFramework.setCameraClip(0f, 1f, 0f, 1f, referenceTask)
        demoFramework.styleTransferPipeline.runContinuously(10, affineTask)
    }

    PicoTheme {
        Column(
            // design-style: opaque-root — match the programmatic sample's control panel.
            modifier = Modifier.fillMaxSize().background(color = Color(color = 0xFF888381)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = "Stylized Camera — Pipeline",
                textAlign = TextAlign.Center,
                style = PicoTheme.typography.titleLarge,
                color = Color.White,
            )
            // demonstrating the usage of dynamic texture in SpatialML
            Spacer(modifier = Modifier.height(30.dp))

            // Selected style preview with a sub-region canvas to update the camera crop.
            // demonstrating the usage of slice tensors, to assign values to a
            // sub-region of a larger tensor
            BoundingBoxSquare(
                bitmap = selectedStyle,
                size = 300.dp,
                onBoxDrawn = { x1, y1, x2, y2 ->
                    Log.i("SpatialML", "update region: [$x1, $x2] [$y1, $y2]")
                    scope.launch {
                        demoFramework
                            .setCameraClip(xMin = x1, yMin = y1, xMax = x2, yMax = y2)
                            .await()
                    }
                },
            )

            Spacer(modifier = Modifier.height(30.dp))

            // reference selector
            bitmaps.chunked(bitmaps.size).forEach { rowBitmaps ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    rowBitmaps.forEachIndexed { idx, bitmap ->
                        val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = "reference for stylization: ${assets[idx]}",
                            modifier =
                                Modifier.padding(4.dp).weight(1f).aspectRatio(1f).clickable {
                                    selectedStyle = bitmap
                                    scope.launch { demoFramework.setReference(bitmap).await() }
                                },
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }
    }
}
