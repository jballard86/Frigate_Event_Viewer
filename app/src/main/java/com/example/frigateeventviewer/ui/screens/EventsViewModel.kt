package com.example.frigateeventviewer.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.frigateeventviewer.data.api.ApiClient
import com.example.frigateeventviewer.data.model.Event
import com.example.frigateeventviewer.data.model.EventsResponse
import com.example.frigateeventviewer.data.preferences.SettingsPreferences
import com.example.frigateeventviewer.data.push.UnreadState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Filter mode for the Events tab. Single source of truth for the API filter param (never null).
 */
enum class EventsFilterMode {
    Unreviewed,
    Reviewed
}

/**
 * UI state for the events list (GET /events?filter=reviewed|unreviewed).
 * [Loading] may hold [previous] so the UI can show the list while refreshing.
 */
sealed class EventsState {
    data class Loading(val previous: EventsResponse? = null) : EventsState()
    data class Success(val response: EventsResponse) : EventsState()
    data class Error(val message: String, val previous: EventsResponse? = null) : EventsState()
}

/**
 * ViewModel for the Events screen.
 * Loads events by filter mode (reviewed / unreviewed); exposes baseUrl, page title, and toggle button label.
 * Keeps previous list on screen while refreshing. Subscribes to [SharedEventViewModel.eventsRefreshRequested].
 */
class EventsViewModel(
    application: Application,
    sharedEventViewModel: SharedEventViewModel
) : AndroidViewModel(application) {

    private val preferences = SettingsPreferences(application)
    private val loadMutex = Mutex()
    private var loadJob: Job? = null

    /** Current filter mode; default Unreviewed. */
    private val _filterMode = MutableStateFlow(EventsFilterMode.Unreviewed)
    val filterMode: StateFlow<EventsFilterMode> = _filterMode.asStateFlow()

    /** Page title for the topBar when Events tab is selected. */
    private val _eventsPageTitle = MutableStateFlow("Unreviewed Events")
    val eventsPageTitle: StateFlow<String> = _eventsPageTitle.asStateFlow()

    /** Base URL for the UI to build media URLs via [com.example.frigateeventviewer.ui.util.buildMediaUrl]. */
    val baseUrl: StateFlow<String?> = preferences.baseUrl
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _state = MutableStateFlow<EventsState>(EventsState.Loading(null))
    val state: StateFlow<EventsState> = _state.asStateFlow()

    /** Events to display: for Unreviewed, server list minus [UnreadState] locally marked reviewed; for Reviewed, server list. */
    private val _displayedEvents = MutableStateFlow<List<Event>>(emptyList())
    val displayedEvents: StateFlow<List<Event>> = _displayedEvents.asStateFlow()

    /** Label for the toggle button: "View Reviewed Events" or "View Unreviewed Events". */
    private val _filterToggleButtonLabel = MutableStateFlow("View Reviewed Events")
    val filterToggleButtonLabel: StateFlow<String> = _filterToggleButtonLabel.asStateFlow()

    init {
        loadEvents()
        viewModelScope.launch {
            UnreadState.locallyMarkedReviewedEventIds.collect {
                updateDisplayedEvents()
            }
        }
        viewModelScope.launch {
            sharedEventViewModel.eventsRefreshRequested.collect { payload ->
                payload.markedReviewedEventId?.let { id ->
                    UnreadState.recordMarkedReviewed(id)
                    // Optimistic: remove from current displayed list
                    val current = (_state.value as? EventsState.Success)?.response
                    if (current != null && _filterMode.value == EventsFilterMode.Unreviewed) {
                        _state.value = EventsState.Success(
                            current.copy(events = current.events.filter { it.event_id != id })
                        )
                    }
                    updateDisplayedEvents()
                }
                payload.deletedEventId?.let { id ->
                    UnreadState.recordDeleted(id)
                    updateDisplayedEvents()
                }
                loadEvents()
            }
        }
    }

    private fun updateDisplayedEvents() {
        val response = when (val s = _state.value) {
            is EventsState.Loading -> s.previous
            is EventsState.Success -> s.response
            is EventsState.Error -> s.previous
        }
        val events = response?.events ?: emptyList()
        val localSet = UnreadState.locallyMarkedReviewedEventIds.value
        _displayedEvents.value = if (_filterMode.value == EventsFilterMode.Unreviewed) {
            events.filter { it.event_id !in localSet }
        } else {
            events
        }
    }

    /** Switches filter mode and refetches. */
    fun setFilterMode(reviewed: Boolean) {
        val newMode = if (reviewed) EventsFilterMode.Reviewed else EventsFilterMode.Unreviewed
        if (_filterMode.value == newMode) return
        _filterMode.value = newMode
        _eventsPageTitle.value = when (newMode) {
            EventsFilterMode.Unreviewed -> "Unreviewed Events"
            EventsFilterMode.Reviewed -> "Reviewed Events"
        }
        _filterToggleButtonLabel.value = when (newMode) {
            EventsFilterMode.Unreviewed -> "View Reviewed Events"
            EventsFilterMode.Reviewed -> "View Unreviewed Events"
        }
        loadEvents()
    }

    /**
     * Re-fetches GET /events with current filter. Keeps previous list on screen while loading.
     */
    fun refresh() {
        loadEvents()
    }

    private fun loadEvents() {
        viewModelScope.launch {
            loadMutex.withLock {
                if (loadJob?.isActive == true) return@launch
                loadJob = launch {
                    runLoadEvents()
                }
            }
        }
    }

    private suspend fun runLoadEvents() {
        val currentSuccess = (_state.value as? EventsState.Success)?.response
        val isRefresh = currentSuccess != null
        _state.value = EventsState.Loading(if (isRefresh) currentSuccess else null)
        _eventsPageTitle.value = when (_filterMode.value) {
            EventsFilterMode.Unreviewed -> "Unreviewed Events"
            EventsFilterMode.Reviewed -> "Reviewed Events"
        }
        _filterToggleButtonLabel.value = when (_filterMode.value) {
            EventsFilterMode.Unreviewed -> "View Reviewed Events"
            EventsFilterMode.Reviewed -> "View Unreviewed Events"
        }
        updateDisplayedEvents()
        val baseUrlValue = preferences.getBaseUrlOnce()
        if (baseUrlValue == null) {
            _state.value = EventsState.Error("No server URL", currentSuccess)
            updateDisplayedEvents()
            return
        }
        val filter = if (_filterMode.value == EventsFilterMode.Reviewed) "reviewed" else "unreviewed"
        try {
            val service = ApiClient.createService(baseUrlValue)
            val response = service.getEvents(filter = filter)
            _state.value = EventsState.Success(response)
            updateDisplayedEvents()
        } catch (e: Exception) {
            _state.value = EventsState.Error(
                e.message ?: "Failed to load events",
                currentSuccess
            )
            updateDisplayedEvents()
        }
        runWatchdog()
    }

    /**
     * Prunes locally marked reviewed set to only ids that still exist on the server.
     * Uses GET /events?filter=all; runs after each load so storage stays bounded.
     */
    private fun runWatchdog() {
        viewModelScope.launch {
            val baseUrlValue = preferences.getBaseUrlOnce() ?: return@launch
            try {
                val service = ApiClient.createService(baseUrlValue)
                val response = service.getEvents(filter = "all")
                val existingIds = response.events.mapTo(mutableSetOf()) { it.event_id }
                UnreadState.pruneToExistingIds(existingIds)
                updateDisplayedEvents()
            } catch (_: Exception) {
                // Ignore; watchdog will run again on next refresh
            }
        }
    }
}

/**
 * Factory for [EventsViewModel] so it receives [Application] and [SharedEventViewModel].
 */
class EventsViewModelFactory(
    private val application: Application,
    private val sharedEventViewModel: SharedEventViewModel
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EventsViewModel::class.java)) {
            return EventsViewModel(application, sharedEventViewModel) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
