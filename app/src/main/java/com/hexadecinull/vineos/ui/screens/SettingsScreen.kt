package com.hexadecinull.vineos.ui.screens

import android.os.Build
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

// ─── Settings Screen ──────────────────────────────────────────────────────────

data class AppSettings(
    val dynamicColor: Boolean = true,
    val keepScreenOn: Boolean = true,
    val defaultRamMb: Int = 1024,
    val defaultStorageMb: Int = 4096,
    val showTechnicalInfo: Boolean = false,
    val allowRootInstances: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.largeTopAppBarColors(
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
            // ── Appearance ────────────────────────────────────────────────────
            SettingsSection(title = "Appearance") {
                // Dynamic color is only meaningful on Android 12+
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

            // ── VM Defaults ───────────────────────────────────────────────────
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
                SwitchSettingsItem(
                    icon = Icons.Outlined.BrightnessHigh,
                    title = "Keep Screen On",
                    subtitle = "Prevent screen from sleeping while a VM is running",
                    checked = settings.keepScreenOn,
                    onCheckedChange = { onSettingsChange(settings.copy(keepScreenOn = it)) }
                )
            }

            // ── Advanced ──────────────────────────────────────────────────────
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

            // ── About ─────────────────────────────────────────────────────────
            SettingsSection(title = "About") {
                InfoSettingsItem(
                    icon = Icons.Outlined.Info,
                    title = "Version",
                    subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                )
                InfoSettingsItem(
                    icon = Icons.Outlined.Android,
                    title = "Host Android",
                    subtitle = "API ${Build.VERSION.SDK_INT} (Android ${Build.VERSION.RELEASE})"
                )
                InfoSettingsItem(
                    icon = Icons.Outlined.Memory,
                    title = "Host ABI",
                    subtitle = Build.SUPPORTED_ABIS.joinToString(", ")
                )
            }
        }
    }
}

// ─── Settings building blocks ─────────────────────────────────────────────────

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
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
fun SwitchSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
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
fun InfoSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
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
