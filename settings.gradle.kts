pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven {
            url = uri("https://artifact.bytedance.com/repository/Volcengine")
            name = ""
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://artifact.bytedance.com/repository/Volcengine")
            name = ""
        }
    }
}

rootProject.name = "SpatialML Samples"

include(":SuperResolutionApp")

include(":SuperResolutionPipelineApp")

include(":StylizedApp")

include(":StylizedPipelineApp")

include(":FaceMediaPipePipelineApp")

include(":FaceLandmarkerApp")

include(":PosePipelineApp")

include(":MnistWildPipelineApp")

include(":common")

include(":perception")
include(":face-landmarker")

include(":SpatialMLHelloApp")
