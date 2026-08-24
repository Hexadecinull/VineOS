package com.hexadecinull.vineos.ui.screens

import com.hexadecinull.vineos.R

enum class LicenseType(val displayName: String, val rawResId: Int) {
    APACHE_2_0("Apache License 2.0", R.raw.license_apache_2_0),
    GPL_2_0("GNU General Public License v2.0", R.raw.license_gpl_2_0),
}

data class OssLibrary(val name: String, val license: LicenseType, val url: String)

// Runtime dependencies actually shipped in the APK; test-only libraries are left out since they never leave the build
val ossLibraries = listOf(
    OssLibrary("Kotlin", LicenseType.APACHE_2_0, "https://github.com/JetBrains/kotlin"),
    OssLibrary("Kotlin Coroutines", LicenseType.APACHE_2_0, "https://github.com/Kotlin/kotlinx.coroutines"),
    OssLibrary("Kotlin Serialization", LicenseType.APACHE_2_0, "https://github.com/Kotlin/kotlinx.serialization"),
    OssLibrary("AndroidX Core", LicenseType.APACHE_2_0, "https://github.com/androidx/androidx"),
    OssLibrary("AndroidX Lifecycle", LicenseType.APACHE_2_0, "https://github.com/androidx/androidx"),
    OssLibrary("AndroidX Activity", LicenseType.APACHE_2_0, "https://github.com/androidx/androidx"),
    OssLibrary("Jetpack Compose", LicenseType.APACHE_2_0, "https://github.com/androidx/androidx"),
    OssLibrary("AndroidX Navigation", LicenseType.APACHE_2_0, "https://github.com/androidx/androidx"),
    OssLibrary("AndroidX Room", LicenseType.APACHE_2_0, "https://github.com/androidx/androidx"),
    OssLibrary("AndroidX DataStore", LicenseType.APACHE_2_0, "https://github.com/androidx/androidx"),
    OssLibrary("Dagger / Hilt", LicenseType.APACHE_2_0, "https://github.com/google/dagger"),
    OssLibrary("OkHttp", LicenseType.APACHE_2_0, "https://github.com/square/okhttp"),
    OssLibrary("Coil", LicenseType.APACHE_2_0, "https://github.com/coil-kt/coil"),
    OssLibrary("QEMU (qemu-arm, user-mode)", LicenseType.GPL_2_0, "https://www.qemu.org/"),
)
