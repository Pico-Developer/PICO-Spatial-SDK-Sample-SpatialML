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
package com.pico.spatial.ml.sample.sr.vm

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.ByteBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The viewmodel to store data that should be shared among GUI components and preserved among
 * re-composition. SR stands for Super Resolution.
 */
class SrViewModel : ViewModel() {
    /** The app's running mode. */
    enum class AppMode {
        /**
         * SecureMR mode is selected, but not confirmed. SecureMR mode means the app cannot get the
         * outputs from SpatialML framework. SecureMR mode being selected means we need to pop up a
         * window to asks the user to confirm their selection.
         */
        SECURE_MR,

        /**
         * SecureMR mode is selected and confirmed. The super-resolution algorithm can be started.
         */
        SECURE_MR_CONFIRMED,

        /**
         * RelaxMR mode is selected, but not confirmed. RelaxMR mode means the app *can* get the
         * outputs from SpatialML framework. RelaxMR mode being selected means we need to pop up a
         * window to asks the user to confirm their selection and to grant camera permission.
         */
        RELAX_MR,

        /**
         * RelaxMR mode is selected and confirmed. The super-resolution algorithm can be started,
         * and the app will use the super-resolution algorithm's output tensor to setup textures for
         * materials used in [com.pico.spatial.ml.sample.sr.view.SuperResolutionSpatialView].
         */
        RELAX_MR_CONFIRMED,

        /**
         * No selection has been made, or a selection was made, but the user cancel the confirmation
         * window (when we will fallback to the beginning window to ask the user to select again).
         */
        NO_SELECTION,
    }

    /** The status of the backend query for images returned from super-resolution algorithm. */
    enum class VQAStatus {
        /** No query has been made. */
        NO_ACTION,

        /**
         * Image has been submitted for a query, but no new answer is obtained.
         * [SrViewModel.latestMessage] will store the previous query result.
         */
        IMAGE_SUBMITTED,

        /** Answer has been received. The answer will be stored in [SrViewModel.latestMessage]. */
        ANSWER_OBTAINED,
    }

    private val _queryStatus = MutableStateFlow(VQAStatus.NO_ACTION)

    /** The query status of VQA. Only valid in RelaxMR mode. */
    val queryStatus: StateFlow<VQAStatus> = _queryStatus

    /** The latest VQA answer message obtained from the search. Only valid in RelaxMR mode. */
    var latestMessage: String = ""

    val latestBitmap: Bitmap =
        createBitmap(SrAlgorithmImpl.ZOOMED_IMG_SIZE, SrAlgorithmImpl.ZOOMED_IMG_SIZE)

    /**
     * Whether the super-resolution algorithm has been initialized and deployed to SpatialML
     * framework.
     */
    var initialized: Boolean = false

    var textureHasReset: Boolean = false
        private set

    private val _appMode = MutableStateFlow(AppMode.NO_SELECTION)

    /** The app mode, i.e., RelaxMR v.s. SecureMR. */
    val appMode: StateFlow<AppMode> = _appMode

    private lateinit var superResolution: SrAlgorithmImpl

    private val vqa = VQAWrapper.Default.ark

    val vqaReady: Boolean
        get() {
            return vqa.ableToQuery()
        }

    /**
     * Initialize the super-resolution algorithm.
     *
     * @param appContext the application context
     * @param initRatio the upscaling ratio at the beginning.
     */
    fun init(appContext: Context, initRatio: Float) {
        if (
            appMode.value != AppMode.SECURE_MR_CONFIRMED &&
                appMode.value != AppMode.RELAX_MR_CONFIRMED
        ) {
            Log.e("SpatialML", "App mode is not confirmed. Got: ${appMode.value}")
            return
        }
        if (initialized) {
            Log.i(
                "SpatialML",
                "super resolution re-init: skip the creation; reuse the instance but reset the ratio",
            )
            superResolution.setUpscaleFactor(initRatio)
        } else {
            superResolution =
                SrAlgorithmImpl(
                    appContext,
                    viewModelScope,
                    useSecureMr = appMode.value == AppMode.SECURE_MR_CONFIRMED,
                )
            val affineTask = superResolution.setUpscaleFactor(initRatio, superResolution.initTask)
            superResolution.mainPipeline.runContinuously(10, affineTask)
            initialized = true
        }
    }

    /** Close the augment window showing the VQA result text. */
    fun closeVQAResult() {
        _queryStatus.value = VQAStatus.NO_ACTION
    }

    /**
     * Change the app mode.
     *
     * @param newMode the new app mode.
     */
    fun updateAppMode(newMode: AppMode) {
        _appMode.value = newMode
    }

    /**
     * reset the upscale ratio.
     *
     * @param ratio the new ratio, which is expected to be among 1.0~16.0.
     */
    fun resetUpscaleRatio(ratio: Float) {
        if (initialized) {
            superResolution.setUpscaleFactor(ratio)
        } else {
            Log.e(
                "SpatialML",
                "super resolution instance is not initialized. Cannot reset the ratio",
            )
        }
    }

    /**
     * In RelaxMR mode, use the super-resolution algorithm's output as a texture for a material's
     * base color.
     *
     * @param material the target material whose base color will be replaced by the super-resolution
     *   output.
     */
    fun useZoomedImageAsBaseColor(material: UnlitMaterial) =
        viewModelScope.launch {
            if (initialized) {
                material.setBaseColorTexture(superResolution.imageReadbackAsTexture().await())
                textureHasReset = true
            } else {
                Log.e(
                    "SpatialML",
                    "Cannot readback as texture, because the super resolution instance is not initialized",
                )
            }
        }

    /**
     * Launch VQA, searching the Internet for the current super-resolution algorithm output image.
     */
    fun askQuestionAboutImage() =
        viewModelScope.launch {
            if (initialized) {
                // copy from contentDeferred.buffer to argbBuffer
                // because (1) the content.buffer is RGB, but Kotlin requires ImageBitMap to be of
                // ARGB.
                // and (2) the contentDeferred.buffer resides in a SharedMemory, which must be
                // closed
                // timely to avoid memory leak, so we copy it out from SharedMemory to a ByteBuffer
                // on normal memory.
                val argbBuffer =
                    ByteBuffer.allocate(
                        SrAlgorithmImpl.ZOOMED_IMG_SIZE * SrAlgorithmImpl.ZOOMED_IMG_SIZE * 4
                    )
                superResolution.imageReadBack().await().use { closableContent ->
                    closableContent.buffer.rewind()
                    while (closableContent.buffer.hasRemaining()) {
                        // NOTE: An ARGB Bitmap is actually RGBA in memory
                        // This is a known issue in Android SDK
                        argbBuffer.put(closableContent.buffer.get())
                        argbBuffer.put(closableContent.buffer.get())
                        argbBuffer.put(closableContent.buffer.get())
                        argbBuffer.put(0xFF.toByte()) // A
                    }
                }
                argbBuffer.rewind()
                latestBitmap.copyPixelsFromBuffer(argbBuffer)
                _queryStatus.value = VQAStatus.IMAGE_SUBMITTED
                try {
                        latestMessage = vqa.queryImage(latestBitmap)
                        null
                    } catch (e: VQAException) {
                        e.message?.let { latestMessage = it }
                        e
                    } catch (e: FileNotFoundException) {
                        latestMessage =
                            "Network failure: authentication failed or query is too" +
                                " frequency/too much. Consider try again laster, or change API key."
                        e
                    } catch (e: IOException) {
                        latestMessage = "Network failure: Internet not connected."
                        e
                    }
                    ?.let { exception ->
                        val trace =
                            exception.stackTrace.joinToString {
                                "\n\tat ${it.fileName}:${it.lineNumber} ${it.className}.${it.methodName}"
                            }
                        Log.e("SpatialML", "${exception.message}$trace")
                    }
                _queryStatus.value = VQAStatus.ANSWER_OBTAINED
            } else {
                Log.e(
                    "SpatialML",
                    "Cannot readback, because the super resolution instance is not initialized",
                )
            }
        }
}
