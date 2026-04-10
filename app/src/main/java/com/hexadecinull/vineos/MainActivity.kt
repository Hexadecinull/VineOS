package com.hexadecinull.vineos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hexadecinull.vineos.data.models.*
import com.hexadecinull.vineos.ui.navigation.Screen
import com.hexadecinull.vineos.ui.navigation.bottomNavItems
import com.hexadecinull.vineos.ui.screens.*
import com.hexadecinull.vineos.ui.theme.VineOSTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Enable edge-to-edge display — content draws behind system bars.
        // Theme.kt's SideEffect handles status/nav bar color.
        enableEdgeToEdge()
        setContent {
            // TODO: inject settings from DataStore via ViewModel
            var settings by remember { mutableStateOf(AppSettings()) }
            VineOSTheme(dynamicColor = settings.dynamicColor) {
                VineOSApp(
                    settings = settings,
                    onSettingsChange = { settings = it }
                )
            }
        }
    }
}

// ─── App Shell ────────────────────────────────────────────────────────────────

@Composable
fun VineOSApp(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
) {
    val navController = rememberNavController()

    // Placeholder state — will be replaced by ViewModel / Repository in next phase
    val instances = remember { mutableStateListOf<VMInstance>() }
    val roms = remember { mutableStateListOf<ROMImage>() }
    val downloadProgress = remember { mutableStateMapOf<String, DownloadProgress>() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            VineBottomNavBar(
                navController = navController,
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn() + slideInHorizontally() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() + slideOutHorizontally() },
        ) {
            // ── Bottom nav destinations ──────────────────────────────────────
            composable(Screen.Home.route) {
                HomeScreen(
                    instances = instances,
                    onLaunchInstance = { /* TODO: VineVMManager.start(it) */ },
                    onStopInstance = { /* TODO: VineVMManager.stop(it) */ },
                    onInstanceDetail = { navController.navigate(Screen.InstanceDetail.createRoute(it.id)) },
                    onDeleteInstance = { instances.remove(it) },
                    onCreateInstance = { navController.navigate(Screen.ROMs.route) },
                    modifier = Modifier.fillMaxSize()
                )
            }

            composable(Screen.ROMs.route) {
                ROMsScreen(
                    roms = roms,
                    downloadProgress = downloadProgress,
                    onDownloadROM = { /* TODO: ROMRepository.download(it) */ },
                    onDeleteROM = { /* TODO: ROMRepository.delete(it) */ },
                    onROMDetail = { navController.navigate(Screen.ROMDetail.createRoute(it.id)) },
                    isLoading = false,
                    modifier = Modifier.fillMaxSize()
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // ── Detail destinations ──────────────────────────────────────────
            composable(Screen.InstanceDetail.route) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                // TODO: InstanceDetailScreen(instanceId = instanceId, ...)
                // Placeholder
                Surface(Modifier.fillMaxSize()) {
                    Text("Instance Detail: $instanceId", modifier = Modifier.padding(
                        androidx.compose.foundation.layout.PaddingValues(16.dp)
                    ))
                }
            }

            composable(Screen.ROMDetail.route) { backStackEntry ->
                val romId = backStackEntry.arguments?.getString("romId") ?: return@composable
                // TODO: ROMDetailScreen(romId = romId, ...)
                Surface(Modifier.fillMaxSize()) {
                    Text("ROM Detail: $romId")
                }
            }

            composable(Screen.VMDisplay.route) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                // TODO: VMDisplayScreen — the main VM framebuffer view
                Surface(Modifier.fillMaxSize()) {
                    Text("VM Display: $instanceId")
                }
            }
        }
    }
}

// ─── Bottom Navigation Bar ────────────────────────────────────────────────────

@Composable
private fun VineBottomNavBar(navController: androidx.navigation.NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Only show bottom nav on top-level destinations
    val showBottomBar = bottomNavItems.any { item ->
        currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
    }

    if (!showBottomBar) return

    NavigationBar {
        bottomNavItems.forEach { item ->
            val selected = currentDestination?.hierarchy?.any {
                it.route == item.screen.route
            } == true

            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label
                    )
                },
                label = { Text(item.label) },
                selected = selected,
                onClick = {
                    navController.navigate(item.screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}
