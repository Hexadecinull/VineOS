package com.hexadecinull.vineos.ui.viewmodel

import android.app.ActivityManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import androidx.lifecycle.ViewModel
import com.hexadecinull.vineos.BuildConfig
import com.hexadecinull.vineos.domain.VineVMManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

data class AboutInfoItem(val label: String, val value: String)
data class AboutInfoSection(val title: String, val items: List<AboutInfoItem>)

@HiltViewModel
class AboutViewModel @Inject constructor(@ApplicationContext private val context: Context, private val vmManager: VineVMManager) :
    ViewModel() {

    val sections: List<AboutInfoSection> = listOf(appSection(), hostOsSection(), deviceSection())

    private fun appSection() = AboutInfoSection(
        title = "VineOS",
        items = listOf(
            AboutInfoItem("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"),
            AboutInfoItem("Application ID", BuildConfig.APPLICATION_ID),
            AboutInfoItem("Build type", "${BuildConfig.BUILD_TYPE}${if (BuildConfig.DEBUG) ", debuggable" else ""}"),
        ),
    )

    private fun hostOsSection() = AboutInfoSection(
        title = "Host Android",
        items = listOf(
            AboutInfoItem("Version", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"),
            AboutInfoItem("Security patch", Build.VERSION.SECURITY_PATCH ?: "Unknown"),
            AboutInfoItem("Build", Build.DISPLAY),
            AboutInfoItem("Fingerprint", Build.FINGERPRINT),
        ),
    )

    private fun deviceSection(): AboutInfoSection {
        val memInfo = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(memInfo)
        val totalRamMb = memInfo.totalMem / (1024 * 1024)

        val metrics = Resources.getSystem().displayMetrics
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}"
        } else {
            "Unknown (needs Android 12+)"
        }

        return AboutInfoSection(
            title = "Device",
            items = listOf(
                AboutInfoItem("Model", "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})"),
                AboutInfoItem("SoC", soc),
                AboutInfoItem("Board / hardware", "${Build.BOARD} / ${Build.HARDWARE}"),
                AboutInfoItem("Bootloader", Build.BOOTLOADER),
                AboutInfoItem("CPU cores", Runtime.getRuntime().availableProcessors().toString()),
                AboutInfoItem("Total RAM", "$totalRamMb MB"),
                AboutInfoItem("Screen", "${metrics.widthPixels}x${metrics.heightPixels} @ ${metrics.densityDpi}dpi"),
                AboutInfoItem("Supported ABIs", Build.SUPPORTED_ABIS.joinToString(", ")),
                AboutInfoItem("64-bit ABIs", Build.SUPPORTED_64_BIT_ABIS.joinToString(", ").ifBlank { "None" }),
                AboutInfoItem("32-bit ABIs", Build.SUPPORTED_32_BIT_ABIS.joinToString(", ").ifBlank { "None" }),
                AboutInfoItem("AArch32 execution", if (vmManager.hostSupports32bit) "Native" else "QEMU only"),
            ),
        )
    }
}
