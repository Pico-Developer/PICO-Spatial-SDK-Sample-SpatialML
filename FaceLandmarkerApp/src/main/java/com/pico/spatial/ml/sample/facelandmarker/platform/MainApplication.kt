package com.pico.spatial.ml.sample.facelandmarker.platform

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pico.spatial.ml.sample.facelandmarker.sample.FACE_MESH_WINDOW_ID
import com.pico.spatial.ml.sample.facelandmarker.sample.FaceMeshPanel
import com.pico.spatial.ml.sample.facelandmarker.sample.FaceMeshSample
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.Form
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize
import com.pico.spatial.ui.foundation.dsl.WorldScale
import com.pico.spatial.ui.foundation.dsl.launch
import com.pico.spatial.ui.platform.resize.CaptionBarType
import com.pico.spatial.ui.platform.resize.ContainerResizeType

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch {
            DefaultStage { FaceMeshSample() }
            // Movable control window in Full Space: the system caption bar handles native
            // drag/reposition, so no manual spatial drag gesture is required.
            WindowContainer(
                FACE_MESH_WINDOW_ID,
                form = Form.Planar,
                properties = {
                    defaultSize = WindowContainerSize(width = 660.dp, height = 640.dp)
                    worldScale = WorldScale.Fixed
                    resizeType = ContainerResizeType.ContentSize
                    defaultCaptionBarType = CaptionBarType.Default
                },
            ) {
                PicoTheme {
                    Box(Modifier.windowConstraints(width = 660.dp, height = 640.dp)) {
                        FaceMeshPanel()
                    }
                }
            }
        }
    }
}
