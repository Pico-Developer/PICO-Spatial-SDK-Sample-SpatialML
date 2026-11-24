// Copyright 2026 PICO. All rights reserved.
package com.pico.spatial.ml.sample.facelandmarker

import android.content.Context
import com.pico.spatial.ml.sample.facelandmarker.FaceModelContract.HEIGHT
import com.pico.spatial.ml.sample.facelandmarker.FaceModelContract.WIDTH
import com.pico.spatial.ml.sample.perception.SpatialMLGraphSession
import com.pico.spatial.ml.sample.perception.SpatialMLGraphSession.Companion.ASSIGNMENT
import com.pico.spatial.ml.sample.perception.SpatialMLGraphSession.Companion.VST
import com.pico.spatial.ml.sample.perception.SpatialMLGraphSession.GraphPipeline
import com.pico.spatial.ml.sample.perception.SpatialMLGraphSession.GraphTensor
import com.pico.spatial.ml.sample.perception.StageRegistration

/**
 * One supported face graph: compact detection, tracked ROI, landmarks/fit, and short publication.
 * Build once. Initialize the camera before submission. Reuse storage only after terminal
 * completion.
 */
class FaceLandmarkerGraph
private constructor(
    private val context: Context,
    backend: Int,
    private val calibration: FaceCalibrationProfile,
    private val session: SpatialMLGraphSession,
) : AutoCloseable {
    private val command = session.global(1, 4)
    private val trackState = session.global(1, 24)
    private val roi = session.global(1, 6)
    private val affine = session.global(2, 3)
    private val sampleAffine = session.global(2, 3)
    private val frame = session.global(1, 6)
    private val intrinsics = session.global(3, 3)
    private val serviceIntrinsics = session.global(3, 3)
    private val cameraToHead = session.global(4, 4)
    private val cameraInitializationComplete = session.global(1, 1)
    private var cameraInitializationRun = 0L
    private var cameraInitialized = false
    private val detectorInfo = session.global(1, 3)
    private val compactDetectorMapping =
        session.global(1, 4, CompactFaceDetector.toImage(calibration.imageTransform))
    private val image = session.globalImage()
    private val detectorImage =
        session.globalImage(CompactFaceDetector.VST_SIZE, CompactFaceDetector.VST_SIZE)
    private val scores = session.global(1, 52)
    private val raw = session.global(1, 1434)
    private val validity = session.global(1, 2)
    private val depth = session.global(1, 1)
    private val pose = session.global(4, 4)
    private val worldPose = session.global(4, 4)
    private val rawWorldPose = session.global(4, 4)
    private val poseState = session.global(1, 24)
    private val poseStatus = session.global(1, 1)
    // Capture/tracking metadata are model ABI inputs, not app-side image readbacks.
    private val captureMetadata = session.global(1, 66)
    private val trackingMetadata = session.global(1, 32)
    private val firstScale = session.global(1, 1)
    private val secondScale = session.global(1, 1)
    private val completedSequence = session.global(1, 1)
    private val poseCompletedSequence = session.global(1, 1)
    private val posePacket = session.global(1, FaceOutputs.POSE.packetSize)
    private val resultPackets =
        FaceOutputs.entries.associateWith { session.global(1, it.packetSize) }
    var submittedOutputs = FaceOutputs.POSE_MESH
        private set

    private val settings =
        floatArrayOf(
            WIDTH.toFloat(),
            HEIGHT.toFloat(),
            .25f,
            .5f,
            1f,
            .35f,
            .45f,
            .1f,
            .25f,
            0f,
            .75f,
        )
    private val acquisition: GraphPipeline
    private val tracking: GraphPipeline
    private val common: GraphPipeline
    private val outputs: Map<FaceOutputs, GraphPipeline>
    private val expressions: GraphPipeline
    private val earlyPoseOutput: GraphPipeline
    private val cameraInitialization: GraphPipeline
    var submittedEarlyPose = false
        private set

    init {
        try {
            acquisition = buildProducer(true, backend)
            tracking = buildProducer(false, backend)
            common = buildCommon(backend)
            outputs =
                resultPackets.mapValues { (selection, packet) -> buildOutput(selection, packet) }
            expressions = buildExpressions(backend)
            earlyPoseOutput = buildOutput(FaceOutputs.POSE, posePacket, poseCompletedSequence)
            cameraInitialization = buildCameraInitialization()
        } catch (error: Throwable) {
            session.close()
            throw error
        }
    }

    /** Obtain service-owned optical calibration without an app camera client or disk cache. */
    private fun buildCameraInitialization(): GraphPipeline =
        session.pipeline(context).apply {
            val timestamp = timestamp()
            namedOp(VST, emptyMap(), mapOf("left image" to image(128), "timestamp" to timestamp))
            namedOp(
                SERVICE_CAMERA_EXTRINSIC,
                mapOf("timestamp" to timestamp),
                mapOf("left" to cameraToHead),
            )
            op(
                ASSIGNMENT,
                listOf(floats(1, 1, floatArrayOf(1f))),
                listOf(cameraInitializationComplete),
            )
        }

    fun submitCameraInitialization() {
        check(cameraInitializationRun == 0L && !cameraInitialized) {
            "Camera initialization may only be submitted once before tracking"
        }
        cameraInitializationRun = cameraInitialization.submit()
    }

    fun cameraInitializationComplete(): Boolean {
        check(cameraInitializationRun != 0L) { "Camera initialization has not been submitted" }
        return session.read(cameraInitializationComplete, 1)[0] == 1f
    }

    /** Read only after the initialization writer finishes; never permit a zero/invalid matrix. */
    fun readInitializedCameraToHead(): FloatArray {
        check(cameraInitializationComplete()) { "Camera initialization is incomplete" }
        val matrix = session.read(cameraToHead, 16)
        require(matrix.all(Float::isFinite)) { "Camera initialization returned non-finite values" }
        require(
            (12..14).all { kotlin.math.abs(matrix[it]) < .000001f } &&
                kotlin.math.abs(matrix[15] - 1f) < .000001f
        ) {
            "Camera initialization did not return a homogeneous transform"
        }
        StageRegistration.inverseRigid(matrix)
        cameraInitialized = true
        return matrix
    }

    /** Adjacent compact and full captures preserve the validated detector-to-image mapping. */
    private fun buildProducer(acquire: Boolean, backend: Int): GraphPipeline =
        session.pipeline(context).apply {
            val timestamp = timestamp()
            val config = floats(1, settings.size, settings)
            val controlTransform = floats(3, 3, calibration.imageTransform)
            val controlTrial =
                if (calibration == FaceCalibrationProfile.SERVICE) serviceIntrinsics
                else floats(3, 3, calibration.sourceIntrinsics)
            val controlMode = floats(1, 1, floatArrayOf(calibration.modelMode))
            if (acquire)
                namedOp(
                    VST,
                    emptyMap(),
                    mapOf(
                        "left image" to detectorImage,
                        "timestamp" to timestamp(),
                        "camera matrix" to floats(3, 3),
                    ),
                )
            namedOp(
                VST,
                emptyMap(),
                mapOf(
                    "left image" to image,
                    "timestamp" to timestamp,
                    "camera matrix" to serviceIntrinsics,
                ),
            )
            var fine: GraphTensor? = null
            var coarse: GraphTensor? = null
            var fineScore: GraphTensor? = null
            var coarseScore: GraphTensor? = null
            if (acquire) {
                val input = compactDetectorInput(detectorImage)
                fine = floats(512, 16)
                coarse = floats(384, 16)
                fineScore = floats(512, 1)
                coarseScore = floats(384, 1)
                model(
                    "face_detector",
                    mapOf("image" to input),
                    mapOf(
                        "box_coords_1" to fine,
                        "box_coords_2" to coarse,
                        "box_scores_1" to fineScore,
                        "box_scores_2" to coarseScore,
                    ),
                    backend,
                )
            }
            val inputs =
                linkedMapOf<String, GraphTensor>().apply {
                    if (acquire) {
                        put("boxes_fine", checkNotNull(fine))
                        put("boxes_coarse", checkNotNull(coarse))
                        put("scores_fine", checkNotNull(fineScore))
                        put("scores_coarse", checkNotNull(coarseScore))
                    }
                    put("settings", config)
                    put("timestamp", timestamp)
                    put("command", command)
                    put("track_state", trackState)
                    put("service_intrinsics", serviceIntrinsics)
                    put("image_transform", controlTransform)
                    put("calibrated_intrinsics", controlTrial)
                    put("camera_profile", controlMode)
                    put("left_camera_to_xr", cameraToHead)
                    if (acquire)
                        put(
                            "detector_to_source",
                            // This model input name denotes the working image space.
                            // Landmark sampling composes its crop with imageTransform, so
                            // detector coordinates must already be in that calibrated space.
                            compactDetectorMapping,
                        )
                }
            val outputs =
                linkedMapOf(
                    "intrinsics" to intrinsics,
                    "roi" to roi,
                    "affine" to affine,
                    "sample_affine" to sampleAffine,
                    "frame" to frame,
                    "next_track_state" to trackState,
                )
            if (acquire) outputs["detector_info"] = detectorInfo
            outputs["capture_metadata"] = captureMetadata
            model(
                if (acquire) "face_detection_roi_update" else "face_tracking_roi_update",
                inputs,
                outputs,
                0,
            )
        }

    private fun buildCommon(backend: Int): GraphPipeline =
        session.pipeline(context).apply {
            val config = floats(1, settings.size, settings)
            val presence = floats(1, 1)
            val input = preprocess(image, sampleAffine)
            model(
                "face_landmark_estimation",
                mapOf("input_12" to input),
                mapOf("Identity" to raw, "Identity_1" to presence, "Identity_2" to floats(1, 1)),
                backend,
            )
            val inputs =
                linkedMapOf(
                    "raw_landmarks" to raw,
                    "presence_logit" to presence,
                    "roi" to roi,
                    "affine" to affine,
                    "settings" to config,
                    "frame" to frame,
                    "intrinsics" to intrinsics,
                    "track_state" to trackState,
                    "left_camera_to_xr" to cameraToHead,
                    "command" to command,
                    "pose_state" to poseState,
                    "capture_metadata" to captureMetadata,
                )
            val outputs =
                linkedMapOf(
                    "next_track_state" to trackState,
                    "validity" to validity,
                    "depth_offset" to depth,
                    "first_scale" to firstScale,
                    "second_scale" to secondScale,
                    "pose" to pose,
                    "next_pose_state" to poseState,
                    "world_pose" to worldPose,
                    "raw_world_pose" to rawWorldPose,
                    "pose_status" to poseStatus,
                    "tracking_metadata" to trackingMetadata,
                )
            model("face_landmark_pose_fit", inputs, outputs, 0)
        }

    private fun buildExpressions(backend: Int): GraphPipeline =
        session.pipeline(context).apply {
            val input = floats(1, 292)
            model(
                "face_blendshape_input_preparation",
                mapOf("raw_landmarks" to raw, "roi" to roi),
                mapOf("blend_input" to input),
                0,
            )
            model(
                "face_blendshapes",
                mapOf("input_points" to input),
                mapOf("tf.__operators__.getitem_3" to scores),
                backend,
            )
        }

    private fun buildOutput(
        outputs: FaceOutputs,
        packet: GraphTensor,
        completion: GraphTensor = completedSequence,
    ): GraphPipeline =
        session.pipeline(context).apply {
            val inputs =
                linkedMapOf(
                    "command" to command,
                    "validity" to validity,
                    "roi" to roi,
                    "tracking_metadata" to trackingMetadata,
                    "camera" to cameraToHead,
                )
            inputs["world_pose"] = rawWorldPose
            if (outputs.mesh) {
                inputs["raw_landmarks"] = raw
                inputs["fit_pose"] = pose
                inputs["first_scale"] = firstScale
                inputs["second_scale"] = secondScale
                inputs["depth"] = depth
            }
            if (outputs.blendshapes) inputs["blendshapes"] = scores
            // One compiled FP32 concatenation, no JS array marshaling or accelerator round trip.
            // CPU preserves sequence/timestamp bits; reduced-precision GPU publication is unsafe.
            val packingModel =
                when (outputs) {
                    FaceOutputs.POSE -> "face_pose_pack"
                    FaceOutputs.POSE_MESH -> "face_pose_mesh_pack"
                    FaceOutputs.ALL -> "face_pose_mesh_blendshapes_pack"
                }
            model(packingModel, inputs, mapOf("result" to packet), 0)
            completionMarker(completion)
        }

    private fun GraphPipeline.completionMarker(sequence: GraphTensor) {
        model(
            "face_completion_marker",
            mapOf("command" to command),
            mapOf("sequence" to sequence),
            0,
        )
    }

    /** Early pose is a snapshot only; the next frame must await [resultComplete]. */
    fun submit(
        sequence: Int,
        seconds: Float,
        acquire: Boolean,
        selection: FaceOutputs = FaceOutputs.POSE_MESH,
    ) {
        submittedOutputs = selection
        check(cameraInitialized) { "Initialize the camera matrix before submitting a face frame" }
        session.reset(
            command,
            floatArrayOf(sequence.toFloat(), seconds, 1f, if (acquire) 1f else 0f),
        )
        var run = common.submit((if (acquire) acquisition else tracking).submit())
        submittedEarlyPose = !acquire && selection != FaceOutputs.POSE
        if (submittedEarlyPose) run = earlyPoseOutput.submit(run)
        if (selection.blendshapes) run = expressions.submit(run)
        outputs.getValue(selection).submit(run)
    }

    fun resultComplete(sequence: Int) = session.read(completedSequence, 1)[0] == sequence.toFloat()

    fun poseComplete(sequence: Int) =
        session.read(poseCompletedSequence, 1)[0] == sequence.toFloat()

    fun readResult() =
        session.read(resultPackets.getValue(submittedOutputs), submittedOutputs.packetSize)

    fun readPoseResult(): FloatArray {
        check(submittedEarlyPose)
        return session.read(posePacket, FaceOutputs.POSE.packetSize)
    }

    override fun close() = session.close()

    companion object {
        private const val SERVICE_CAMERA_EXTRINSIC = 22

        fun create(
            context: Context,
            backend: Int,
            calibration: FaceCalibrationProfile,
        ): FaceLandmarkerGraph {
            val session = SpatialMLGraphSession(WIDTH, HEIGHT, FaceModelContract.ASSETS)
            return FaceLandmarkerGraph(context, backend, calibration, session)
        }
    }
}
