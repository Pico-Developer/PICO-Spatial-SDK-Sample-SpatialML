# PICO SpatialML 示例

[English](README.md) | [简体中文](README_zh.md)

本仓库提供九个可独立运行的 Kotlin 应用，使用 PICO Spatial SDK 在 PICO OS 6 上演示 SpatialML。
示例涵盖相机输入、本地模型推理、硬件加速、异步结果回读，以及受保护的空间场景渲染。
部分示例使用 Kotlin API 构建处理图，其他示例加载声明式 pipeline 包。全部使用 Spatial SDK **6.2.8**。

## 示例应用

建议先运行 [SpatialMLHelloApp](SpatialMLHelloApp/README.md)，验证服务连接与相机显示，
再按需要选择其他示例。

| 应用 | 演示内容 |
| --- | --- |
| [SpatialMLHelloApp](SpatialMLHelloApp/README.md) | 无需模型，在受保护的面板上显示实时相机画面。 |
| [SuperResolutionApp](SuperResolutionApp/README_zh.md) | 使用 Kotlin 构建 Real-ESRGAN 相机放大处理图，提供受保护显示、相机回读及可选视觉问答。 |
| [SuperResolutionPipelineApp](SuperResolutionPipelineApp/README.md) | 使用 pipeline 包实现相同的超分辨率体验。 |
| [StylizedApp](StylizedApp) | 使用 Kotlin 构建相机风格预测与转换处理图。 |
| [StylizedPipelineApp](StylizedPipelineApp) | 使用 pipeline 包实现相机风格预测与转换。 |
| [FaceMediaPipePipelineApp](FaceMediaPipePipelineApp) | 使用 pipeline 包执行 MediaPipe 人脸检测并显示检测框。 |
| [FaceLandmarkerApp](FaceLandmarkerApp/README.md) | 跟踪 478 个人脸关键点、姿态及可选表情，在 Full Space 透视画面上渲染人脸网格和八种特效。 |
| [PosePipelineApp](PosePipelineApp) | 使用 pipeline 包进行姿态推理，在 Stage 中显示关节和肢体。 |
| [MnistWildPipelineApp](MnistWildPipelineApp) | 识别相机裁剪图中的手写数字，显示裁剪图、分类及置信度。 |

每个应用都有独立的包名、资源、APK 和 Android Studio 运行目标。模型随仓库提供；
超分辨率示例的可选 VQA 功能有独立的[后端配置说明](SuperResolutionApp/README_zh.md#可选视觉问答)。

## 环境要求

- Android Studio、JDK 21 和 Android SDK 35。
- Git LFS，用于下载模型和美术资源。
- 搭载兼容 PICO OS 6 SpatialML 服务的物理 PICO 头显，并启用 USB 调试。
  相机推理和头显内的坐标对齐需要在设备上验证。

请参照官方指南配置开发环境：

- [PICO Spatial SDK 环境配置指南（中国大陆）](https://developer-cn.picoxr.com/document/spatial-sdk/set-up-development-environment/)
- [PICO Spatial SDK 环境配置指南（非中国大陆）](https://developer.picoxr.com/document/spatial-sdk/set-up-development-environment/)

仓库在 [gradle/libs.versions.toml](gradle/libs.versions.toml) 中固定 SDK 和工具链版本，
并提供 Gradle wrapper。

## 快速开始

1. 克隆仓库并下载 Git LFS 资源：

   ```bash
   git clone https://github.com/Pico-Developer/PICO-Spatial-SDK-Sample-SpatialML.git
   cd PICO-Spatial-SDK-Sample-SpatialML
   git lfs pull
   ```

2. 在 Android Studio 中打开仓库根目录并同步 Gradle。
3. 连接头显，在运行目标中选择一个应用模块并运行，按对应示例指南完成权限授权和操作。

在仓库根目录构建单个示例：

```bash
./gradlew :SpatialMLHelloApp:assembleDebug
```

将 `SpatialMLHelloApp` 替换为表格中的任意应用模块。调试 APK 位于
`<module>/build/outputs/apk/debug/`。使用 `./gradlew assembleDebug` 构建全部九个应用。
排查运行问题时，可按应用的 logcat 标签及服务标签 `Secure MR::Server` 过滤日志。

## 共享模块与资源

| 目录 | 用途 |
| --- | --- |
| [face-landmarker](face-landmarker/README.md) | FaceLandmarkerApp 使用的人脸处理图、模型、结果解码、跟踪与网格工具。 |
| [perception](perception/README.md) | 采集时刻的坐标变换、Stage 姿态历史和原生处理图资源管理。 |
| [common](common) | 相机和 pipeline 示例的共享工具，包括 VQA 集成。 |
| [shared-assets](shared-assets) | 超分辨率和风格化示例各自共享的模型及美术资源。 |
| [buildSrc](buildSrc) | Android 应用与库的共享构建约定。 |

## 资源与支持

- [PICO 开发者网站（中国大陆）](https://developer-cn.picoxr.com/)
- [PICO 开发者网站（非中国大陆）](https://developer.picoxr.com/)
- [PICO 开发者支持门户](https://picodevsupport.freshdesk.com/support/home)

## 许可证

© 2026 PICO。源代码依据 [Apache 2.0 License](LICENSE.txt) 开源。
模型和美术资源的使用条款请参阅各示例随附的许可证和声明。
