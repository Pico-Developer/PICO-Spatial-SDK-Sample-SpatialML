plugins { `kotlin-dsl` }

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.buildsrc.agp)
    implementation(libs.buildsrc.kotlin)
    implementation(libs.buildsrc.spotless)
}
