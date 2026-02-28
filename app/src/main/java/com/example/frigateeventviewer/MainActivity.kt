package com.example.frigateeventviewer

import android.content.Intent
import android.content.Context
import android.app.NotificationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.frigateeventviewer.data.api.ApiClient
import com.example.frigateeventviewer.data.model.Event
import com.example.frigateeventviewer.data.preferences.SettingsPreferences
import com.example.frigateeventviewer.data.util.EventMatching
import com.example.frigateeventviewer.data.push.FcmTokenManager
import com.example.frigateeventviewer.data.push.FrigateFirebaseMessagingService
import com.example.frigateeventviewer.data.push.PushConstants
import com.example.frigateeventviewer.ui.screens.DailyReviewViewModel
import com.example.frigateeventviewer.ui.screens.DailyReviewViewModelFactory
import com.example.frigateeventviewer.ui.screens.DeepLinkViewModel
import com.example.frigateeventviewer.ui.screens.EventDetailScreen
import com.example.frigateeventviewer.ui.screens.EventDetailViewModel
import com.example.frigateeventviewer.ui.screens.EventDetailViewModelFactory
import com.example.frigateeventviewer.ui.screens.EventsViewModel
import com.example.frigateeventviewer.ui.screens.EventsViewModelFactory
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

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permission result; notifications will show or not based on user choice.
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyDeepLinkOrNotificationIntent(intent)
    }

    /**
     * Applies pending ce_id from deep link (intent.data) or notification tap (EXTRA_CE_ID).
     * Triggers resolution so LaunchedEffect(resolveTrigger) can navigate to event_detail.
     */
    private fun applyDeepLinkOrNotificationIntent(intent: Intent?) {
        if (intent == null) return
        val uri = intent.data
        val ceId = DeepLinkViewModel.parseDeepLinkCeId(uri)
            ?: intent.getStringExtra(FrigateFirebaseMessagingService.EXTRA_CE_ID)?.takeIf { it.isNotBlank() }
        if (ceId != null) {
            deepLinkViewModel.setFromIntent(uri ?: Uri.parse("buffer://event_detail/$ceId"))
        }
    }

    private val deepLinkViewModel: DeepLinkViewModel by lazy {
        ViewModelProvider(this)[DeepLinkViewModel::class.java]
    }

    override fun onResume() {
        super.onResume()
        updateUnreadBadge()
    }

    /**
     * Fetches GET /api/events/unread_count and updates the app icon badge via a silent
     * notification. The builder's number is set so launchers can show the count; when count is 0 the notification is canceled.
     */
    private fun updateUnreadBadge() {
        lifecycleScope.launch(Dispatchers.IO) {
            val baseUrl = SettingsPreferences(applicationContext).getBaseUrlOnce()
            if (baseUrl.isNullOrBlank()) return@launch
            try {
                val response = ApiClient.createService(baseUrl).getUnreadCount()
                val count = response.unread_count
                withContext(Dispatchers.Main) {
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (count <= 0) {
                        notificationManager.cancel(PushConstants.BADGE_NOTIFICATION_ID)
                    } else {
                        val builder = NotificationCompat.Builder(applicationContext, PushConstants.CHANNEL_ID_BADGE)
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
        val activity = this
        setContent {
            FrigateEventViewerTheme {
                val navController = rememberNavController()
                val sharedEventViewModel: SharedEventViewModel = viewModel<SharedEventViewModel>()
                val context = LocalContext.current
                val resolveTrigger by deepLinkViewModel.resolveTrigger.collectAsState(initial = 0)

                // Wait one frame so NavHost is composed before any navigate().
                LaunchedEffect(Unit) {
                    delay(50)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                    applyDeepLinkOrNotificationIntent(intent)
                    val uri = intent?.data
                    val ceIdFromUri = DeepLinkViewModel.parseDeepLinkCeId(uri)
                    val ceIdFromExtra = intent?.getStringExtra(FrigateFirebaseMessagingService.EXTRA_CE_ID)?.takeIf { it.isNotBlank() }
                    if (ceIdFromUri == null && ceIdFromExtra == null) {
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
                    val ceId = deepLinkViewModel.pendingCeId.value ?: return@LaunchedEffect
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
                            response.events.find { e -> EventMatching.eventMatchesCeId(e, ceId) }
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
                        activity.intent = Intent()
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
                        val eventsViewModel: EventsViewModel =
                            viewModel(factory = EventsViewModelFactory(application, sharedEventViewModel))
                        MainTabsScreen(
                            navController = navController,
                            sharedEventViewModel = sharedEventViewModel,
                            dailyReviewViewModel = dailyReviewViewModel,
                            eventsViewModel = eventsViewModel
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
                            onEventActionCompleted = { markedReviewedEventId, deletedEventId ->
                                sharedEventViewModel.requestEventsRefresh(
                                    markedReviewedEventId = markedReviewedEventId,
                                    deletedEventId = deletedEventId
                                )
                            },
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
