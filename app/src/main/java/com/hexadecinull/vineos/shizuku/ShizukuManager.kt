package com.hexadecinull.vineos.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.hexadecinull.vineos.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import rikka.shizuku.Shizuku

data class ShizukuStatus(val isInstalled: Boolean, val isRunning: Boolean, val isGranted: Boolean, val serverUid: Int)

data class NamespaceProbeResult(val unshareOk: Boolean, val unshareErrno: Int, val mountOk: Boolean, val mountErrno: Int, val uid: Int)

@Singleton
class ShizukuManager @Inject constructor(@ApplicationContext private val context: Context) {
    private val _status = MutableStateFlow(currentStatus())
    val status: Flow<ShizukuStatus> = _status.asStateFlow()

    private val binderListener = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDeadListener = Shizuku.OnBinderDeadListener { refresh() }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    init {
        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)
    }

    fun refresh() {
        _status.value = currentStatus()
    }

    fun requestPermission(requestCode: Int = REQUEST_CODE) {
        if (Shizuku.isPreV11()) return
        Shizuku.requestPermission(requestCode)
    }

    // Runs the native namespace probe in the shell/root process Shizuku hosts, to answer directly whether this device's ADB-shell privilege level can do what the container engine needs, see docs/NOROOT_RESEARCH.md
    fun probeNamespaceSupport(): Flow<Result<NamespaceProbeResult>> = callbackFlow {
        if (!_status.value.isRunning || !_status.value.isGranted) {
            trySend(Result.failure(IllegalStateException("Shizuku is not connected or not granted")))
            close()
            return@callbackFlow
        }

        val args = Shizuku.UserServiceArgs(ComponentName(BuildConfig.APPLICATION_ID, VineShizukuService::class.java.name))
            .daemon(false)
            .processNameSuffix("shizuku_probe")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                val result = runCatching {
                    val service = IVineShizukuService.Stub.asInterface(binder)
                    parseProbeResult(service.probeNamespaces())
                }
                trySend(result)
                Shizuku.unbindUserService(args, this, true)
                close()
            }

            override fun onServiceDisconnected(name: ComponentName) {}
        }

        runCatching { Shizuku.bindUserService(args, connection) }
            .onFailure {
                trySend(Result.failure(it))
                close()
            }

        awaitClose { }
    }

    private fun parseProbeResult(raw: String): NamespaceProbeResult {
        val fields = raw.split(";").associate {
            val (k, v) = it.split("=")
            k to v
        }
        return NamespaceProbeResult(
            unshareOk = fields["unshare_ok"] == "1",
            unshareErrno = fields["unshare_errno"]?.toIntOrNull() ?: 0,
            mountOk = fields["mount_ok"] == "1",
            mountErrno = fields["mount_errno"]?.toIntOrNull() ?: 0,
            uid = fields["uid"]?.toIntOrNull() ?: -1,
        )
    }

    private fun currentStatus(): ShizukuStatus {
        val isInstalled = isShizukuAppInstalled()
        val isRunning = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val isGranted = isRunning &&
            runCatching { Shizuku.checkSelfPermission() }
                .getOrDefault(PackageManager.PERMISSION_DENIED) == PackageManager.PERMISSION_GRANTED
        val uid = if (isRunning) runCatching { Shizuku.getUid() }.getOrDefault(-1) else -1
        return ShizukuStatus(isInstalled, isRunning, isGranted, uid)
    }

    private fun isShizukuAppInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
        true
    }.getOrDefault(false)

    companion object {
        const val REQUEST_CODE = 9000
    }
}
