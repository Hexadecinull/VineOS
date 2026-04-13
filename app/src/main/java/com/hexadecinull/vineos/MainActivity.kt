package com.hexadecinull.vineos

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hexadecinull.vineos.ui.navigation.Screen
import com.hexadecinull.vineos.ui.navigation.bottomNavItems
import com.hexadecinull.vineos.ui.screens.AppSettings
import com.hexadecinull.vineos.ui.screens.HomeScreen
import com.hexadecinull.vineos.ui.screens.ROMsScreen
import com.hexadecinull.vineos.ui.screens.SettingsScreen
import com.hexadecinull.vineos.ui.screens.VMDisplayScreen
import com.hexadecinull.vineos.ui.theme.VineOSTheme
import com.hexadecinull.vineos.ui.viewmodel.HomeViewModel
import com.hexadecinull.vineos.ui.viewmodel.ROMsViewModel
import com.hexadecinull.vineos.ui.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsVm: SettingsViewModel = hiltViewModel()
            val settings by settingsVm.settings.collectAsStateWithLifecycle()

            LaunchedEffect(settings.keepScreenOn) {
                if (settings.keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            VineOSTheme(dynamicColor = settings.dynamicColor) {
                VineOSApp(
                    settings = settings,
                    onSettingsChange = { settingsVm.update(it) },
                )
            }
        }
    }
}

@Composable
fun VineOSApp(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
) {
    val navController = rememberNavController()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = { VineBottomNavBar(navController) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn() + slideInHorizontally { it / 8 } },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() + slideOutHorizontally { it / 8 } },
        ) {
            composable(Screen.Home.route) {
                val vm: HomeViewModel = hiltViewModel()
                val uiState by vm.uiState.collectAsStateWithLifecycle()
                HomeScreen(
                    instances = uiState.instances,
                    onLaunchInstance = { instance ->
                        vm.launchInstance(instance)
                        navController.navigate(Screen.VMDisplay.createRoute(instance.id))
                    },
                    onStopInstance = vm::stopInstance,
                    onInstanceDetail = { navController.navigate(Screen.InstanceDetail.createRoute(it.id)) },
                    onDeleteInstance = vm::deleteInstance,
                    onCreateInstance = { navController.navigate(Screen.ROMs.route) },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            composable(Screen.ROMs.route) {
                val vm: ROMsViewModel = hiltViewModel()
                val uiState by vm.uiState.collectAsStateWithLifecycle()
                ROMsScreen(
                    roms = uiState.roms,
                    downloadProgress = uiState.downloadProgress,
                    onDownloadROM = vm::download,
                    onDeleteROM = vm::delete,
                    onROMDetail = { navController.navigate(Screen.ROMDetail.createRoute(it.id)) },
                    isLoading = uiState.isLoading,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // VM display — full-screen, no bottom bar, direct to the running guest
            composable(Screen.VMDisplay.route) { back ->
                val id = back.arguments?.getString("instanceId") ?: return@composable
                VMDisplayScreen(
                    instanceId = id,
                    onBack = { navController.popBackStack() },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            composable(Screen.InstanceDetail.route) { back ->
                val id = back.arguments?.getString("instanceId") ?: return@composable
                // InstanceDetailScreen — Phase 2
                androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
                    Text("Instance Detail — $id", modifier = Modifier.padding(16.dp))
                }
            }

            composable(Screen.ROMDetail.route) { back ->
                val id = back.arguments?.getString("romId") ?: return@composable
                androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
                    Text("ROM Detail — $id")
                }
            }
        }
    }
}

@Composable
private fun VineBottomNavBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val current = navBackStackEntry?.destination

    // Hide bottom bar on full-screen VM display
    val showBar = bottomNavItems.any { item ->
        current?.hierarchy?.any { it.route == item.screen.route } == true
    }
    if (!showBar) return

    NavigationBar {
        bottomNavItems.forEach { item ->
            val selected = current?.hierarchy?.any { it.route == item.screen.route } == true
            NavigationBarItem(
                icon = { Icon(if (selected) item.selectedIcon else item.unselectedIcon, item.label) },
                label = { Text(item.label) },
                selected = selected,
                onClick = {
                    navController.navigate(item.screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
    }
}
