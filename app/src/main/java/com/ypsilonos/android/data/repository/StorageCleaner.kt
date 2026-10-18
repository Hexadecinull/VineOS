package com.ypsilonos.android.data.repository

import android.content.Context
import com.ypsilonos.android.data.models.VMInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// StorageCleaner removes per-instance temporary files (logcat, temp ROM
// downloads, extracted QEMU binaries, framebuffer dumps) to free space on
// low-end devices with limited internal storage.
object StorageCleaner {

    // Directories considered safe to clean inside an instance directory.
    private val cleanableDirs = listOf("cache", "tmp", "logs", "download", "backup")

    // Returns bytes freed.
    suspend fun cleanInstance(context: Context, instance: VMInstance): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        val root = File(instance.storagePath)
        if (root.exists()) {
            cleanableDirs.forEach { name ->
                val d = File(root, name)
                freed += deleteTree(d)
            }
            // Also clean loose .log / .tmp files at the instance root.
            root.listFiles()?.forEach { f ->
                if (f.extension == "log" || f.extension == "tmp") {
                    freed += if (f.delete()) f.length() else 0L
                }
            }
        }
        freed
    }

    // Cleans all instances' temp files.
    suspend fun cleanAllInstances(context: Context, instances: List<VMInstance>): Long =
        withContext(Dispatchers.IO) {
            instances.fold(0L) { acc, instance ->
                acc + cleanInstance(context, instance)
            }
        }

    private fun deleteTree(dir: File): Long {
        if (!dir.exists()) return 0L
        var size = 0L
        dir.listFiles()?.forEach { f ->
            if (f.isDirectory) {
                size += deleteTree(f)
            } else {
                size += f.length()
                f.delete()
            }
        }
        dir.delete()
        return size
    }
}
