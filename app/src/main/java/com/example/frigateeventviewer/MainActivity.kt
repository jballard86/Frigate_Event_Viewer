package com.example.frigateeventviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.frigateeventviewer.data.preferences.SettingsPreferences
import com.example.frigateeventviewer.ui.screens.DashboardScreen
import com.example.frigateeventviewer.ui.screens.EventDetailScreen
import com.example.frigateeventviewer.ui.screens.EventDetailViewModel
import com.example.frigateeventviewer.ui.screens.EventDetailViewModelFactory
import com.example.frigateeventviewer.ui.screens.EventsScreen
import com.example.frigateeventviewer.ui.screens.SharedEventViewModel
import com.example.frigateeventviewer.ui.screens.SettingsScreen
import com.example.frigateeventviewer.ui.theme.FrigateEventViewerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FrigateEventViewerTheme {
                val navController = rememberNavController()
                val sharedEventViewModel: SharedEventViewModel = viewModel()
                val context = LocalContext.current
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                val showBottomBar = currentRoute == "dashboard" || currentRoute == "events"

                LaunchedEffect(Unit) {
                    val preferences = SettingsPreferences(context.applicationContext)
                    val baseUrl = preferences.getBaseUrlOnce()
                    if (baseUrl != null) {
                        navController.navigate("dashboard") {
                            popUpTo("settings") { inclusive = true }
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = currentRoute == "dashboard",
                                    onClick = {
                                        navController.navigate("dashboard") {
                                            launchSingleTop = true
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                                    label = { Text("Dashboard") }
                                )
                                NavigationBarItem(
                                    selected = currentRoute == "events",
                                    onClick = {
                                        navController.navigate("events") {
                                            launchSingleTop = true
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Events") },
                                    label = { Text("Events") }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "settings",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("settings") {
                            SettingsScreen(
                                onNavigateToDashboard = {
                                    navController.navigate("dashboard") {
                                        popUpTo("settings") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("dashboard") {
                            DashboardScreen()
                        }
                        composable("events") {
                            EventsScreen(
                                onEventClick = { event ->
                                    sharedEventViewModel.selectEvent(event)
                                    navController.navigate("event_detail")
                                }
                            )
                        }
                        composable("event_detail") {
                            val selectedEvent by sharedEventViewModel.selectedEvent.collectAsState()
                            BackHandler {
                                sharedEventViewModel.selectEvent(null)
                                navController.popBackStack()
                            }
                            EventDetailScreen(
                                selectedEvent = selectedEvent,
                                onBack = {
                                    sharedEventViewModel.selectEvent(null)
                                    navController.popBackStack()
                                },
                                viewModel = viewModel<EventDetailViewModel>(
                                    factory = EventDetailViewModelFactory(
                                        LocalContext.current.applicationContext as android.app.Application
                                    )
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
