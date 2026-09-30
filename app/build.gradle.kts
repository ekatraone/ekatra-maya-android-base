plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("com.google.firebase.firebase-perf")
    // Hilt DI
    id("com.google.dagger.hilt.android")
}

import com.android.build.api.dsl.ApkSigningConfig
import java.time.Instant
import java.time.format.DateTimeFormatter

// Version configuration - Update these for each release
val versionMajor = 1
val versionMinor = 1
val versionPatch = 2
val versionBuild = 1  // Increment for each build

// Configures a persona's release signingConfig, tolerant of a missing keystore/env
// vars for non-release tasks (local debug/assemble), but strict for actual release builds.
fun configurePersonaSigning(
    config: ApkSigningConfig,
    persona: String,
    keystoreFileName: String,
    alias: String
) {
    val isReleaseTask = gradle.startParameter.taskNames.any {
        it.contains("Release", ignoreCase = true) && it.contains(persona, ignoreCase = true)
    }

    val keystoreFile = file("../../keystore/$keystoreFileName")
    val storePass = System.getenv("KEYSTORE_PASSWORD") ?: ""
    val keyPass = System.getenv("KEY_PASSWORD") ?: ""

    if (isReleaseTask) {
        if (!keystoreFile.exists()) {
            throw GradleException("Release keystore missing at ../keystore/$keystoreFileName")
        }
        if (storePass.isBlank() || keyPass.isBlank()) {
            throw GradleException("Release signing passwords not set. Please set KEYSTORE_PASSWORD and KEY_PASSWORD env vars.")
        }
        config.storeFile = keystoreFile
        config.storePassword = storePass
        config.keyAlias = alias
        config.keyPassword = keyPass
    } else if (keystoreFile.exists()) {
        // For debug/non-release tasks, don't fail the build if keystore/env vars are absent.
        config.storeFile = keystoreFile
        config.storePassword = storePass.ifBlank { "debug-pass" }
        config.keyAlias = alias
        config.keyPassword = keyPass.ifBlank { "debug-pass" }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "org.ekatra.alfred"
    compileSdk = 35
    ndkVersion = "26.1.10909125"

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    defaultConfig {
        minSdk = 24
        targetSdk = 35
        
        // versionCode must be incremented for each Play Store upload
        // Formula: major*10000 + minor*100 + patch (allows 99 patches per minor)
        versionCode = versionMajor * 10000 + versionMinor * 100 + versionPatch
        versionName = "$versionMajor.$versionMinor.$versionPatch"
        
        // Build metadata
        val buildTime = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
        buildConfigField("int", "VERSION_BUILD", "$versionBuild")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // x86_64 dropped: KleidiAI ARM kernels don't compile for x86_64,
            // and all target devices (Indian budget phones) are arm64.
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DCMAKE_BUILD_TYPE=Release",
                    "-DLLAMA_NATIVE=OFF",
                    // S.12: ARM KleidiAI int8/i8mm micro-kernels; ~1.7× tok/s on Cortex-A v8.2+
                    // (Moto G73 / Dimensity 930 supports i8mm — confirmed arm64 build target)
                    "-DGGML_CPU_KLEIDIAI=ON"
                )
                cppFlags += listOf("-O3", "-fPIC")
            }
        }
    }

    // Release signing configuration (fail only on release tasks), one per persona flavor
    signingConfigs {
        create("asmaa") {
            configurePersonaSigning(this, "asmaa", "asmaa-ai-release.keystore", "asmaa-ai")
        }
        create("maya") {
            configurePersonaSigning(this, "maya", "maya-ai-release.keystore", "maya-ai")
        }
    }

    flavorDimensions += "persona"
    productFlavors {
        create("asmaa") {
            dimension = "persona"
            applicationId = "org.ekatra.asmaa"
            signingConfig = signingConfigs.getByName("asmaa")
        }
        create("maya") {
            dimension = "persona"
            applicationId = "org.ekatra.alfred"
            signingConfig = signingConfigs.getByName("maya")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // No signingConfig here — each persona flavor supplies its own release
            // signingConfig above (asmaa-ai-release.keystore / maya-ai-release.keystore).
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            // Force the standard auto-generated debug keystore regardless of flavor,
            // since each flavor's Firebase project has that debug cert's SHA-1 registered.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true  // Enable BuildConfig generation
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.webkit:webkit:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Room (SQLite)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    
    // NanoHTTPD for simple HTTP server (multi-device mode)
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    
    // Jetpack Compose for native UI
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.compose.runtime:runtime-livedata")
    
    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.5.0")

    // === Hilt DI ===
    implementation("com.google.dagger:hilt-android:2.51")
    ksp("com.google.dagger:hilt-android-compiler:2.51")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // === Firebase ===
    implementation(platform("com.google.firebase:firebase-bom:34.9.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-perf")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-config")
    implementation("com.google.firebase:firebase-messaging")
    
    // QR Code generation
    implementation("com.google.zxing:core:3.5.3")

    // Google Sign-In via Credential Manager (replaces deprecated play-services-auth GoogleSignIn)
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    
    // WorkManager for background sync
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // === Testing ===
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("app.cash.turbine:turbine:1.0.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("androidx.work:work-testing:2.9.0")
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.51")
    kspAndroidTest("com.google.dagger:hilt-android-compiler:2.51")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
