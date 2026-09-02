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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pico.spatial.ml.sample.sr.vm.SrViewModel
import com.pico.spatial.ml.sample.sr.vm.SrViewModel.AppMode
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults.buttonColors
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.defaultColorScheme
import com.pico.spatial.ui.foundation.material.backgroundMaterial

/**
 * The first and backbone UI in the super-resolution window container after launched. It gives users
 * a selection between SecureMR and RelaxMR mode, and calling SpatialML functionalities according to
 * the user choices.
 *
 * @param srViewModel the viewmodel, maintaining SpatialML session (as well as resources) and
 *   connection to the VQA RESTFul API.
 */
@Preview
@Composable
fun MainContainer(srViewModel: SrViewModel = viewModel()) {

    val appMode by srViewModel.appMode.collectAsState()

    PicoTheme(
        colorScheme = defaultColorScheme()
    ) {
        when (appMode) {
            AppMode.NO_SELECTION ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .backgroundMaterial(true)
                        .background(Color.LightGray),
                ) {
                    Text(
                        "SpatialML Demo",
                        textAlign = TextAlign.Center,
                        fontSize = 8.em,
                        softWrap = false,
                        modifier =
                            Modifier.padding(
                                start = 10.dp,
                                end = 10.dp,
                                top = 20.dp,
                                bottom = 8.dp
                            ),
                    )
                    Text(
                        "The app can be run in SecureMR mode or RelaxMR mode. In SecureMR mode, the" +
                                " zoomed camera image will be handled securely by the system, and rendered" +
                                " in a separated window, isolated from the app to fully protect users privacy." +
                                " In RelaxMR mode, the zoomed camera image will be returned to the app," +
                                " allowing for better user interfaces, but will request the user to explicitly" +
                                " allow the app to access the camera data.",
                        textAlign = TextAlign.Justify,
                        fontSize = 4.em,
                        softWrap = true,
                        modifier =
                            Modifier.padding(
                                start = 40.dp,
                                end = 40.dp,
                                top = 35.dp,
                                bottom = 8.dp
                            ),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = { srViewModel.updateAppMode(AppMode.SECURE_MR) },
                            modifier = Modifier.padding(12.dp),
                            colors = buttonColors(Color.White),
                        ) {
                            Text("SecureMR mode", color = Color.Black)
                        }

                        Button(
                            onClick = { srViewModel.updateAppMode(AppMode.RELAX_MR) },
                            modifier = Modifier.padding(12.dp),
                            colors = buttonColors(
                                containerColor = Color.Black,
                                contentColor = Color.White,
                            )
                        ) {
                            Text("RelaxMR mode", color = Color.White)
                        }
                    }
                }

            AppMode.RELAX_MR -> CameraConfirm(srViewModel)
            AppMode.SECURE_MR -> SecureMRConfirm(srViewModel)
            else -> SuperResolutionSpatialView(srViewModel)
        }
    }
}
