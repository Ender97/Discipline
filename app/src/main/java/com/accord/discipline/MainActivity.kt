package com.accord.discipline

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.accord.discipline.ui.theme.DisciplineTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
                onResult = { isGranted -> /* Handle permission result if needed */ }
            )

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            val viewModel: MainViewModel = hiltViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()

            val darkTheme = when (state.darkModeConfig) {
                "light" -> false
                "dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            DisciplineTheme(
                darkTheme = darkTheme,
                dynamicColor = state.dynamicColor,
                accentColor = state.accentColor
            ) {
                val navController = rememberNavController()

                // Navigation logic
                if (state.isLoading) {
                    LoadingScreen()
                } else if (state.isEditingServer || state.serverUrl.isBlank()) {
                    ServerSetupScreen(
                        currentUrl = state.serverUrl,
                        onSave = { viewModel.setServerUrl(it) },
                        onSkip = { viewModel.enterOfflineMode() },
                        onCancel = if (state.serverUrl.isNotBlank()) { { viewModel.cancelServerSetup() } } else null
                    )
                } else if (!state.isLoggedIn) {
                    LoginScreen(
                        viewModel = viewModel,
                        state = state,
                        onChangeServer = { viewModel.openServerSetup() },
                        onSkip = { viewModel.enterOfflineMode() }
                    )
                } else if (state.isServerUnreachable && !state.isOfflineMode) {
                    ServerUnreachableScreen(
                        serverUrl = state.serverUrl,
                        error = state.error,
                        isLoading = state.isLoading,
                        onRetry = { viewModel.retryConnection() },
                        onOpenTailscale = { viewModel.openTailscale() },
                        onChangeServer = { viewModel.openServerSetup() },
                        onUseOffline = { viewModel.enterOfflineMode() }
                    )
                } else {
                    Scaffold(
                        bottomBar = {
                            Column {
                                if (state.isOfflineMode) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.CloudOff, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "Solo Mode (Offline) • All data saved on phone",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                                NavigationBar(
                                    tonalElevation = 4.dp,
                                    containerColor = MaterialTheme.colorScheme.surface
                                ) {
                                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                                    val currentRoute = navBackStackEntry?.destination?.route
                                    
                                    val navItems = listOf(
                                        Triple("habits", "Habits", Icons.Default.CheckBox),
                                        Triple("rewards", "Rewards", Icons.Default.Redeem),
                                        Triple("punishments", "Punishment", Icons.Default.Warning),
                                        Triple("stats", "Stats", Icons.Default.BarChart),
                                        Triple("profile", "Profile", Icons.Default.Person)
                                    )

                                    navItems.forEach { (route, label, icon) ->
                                        val isSelected = currentRoute == route
                                        NavigationBarItem(
                                            selected = isSelected,
                                            onClick = { 
                                                if (currentRoute != route) {
                                                    navController.navigate(route) {
                                                        popUpTo("habits") { saveState = true }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                            },
                                            icon = { 
                                                Icon(
                                                    imageVector = icon, 
                                                    contentDescription = null,
                                                    modifier = Modifier.size(if (isSelected) 26.dp else 22.dp)
                                                ) 
                                            },
                                            label = { 
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.ExtraBold else androidx.compose.ui.text.font.FontWeight.Medium
                                                ) 
                                            },
                                            alwaysShowLabel = true,
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    ) { padding ->
                        NavHost(navController, startDestination = "habits", modifier = Modifier.padding(padding)) {
                            composable("habits") { HabitsScreen(viewModel, state) }
                            composable("rewards") { RewardsScreen(viewModel, state) }
                            composable("punishments") { PunishmentsScreen(viewModel, state) }
                            composable("stats") { StatsScreen(viewModel, state) }
                            composable("profile") { 
                                ProfileScreen(
                                    viewModel = viewModel, 
                                    state = state, 
                                    onChangeServer = { 
                                        viewModel.openServerSetup()
                                    }
                                ) 
                            }
                        }
                    }
                }
                
                // Show notifications
                state.notification?.let { msg ->
                    LaunchedEffect(msg) {
                        // In a real app, use SnackbarHost. For now, just Log or Toast
                        viewModel.clearNotification()
                    }
                }
            }
        }
    }
}