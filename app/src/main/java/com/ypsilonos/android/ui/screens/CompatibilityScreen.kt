package com.ypsilonos.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItem
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ypsilonos.android.data.models.BuildProps
import com.ypsilonos.android.data.models.GameMode
import com.ypsilonos.android.data.models.VMInstance
import com.ypsilonos.android.ui.theme.VineGreen80
import com.ypsilonos.android.ui.viewmodel.CompatibilityViewModel
import dagger.hilt.android.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompatibilityScreen(
    instanceId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: CompatibilityViewModel = hiltViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val instance by vm.instance.collectAsStateWithLifecycle()

    // Load the instance once.
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(instanceId) {
        if (!loaded) {
            vm.loadById(instanceId)
            loaded = true
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(instance?.name ?: "Compatibility") },
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            if (instance == null) {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            // Instance header card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = instance.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = instance.androidVersionDisplay,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Game mode preset chips
            Section(title = "Game / preset mode") {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(vm.gameModes) { mode ->
                        val selected = instance.gameMode == mode.entryName
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .clickable { vm.applyPreset(mode) },
                        ) {
                            Card(
                                modifier = Modifier.fillMaxSize(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected) VineGreen80 else MaterialTheme.colorScheme.surfaceContainer,
                                ),
                                shape = RoundedCornerShape(12.dp),
                                onClick = { vm.applyPreset(mode) },
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(
                                        imageVector = gameModeIcon(mode),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = mode.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Low-end devices: use 'Low-end' (512 MB RAM, 0.6× render scale) for best performance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Device info
            Section(title = "Host device") {
                InfoRow(icon = Icons.Default.Memory, title = "Available RAM", value = "${state.availableRamMb} MB")
                InfoRow(icon = Icons.Default.Fingerprint, title = "Build fingerprint", value = state.deviceFingerprint.take(60).let { if (it.isNotEmpty()) it else "—" })
            }

            // RAM guard warning
            state.ramGuardWarning?.let { warning ->
                Section(title = "⚠️ Memory warning") {
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // Runtime toggles
            Section(title = "Runtime compatibility") {
                ListItem(
                    headlineContent = { Text("Hide root from games") },
                    supportingContent = {
                        Text(
                            "Renames su/magisk artifacts inside the guest so SafetyNet and game root-detection see a clean device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = instance.rootHidden,
                            onCheckedChange = { vm.setRootHidden(it) },
                        )
                    },
                )
                ListItem(
                    headlineContent = { Text("SELinux permissive") },
                    supportingContent = {
                        Text(
                            "Disable SELinux inside the guest. Some games and apps require this to launch.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = !instance.selinuxEnforcing,
                            onCheckedChange = { vm.setSELinux(!it) },
                        )
                    },
                )
                ListItem(
                    headlineContent = { Text("Network isolation") },
                    supportingContent = {
                        Text(
                            "Block guest network access. Prevents games/apps from phoning home.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = instance.networkIsolated,
                            onCheckedChange = { vm.setNetworkIsolated(it) },
                        )
                    },
                )
                ListItem(
                    headlineContent = { Text("Auto-suspend on background") },
                    supportingContent = {
                        Text(
                            "Suspend the VM automatically when YpsilonOS is backgrounded. Saves CPU and battery.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = instance.autoSuspend,
                            onCheckedChange = { vm.setAutoSuspend(it) },
                        )
                    },
                )
            }

            // Render scale
            Section(title = "Display render scale") {
                val scale = instance.renderScale
                SliderSettingsItem(
                    icon = Icons.Default.AspectRatio,
                    title = "Render scale",
                    subtitle = "${(scale * 100).toInt()}% (${scale}x)",
                    value = scale,
                    valueRange = 0.25f..1.0f,
                    steps = 4, // 0.25, 0.5, 0.75, 1.0
                    onValueChange = { vm.setRenderScale(it) },
                )
                Text(
                    text = "Lower values reduce fill-rate and help games run on low-end devices.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Build prop spoofing
            Section(title = "Build.prop spoof (Play Integrity / SafetyNet)") {
                val props = BuildProps.toMap(instance.buildPropsJson)
                if (props.isEmpty()) {
                    Text(
                        "No custom props set. Tap + to add a key/value override (e.g. ro.product.model).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(props.entries.toList()) { (key, value) ->
                            ListItem(
                                headlineContent = { Text(key) },
                                supportingContent = { Text(value, style = MaterialTheme.typography.bodySmall) },
                                trailingContent = {
                                    IconButton(onClick = { vm.setBuildProp(key, "") }) {
                                        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null)
                                    }
                                },
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    var key by remember { mutableStateOf("") }
                    var value by remember { mutableStateOf("") }
                    BasicTextField(
                        value = key,
                        onValueChange = { key = it },
                        placeholder = { Text("ro.product.model") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    BasicTextField(
                        value = value,
                        onValueChange = { value = it },
                        placeholder = { Text("Pixel 7") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    IconButton(
                        onClick = {
                            if (key.isNotBlank() && value.isNotBlank()) {
                                vm.setBuildProp(key, value)
                            }
                        },
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    }
                }
                TextButton(
                    onClick = { vm.clearBuildProps() },
                    colors = TextButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Clear all props")
                }
            }

            // Backup / storage cleanup
            Section(title = "Instance maintenance") {
                val coroutineScope = rememberCoroutineScope()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val path = vm.backupInstance(LocalContext.current)
                                if (path != null) {
                                    _uiState.update { it.copy(message = "Backup saved to $path") }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Backup")
                    }
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                val freed = vm.cleanStorage(LocalContext.current)
                                if (freed > 0) {
                                    _uiState.update { it.copy(message = "Cleaned ${freed / 1024} KB") }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Clean storage")
                    }
                }
                Text(
                    text = "Backups are saved to Documents/YpsilonOS/backups. Storage cleanup removes temp/cache files only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Apply button
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { vm.applyToInstance() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text("Apply to instance")
                }
            }
            if (state.message != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.message!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VineGreen80,
                )
            }
            if (state.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.error!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        content()
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, value: String) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value, style = MaterialTheme.typography.bodySmall) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
    )
}

@Composable
private fun SliderSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun gameModeIcon(mode: GameMode): ImageVector {
    return when (mode) {
        GameMode.LOW_END -> Icons.Default.BatterySaver
        GameMode.BALANCED -> Icons.Default.Speed
        GameMode.HIGH_END -> Icons.Default.RocketLaunch
        GameMode.POKEMON_GO -> Icons.Default.GameController
    }
}
