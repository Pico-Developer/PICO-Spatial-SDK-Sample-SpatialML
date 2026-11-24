plugins {
    id("spatialml.sample-application")
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.pico.spatial.ml.sample.mnistwildpipeline"
    defaultConfig { applicationId = "com.pico.spatial.ml.sample.mnistwildpipeline" }
}

dependencies {
    implementation(libs.spatial.core)
    implementation(libs.spatial.tracking)
    implementation(libs.spatial.ml.readback)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.androidx.annotation)
}
