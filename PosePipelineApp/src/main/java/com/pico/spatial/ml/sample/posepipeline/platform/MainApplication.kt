package com.pico.spatial.ml.sample.posepipeline.platform

import android.app.Application
import com.pico.spatial.sample.features.spatialml.posepipeline.PoseSample
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.launch

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch { DefaultStage { PoseSample() } }
    }
}
