# Spatial ML

[English](README.md) | [简体中文](README_zh.md)

该项目是一款基于 PICO OS 6 的参考示例应用，使用 PICO Spatial SDK 演示 SpatialML 的能力。本示例是面向开发者的实践指南，涵盖 CV 算法部署、多模态空间数据作为算法输入、本地机器学习推理的硬件加速、算法输出数据的异步回读（readback）、权限处理，以及 _SecureMR_ 隔离沙箱环境——在充分保护用户隐私的同时，释放应用的 MR 创意潜力。

![Screenshot](.github/screenshot.jpeg)

## 功能特性

本项目目前包含一个示例应用：超分辨率应用（SuperResolutionApp），具备以下能力：

- 部署基于神经网络的实时超分辨率模型，用于对透视相机画面进行放大显示
- 提供 Relax 与 SecureMR 两种模式。前者需要用户授予相机权限；后者采用 _SecureMR_ 沙箱，在保护用户隐私的同时实现图像放大
- 实现基于大语言模型的视觉问答（Visual Question Answer，VQA）交互（仅在 RelaxMR 模式下可用），允许用户对放大后的相机画面发起 VQA 查询，并可接入自选的 LLM 后端
- 展示如何使用 Kotlin 协程（Coroutine）异步调用 SpatialML API

## 环境要求

构建和运行本项目前，请参照 PICO Spatial SDK 官方文档完成开发环境配置：

- [PICO Spatial SDK 环境配置指南（中国大陆）](https://developer-cn.picoxr.com/document/spatial-sdk/set-up-development-environment/)
- [PICO Spatial SDK 环境配置指南（非中国大陆）](https://developer.picoxr.com/document/spatial-sdk/set-up-development-environment/)

## 快速开始

### 1. 克隆项目

```bash
git clone <repository-url>
cd SpatialML
```

### 2. 在 Android Studio 中打开项目

启动 Android Studio，选择 **Open an Existing Project**，然后选择 `SpatialML` 目录。

### **可选** 申请火山引擎 API Key

如上所述，超分辨率示例应用内置了基于大语言模型的视觉问答（VQA）接口，位于 [`SuperResolutionApp/src/main/java/com/pico/spatial/ml/sample/sr/vm/VQAWrapper.kt`](SuperResolutionApp/src/main/java/com/pico/spatial/ml/sample/sr/vm/VQAWrapper.kt)。该接口的内置实现以火山云引擎作为后端。如需开箱即用地体验 VQA 功能，请申请火山引擎 API Key。

申请完成后，连接 PICO 设备，确保已启用 USB 调试，然后执行：

```bash
adb shell setprop debug.spatialml.apikey <API-KEY>
```

若未配置 API Key，进入 _RelaxMR_ 模式后，"Ask AI" 按钮将处于不可点击状态。

### **可选** 替换为自选 VQA 后端

除了申请火山引擎 API Key 外，你也可以将 VQA 接口的默认实现替换为任意符合条件的 VQA 后端，唯一要求是该后端提供 RESTful API。

你可以参考 [`SuperResolutionApp/src/main/java/com/pico/spatial/ml/sample/sr/vm/VQAWrapper.kt`](SuperResolutionApp/src/main/java/com/pico/spatial/ml/sample/sr/vm/VQAWrapper.kt) 中的示例代码；默认实现定义如下：

```kt
val ark
    get() = create<FileUploadResponse, QueryResponse>(
        // ...
    )
```

### 3.运行项目

PICO 模拟器暂**不支持** SpatialML 功能，请使用搭载 PICO OS 6 的物理设备运行本示例。

1. 将 PICO 设备连接至开发机
2. 在设备上启用 **USB 调试（USB Debugging）**
3. 点击 **Run 'app'** 将应用直接部署到设备
4. 运行时可通过 `Secure MR::Server` 标签过滤 logcat 日志

## 项目结构

SpatialML 示例采用模块化架构，将应用逻辑与 3D 资源分离：

```bash
SpatialML/
├── SuperResolution/                            # 主应用模块
│   └── src/main/
│       ├── java/com/pico/spatial/ml/sample/sr/ # Kotlin 源代码
│       │   ├── MainApplication.kt              # 应用入口
│       │   ├── MainActivity.kt                 # 空间容器定义与根 Composable
│       │   ├── view/                           # 所有 Composable UI 的定义
│       │   ├── vm/                             # UI 所依赖的 ViewModel，包含核心算法实现，
│       │   │                                   # 涵盖 SpatialML API 调用和 VQA 触发
│       │   └── helper/                         # 工具类
│       ├── assets/                             # 应用运行和渲染所需素材资源
│       ├── res/                                # Android 资源
│       └── AndroidManifest.xml                 # 包含 Spatial 组件声明的应用清单
├── gradle/                               # Gradle 依赖与构建脚本
├── build.gradle.kts                      # 根构建配置
└── settings.gradle.kts                   # 项目设置与 SDK 配置
```

## 学习资源

深入了解空间应用开发，欢迎访问官方开发者网站获取完整资源：

- [PICO 开发者网站（中国大陆）](https://developer-cn.picoxr.com/)
- [PICO 开发者网站（非中国大陆）](https://developer.picoxr.com/)

## 许可证

© 2026 PICO。本项目的源代码依据 [Apache 2.0 License](LICENSE.txt) 协议开源。本项目的美术资产（包括但不限于纹理、3D 模型和音频）基于 [Creative Commons BY 4.0](https://creativecommons.org/licenses/by/4.0/) 授权。

## 支持

如有任何问题、疑惑或功能建议，欢迎通过 [PICO 开发者支持门户](https://picodevsupport.freshdesk.com/support/home) 提交工单与我们联系。

---

为空间应用开发者提供的官方参考示例
