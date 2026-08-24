package com.hexadecinull.vineos.ui.screens

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hexadecinull.vineos.data.models.AbiCompat
import com.hexadecinull.vineos.data.models.DownloadProgress
import com.hexadecinull.vineos.data.models.ROMDownloadState
import com.hexadecinull.vineos.data.models.ROMImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ROMsScreen(
    roms: List<ROMImage>,
    downloadProgress: Map<String, DownloadProgress>,
    onDownloadROM: (ROMImage) -> Unit,
    onDeleteROM: (ROMImage) -> Unit,
    onROMDetail: (ROMImage) -> Unit,
    onImportRom: (Uri) -> Unit,
    importError: String?,
    onImportErrorShown: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val hostAbis = remember { Build.SUPPORTED_ABIS.toList() }
    val snackbarHostState = remember { SnackbarHostState() }
    val pickRomLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImportRom)
    }

    LaunchedEffect(importError) {
        importError?.let {
            snackbarHostState.showSnackbar(it)
            onImportErrorShown()
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("ROMs") },
                actions = {
                    IconButton(onClick = { pickRomLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Outlined.FileOpen, contentDescription = "Import a .vrom file from storage")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (roms.isEmpty()) {
            ROMsEmptyState(modifier = Modifier.fillMaxSize().padding(innerPadding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = "Available ROMs",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            items(roms, key = { it.id }) { rom ->
                ROMCard(
                    rom = rom,
                    hostAbis = hostAbis,
                    progress = downloadProgress[rom.id],
                    onDownload = { onDownloadROM(rom) },
                    onDelete = { onDeleteROM(rom) },
                    onClick = { onROMDetail(rom) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ROMCard(
    rom: ROMImage,
    hostAbis: List<String>,
    progress: DownloadProgress?,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    val runMode = remember(rom.id, hostAbis) { AbiCompat.romRunMode(rom, hostAbis) }
    val unavailable = runMode == AbiCompat.RunMode.UNAVAILABLE

    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        enabled = !unavailable,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = if (unavailable) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = rom.apiLevel.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (unavailable) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rom.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (unavailable) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        text = "Android ${rom.androidVersion} · API ${rom.apiLevel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rom.supportedAbis.forEach { abi -> ROMFeatureChip(label = abi) }
                        ROMSizeChip(bytes = rom.sizeBytes)
                        if (rom.isLocal) ROMLocalChip()
                        when (runMode) {
                            AbiCompat.RunMode.QEMU        -> ROMQemuChip()
                            AbiCompat.RunMode.UNAVAILABLE -> ROMUnavailableChip()
                            else                          -> {}
                        }
                    }
                }

                when {
                    unavailable -> {}
                    rom.isDownloaded -> {
                        FilledTonalIconButton(onClick = {}) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = "Downloaded",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    progress?.state == ROMDownloadState.DOWNLOADING -> {}
                    else -> {
                        FilledTonalIconButton(onClick = onDownload) {
                            Icon(Icons.Filled.Download, contentDescription = "Download ${rom.displayName}")
                        }
                    }
                }
            }

            if (progress != null && progress.state == ROMDownloadState.DOWNLOADING) {
                Spacer(Modifier.height(10.dp))
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Downloading…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${progress.progressPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress.progressFraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ROMFeatureChip(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun ROMSizeChip(bytes: Long) {
    val label = when {
        bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576L     -> "%.0f MB".format(bytes / 1_048_576.0)
        else                    -> "$bytes B"
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ROMLocalChip() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = "Local",
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun ROMQemuChip() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = "QEMU",
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun ROMUnavailableChip() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = "Incompatible",
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun ROMsEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.Storage,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "No ROMs available",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Check your internet connection, or import a .vrom file from storage using the button above.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
