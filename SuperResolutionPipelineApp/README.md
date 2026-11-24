# Super Resolution pipeline sample

[All samples](../README.md)

`SuperResolutionPipelineApp` provides the same camera zoom, SpatialML/RelaxMR modes and optional
VQA controls as [SuperResolutionApp](../SuperResolutionApp/README.md). It loads schema-v2 pipeline
packages instead of constructing the inference graph in Kotlin. See the Super Resolution guide
for [modes and controls](../SuperResolutionApp/README.md#modes-and-controls) and
[VQA backend setup](../SuperResolutionApp/README.md#optional-visual-question-answering).

## Build and run

Follow the repository's [environment setup](../README.md#requirements). From the repository root:

```bash
./gradlew :SuperResolutionPipelineApp:assembleDebug
```

Select `SuperResolutionPipelineApp` in Android Studio and run it on the headset.
The APK is `SuperResolutionPipelineApp/build/outputs/apk/debug/SuperResolutionPipelineApp-debug.apk`;
the application ID is `com.pico.spatial.ml.sample.srpipeline`.

## Pipeline structure

[SrAlgorithmImpl.kt](src/main/java/com/pico/spatial/sample/features/spatialml/superresolutionpipeline/vm/SrAlgorithmImpl.kt)
loads the [package manifest](src/main/assets/SpatialML/superresolution-pipeline/manifest.json),
updates crop parameters through the [`affine` pipeline](src/main/assets/SpatialML/superresolution-pipeline/pipeline/affine.json),
then submits the [`super_resolution` pipeline](src/main/assets/SpatialML/superresolution-pipeline/pipeline/super_resolution.json).
Kotlin connects the output to a protected display or an app-readable texture, depending on the mode.
The model and display/controller geometry come from [shared assets](../shared-assets/superresolution).
