package com.hexadecinull.vineos.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.hexadecinull.vineos.MainActivity
import com.hexadecinull.vineos.R
import com.hexadecinull.vineos.VineApplication.Companion.CHANNEL_VM_RUNNING
import dagger.hilt.android.AndroidEntryPoint

/**
 * VineService — a foreground service that keeps the VineOS runtime alive
 * when the user backgrounds the app.
 *
 * This service is started when the first VM instance is launched and stopped
 * when all instances are stopped. It holds a wake lock to prevent the CPU
 * from sleeping while a VM is active.
 *
 * Architecture:
 * - Started/stopped by VineVMManager via startForegroundService() / stopSelf()
 * - Communicates VM state back to the UI via a Flow (exposed via ViewModel)
 * - The actual native container lifecycle is managed by VineRuntime (JNI)
 *
 * TODO (Phase 2):
 * - Inject VineVMManager via Hilt
 * - Expose live VM state updates via a bound service interface or Flow
 * - Handle system-initiated service restarts gracefully (START_STICKY)
 */
@AndroidEntryPoint
class VineService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        return when (action) {
            ACTION_START -> {
                startForeground(NOTIF_ID, buildNotification(runningCount = 0))
                START_STICKY
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                START_NOT_STICKY
            }
            else -> START_NOT_STICKY
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null // Unbound service

    override fun onDestroy() {
        super.onDestroy()
        // TODO: stop all running VM instances via VineVMManager
    }

    // ─── Notification ────────────────────────────────────────────────────────

    private fun buildNotification(runningCount: Int): Notification {
        val contentText = when (runningCount) {
            0 -> "No instances running"
            1 -> "1 instance running"
            else -> "$runningCount instances running"
        }

        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_VM_RUNNING)
            .setContentTitle("VineOS")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_info_details) // TODO: replace with vine leaf icon
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.hexadecinull.vineos.START_SERVICE"
        const val ACTION_STOP  = "com.hexadecinull.vineos.STOP_SERVICE"
        private const val NOTIF_ID = 1001
    }
}
