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

package com.pico.spatial.sample.features.spatialml.facedetection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text

@Composable
fun FaceDetectionSample() {
    val context = LocalContext.current.applicationContext
    val pipeline = remember { FaceDetectionPipeline(context) }

    DisposableEffect(pipeline) {
        pipeline.start()
        onDispose { pipeline.close() }
    }

    PicoTheme {
        Column(
            modifier = Modifier.fillMaxSize().background(Color(color = 0xFF22262E)).padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "SpatialML Face Detection",
                textAlign = TextAlign.Center,
                style = PicoTheme.typography.titleLarge,
                color = Color.White,
            )
            Text(
                modifier = Modifier.padding(top = 16.dp),
                text =
                    "Move the SpatialML portal near a face. " +
                        "The face frame appears when detection succeeds.",
                textAlign = TextAlign.Center,
                style = PicoTheme.typography.bodyMedium,
                color = Color.White,
            )
        }
    }
}
