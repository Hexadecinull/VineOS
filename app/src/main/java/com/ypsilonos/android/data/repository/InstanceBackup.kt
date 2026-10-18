package com.ypsilonos.android.data.repository

import android.content.Context
import android.os.Environment
import com.ypsilonos.android.data.models.VMInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToString
import kotlinx.serialization.serializers.Map
import kotlinx.serialization.serializers.Serializable
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale

// InstanceBackup exports / imports instance configuration so users can move
// instances between devices or back them up before risky changes (e.g. before
// enabling root-hide or build.prop spoofing for Pokémon GO).
object InstanceBackup {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class InstanceBundle(
        val instance: VMInstance,
        val exportedAt: Long = System.currentTimeMillis(),
        val ypsilonosVersion: String = "0.3.0",
    )

    // Returns the backup directory: <external>/Documents/YpsilonOS/backups
    fun backupDir(context: Context): File {
        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "YpsilonOS/backups",
        )
        dir.mkdirs()
        return dir
    }

    // Export a single instance to a .json bundle file.
    suspend fun exportInstance(
        context: Context,
        instance: VMInstance,
    ): File = withContext(Dispatchers.IO) {
        val dir = backupDir(context)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(java.util.Date())
        val safeName = instance.name.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val file = File(dir, "instance_$safeName\_$timestamp.json")
        val bundle = InstanceBundle(instance)
        val jsonStr = json.encodeToString(InstanceBundle.serializer(), bundle)
        FileOutputStream(file).use { fos ->
            fos.write(jsonStr.toByteArray(StandardCharsets.UTF_8))
        }
        file
    }

    // List all backup files, newest first.
    fun listBackups(context: Context): List<File> {
        val dir = backupDir(context)
        return dir.listFiles()
            ?.filter { it.extension == "json" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    // Delete a backup file.
    fun deleteBackup(file: File): Boolean = file.delete()

    // Returns the backup directory as a user-readable path.
    fun backupPath(context: Context): String = backupDir(context).absolutePath
}
