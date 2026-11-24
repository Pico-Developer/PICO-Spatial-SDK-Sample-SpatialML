plugins {
    id("spatialml.sample-application")
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.pico.spatial.ml.sample.facemediapipe"
    defaultConfig { applicationId = "com.pico.spatial.ml.sample.facemediapipe" }
}
