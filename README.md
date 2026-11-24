# PICO SpatialML Samples

[English](README.md) | [简体中文](README_zh.md)

Nine independent Kotlin applications demonstrate SpatialML with the PICO Spatial SDK on PICO OS 6.
They cover camera input, local model inference, hardware acceleration, asynchronous result readback,
and rendering in protected spatial scenes. Some samples build graphs with Kotlin APIs; others load
declarative pipeline packages. All use Spatial SDK **6.2.8**.

## Samples

Start with [SpatialMLHelloApp](SpatialMLHelloApp/README.md) to check service connection and camera
display, then choose a sample for the feature you want to explore.

| Application | What it demonstrates |
| --- | --- |
| [SpatialMLHelloApp](SpatialMLHelloApp/README.md) | Live camera display in a protected panel, without a model. |
| [SuperResolutionApp](SuperResolutionApp/README.md) | Camera zoom with Real-ESRGAN, a Kotlin-built graph, protected display or camera readback, and optional visual question answering. |
| [SuperResolutionPipelineApp](SuperResolutionPipelineApp/README.md) | The same Super Resolution experience using a pipeline package. |
| [StylizedApp](StylizedApp) | Camera style prediction and transfer with a Kotlin-built graph. |
| [StylizedPipelineApp](StylizedPipelineApp) | Camera style prediction and transfer using a pipeline package. |
| [FaceMediaPipePipelineApp](FaceMediaPipePipelineApp) | MediaPipe face detection and detection-frame display using pipeline packages. |
| [FaceLandmarkerApp](FaceLandmarkerApp/README.md) | 478 face landmarks, pose and optional expressions, with a face mesh and eight effects over passthrough in Full Space. |
| [PosePipelineApp](PosePipelineApp) | Pose inference from a pipeline package, rendered as joints and limbs in a Stage. |
| [MnistWildPipelineApp](MnistWildPipelineApp) | Handwritten digit recognition from camera crops, with crop, class and confidence displays. |

Each application has its own package, assets, APK and Android Studio run target. Model assets are
included; the optional Super Resolution VQA feature has its own [backend setup](SuperResolutionApp/README.md#optional-visual-question-answering).

## Requirements

- Android Studio, JDK 21 and Android SDK 35.
- Git LFS to download model and artwork assets.
- A physical PICO headset with compatible PICO OS 6 SpatialML services and USB debugging enabled.
  Camera inference and headset alignment require device testing.

Configure the development environment using the official guide:

- [PICO Spatial SDK Setup Guide (Outside Chinese Mainland)](https://developer.picoxr.com/document/spatial-sdk/set-up-development-environment/)
- [PICO Spatial SDK Setup Guide (Chinese Mainland)](https://developer-cn.picoxr.com/document/spatial-sdk/set-up-development-environment/)

The repository pins SDK and toolchain versions in [gradle/libs.versions.toml](gradle/libs.versions.toml)
and uses the included Gradle wrapper.

## Getting started

1. Clone the repository and download its Git LFS assets:

   ```bash
   git clone https://github.com/Pico-Developer/PICO-Spatial-SDK-Sample-SpatialML.git
   cd PICO-Spatial-SDK-Sample-SpatialML
   git lfs pull
   ```

2. Open the repository root in Android Studio and sync Gradle.
3. Connect the headset, choose an application module from the run-target selector, and run it.
   Follow that sample's guide for permissions and controls.

To build one sample from the repository root:

```bash
./gradlew :SpatialMLHelloApp:assembleDebug
```

Replace `SpatialMLHelloApp` with any application module from the table. Its debug APK is written to
`<module>/build/outputs/apk/debug/`. Build all nine applications with `./gradlew assembleDebug`.
Use the selected application's logcat tag and `Secure MR::Server` to inspect runtime failures.

## Shared modules and assets

| Directory | Purpose |
| --- | --- |
| [face-landmarker](face-landmarker/README.md) | Face graph, models, result decoding, tracking and mesh helpers used by FaceLandmarkerApp. |
| [perception](perception/README.md) | Capture-time transforms, Stage pose history and native graph resource ownership. |
| [common](common) | Shared helpers for the camera and pipeline samples, including VQA integration. |
| [shared-assets](shared-assets) | Models and artwork shared by the Super Resolution and Stylized sample pairs. |
| [buildSrc](buildSrc) | Shared Android application and library build conventions. |

## Resources and support

- [PICO Developer Website (Outside Chinese Mainland)](https://developer.picoxr.com/)
- [PICO Developer Website (Chinese Mainland)](https://developer-cn.picoxr.com/)
- [PICO Developer Support Portal](https://picodevsupport.freshdesk.com/support/home)

## License

© 2026 PICO. Source code is licensed under the [Apache 2.0 License](LICENSE.txt).
See the model and artwork licenses and notices included with each sample for asset terms.
