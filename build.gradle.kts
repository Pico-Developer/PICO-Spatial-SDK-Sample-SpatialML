// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
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
