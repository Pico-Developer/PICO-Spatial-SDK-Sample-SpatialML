package com.pico.spatial.ml.sample.stylized.platform

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pico.spatial.sample.features.spatialml.stylization.SimpleDemoWindow
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.launch

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch {
            DefaultWindowContainer {
                PicoTheme {
                    Box(Modifier.windowConstraints(width = 640.dp, height = 720.dp)) {
                        SimpleDemoWindow()
                    }
                }
            }
        }
    }
}
