# 超分辨率示例

[English](README.md) | [简体中文](README_zh.md) | [全部示例](../README_zh.md)

`SuperResolutionApp` 使用 Real-ESRGAN 模型放大头显的透视相机画面，
通过 Kotlin API 构建 SpatialML 处理图，并使用协程异步提交任务和回读结果。
使用 pipeline 包加载同等处理图的版本见 [SuperResolutionPipelineApp](../SuperResolutionPipelineApp/README.md)。

![超分辨率示例截图](../.github/screenshot.jpeg)

## 构建与运行

按仓库的[环境要求](../README_zh.md#环境要求)配置 Git LFS 资源、JDK 21、Android SDK 35
和物理 PICO OS 6 头显。在仓库根目录执行：

```bash
./gradlew :SuperResolutionApp:assembleDebug
```

在 Android Studio 中选择 `SuperResolutionApp` 并运行到头显。
APK 位于 `SuperResolutionApp/build/outputs/apk/debug/SuperResolutionApp-debug.apk`；
应用包名为 `com.pico.spatial.ml.sample.sr`。

## 模式与操作

打开应用后选择模式：

| 模式 | 相机数据与显示 | VQA |
| --- | --- | --- |
| **SpatialML** | 图像留在隔离的 SpatialML 服务中，显示在受保护的空间窗口里。 | 不可用。 |
| **RelaxMR** | 请求相机权限，将图像返回应用，用于显示及可选的云端查询。 | 配置后端后可用。 |

拖动手柄调节放大倍数。两种模式都在本地执行超分辨率推理；
只有 RelaxMR 模式的可选 **Ask AI** 功能需要 API Key。

## 可选视觉问答

**Ask AI** 将放大的相机图像发送至 VQA 后端并显示回复。
默认实现使用字节跳动火山引擎。申请 API Key 后，在使用该功能前对目标头显执行：

```bash
adb -s HEADSET_SERIAL shell setprop debug.spatialml.apikey YOUR_API_KEY
```

未设置 Key 时，**Ask AI** 按钮不可点击。若要使用其他 REST API，修改
[`VQAWrapper.kt`](../common/src/main/java/com/pico/spatial/ml/sample/sr/vm/VQAWrapper.kt)。
其中的 `ark` 实现演示了如何通过 `VQAWrapper.create` 配置图像上传、身份验证、请求格式与响应解析。

## 代码导航

| 文件或目录 | 用途 |
| --- | --- |
| [MainContainer.kt](src/main/java/com/pico/spatial/ml/sample/sr/view/MainContainer.kt) | 模式选择与确认。 |
| [SuperResolutionSpatialView.kt](src/main/java/com/pico/spatial/ml/sample/sr/view/SuperResolutionSpatialView.kt) | 相机权限、放大手柄、显示与 VQA 结果。 |
| [SrAlgorithmImpl.kt](src/main/java/com/pico/spatial/ml/sample/sr/vm/SrAlgorithmImpl.kt) | Kotlin 处理图构建、相机裁剪、模型推理及结果回读。 |
| [SrViewModel.kt](src/main/java/com/pico/spatial/ml/sample/sr/vm/SrViewModel.kt) | 模式、放大倍数与查询状态。 |
| [共享资源](../shared-assets/superresolution) | 两个超分辨率版本共同使用的 Real-ESRGAN 模型及显示器、手柄模型。 |
