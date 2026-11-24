# Shared perception utilities

Use `implementation(project(":perception"))` for camera-to-Stage registration, capture-time head-pose lookup, or native SpatialML graph ownership. This module has no face models, Compose UI, camera-cache client, diagnostics or export code.

The Face Landmarker [sample README](../FaceLandmarkerApp/README.md) shows how these utilities support the application lifecycle and camera-to-rendering flow.

| Utility | Responsibility |
| --- | --- |
| `StageRegistration` | Pure matrix composition, rigid inverse, and point transformation |
| `StagePoseHistory` | Public HMD callback lifecycle, bounded history and capture-time interpolation |
| `SpatialMLGraphSession` | Native sessions, tensors, pipelines, bindings and readback memory |

## Coordinate contract

Matrices use meters, right-handed coordinates, row-major storage, and column vectors:

```text
serviceToStage = headToStageAtCapture * leftCameraToHead * inverse(leftCameraToService)
stagePoint = serviceToStage * servicePoint
stageObjectPose = serviceToStage * serviceObjectPose
```

The face sample obtains its optical matrix once with 128-square VST → CameraSpaceToWorld → completion. In the current Spatial service that operator supplies an extrinsic; tracked pose lookup belongs to the XR implementation. Head motion in a Spatial app comes from its Stage history. Keep these API modes distinct.

When the graph applies the same camera-to-head matrix as the app, the extrinsic and inverse cancel, leaving `headToStageAtCapture` for the already head-relative result. Keep the explicit composition to support calibrated differences. A feature-specific head correction belongs with that feature’s camera profile, before head-to-Stage; it must not become a generic offset in this module.

```kotlin
val serviceToStage = StageRegistration.serviceToStage(
    headToStageAtCapture, leftCameraToHead, leftCameraToService,
)
val pointsInStage = StageRegistration.transformVertices(serviceToStage, pointsInService)
val objectInStage = StageRegistration.multiply(serviceToStage, objectInService)
```

Use one exposure’s image/timestamp/calibration. Never substitute current head pose for an unavailable historical sample. There is no Unity Z reflection. `inverseRigid` accepts a rigid transform; a fitted face matrix containing scale requires a similarity inverse instead.

## Pose history lifecycle and timing

1. Start `StagePoseHistory` while the owning Stage is active and wait for `ready`.
2. Connect SpatialML and initialize the consuming graph’s camera calibration.
3. For each completed result, look up `poses.atCapture(captureSeconds)` using its VST exposure timestamp.
4. Withhold spatial presentation when the lookup returns null.
5. Close inference/graph and history when the Stage closes.

History timestamps the **public HMD tracking callback** with `System.nanoTime()` inside the history mutex, before UI dispatch. It retains at most 2048 samples, interpolates gaps up to 100 ms, and holds the latest sample for at most 50 ms. This approximates capture-time tracking; it does not establish hardware timestamp accuracy. No internal SDK timestamp getter or UTC conversion is used.

## Native graph ownership

Construct `SpatialMLGraphSession(width, height, assetRoot)` with the consuming feature’s camera contract and an Android asset root. It creates a disabled, zero-size SpatialML container; the app owns Stage rendering. Build resources once, submit repeatedly, and close children before the session. Keep the SpatialML connection alive until graph cleanup finishes.

`global` allocates shared float32 matrices. `globalImage` and `globalTimestamp` preserve the service ABI. Bind globals explicitly into each pipeline and chain runs with `submit(waitFor)`. `read`/`readBytes` validate byte counts and close/unmap shared memory. They do not identify a requested frame: the consumer must check completion and packet sequence before storage reuse.

The current adapter uses public platform binding signatures, compiled locally because the corresponding foundation artifact is absent from the configured Maven repository. These signature classes are **compile-only** and must never be packaged in an APK. Prefer a supported package loader when it can represent the entire required graph; do not silently omit unsupported operators.
