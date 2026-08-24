package com.hexadecinull.vineos.ui.screens

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.hexadecinull.vineos.BuildConfig
import com.hexadecinull.vineos.shizuku.ShizukuStatus
import com.hexadecinull.vineos.ui.viewmodel.ProbeState

data class AppSettings(
    val dynamicColor: Boolean = true,
    val keepScreenOn: Boolean = true,
    val defaultRamMb: Int = 1024,
    val defaultStorageMb: Int = 4096,
    val defaultCpuCores: Int = 0,
    val showTechnicalInfo: Boolean = false,
    val allowRootInstances: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onAboutClick: () -> Unit,
    shizukuStatus: ShizukuStatus,
    onRequestShizukuPermission: () -> Unit,
    probeState: ProbeState,
    onRunProbe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            SettingsSection(title = "Appearance") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SwitchSettingsItem(
                        icon = Icons.Outlined.Palette,
                        title = "Material You",
                        subtitle = "Use wallpaper-based dynamic colors",
                        checked = settings.dynamicColor,
                        onCheckedChange = { onSettingsChange(settings.copy(dynamicColor = it)) }
                    )
                }
            }

            SettingsSection(title = "VM Defaults") {
                SliderSettingsItem(
                    icon = Icons.Outlined.Memory,
                    title = "Default RAM",
                    subtitle = "${settings.defaultRamMb} MB",
                    value = settings.defaultRamMb.toFloat(),
                    valueRange = 512f..4096f,
                    steps = 6,  // 512, 768, 1024, 1536, 2048, 3072, 4096
                    onValueChange = { onSettingsChange(settings.copy(defaultRamMb = it.toInt())) }
                )
                SliderSettingsItem(
                    icon = Icons.Outlined.Storage,
                    title = "Default Storage",
                    subtitle = "${settings.defaultStorageMb} MB",
                    value = settings.defaultStorageMb.toFloat(),
                    valueRange = 2048f..16384f,
                    steps = 6,  // 2048, 4096, 6144, ... 16384
                    onValueChange = { onSettingsChange(settings.copy(defaultStorageMb = it.toInt())) }
                )
                SliderSettingsItem(
                    icon = Icons.Outlined.DeveloperBoard,
                    title = "Default CPU Cores",
                    subtitle = if (settings.defaultCpuCores == 0) "Unlimited" else "${settings.defaultCpuCores} cores",
                    value = settings.defaultCpuCores.toFloat(),
                    valueRange = 0f..8f,
                    steps = 7,
                    onValueChange = { onSettingsChange(settings.copy(defaultCpuCores = it.toInt())) }
                )
                SwitchSettingsItem(
                    icon = Icons.Outlined.BrightnessHigh,
                    title = "Keep Screen On",
                    subtitle = "Prevent screen from sleeping while a VM is running",
                    checked = settings.keepScreenOn,
                    onCheckedChange = { onSettingsChange(settings.copy(keepScreenOn = it)) }
                )
            }

            SettingsSection(title = "Advanced") {
                SwitchSettingsItem(
                    icon = Icons.Outlined.Code,
                    title = "Show Technical Info",
                    subtitle = "Display kernel version, ABI, namespace info on instance cards",
                    checked = settings.showTechnicalInfo,
                    onCheckedChange = { onSettingsChange(settings.copy(showTechnicalInfo = it)) }
                )
                SwitchSettingsItem(
                    icon = Icons.Outlined.AdminPanelSettings,
                    title = "Allow Rooted Instances",
                    subtitle = "Enable Magisk / root in VM instances (requires host root)",
                    checked = settings.allowRootInstances,
                    onCheckedChange = { onSettingsChange(settings.copy(allowRootInstances = it)) }
                )
            }

            SettingsSection(title = "Shizuku") {
                ShizukuSettingsContent(
                    status = shizukuStatus,
                    onRequestPermission = onRequestShizukuPermission,
                    probeState = probeState,
                    onRunProbe = onRunProbe,
                )
            }

            SettingsSection(title = "About") {
                NavigateSettingsItem(
                    icon = Icons.Outlined.Info,
                    title = "About VineOS",
                    subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    onClick = onAboutClick,
                )
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    }
}

@Composable
fun SwitchSettingsItem(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
    )
}

@Composable
fun SliderSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun InfoSettingsItem(icon: ImageVector, title: String, subtitle: String) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    )
}

@Composable
fun NavigateSettingsItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ShizukuSettingsContent(status: ShizukuStatus, onRequestPermission: () -> Unit, probeState: ProbeState, onRunProbe: () -> Unit) {
    val (statusText, statusColor) = when {
        !status.isInstalled -> "Not installed" to MaterialTheme.colorScheme.onSurfaceVariant
        !status.isRunning -> "Installed, not running" to MaterialTheme.colorScheme.onSurfaceVariant
        !status.isGranted -> "Running, permission not granted" to MaterialTheme.colorScheme.error
        else -> "Connected (uid ${status.serverUid})" to MaterialTheme.colorScheme.primary
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Outlined.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(modifier = Modifier.weight(1f)) {
                Text("Shizuku", style = MaterialTheme.typography.bodyLarge)
                Text(statusText, style = MaterialTheme.typography.bodySmall, color = statusColor)
            }
        }

        Spacer(Modifier.height(8.dp))

        when {
            !status.isInstalled -> Text(
                "Install Shizuku from shizuku.rikka.app, then enable it via Wireless debugging or root",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            !status.isRunning -> Text(
                "Start Shizuku from its own app (Developer options → Wireless debugging, or a root shell)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            !status.isGranted -> FilledTonalButton(onClick = onRequestPermission) { Text("Grant permission") }
            else -> {
                FilledTonalButton(onClick = onRunProbe, enabled = probeState !is ProbeState.Running) {
                    Text(if (probeState is ProbeState.Running) "Testing…" else "Test namespace access")
                }
                when (probeState) {
                    is ProbeState.Done -> {
                        val r = probeState.result
                        val summary = if (r.unshareOk && r.mountOk) {
                            "This privilege level can set up VM containers without root"
                        } else {
                            "Blocked: unshare ${if (r.unshareOk) "ok" else "errno ${r.unshareErrno}"}, " +
                                "mount ${if (r.mountOk) "ok" else "errno ${r.mountErrno}"}"
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (r.unshareOk && r.mountOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                    is ProbeState.Failed -> {
                        Spacer(Modifier.height(6.dp))
                        Text(probeState.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    else -> {}
                }
            }
        }
    }
}
