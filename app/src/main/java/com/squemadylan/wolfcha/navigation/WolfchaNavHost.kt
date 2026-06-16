package com.squemadylan.wolfcha.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.squemadylan.wolfcha.data.local.PreferencesDataStore
import com.squemadylan.wolfcha.ui.screens.game.GameScreen
import com.squemadylan.wolfcha.ui.screens.home.HomeScreen
import com.squemadylan.wolfcha.ui.screens.howtoplay.HowToPlayScreen
import com.squemadylan.wolfcha.ui.screens.replay.ReplayDetailScreen
import com.squemadylan.wolfcha.ui.screens.replay.ReplayListScreen
import com.squemadylan.wolfcha.ui.screens.roles.RolesScreen
import com.squemadylan.wolfcha.ui.screens.settings.LlmSettingsScreen
import com.squemadylan.wolfcha.ui.screens.settings.SettingsScreen
import com.squemadylan.wolfcha.ui.screens.settings.TtsSettingsScreen
import com.squemadylan.wolfcha.ui.theme.DarkBackground
import com.squemadylan.wolfcha.ui.theme.DarkSurface
import com.squemadylan.wolfcha.ui.theme.TextPrimary
import com.squemadylan.wolfcha.ui.theme.TextSecondary
import com.squemadylan.wolfcha.ui.theme.WolfchaPrimary
import com.squemadylan.wolfcha.ui.viewmodel.AppViewModel
import com.squemadylan.wolfcha.ui.viewmodel.AppViewModelFactory
import com.squemadylan.wolfcha.ui.viewmodel.GameViewModel
import com.squemadylan.wolfcha.ui.viewmodel.GameViewModelFactory

@Composable
fun WolfchaNavHost(
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val preferencesDataStore = remember { PreferencesDataStore(context.applicationContext) }

    val appViewModel: AppViewModel = viewModel(
        viewModelStoreOwner = activity,
        factory = AppViewModelFactory(context.applicationContext as android.app.Application, preferencesDataStore)
    )
    val gameViewModel: GameViewModel = viewModel(
        viewModelStoreOwner = activity,
        factory = GameViewModelFactory(context.applicationContext as android.app.Application, preferencesDataStore)
    )

    Scaffold(
        bottomBar = {
            BottomNavigationBar(navController)
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = WolfchaDestinations.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(WolfchaDestinations.Home.route) {
                HomeScreen(
                    appViewModel = appViewModel,
                    onStartGame = {
                        gameViewModel.startNewGame(appViewModel.buildGameSettings())
                        navController.navigate(WolfchaDestinations.Game.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToReplays = {
                        navController.navigate(WolfchaDestinations.ReplayList.route)
                    }
                )
            }
            composable(WolfchaDestinations.Game.route) {
                GameScreen(
                    gameViewModel = gameViewModel,
                    appViewModel = appViewModel
                )
            }
            composable(WolfchaDestinations.Roles.route) {
                RolesScreen(
                    onRoleClick = { roleName ->
                        navController.navigate(WolfchaDestinations.RoleDetail.createRoute(roleName))
                    }
                )
            }
            composable(WolfchaDestinations.Settings.route) {
                SettingsScreen(
                    appViewModel = appViewModel,
                    onNavigateToLlmSettings = {
                        navController.navigate(WolfchaDestinations.LlmSettings.route)
                    },
                    onNavigateToTtsSettings = {
                        navController.navigate(WolfchaDestinations.TtsSettings.route)
                    },
                    onNavigateToHowToPlay = {
                        navController.navigate(WolfchaDestinations.HowToPlay.route)
                    }
                )
            }
            composable(WolfchaDestinations.HowToPlay.route) {
                HowToPlayScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(WolfchaDestinations.RoleDetail.route) { backStackEntry ->
                val roleName = backStackEntry.arguments?.getString("roleName") ?: ""
                // Role detail screen placeholder
            }
            composable(WolfchaDestinations.LlmSettings.route) {
                LlmSettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(WolfchaDestinations.TtsSettings.route) {
                TtsSettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            // U6 复盘
            composable(WolfchaDestinations.ReplayList.route) {
                ReplayListScreen(
                    onBack = { navController.popBackStack() },
                    onOpen = { gid -> navController.navigate(WolfchaDestinations.ReplayDetail.createRoute(gid)) }
                )
            }
            composable(WolfchaDestinations.ReplayDetail.route) { backStackEntry ->
                val gameId = backStackEntry.arguments?.getString("gameId") ?: ""
                ReplayDetailScreen(
                    gameId = gameId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun BottomNavigationBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    // Hide bottom bar on sub-pages like LLM settings or how to play
    if (currentRoute == WolfchaDestinations.LlmSettings.route ||
        currentRoute == WolfchaDestinations.TtsSettings.route ||
        currentRoute == WolfchaDestinations.HowToPlay.route ||
        currentRoute == WolfchaDestinations.ReplayList.route ||
        currentRoute?.startsWith("role_detail") == true ||
        currentRoute?.startsWith("replay_detail") == true
    ) {
        return
    }

    NavigationBar(
        containerColor = DarkSurface,
        contentColor = TextPrimary,
        tonalElevation = 0.dp
    ) {
        bottomNavItems.forEach { destination ->
            val selected = currentDestination?.hierarchy?.any {
                it.route == destination.route
            } == true

            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = getIconForDestination(destination),
                        contentDescription = destination.title
                    )
                },
                label = { Text(destination.title) },
                selected = selected,
                onClick = {
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = WolfchaPrimary,
                    selectedTextColor = WolfchaPrimary,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                    indicatorColor = WolfchaPrimary.copy(alpha = 0.2f)
                )
            )
        }
    }
}

private fun getIconForDestination(destination: WolfchaDestinations): ImageVector {
    return when (destination) {
        is WolfchaDestinations.Home -> Icons.Default.Home
        is WolfchaDestinations.Game -> Icons.Default.Star
        is WolfchaDestinations.Roles -> Icons.Default.Info
        is WolfchaDestinations.Settings -> Icons.Default.Settings
        else -> Icons.Default.Home
    }
}
