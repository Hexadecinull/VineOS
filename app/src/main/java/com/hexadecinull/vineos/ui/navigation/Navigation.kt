package com.hexadecinull.vineos.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.ui.graphics.vector.ImageVector

// ─── Destinations ─────────────────────────────────────────────────────────────

sealed class Screen(val route: String) {
    // Bottom nav destinations
    data object Home : Screen("home")
    data object ROMs : Screen("roms")
    data object Settings : Screen("settings")

    // Detail screens (not in bottom nav)
    data object InstanceDetail : Screen("instance/{instanceId}") {
        fun createRoute(instanceId: String) = "instance/$instanceId"
    }
    data object ROMDetail : Screen("rom/{romId}") {
        fun createRoute(romId: String) = "rom/$romId"
    }
    data object CreateInstance : Screen("create_instance/{romId}") {
        fun createRoute(romId: String) = "create_instance/$romId"
    }
    data object VMDisplay : Screen("vm_display/{instanceId}") {
        fun createRoute(instanceId: String) = "vm_display/$instanceId"
    }
}

// ─── Bottom Nav Items ─────────────────────────────────────────────────────────

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem(
        screen = Screen.Home,
        label = "Instances",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
    ),
    BottomNavItem(
        screen = Screen.ROMs,
        label = "ROMs",
        selectedIcon = Icons.Filled.Storage,
        unselectedIcon = Icons.Outlined.Storage,
    ),
    BottomNavItem(
        screen = Screen.Settings,
        label = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    ),
)
