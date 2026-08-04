/*
 * Copyright 2026 PICO. All rights reserved.
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
package com.pico.spatial.ml.sample.sr.view

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pico.spatial.ml.sample.sr.vm.SrViewModel
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults.buttonColors
import com.pico.spatial.ui.design.Slider
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.windows.Toolbar
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.foundation.tooltip
import kotlin.math.pow

/**
 * The control bar in RelaxMR mode, including a slider bar for zooming factor, and a button to
 * launch VQA.
 *
 * @param srViewModel the viewmodel which maintains both the VQA RESTFul API connection and
 *   SpatialML session. The change of the zooming factor, and the launch of the VQA will both be
 *   handled by the viewmodel.
 */
@Preview
@Composable
fun ControlBarAugment(srViewModel: SrViewModel = viewModel()) {
    val currentZoomFactor = remember { mutableStateOf("4.0x") }
    var sliderValue by remember { mutableStateOf(1f) }

    Toolbar {
        Row(
            Modifier.wrapContentWidth().wrapContentHeight().backgroundMaterial(true),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Slider(
                value = sliderValue,
                valueRange = 0f..2f,
                onValueChange = { newValue ->
                    val ratio = 4f.pow(newValue)
                    val residual = (10 * (ratio - ratio.toInt())).toInt()
                    currentZoomFactor.value = "${ratio.toInt()}.${residual}x"
                    srViewModel.resetUpscaleRatio(ratio)
                    sliderValue = newValue
                },
                modifier = Modifier.width(200.dp).tooltip("Slide to zoom in and zoom out"),
            )
            Text(
                text = currentZoomFactor.value,
                textAlign = TextAlign.Center,
                color = Color.Black,
                fontWeight = FontWeight.Normal,
                fontSize = 24.sp,
                modifier = Modifier.padding(5.dp).padding(end = 10.dp),
            )
            Button(
                enabled = srViewModel.vqaReady,
                onClick = { srViewModel.askQuestionAboutImage() },
                colors = buttonColors(Color.Black),
                modifier =
                    Modifier.tooltip(
                        if (srViewModel.vqaReady) "Take a picture and ask AI about what you saw"
                        else "VQA disabled because of incomplete configuration",
                        if (srViewModel.vqaReady) ""
                        else
                            "To use the default Volcano VQA engine, use adb shell setprop" +
                                " debug.spatialml.apikey <API KEY> to setup.",
                    ),
            ) {
                Text("📷 Ask AI", color = Color.White, softWrap = false)
            }
        }
    }
}
