package io.vault.mobile.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

import io.vault.mobile.data.local.PreferenceManager
import io.vault.mobile.ui.screens.*
import io.vault.mobile.ui.theme.NeonBlue
import io.vault.mobile.ui.viewmodel.VaultViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object VaultList : Screen("vault_list", "Vault", Icons.Default.Shield)
    object MediaVault : Screen("media_vault", "Media", Icons.Default.PhotoLibrary)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object AddPassword : Screen("add_password", "Add", Icons.Default.Add)
    object Backup : Screen("backup", "Backup", Icons.Default.CloudUpload)
}

@Composable
fun VaultNavigation() {
    val navController = rememberNavController()
    val viewModel: VaultViewModel = hiltViewModel()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val bottomNavItems = listOf(
        Screen.VaultList,
        Screen.MediaVault,
        Screen.Settings
    )

    Scaffold(
        bottomBar = {
            if (bottomNavItems.any { it.route == currentDestination?.route }) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = NeonBlue
                ) {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NeonBlue,
                                selectedTextColor = NeonBlue,
                                indicatorColor = Color.Transparent,
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.VaultList.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.VaultList.route) {
                VaultListScreen(
                    viewModel = viewModel,
                    onAddClick = { 
                        viewModel.clearSelection()
                        navController.navigate(Screen.AddPassword.route) 
                    },
                    onEntryClick = { entry ->
                        viewModel.selectEntry(entry)
                        navController.navigate(Screen.AddPassword.route)
                    }
                )
            }
            composable(Screen.AddPassword.route) {
                AddPasswordScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.MediaVault.route) {
                MediaVaultScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToBackup = { navController.navigate(Screen.Backup.route) }
                )
            }
            composable(Screen.Backup.route) {
                BackupRestoreScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
