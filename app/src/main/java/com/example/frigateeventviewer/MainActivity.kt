package com.example.frigateeventviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.frigateeventviewer.data.preferences.SettingsPreferences
import com.example.frigateeventviewer.data.model.Event
import com.example.frigateeventviewer.ui.screens.DashboardScreen
import com.example.frigateeventviewer.ui.screens.DailyReviewScreen
import com.example.frigateeventviewer.ui.screens.DailyReviewViewModel
import com.example.frigateeventviewer.ui.screens.DailyReviewViewModelFactory
import com.example.frigateeventviewer.ui.screens.EventDetailScreen
import com.example.frigateeventviewer.ui.screens.EventDetailViewModel
import com.example.frigateeventviewer.ui.screens.EventDetailViewModelFactory
import com.example.frigateeventviewer.ui.screens.EventsScreen
import com.example.frigateeventviewer.ui.screens.SharedEventViewModel
import com.example.frigateeventviewer.ui.screens.SettingsScreen
import com.example.frigateeventviewer.ui.screens.MainTabsScreen
import com.example.frigateeventviewer.ui.theme.FrigateEventViewerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FrigateEventViewerTheme {
                val navController = rememberNavController()
                val sharedEventViewModel: SharedEventViewModel = viewModel<SharedEventViewModel>()
                val context = LocalContext.current

                LaunchedEffect(Unit) {
                    val preferences = SettingsPreferences(context.applicationContext)
                    val baseUrl = preferences.getBaseUrlOnce()
                    if (baseUrl != null) {
                        navController.navigate("main_tabs") {
                            popUpTo("settings") { inclusive = true }
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = "settings"
                ) {
                    composable("settings") {
                        val canPop = navController.previousBackStackEntry != null
                        SettingsScreen(
                            onNavigateToDashboard = {
                                navController.navigate("main_tabs") {
                                    popUpTo("settings") { inclusive = true }
                                }
                            },
                            onBack = if (canPop) { { navController.popBackStack() } } else null
                        )
                    }
                    composable("main_tabs") {
                        val dailyReviewViewModel: DailyReviewViewModel =
                            viewModel(factory = DailyReviewViewModelFactory(application))
                        MainTabsScreen(
                            navController = navController,
                            sharedEventViewModel = sharedEventViewModel,
                            dailyReviewViewModel = dailyReviewViewModel
                        )
                    }
                    composable("event_detail") {
                        val selectedEvent by sharedEventViewModel.selectedEvent.collectAsState()
                        val eventDetailViewModel: EventDetailViewModel =
                            viewModel(factory = EventDetailViewModelFactory(application))

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
                            viewModel = eventDetailViewModel
                        )
                    }
                }
            }
        }
    }
}
