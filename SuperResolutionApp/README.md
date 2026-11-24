# Super Resolution sample

[English](README.md) | [简体中文](README_zh.md) | [All samples](../README.md)

`SuperResolutionApp` uses a Real-ESRGAN model to enlarge the headset's see-through camera image.
It builds its SpatialML graph with Kotlin APIs and uses coroutines for asynchronous submission
and result readback. For the equivalent graph loaded from a pipeline package, see
[SuperResolutionPipelineApp](../SuperResolutionPipelineApp/README.md).

![Super Resolution screenshot](../.github/screenshot.jpeg)

## Build and run

Follow the repository's [environment setup](../README.md#requirements), including Git LFS assets,
JDK 21, Android SDK 35 and a physical PICO OS 6 headset. From the repository root:

```bash
./gradlew :SuperResolutionApp:assembleDebug
```

Select `SuperResolutionApp` in Android Studio and run it on the headset.
The APK is `SuperResolutionApp/build/outputs/apk/debug/SuperResolutionApp-debug.apk`;
the application ID is `com.pico.spatial.ml.sample.sr`.

## Modes and controls

Choose a mode when the app opens:

| Mode | Camera data and display | VQA |
| --- | --- | --- |
| **SpatialML** | Images stay in the isolated SpatialML service and appear in a protected spatial window. | Unavailable. |
| **RelaxMR** | Requests camera permission and returns images to the app for display and optional cloud queries. | Available when a backend is configured. |

Drag the handle to change the zoom level. Both modes run local Super Resolution inference;
an API key is needed only for the optional **Ask AI** feature in RelaxMR mode.

## Optional visual question answering

**Ask AI** sends the zoomed camera image to a VQA backend and displays its response.
The default implementation uses ByteDance Volcano Engine. Obtain an API key, then set it on the
intended headset before using the feature:

```bash
adb -s HEADSET_SERIAL shell setprop debug.spatialml.apikey YOUR_API_KEY
```

Without a key, **Ask AI** is disabled. To use another REST API, adapt
[`VQAWrapper.kt`](../common/src/main/java/com/pico/spatial/ml/sample/sr/vm/VQAWrapper.kt).
Its `ark` implementation shows how to configure image upload, authentication, request formatting
and response parsing through `VQAWrapper.create`.

## Follow the implementation

| File or directory | Purpose |
| --- | --- |
| [MainContainer.kt](src/main/java/com/pico/spatial/ml/sample/sr/view/MainContainer.kt) | Mode selection and confirmation. |
| [SuperResolutionSpatialView.kt](src/main/java/com/pico/spatial/ml/sample/sr/view/SuperResolutionSpatialView.kt) | Camera permission, zoom handle, display and VQA results. |
| [SrAlgorithmImpl.kt](src/main/java/com/pico/spatial/ml/sample/sr/vm/SrAlgorithmImpl.kt) | Kotlin graph construction, camera crops, model inference and result readback. |
| [SrViewModel.kt](src/main/java/com/pico/spatial/ml/sample/sr/vm/SrViewModel.kt) | Mode, zoom and query state. |
| [Shared assets](../shared-assets/superresolution) | Real-ESRGAN model and display/controller geometry used by both Super Resolution variants. |
