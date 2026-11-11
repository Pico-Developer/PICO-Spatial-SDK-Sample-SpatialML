# Spatial ML

[English](README.md) | [简体中文](README_zh.md)

A reference application built with the PICO Spatial SDK that demonstrates SpatialML capabilities for
spatial applications on PICO OS 6. This SpatialML sample serves as a practical guide for developers,
showcasing the functionalities for CV algorithm deployment, multi-modality spatial data as
algorithm inputs, hardware acceleration for local machine learning inference, async readback of
algorithm output data, permission handling, and the distinctive _SecureMR_ isolated sandbox
environment which offers a comprehensive protection of user privacy while unleashing app's MR
creativity.

![Screenshot](.github/screenshot.jpeg)

## Features

There is currently one sample app under this project: the SuperResolution App, which:

- _Deploys_ a real-time neural-network-based SuperResolution model, to zoom in the see-through camera images,
- _Provides_ two modes: the Relax and SecureMR modes: the former requires camera permission from users,
  whereas the latter adopts the _SecureMR_ sandbox, providing zooming effect while protecting users
  privacy,
- _Implements_ a large-language-model-based visual-question-answer (VQA) interface in RelaxMR mode,
  which allow users to launch a VQA query on the zoomed camera images, using your LLM backend of
  your own choice, and
- _Showcase_ how to use Kotlin co-routines to asynchronously invoke the SpatialML APIs.

## Requirements

To build and run this project, configure your development environment by following the official PICO Spatial SDK setup guide:

- [PICO Spatial SDK Setup Guide (Outside Chinese Mainland)](https://developer.picoxr.com/document/spatial-sdk/set-up-development-environment/)
- [PICO Spatial SDK Setup Guide (Chinese Mainland)](https://developer-cn.picoxr.com/document/spatial-sdk/set-up-development-environment/)

## Getting Started

### 1. Clone the Repository

```bash
git clone <repository-url>
cd SpatialML
```

### 2. Open in Android Studio

Launch Android Studio, select **Open an Existing Project**, and choose the `SpatialML` directory.

### **[Optional]** Apply for ByteDance Volcano API Key

As is mentioned above, the SuperResolution Demo App come with a LLM-based VQA interface, at [`SuperResolutionApp/src/main/java/com/pico/spatial/ml/sample/sr/vm/VQAWrapper.kt`](SuperResolutionApp/src/main/java/com/pico/spatial/ml/sample/sr/vm/VQAWrapper.kt). The built-in implementation to that interface uses the Volcano Cloud Engine as its backend. In
order to use the VQA feature out-of-box, you may need to apply for a Volcano API key.

Then, connect to the PICO device, ensure the USB debugging is enabled on that device, and run

```bash
adb shell setprop debug.spatialml.apikey <API-KEY>
```

If no API Key is provided, when you launch the app and enter _RelaxMR_ mode,
you will find the "Ask AI" button not clickable.

### **[Optional]** Replace the Volcano LLM backend with VQA APIs of your own choice

Instead of applying for a Volcano API key, you can also replace the default implementation of the
VQA interface with any VQA backend of your own choice. The only requirement is the VQA backend
should provides RESTFul API calls.

You can refer to the sample codes in [`SuperResolutionApp/src/main/java/com/pico/spatial/ml/sample/sr/vm/VQAWrapper.kt`](SuperResolutionApp/src/main/java/com/pico/spatial/ml/sample/sr/vm/VQAWrapper.kt), where the default implementation is defined:

```kt
val ark
    get() = create<FileUploadResponse, QueryResponse>(
        // ...
    )
```

### 3. Run on Device

PICO Emulator _DOES NOT_ support SpatialML features, so a PICO device with PICO OS 6 is required.

1. Connect your PICO device to your development machine
2. Enable **USB Debugging** on the device
3. Click **Run 'app'** to deploy directly to your device
4. You can filter the logcat logs using `Secure MR::Server` tag to capture the logs at run time. 

## Project Structure

SpatialML Sample follows a modular architecture to separate application logic from 3D resources:

```bash
SpatialML/
├── SuperResolution/                            # Main application module
│   └── src/main/
│       ├── java/com/pico/spatial/ml/sample/sr/ # Kotlin source files
│       │   ├── MainApplication.kt              # Application entry point
│       │   ├── MainActivity.kt                 # Container definition and the root composable
│       │   ├── view/                           # Composable views for UI
│       │   ├── vm/                             # View model used by the composable view, which contains the
│       │   │                                   # core algorithm implementation, for both Spatial ML API calls
│       │   │                                   # and VQA invocations. 
│       │   └── helper/                         # Helper utilities
│       ├── assets/                             # Application assets for rendering
│       ├── res/                                # Android resources
│       └── AndroidManifest.xml                 # App manifest with Spatial components
├── gradle/                               # Gradle dependencies and scripts
├── build.gradle.kts                      # Root build configuration
└── settings.gradle.kts                   # Project settings and SDK setup
```

## Learning Resources

To deepen your understanding of spatial app development, explore the comprehensive resources on our official developer websites:

- [PICO Developer Website (Outside Chinese Mainland)](https://developer.picoxr.com/)
- [PICO Developer Website (Chinese Mainland)](https://developer-cn.picoxr.com/)

## License

© 2026 PICO. The project's source code is licensed under the [Apache 2.0 License](LICENSE.txt). All assets, including textures, 3D models, audio, and others, are licensed via [Creative Commons BY 4.0](https://creativecommons.org/licenses/by/4.0/).

## Support

For questions, issues, or feature requests, please contact us through the [PICO Developer Support Portal](https://picodevsupport.freshdesk.com/support/home) and submit a ticket.

---

Developed as a reference for spatial application developers
