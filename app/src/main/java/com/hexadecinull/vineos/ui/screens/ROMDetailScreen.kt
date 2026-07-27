package com.hexadecinull.vineos.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hexadecinull.vineos.data.models.AbiCompat
import com.hexadecinull.vineos.data.models.ROMDownloadState
import com.hexadecinull.vineos.ui.viewmodel.ROMDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ROMDetailScreen(
    romId: String,
    onBack: () -> Unit,
    onCreateInstance: (String) -> Unit,
    modifier: Modifier = Modifier,
    vm: ROMDetailViewModel = hiltViewModel(),
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val hostAbis = remember { Build.SUPPORTED_ABIS.toList() }
    LaunchedEffect(romId) { vm.load(romId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.rom?.displayName ?: "ROM") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        val rom = uiState.rom
        if (rom == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(rom.description, style = MaterialTheme.typography.bodyLarge)

            DetailRow("Android version", rom.androidVersion)
            DetailRow("API level", rom.apiLevel.toString())
            DetailRow("Size", formatBytes(rom.sizeBytes))
            DetailRow("32-bit app support", if (rom.has32BitSupport) "Yes" else "No")
            DetailRow("Released", rom.releaseDate)

            Column {
                Text("Guest ABIs on this device", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                rom.supportedAbis.forEach { abi ->
                    val runMode = AbiCompat.hostCanRun(abi, hostAbis)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(abi, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            runMode.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = when (runMode) {
                                AbiCompat.RunMode.NATIVE -> MaterialTheme.colorScheme.primary
                                AbiCompat.RunMode.QEMU -> MaterialTheme.colorScheme.tertiary
                                AbiCompat.RunMode.UNAVAILABLE -> MaterialTheme.colorScheme.error
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            val unavailable = uiState.runMode == AbiCompat.RunMode.UNAVAILABLE
            when {
                unavailable -> Text(
                    "This ROM isn't compatible with your device's CPU architecture.",
                    color = MaterialTheme.colorScheme.error,
                )
                rom.isDownloaded -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = vm::delete, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Delete")
                    }
                    Button(onClick = { onCreateInstance(rom.id) }, modifier = Modifier.weight(1f)) {
                        Text("Create Instance")
                    }
                }
                uiState.progress?.state == ROMDownloadState.DOWNLOADING -> {
                    val pct = uiState.progress?.progressPercent ?: 0
                    LinearProgressIndicator(
                        progress = { (uiState.progress?.progressFraction ?: 0f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Downloading, $pct%", style = MaterialTheme.typography.labelSmall)
                }
                else -> Button(onClick = vm::download, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Download (${formatBytes(rom.sizeBytes)})")
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.0f MB".format(bytes / 1_048_576.0)
    else -> "$bytes B"
}
