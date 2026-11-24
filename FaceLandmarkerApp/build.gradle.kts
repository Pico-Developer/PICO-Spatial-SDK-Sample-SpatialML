plugins {
    id("spatialml.sample-application")
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.pico.spatial.ml.sample.facelandmarker"
    defaultConfig { applicationId = "com.pico.spatial.ml.sample.facelandmarker" }
}

dependencies {
    implementation(project(":face-landmarker"))
    implementation(libs.spatial.core)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
}
