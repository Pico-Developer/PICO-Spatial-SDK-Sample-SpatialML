plugins { id("spatialml.sample-library") }

android { namespace = "com.pico.spatial.ml.sample.facelandmarker.feature" }

dependencies {
    api(project(":perception"))
    compileOnly(project(path = ":perception", configuration = "platformApi"))
    api(libs.coroutines.android)
    implementation(libs.spatial.ml.securemr)
}

// Static assets are declared directly so every build packages the same graph.
android.sourceSets.getByName("main").assets.srcDir("pipeline")
