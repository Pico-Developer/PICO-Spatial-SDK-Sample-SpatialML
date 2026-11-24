import com.android.build.api.dsl.ApplicationExtension
import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.diffplug.spotless")
}

extensions.configure<ApplicationExtension> {
    compileSdk = 35

    defaultConfig {
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        ndk { abiFilters.add("arm64-v8a") }
    }

    buildTypes {
        getByName("release") {
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
        noCompress.add(".gltf")
        noCompress.add(".json")
        noCompress.add(".tflite")
        noCompress.add(".wav")
        noCompress.add(".usdz")
    }

    buildFeatures { compose = true }
    lint { disable.add("ComposeModifierMissing") }
}

extensions.configure<KotlinAndroidProjectExtension> {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_1_8) }
}

extensions.configure<SpotlessExtension> {
    java {
        target("**/*.java")
        targetExclude("**/build/**/*.java", ".gradle/**", "**/.gradle/**")
        googleJavaFormat("1.17.0").aosp().reflowLongStrings()
    }
    kotlin {
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

val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
dependencies {
    add("implementation", platform(libsCatalog.findLibrary("spatial-bom").get()))
    add("implementation", libsCatalog.findLibrary("spatial-ml-securemr").get())
    add("implementation", libsCatalog.findLibrary("spatial-ui-foundation").get())
    add("implementation", libsCatalog.findLibrary("spatial-ui-platform").get())
    add("implementation", libsCatalog.findLibrary("spatial-ui-design").get())
    add("implementation", libsCatalog.findLibrary("coroutines-android").get())
}
