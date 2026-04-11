plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.ktlint)  // apply here so ktlintCheck is available in :app subproject
}

android {
    namespace = "com.hexadecinull.vineos"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hexadecinull.vineos"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-alpha"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a")
            // x86_64 intentionally excluded from production builds:
            // VineOS is ARM-only. x86_64 emulator support is a future concern.
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-Wall", "-Wextra", "-Wpedantic")
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_PLATFORM=android-26",
                )
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
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
        compose = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// ── ktlint configuration ──────────────────────────────────────────────────────
ktlint {
    version.set("1.3.1")
    android.set(true)       // Enables Android-specific rules
    outputToConsole.set(true)
    ignoreFailures.set(false)
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
    }
    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
        include("**/kotlin/**")
    }
}

dependencies {
    // Core
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)     // collectAsStateWithLifecycle
    implementation(libs.activity.compose)

    // Compose BOM — all Compose versions aligned via BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room DB
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Coroutines
    implementation(libs.coroutines.android)

    // Serialization
    implementation(libs.kotlinx.serialization)

    // Networking (ROM downloader)
    implementation(libs.okhttp)

    // Image loading (ROM thumbnails)
    implementation(libs.coil.compose)

    // ── Unit tests (JVM, no device needed) ───────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.room.testing)

    // ── Instrumented tests (require device/emulator) ──────────────────────────
    androidTestImplementation(libs.junit.android)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.truth)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)

    // Debug-only
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
