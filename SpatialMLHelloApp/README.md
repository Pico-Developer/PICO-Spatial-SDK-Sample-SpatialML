# First SpatialML scene

`SpatialMLHelloApp` displays the right-eye camera image on a protected SpatialML panel. Use it to
check SDK setup, service connection and scene output before adding a model. It targets PICO Spatial
SDK 6.2.8 and requires a physical PICO headset with compatible PICO OS 6 services for live camera input.

## Build and run

From the samples repository root:

```bash
./gradlew :SpatialMLHelloApp:assembleDebug
```

Open the repository in Android Studio, select `SpatialMLHelloApp`, and run it on the selected headset.
The debug APK is `SpatialMLHelloApp/build/outputs/apk/debug/SpatialMLHelloApp-debug.apk`.
The application ID is `com.pico.spatial.ml.sample.hello`; the launcher activity is `.MainActivity`.

The build copies only `SpatialML/Display512.glb` from `shared-assets/superresolution` into the APK.
No model download or cloud API key is required. Camera pixels stay inside the protected SpatialML
scene; this app does not read them into application memory.

## Follow the implementation

| File | Purpose |
| --- | --- |
| [MainApplication.kt](src/main/java/com/pico/spatial/ml/sample/hello/MainApplication.kt) | Spatial window, status UI and coroutine ownership |
| [CameraPreview.kt](src/main/java/com/pico/spatial/ml/sample/hello/CameraPreview.kt) | Service connection, image acquisition, color conversion and protected display |
| [ReadbackExample.kt](src/main/java/com/pico/spatial/ml/sample/hello/ReadbackExample.kt) | Numeric result copying within the readback snapshot lifetime |
| [ModelBuffers.kt](src/main/java/com/pico/spatial/ml/sample/hello/ModelBuffers.kt) | Example model tensor allocation and binding |
| [OperatorCalls.kt](src/main/java/com/pico/spatial/ml/sample/hello/OperatorCalls.kt) | Named SDK operator calls for compilation and API reference |

The last three files are integration examples compiled with the app; the camera preview does not
invoke them. Their input tensors must satisfy the corresponding operator contracts before use.
Camera-derived numeric readback also requires the normal application permission flow.

`CameraPreview` waits up to 10 seconds for service readiness, constructs its graphs once and requests
frames every 100 ms. That interval controls submission rate; it is not a native completion fence.
Closing the window cancels new submissions and submits a hide task after the last accepted task.
SDK handles use cleaner-managed disposal; the sample does not provide a deterministic session close.

## Check on the headset

- Confirm the protected panel shows changing camera content, not just an advancing submission count.
- Close and reopen the window; check that the old panel clears and the new run produces live content.
- Disconnect or stop the service during a separate diagnostic run and confirm failures are reported.
- Use `SpatialMLHello` in logcat to inspect a failed hide submission.

A successful build checks API compatibility. Live output and close/reopen behavior need device checks.
