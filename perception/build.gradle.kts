plugins { id("spatialml.sample-library") }

android { namespace = "com.pico.spatial.ml.sample.perception" }

// SpatialML's platform bindings are compileOnly in the SDK and live on the headset. Compile the
// public signatures locally because the foundation artifact is absent from the public Maven repo.
// These declarations are never packaged in the APK.
val compilePlatformBindings by
    tasks.registering(JavaCompile::class) {
        source(fileTree("platform-api") { include("**/*.java") })
        classpath = files(android.bootClasspath)
        destinationDirectory.set(layout.buildDirectory.dir("platform-api/classes"))
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
    }
val platformBindingsJar by
    tasks.registering(Jar::class) {
        dependsOn(compilePlatformBindings)
        from(compilePlatformBindings.flatMap { it.destinationDirectory })
        archiveFileName.set("spatialml-platform-api.jar")
        destinationDirectory.set(layout.buildDirectory.dir("platform-api"))
    }

val platformApi by
    configurations.creating {
        isCanBeConsumed = true
        isCanBeResolved = false
    }

artifacts { add(platformApi.name, platformBindingsJar) }

dependencies {
    compileOnly(files(platformBindingsJar))
    api(libs.spatial.core)
    api(libs.spatial.foundation)
    api(libs.spatial.tracking)
}
