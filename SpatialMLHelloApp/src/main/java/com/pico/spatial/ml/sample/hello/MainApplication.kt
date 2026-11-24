package com.pico.spatial.ml.sample.hello

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.launch
import com.pico.spatial.ui.platform.stub.SpatialLaunchActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch {
            DefaultWindowContainer {
                PicoTheme {
                    var status by remember { mutableStateOf("Connecting to SpatialML…") }
                    LaunchedEffect(Unit) {
                        try {
                            withContext(Dispatchers.IO) {
                                CameraPreview(applicationContext).run { message ->
                                    withContext(Dispatchers.Main) { status = message }
                                }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            status =
                                "Preview stopped: ${error.message ?: error.javaClass.simpleName}"
                        }
                    }
                    Column(Modifier.padding(24.dp)) {
                        Text("SpatialML camera preview")
                        Text(status)
                        Text("Look for the live camera panel. Close this window to stop.")
                    }
                }
            }
        }
    }
}

class MainActivity : SpatialLaunchActivity()
