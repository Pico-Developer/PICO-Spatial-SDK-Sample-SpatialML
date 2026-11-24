plugins {
    id("spatialml.sample-application")
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.pico.spatial.ml.sample.posepipeline"
    defaultConfig { applicationId = "com.pico.spatial.ml.sample.posepipeline" }
}

dependencies {
    implementation(libs.spatial.core)
    implementation(libs.spatial.ml.readback)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
}
