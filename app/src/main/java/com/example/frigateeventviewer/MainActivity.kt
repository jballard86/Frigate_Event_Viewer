package com.example.frigateeventviewer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.frigateeventviewer.data.api.ApiClient
import com.example.frigateeventviewer.data.preferences.SettingsPreferences
import com.example.frigateeventviewer.data.push.FcmTokenManager
import com.example.frigateeventviewer.data.push.PushConstants
import com.example.frigateeventviewer.ui.screens.DailyReviewViewModel
import com.example.frigateeventviewer.ui.screens.DailyReviewViewModelFactory
import com.example.frigateeventviewer.ui.screens.DeepLinkViewModel
import com.example.frigateeventviewer.ui.screens.EventDetailScreen
import com.example.frigateeventviewer.ui.screens.EventDetailViewModel
import com.example.frigateeventviewer.ui.screens.EventDetailViewModelFactory
import com.example.frigateeventviewer.ui.screens.EventNotFoundScreen
import com.example.frigateeventviewer.ui.screens.SharedEventViewModel
import com.example.frigateeventviewer.ui.screens.SettingsScreen
import com.example.frigateeventviewer.ui.screens.MainTabsScreen
import com.example.frigateeventviewer.ui.screens.SnoozeScreen
import com.example.frigateeventviewer.ui.theme.FrigateEventViewerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkViewModel.setFromIntent(intent.data)
    }

    private val deepLinkViewModel: DeepLinkViewModel by lazy {
        androidx.lifecycle.ViewModelProvider(this)[DeepLinkViewModel::class.java]
    }

    override fun onResume() {
        super.onResume()
        updateUnreadBadge()
    }

    /**
     * Fetches GET /api/events/unread_count and updates the app icon badge via a silent
     * notification with [NotificationCompat.setNumber]. When count is 0 the notification is cancelled.
     */
    private fun updateUnreadBadge() {
        val baseUrl = SettingsPreferences(applicationContext).getBaseUrlOnce()
        if (baseUrl.isNullOrBlank()) return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.createService(baseUrl).getUnreadCount()
                val count = response.unread_count
                withContext(Dispatchers.Main) {
                    val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    if (count <= 0) {
                        notificationManager.cancel(PushConstants.BADGE_NOTIFICATION_ID)
                    } else {
                        val builder = NotificationCompat.Builder(this@MainActivity, PushConstants.CHANNEL_ID_BADGE)
                            .setSmallIcon(R.mipmap.ic_launcher)
                            .setContentTitle("Unreviewed events")
                            .setContentText(if (count == 1) "1 event" else "$count events")
                            .setNumber(count)
                            .setPriority(NotificationCompat.PRIORITY_MIN)
                            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                            .setSilent(true)
                        notificationManager.notify(PushConstants.BADGE_NOTIFICATION_ID, builder.build())
                    }
                }
            } catch (_: Exception) {
                // Ignore; badge will update on next resume
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FrigateEventViewerTheme {
                val navController = rememberNavController()
                val sharedEventViewModel: SharedEventViewModel = viewModel<SharedEventViewModel>()
                val context = LocalContext.current
                val resolveTrigger by deepLinkViewModel.resolveTrigger.collectAsState(initial = 0)
                val pendingCeId by deepLinkViewModel.pendingCeId.collectAsState()

                // Wait one frame so NavHost is composed before any navigate().
                LaunchedEffect(Unit) {
                    delay(50)
                    val uri = intent?.data
                    if (DeepLinkViewModel.parseDeepLinkCeId(uri) != null) {
                        deepLinkViewModel.setFromIntent(uri)
                    } else {
                        val preferences = SettingsPreferences(context.applicationContext)
                        val baseUrl = preferences.getBaseUrlOnce()
                        if (baseUrl != null) {
                            navController.navigate("main_tabs") {
                                popUpTo("settings") { inclusive = true }
                            }
                        }
                        FcmTokenManager(context.applicationContext).registerIfPossible()
                    }
                }

                LaunchedEffect(resolveTrigger) {
                    if (resolveTrigger == 0) return@LaunchedEffect
                    val ceId = deepLinkViewModel.pendingCeId ?: return@LaunchedEffect
                    delay(50)
                    val preferences = SettingsPreferences(context.applicationContext)
                    val baseUrl = preferences.getBaseUrlOnce()
                    if (baseUrl == null) {
                        navController.navigate("settings")
                        return@LaunchedEffect
                    }
                    val event = withContext(Dispatchers.IO) {
                        try {
                            val service = ApiClient.createService(baseUrl)
                            val response = service.getEvents("all")
                            response.events.find { e ->
                                e.event_id == ceId || (e.camera == "events" && e.subdir == ceId)
                            }
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (event != null) {
                        sharedEventViewModel.selectEvent(event)
                        if (navController.currentBackStackEntry?.destination?.route?.startsWith("event_not_found") == true) {
                            navController.popBackStack()
                        } else {
                            navController.navigate("main_tabs") {
                                popUpTo("settings") { inclusive = true }
                            }
                        }
                        navController.navigate("event_detail")
                        setIntent(Intent())
                        deepLinkViewModel.clearPending()
                    } else {
                        navController.navigate("main_tabs") {
                            popUpTo("settings") { inclusive = true }
                        }
                        navController.navigate("event_not_found/$ceId")
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
                    composable(
                        "event_not_found/{ce_id}",
                        arguments = listOf(navArgument("ce_id") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val ceIdArg = backStackEntry.arguments?.getString("ce_id") ?: return@composable
                        BackHandler {
                            deepLinkViewModel.clearPending()
                            navController.popBackStack()
                        }
                        EventNotFoundScreen(
                            ceId = ceIdArg,
                            onRefresh = { deepLinkViewModel.retryResolve() },
                            onBack = {
                                deepLinkViewModel.clearPending()
                                navController.popBackStack()
                            }
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
                            onEventActionCompleted = sharedEventViewModel::requestEventsRefresh,
                            viewModel = eventDetailViewModel
                        )
                    }
                    composable("snooze") {
                        SnoozeScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
