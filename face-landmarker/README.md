# Reusable face pipeline and tracking

This Android library contains the developer face tracking implementation: one graph, compact pose/mesh/expression decoding, capture-time registration, pose/shape filtering, measured-result retention, and small mesh helpers. It has no diagnostic, recording, calibration-editing or effect UI APIs.

The [sample README](../FaceLandmarkerApp/README.md) explains setup, controls and the complete camera-to-rendering flow.

Source folders retain the `com.pico.spatial.ml.sample.facelandmarker` Kotlin package.

| Folder | Responsibility | Start here |
| --- | --- | --- |
| `pipeline` | Model/camera contracts, preprocessing and ordered graph execution | `FaceLandmarkerGraph`, `FaceImagePreprocessing` |
| `tracking` | Decode/register/filter frames and retain measured results | `FaceLandmarkerRunner`, `FaceFrame`, `FaceOutputs`, `FaceTrackingState` |
| `rendering` | Mesh preparation, topology, depth material and draw ordering | `FaceMeshPreparation`, `FaceOcclusionMaterial` |

## Reuse the pipeline

Add `implementation(project(":face-landmarker"))`. Start a `StagePoseHistory` while the Stage is active, create a `FaceLandmarkerRunner(context, backend = 2, poses = poses, calibrationProfile = FaceCalibrationProfile.forRom(Build.FINGERPRINT))`, and observe `frame` and `status`. Start only after permission and scene readiness. Close both owners with the Stage. The [sample controller](../FaceLandmarkerApp/src/main/java/com/pico/spatial/ml/sample/facelandmarker/sample/FaceMeshController.kt) shows the complete lifecycle.

Published spatial data is already in Stage coordinates. Pose-only frames refresh placement; terminal pose-plus-mesh frames also refresh the surface. `FaceTrackingState` retains a good measurement for at most 350 ms and never predicts motion. A custom renderer must handle absent, stale and invalid measurements.

Call `runner.setOutputs(FaceOutputs.ALL)` for lion dance animation or expression-driven eyes, or `FaceOutputs.POSE_MESH` for other effects. Selection is captured per submission; changing it suppresses mismatched in-flight publication. The public app owns the effect selector and renderers.

A direct graph consumer must create the SpatialML connection first, call `submitCameraInitialization()`, wait for `cameraInitializationComplete()`, and read/validate `readInitializedCameraToHead()` before submitting face frames. The graph has terminal outputs for masks 1, 3 and 7, and a separate early pose output while tracking. Keep one frame in flight through terminal completion.

`FaceCalibrationProfile` pairs image mapping, source intrinsics and head correction. `SWAN_STEREO` is selected only for the exact tested ROM; other ROMs use `SERVICE` and require physical validation. This is runtime calibration needed for correct placement, not a calibration experiment UI.

## Default pipelines

The following is the supported graph with either camera profile. Backend 2 is NPU, 1 is GPU, and 0 is CPU. Weightless control/packing models use CPU to preserve their integer/float contracts.

| Pipeline | Operators in order | When used |
| --- | --- | --- |
| `camera_initialization` | RectifiedVSTAccess (128 square), CameraSpaceToWorld, Assignment | Once before tracking |
| `detector` | RectifiedVSTAccess (compact view), RectifiedVSTAccess (full RGB), face_detector_image_preprocessing (GPU), face_detector (NPU), face_detection_roi_update (CPU) | Acquisition/reacquisition |
| `tracking` | RectifiedVSTAccess (full RGB), face_tracking_roi_update (CPU) | Established ROI |
| `landmark_fit` | face_landmark_image_preprocessing (GPU), face_landmark_estimation (NPU), face_landmark_pose_fit (CPU) | Every submitted face frame |
| `expressions` | face_blendshape_input_preparation (CPU), face_blendshapes (NPU) | Expression effects only |
| `output_1`, `output_3`, `output_7` | Selected face packet-packing model, face_completion_marker (CPU) | Terminal pose, pose/mesh or pose/mesh/blendshapes |
| `early_pose_output` | face_pose_pack, face_completion_marker (CPU) | Optional early pose; separate storage |

A normal frame runs **detector OR tracking → landmark_fit → optional expressions → selected terminal output**, with early pose publication on tracked frames before the optional work. Early completion never authorizes a new capture. Graph construction order is not submission order: camera initialization is built last but submitted first. Default ordinary pipelines contain no JavaScript operators.

## Tensor and coordinate contracts

| Boundary | Contract |
| --- | --- |
| Camera | 3248 × 2464 full RGB source on the tested ROM; preserve its capture timestamp |
| Compact detector | 336-square service output → logical 336 × 256 → center 256-square; retain inverse mapping to full image |
| Neural input | RGB float32 `[1,256,256,3]`, range `[0,1]`; inverse affine crop, normalize by 255 |
| Detector output | 896 anchors across two heads |
| Landmarks | 478 XYZ landmarks, presence and auxiliary scalar |
| Pose packet | Mask 1: 78 floats / 312 bytes |
| Pose and mesh packet | Mask 3: 1531 floats / 6124 bytes |
| Pose, mesh and blendshapes | Mask 7: 1583 floats / 6332 bytes |
| Rendering | Meters; right-handed Stage coordinates; row-major matrices multiplying column vectors |

`FaceOutputPacket` validates the selected ABI and produces a typed `FaceFrame` with pose, vertices, tracking identity and capture metadata. Check both completion marker and requested packet sequence: readback can return a previous completed snapshot. Timeout aborts the run and never permits unsafe tensor reuse.

The service awaits every executing pipeline that writes a tensor being read. Publication pipelines therefore read completed work globals and write separate readback tensors. An alias to work storage would not isolate ownership. The early-pose packet/marker and terminal packet/marker must also stay distinct.

`CameraSpaceToWorld` in the current Spatial service supplies the camera extrinsic; the XR build’s tracked-pose behavior does not apply. Initialize `cameraToHead` once through the service and use that same matrix for app registration. Per-frame detection/tracking do not write it. Stage motion comes from historical public HMD callbacks, queried with the VST exposure timestamp. See [perception’s transform contract](../perception/README.md#coordinate-contract).

`FaceFrame.vertices` are Stage-space points. The retained renderer first computes local vertices with the inverse fitted similarity transform, then parents that local mesh under the Stage face pose. Applying the pose again to Stage vertices would double-transform them. Upload shape independently of pose; reject late uploads from an expired tracking generation.

## Runtime models

All thirteen models are used. Three perform learned inference; the others implement preprocessing, tracking/fit arithmetic and result packing as TFLite operations. Their filenames describe the operation rather than the output-mask number.

| Model (`.tflite`) | Role / when used |
| --- | --- |
| `face_detector_image_preprocessing` | Restore and crop the compact RGB image for detection; GPU. |
| `face_detector` | Detect a face when acquiring or reacquiring; NPU. |
| `face_detection_roi_update` | Decode detections and update the tracking ROI and capture metadata; CPU. |
| `face_tracking_roi_update` | Refresh an established ROI with the new capture metadata; CPU. |
| `face_landmark_image_preprocessing` | Crop and normalize full RGB for landmark inference; GPU. |
| `face_landmark_estimation` | Estimate 478 landmarks and face presence; NPU. |
| `face_landmark_pose_fit` | Fit the face pose and update geometry/tracking state; CPU. |
| `face_blendshape_input_preparation` | Prepare landmark inputs for expression estimation; CPU, expression effects only. |
| `face_blendshapes` | Estimate 52 expression coefficients; NPU, expression effects only. |
| `face_pose_pack` | Pack pose-only results, including early pose publication; CPU. |
| `face_pose_mesh_pack` | Pack pose and mesh results; CPU, ordinary effects. |
| `face_pose_mesh_blendshapes_pack` | Pack pose, mesh and expression results; CPU, expression effects. |
| `face_completion_marker` | Publish the completed submission sequence after each result packet; CPU. |

Backend choices above are the sample defaults. Model bytes and tensor ports are preserved when files are renamed; upstream source filenames remain recorded in provenance.

## Assets and build

The public build has no experiment flags. Static assets under [pipeline/SpatialML/face-landmarker](pipeline/SpatialML/face-landmarker) are packaged directly. Only the thirteen TFLite files referenced by the graph are included, together with canonical geometry, topology, licenses and provenance. The depth material lives in `src/main/assets/occlusion/face-occlusion.bundle`.

The three trained detector/landmark/blendshape models are unchanged. Six weightless control/packing models have descriptive tensor-port names; their operators, buffers and arithmetic are unchanged. [runtime-models.json](pipeline/SpatialML/face-landmarker/runtime-models.json) records exact hashes, reference hashes and port renames. The model’s 32-float tracking metadata supplies timestamps, intrinsics and validity to the renderer; it is not a diagnostic export. The public APK also includes two app-owned helmet GLBs, the articulated lion GLB and their notices.

## Platform boundary

The current SDK package loader cannot represent every required operator, including camera initialization. The existing `SpatialMLGraphSession` adapter in [perception](../perception/README.md) manages native handles through compile-only public platform signatures. Those signatures must not be packaged in an APK. This adapter does not use the SDK’s internal head-pose timestamp ABI.

This is a reusable Kotlin pipeline module, not a Pipeline Zoo archive. A future declarative migration needs loader support, conditional submissions, capture identity, completion ownership and headset parity. The service still maps image inputs through CPU-accessible storage; this refactor does not add GPU zero-copy.

Use the [sample guide](../FaceLandmarkerApp/README.md) for rendering, build instructions and validation, and the [perception guide](../perception/README.md) for shared registration and native resource ownership.
