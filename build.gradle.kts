// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.compose.compiler) apply false
}

subprojects {
    configurations.all {
        resolutionStrategy {
            exclude("androidx.compose.ui", "ui")
            exclude("androidx.compose.ui", "ui-graphics")
            exclude("androidx.compose.ui", "ui-text")
            exclude("androidx.compose.foundation", "foundation")
        }
    }
}

// >>> INTERNAL_CONFIG_BLOCK_START
// Apply internal configurations globally if the internalConfig folder exists
val internalConfigDir = rootProject.file("internalConfig")
if (internalConfigDir.exists()) {
    subprojects {
        // Apply to any module that uses the Android Application plugin
        plugins.withType<com.android.build.gradle.AppPlugin> {
            val internalScript = rootProject.file("internalConfig/internal-config.gradle")
            if (internalScript.exists()) {
                println("Apply internal config script to ${project.name}: $internalScript")
                apply(from = internalScript)
            }
        }
    }
}
// <<< INTERNAL_CONFIG_BLOCK_END