plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.serialization)
    alias(libs.plugins.spotless)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.pico.spatial.ml.sample.sr"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pico.spatial.ml.sample.sr"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        ndk { abiFilters.add("arm64-v8a") }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    androidResources {
        noCompress.add(".bundle")
        noCompress.add(".glb")
        noCompress.add(".wav")
        noCompress.add(".usdz")
    }

    kotlinOptions { jvmTarget = "1.8" }

    buildFeatures { compose = true }

    lint { disable.add("ComposeModifierMissing") }
}

spotless {
    java {
        target("**/*.java")
        targetExclude("**/build/**/*.java", ".gradle/**", "**/.gradle/**")
        googleJavaFormat("1.17.0").aosp().reflowLongStrings()
    }

    kotlin {
        // To support some generated code to ignore spotless check
        toggleOffOn()
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
    implementation(platform(libs.spatial.bom))
    implementation(libs.spatial.core)
    implementation(libs.spatial.ml.securemr)
    implementation(libs.spatial.ml.readback)
    implementation(libs.spatial.ui.foundation)
    implementation(libs.spatial.ui.platform)
    implementation(libs.spatial.ui.design)

    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.activity.compose)
    implementation(libs.ui.tooling.preview)
    implementation(libs.coroutines.android)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.koin.androidx.compose)
    implementation(libs.kotlinx.serialization.json)
}
