plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.serialization)
    id("com.diffplug.spotless")
}

android {
    namespace = "com.pico.spatial.ml.sample.common"
    compileSdk = 35

    defaultConfig { minSdk = 31 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions { jvmTarget = "1.8" }
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**/*.kt", ".gradle/**", "**/.gradle/**")
        ktfmt().kotlinlangStyle()
    }
    kotlinGradle {
        target("**/*.gradle.kts", "**/*.gradle")
        targetExclude(
            "**/build/**/*.gradle.kts",
            "**/build/**/*.gradle",
            ".gradle/**",
            "**/.gradle/**",
        )
        ktfmt().kotlinlangStyle()
    }
    format("xml") { target("**/*.xml") }
}

dependencies {
    api(platform(libs.spatial.bom))
    api(libs.spatial.ml.securemr)
    api(libs.coroutines.android)
    api(libs.kotlinx.serialization.json)
}
