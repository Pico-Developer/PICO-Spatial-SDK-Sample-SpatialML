plugins {
    id("spatialml.sample-application")
    alias(libs.plugins.compose.compiler)
}

val tutorialAssets =
    tasks.register<Sync>("prepareTutorialAssets") {
        from(rootProject.file("shared-assets/superresolution")) {
            include("SpatialML/Display512.glb")
        }
        into(layout.buildDirectory.dir("generated/tutorialAssets"))
    }

android {
    namespace = "com.pico.spatial.ml.sample.hello"
    defaultConfig { applicationId = "com.pico.spatial.ml.sample.hello" }
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/tutorialAssets"))
}

tasks.named("preBuild") { dependsOn(tutorialAssets) }

dependencies {
    implementation(libs.spatial.core)
    implementation(libs.spatial.ml.readback)
}
