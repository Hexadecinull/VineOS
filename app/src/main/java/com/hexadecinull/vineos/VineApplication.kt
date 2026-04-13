package com.hexadecinull.vineos

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VineApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_VM_RUNNING,
                    "Running VMs",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Shown while a VineOS virtual machine is active"
                    setShowBadge(false)
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ROM_DOWNLOAD,
                    "ROM Downloads",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Progress notifications for ROM image downloads"
                    setShowBadge(false)
                },
            )
        }
    }

    companion object {
        const val CHANNEL_VM_RUNNING = "vine_vm_running"
        const val CHANNEL_ROM_DOWNLOAD = "vine_rom_download"
    }
}
