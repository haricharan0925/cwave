import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.vibe.player"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vibe.player"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        // x86_64 = Pixel emulator, arm64-v8a = real phones. Each ABI adds ~50 MB (Python + FFmpeg).
        ndk { abiFilters += listOf("x86_64", "arm64-v8a") }
    }

    buildTypes {
        debug { isMinifyEnabled = false }
        // Personal-use build: release-mode runtime (ART optimisations, no debug overhead) but signed with the
        // debug keystore so it installs over the debug build and no key management is needed.
        release {
            isMinifyEnabled = false
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        // youtubedl-android executes libpython.so directly, so native libs must be extracted to disk.
        jniLibs { useLegacyPackaging = true }
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-opt-in=androidx.media3.common.util.UnstableApi",
        )
    }
}

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2025.09.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.navigation:navigation-compose:2.9.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-service:2.9.4")
    implementation("androidx.documentfile:documentfile:1.1.0")

    // Playback
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-session:1.8.0")
    implementation("androidx.media3:media3-common:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.10.2")

    // Persistence
    implementation("androidx.room:room-runtime:2.8.1")
    implementation("androidx.room:room-ktx:2.8.1")
    ksp("androidx.room:room-compiler:2.8.1")
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    // Background work
    implementation("androidx.work:work-runtime-ktx:2.10.5")

    // Images
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")

    // Drag-to-reorder lists
    implementation("sh.calvin.reorderable:reorderable:2.4.3")

    // Audio conversion: FFmpeg (LGPL-3.0 build, audio codecs only)
    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-audio:8.1.9")

    // YouTube / yt-dlp downloads (bundled Python + yt-dlp + ffmpeg)
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
}
