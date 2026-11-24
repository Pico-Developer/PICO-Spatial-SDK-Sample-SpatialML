package com.pico.spatial.ml.sample.mnistwildpipeline.platform

import android.app.Application
import com.pico.spatial.sample.features.spatialml.mnistwildpipeline.MnistWildSample
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.launch

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch { DefaultStage { MnistWildSample() } }
    }
}
