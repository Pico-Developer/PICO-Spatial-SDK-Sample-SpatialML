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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pico.spatial.ml.sample.sr.vm.SrViewModel
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults.buttonColors
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.windows.Sheet
import com.pico.spatial.ui.foundation.material.backgroundMaterial

/**
 * A sheet GUI (a smaller floating dialogue-like window) to ask the user to confirm the SecureMR
 * mode. The reason for this confirmation is that
 * 1. SecureMR's GUI is less convenient from a user's perspective, and that
 * 1. We explain what is SecureMR and how it protects user's privacy.
 *
 * This UI should only be displayed if user choose the app to run in SecureMR mode.
 *
 * @param srViewModel the viewmodel. On confirmation of the camera access, [SrViewModel.appMode]
 *   turns from [SrViewModel.AppMode.SECURE_MR] to [SrViewModel.AppMode.SECURE_MR_CONFIRMED].
 */
@Preview
@Composable
fun SecureMRConfirm(srViewModel: SrViewModel = viewModel()) {
    Sheet(
        modifier = Modifier.backgroundMaterial(true).background(Color.LightGray),
        title = {
            Text(
                "SecureMR mode",
                textAlign = TextAlign.Center,
                fontSize = 5.em,
                softWrap = false,
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 20.dp, bottom = 8.dp),
            )
        },
        leadingAction = null,
        onDismissRequest = { srViewModel.updateAppMode(SrViewModel.AppMode.NO_SELECTION) },
        bottom = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                Button(
                    onClick = { srViewModel.updateAppMode(SrViewModel.AppMode.NO_SELECTION) },
                    modifier = Modifier.padding(12.dp),
                    colors = buttonColors(Color.White),
                ) {
                    Text("Deny", color = Color.Black)
                }
                Button(
                    onClick = {
                        srViewModel.updateAppMode(SrViewModel.AppMode.SECURE_MR_CONFIRMED)
                    },
                    modifier = Modifier.padding(12.dp),
                    colors = buttonColors(
                        containerColor = Color.Black,
                        contentColor = Color.White,
                    )
                ) {
                    Text("Confirm", color = Color.White)
                }
            }
        },
    ) {
        Text(
            "In SecureMR mode, the camera data will be processed securely by PICO OS." +
                " PICO OS will not give the app any camera data. The zoomed camera images" +
                " will be shown in a separated system window, isolated from the app to" +
                " guarantee the privacy protection. User experience might be slightly" +
                " impacted.",
            textAlign = TextAlign.Justify,
            fontSize = 3.em,
            softWrap = true,
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 20.dp, bottom = 8.dp),
        )
    }
}
