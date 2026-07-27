package com.hexadecinull.vineos.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hexadecinull.vineos.data.models.VMStatus
import com.hexadecinull.vineos.ui.viewmodel.InstanceDetailViewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstanceDetailScreen(
    instanceId: String,
    onBack: () -> Unit,
    onLaunch: (String) -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    vm: InstanceDetailViewModel = hiltViewModel(),
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    var showDiagnostics by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(instanceId) { vm.load(instanceId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.instance?.name ?: "Instance") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        val instance = uiState.instance
        if (instance == null) {
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(instance.iconEmoji, style = MaterialTheme.typography.displaySmall)
                Column {
                    Text(instance.androidVersionDisplay, style = MaterialTheme.typography.titleMedium)
                    Text(
                        instance.status.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            DetailRow("RAM", "${instance.ramMb} MB")
            DetailRow("Storage", "${instance.storageMb} MB")
            DetailRow("Root access", if (instance.isRooted) "Enabled" else "Disabled")
            DetailRow("Created", DateFormat.getDateTimeInstance().format(Date(instance.createdAt)))
            DetailRow("Last used", DateFormat.getDateTimeInstance().format(Date(instance.lastUsedAt)))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (instance.status == VMStatus.STOPPED || instance.status == VMStatus.ERROR) {
                    Button(onClick = { vm.launch(instance); onLaunch(instance.id) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Launch")
                    }
                } else {
                    OutlinedButton(onClick = { vm.stop(instance) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Stop")
                    }
                }
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    enabled = instance.status == VMStatus.STOPPED,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                }
            }

            TextButton(onClick = {
                showDiagnostics = !showDiagnostics
                if (showDiagnostics) vm.refreshDiagnostics()
            }) {
                Text(if (showDiagnostics) "Hide diagnostics" else "Show diagnostics")
            }

            if (showDiagnostics) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SelectionContainer {
                        Text(
                            uiState.diagnostics.ifBlank { "No diagnostics available. Launch the instance first." },
                            modifier = Modifier.padding(12.dp),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }

    val dialogInstance = uiState.instance
    if (showDeleteConfirm && dialogInstance != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ${dialogInstance.name}?") },
            text = { Text("This removes the instance and its storage. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(dialogInstance)
                    showDeleteConfirm = false
                    onDeleted()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
