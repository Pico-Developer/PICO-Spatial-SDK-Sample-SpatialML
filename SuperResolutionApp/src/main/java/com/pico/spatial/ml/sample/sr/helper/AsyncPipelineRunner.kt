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
package com.pico.spatial.ml.sample.sr.helper

import android.util.Log
import com.pico.spatial.ml.securemr.GlobalTensor
import com.pico.spatial.ml.securemr.Pipeline
import com.pico.spatial.ml.securemr.PipelineTensorPlaceholder
import com.pico.spatial.ml.securemr.SpatialMLException
import com.pico.spatial.ml.securemr.SpatialMLSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Allow caller to run SpatialML pipeline in an async manner.
 *
 * @param scope the co-routine scope in which the pipeline will be async-ly run.
 * @param session the deferred SpatialML session object.
 * @param pipelineCreator the lambda to set up the pipeline. The first parameter is the pipeline to
 *   be set up, the second is the local-placeholder-to-global-tensor map that will be used when
 *   [Pipeline.submit] is called to submit this pipeline.
 */
class AsyncPipelineRunner(
    val scope: CoroutineScope,
    session: Deferred<SpatialMLSession>,
    pipelineCreator: (Pipeline, MutableMap<PipelineTensorPlaceholder, GlobalTensor>) -> Unit,
) {
    private val placeholderMap: MutableMap<PipelineTensorPlaceholder, GlobalTensor> = mutableMapOf()

    private val pipelineDeferred =
        scope.async {
            val pipeline = session.await().newPipeline()
            pipelineCreator(pipeline, placeholderMap)
            pipeline
        }

    /**
     * Run this pipeline continuously in a co-routine. The co-routine job will be returned.
     *
     * @param frequency the frequency to run the pipeline. Unit is Hz.
     * @param startAfter run the pipeline after which pipeline task.
     * @param tensorMapUpdate an optional callback to update the placeholder-to-global-tensor map
     *   that has been set up by the `pipelineCreator` in constructor. Recall that this map will be
     *   used as the parameters for [Pipeline.submit]. By updating the tensor map here, you can
     *   reuse the same pipeline to handle different global tensors, as the same placeholder can
     *   refer to different global tensors at run time. The callback will be called before every
     *   [Pipeline.submit] is made.
     * @return the co-routine [kotlinx.coroutines.Job]
     */
    fun runContinuously(
        frequency: Int,
        startAfter: Deferred<Pipeline.RunTask>? = null,
        tensorMapUpdate: ((MutableMap<PipelineTensorPlaceholder, GlobalTensor>) -> Unit)? = null,
    ) =
        scope.launch {
            val intervalInMs = (1000 / frequency).toLong()
            val pipeline = pipelineDeferred.await()
            var task: Pipeline.RunTask? = startAfter?.await()
            Log.i("SpatialML", "pipeline submitted at $intervalInMs")
            var failureAllowance = 5
            while (failureAllowance > 0) {
                // until cancellation or 5 successive task submission failures
                try {
                    if (tensorMapUpdate != null) {
                        tensorMapUpdate(placeholderMap)
                    }
                    pipeline.submit(placeholderMap, null, task)
                    failureAllowance = 5 // reset allowance
                } catch (e: SpatialMLException) {
                    failureAllowance -= 1
                    Log.w(
                        "SpatialML",
                        "pipeline submit fail due to exception: $e (${5 - failureAllowance}/5)",
                    )
                }
                task = null
                delay(intervalInMs)
            }
        }

    /**
     * Run this pipeline once. You can use this method to ensure the pipeline is run after tensors'
     * values having been reset.
     *
     * @param prevTask the task that need to be wait before this pipeline is executed.
     * @param valueReSetter the lambda which re-set the value of tensors that may be used by the
     *   pipeline before it is run.
     * @return A [Deferred] [Pipeline.RunTask], that you used by other [runContinuously] or
     *   [runOnceAfterValueReset] for sequential execution.
     */
    fun runOnceAfterValueReset(
        prevTask: Deferred<Pipeline.RunTask>? = null,
        tensorMapUpdate: ((MutableMap<PipelineTensorPlaceholder, GlobalTensor>) -> Unit)? = null,
        valueReSetter: (Pipeline) -> Unit,
    ) =
        scope.async {
            Log.i(
                "SpatialML",
                "about to run the pipeline once, waiting for the pipeline creation to finish",
            )
            val pipeline = pipelineDeferred.await()
            Log.i("SpatialML", "pipeline creation finish, ready to reset the values")
            try {
                if (tensorMapUpdate != null) {
                    tensorMapUpdate(placeholderMap)
                }
                valueReSetter(pipeline)
            } catch (e: SpatialMLException) {
                this.cancel("cancel due to SpatialML exception", e)
            }
            Log.i("SpatialML", "value reset done, ready to submit")
            pipeline.submit(placeholderMap, null, prevTask?.await())
        }
}
