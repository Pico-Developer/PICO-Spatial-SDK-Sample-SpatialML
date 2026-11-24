plugins {
    id("spatialml.sample-application")
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.pico.spatial.ml.sample.srpipeline"
    defaultConfig { applicationId = "com.pico.spatial.ml.sample.srpipeline" }
    sourceSets["main"].assets.srcDir(rootProject.file("shared-assets/superresolution"))
}

dependencies {
    implementation(project(":common"))
    implementation(libs.spatial.core)
    implementation(libs.spatial.ml.readback)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)
    implementation(libs.ui.tooling.preview)
}
