/*
 * Copyright 2025 - 2026 PICO. All rights reserved.
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

package com.pico.spatial.sample.features.spatialml

import android.util.Log
import com.pico.spatial.ml.securemr.Pipeline
import com.pico.spatial.ml.securemr.PipelinePackageBundle
import com.pico.spatial.ml.securemr.SpatialMLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Submits a named JSON pipeline with the placeholder bindings supplied by the package loader. */
class LoadedPipelineRunner(
    private val scope: CoroutineScope,
    private val bundle: Deferred<PipelinePackageBundle>,
    private val pipelineId: String,
) {
    private val updates = Mutex()

    fun runOnceAfterValueReset(
        prevTask: Deferred<Pipeline.RunTask>? = null,
        valueResetter: (PipelinePackageBundle) -> Unit = {},
    ): Deferred<Pipeline.RunTask> =
        scope.async(Dispatchers.IO) {
            val packageBundle = bundle.await()
            val loaded = packageBundle.pipelines.getValue(pipelineId)
            val waitFor = prevTask?.await()
            updates.withLock {
                valueResetter(packageBundle)
                loaded.pipeline.submit(loaded.submitBindings, null, waitFor)
            }
        }

    fun runContinuously(frequency: Int, startAfter: Deferred<Pipeline.RunTask>? = null) =
        scope.launch(Dispatchers.IO) {
            require(frequency in 1..1000)
            val loaded = bundle.await().pipelines.getValue(pipelineId)
            var waitFor = startAfter?.await()
            var failureAllowance = MAX_SUCCESSIVE_SUBMISSION_FAILURES
            while (isActive && failureAllowance > 0) {
                try {
                    loaded.pipeline.submit(loaded.submitBindings, null, waitFor)
                    failureAllowance = MAX_SUCCESSIVE_SUBMISSION_FAILURES
                } catch (exception: SpatialMLException) {
                    failureAllowance -= 1
                    Log.w(
                        TAG,
                        "Pipeline '$pipelineId' submission failed " +
                            "(${MAX_SUCCESSIVE_SUBMISSION_FAILURES - failureAllowance}/" +
                            "$MAX_SUCCESSIVE_SUBMISSION_FAILURES)",
                        exception,
                    )
                }
                waitFor = null
                delay(1000L / frequency)
            }
        }

    private companion object {
        const val TAG = "SpatialML"
        const val MAX_SUCCESSIVE_SUBMISSION_FAILURES = 5
    }
}
