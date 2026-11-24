// Copyright 2026 PICO. All rights reserved.
package com.pico.spatial.ml.sample.facelandmarker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text

/** Built-in buttons inherit theme, hover, haptics and slot colors from SpatialUI. */
@Composable
fun FaceEffectChoices(selected: FaceEffect, enabled: Boolean, onSelect: (FaceEffect) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in FaceEffect.entries.chunked(3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (effect in row) Button(onClick = { onSelect(effect) }, enabled = enabled) {
                    Text(if (effect == selected) "✓ ${effect.label}" else effect.label)
                }
            }
        }
    }
}
