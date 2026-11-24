plugins {
    id("spatialml.sample-application")
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.pico.spatial.ml.sample.stylizedpipeline"
    defaultConfig { applicationId = "com.pico.spatial.ml.sample.stylizedpipeline" }
    sourceSets["main"].assets.srcDir(rootProject.file("shared-assets/stylized"))
}

dependencies {
    implementation(project(":common"))
    implementation(libs.ui.tooling.preview)
}
