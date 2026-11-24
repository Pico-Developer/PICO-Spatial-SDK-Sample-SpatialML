plugins {
    id("spatialml.sample-application")
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.pico.spatial.ml.sample.stylized"
    defaultConfig { applicationId = "com.pico.spatial.ml.sample.stylized" }
    sourceSets["main"].assets.srcDir(rootProject.file("shared-assets/stylized"))
}

dependencies { implementation(libs.ui.tooling.preview) }
