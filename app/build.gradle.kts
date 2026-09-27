plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.devfahim00.lychee"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.devfahim00.lychee"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"
        vectorDrawables {
            useSupportLibrary = true
        }
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // yt-dlp runtime: Python 3.14 (termux-built, includes curl_cffi, cffi, brotli,
    // pycryptodomex, mutagen, websockets, requests, certifi) + QuickJS binary
    implementation("io.github.deniscerri:ytdlnis_packages.python:3.14.6")
    // ffmpeg + ffprobe
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.17.2")
    // aria2c
    implementation("io.github.junkfood02.youtubedl-android:aria2c:0.18.1")
    // symlink-preserving zip extraction
    implementation("org.apache.commons:commons-compress:1.27.1")

    implementation(platform("androidx.compose:compose-bom:2026.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("io.coil-kt:coil-compose:2.7.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
