package com.example.frigateeventviewer.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import com.example.frigateeventviewer.data.api.ApiClient
import com.example.frigateeventviewer.data.model.Event
import com.example.frigateeventviewer.data.model.EventsResponse
import com.example.frigateeventviewer.data.preferences.SettingsPreferences
import com.example.frigateeventviewer.data.push.UnreadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Filter mode for the Events tab. Single source of truth for the API filter param (never null).
 */
enum class EventsFilterMode {
    Unreviewed,
    Reviewed,
    Saved
}

/**
 * UI state for the events list (GET /events?filter=reviewed|unreviewed|saved).
 * [Loading] may hold [previous] so the UI can show the list while refreshing.
 */
sealed class EventsState {
    data class Loading(val previous: EventsResponse? = null) : EventsState()
    data class Success(val response: EventsResponse) : EventsState()
    data class Error(val message: String, val previous: EventsResponse? = null) : EventsState()
}

/**
 * ViewModel for the Events screen.
 * Loads events by filter mode (reviewed / unreviewed / saved); exposes baseUrl, page title, and dropdown selection label.
 * Keeps previous list on screen while refreshing. Subscribes to [SharedEventViewModel.eventsRefreshRequested].
 * Filter mode is stored in [SavedStateHandle] so it survives configuration changes (e.g. rotation).
 */
class EventsViewModel(
    application: Application,
    sharedEventViewModel: SharedEventViewModel,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val preferences = SettingsPreferences(application)
    private val loadMutex = Mutex()
    private var loadJob: Job? = null
    private var lastFetchTime = 0L

    /** Current filter mode; restored from [SavedStateHandle] on init, default Unreviewed. */
    private val _filterMode = MutableStateFlow(restoreFilterMode())
    val filterMode: StateFlow<EventsFilterMode> = _filterMode.asStateFlow()

    /** Page title for the topBar when Events tab is selected. */
    private val _eventsPageTitle = MutableStateFlow(titleForMode(_filterMode.value))
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

    /** Events to display: for Unreviewed, server list minus [UnreadState] locally marked reviewed; for Reviewed/Saved, server list. */
    private val _displayedEvents = MutableStateFlow<List<Event>>(emptyList())
    val displayedEvents: StateFlow<List<Event>> = _displayedEvents.asStateFlow()

    /** Label for the filter dropdown trigger: "Unreviewed", "Reviewed", or "Saved". */
    private val _dropdownSelectionLabel = MutableStateFlow(dropdownLabelForMode(_filterMode.value))
    val dropdownSelectionLabel: StateFlow<String> = _dropdownSelectionLabel.asStateFlow()

    init {
        if (savedStateHandle.get<String>(KEY_FILTER_MODE) == null) {
            savedStateHandle[KEY_FILTER_MODE] = _filterMode.value.name
        }
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
                        val filteredEvents = withContext(Dispatchers.Default) {
                            current.events.filter { it.event_id != id }
                        }
                        _state.value = EventsState.Success(
                            current.copy(events = filteredEvents)
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

    private suspend fun updateDisplayedEvents() {
        val response = when (val s = _state.value) {
            is EventsState.Loading -> s.previous
            is EventsState.Success -> s.response
            is EventsState.Error -> s.previous
        }
        val events = response?.events ?: emptyList()
        val localSet = UnreadState.locallyMarkedReviewedEventIds.value
        val filtered = withContext(Dispatchers.Default) {
            when (_filterMode.value) {
                EventsFilterMode.Unreviewed -> events.filter { it.event_id !in localSet }
                EventsFilterMode.Reviewed, EventsFilterMode.Saved -> events
            }
        }
        _displayedEvents.value = filtered
    }

    /** Switches filter mode and refetches. Persists mode to [SavedStateHandle] for rotation survival. */
    fun setFilterMode(mode: EventsFilterMode) {
        if (_filterMode.value == mode) return
        _filterMode.value = mode
        savedStateHandle[KEY_FILTER_MODE] = mode.name
        _eventsPageTitle.value = titleForMode(mode)
        _dropdownSelectionLabel.value = dropdownLabelForMode(mode)
        loadEvents()
    }

    /**
     * Re-fetches GET /events with current filter. Keeps previous list on screen while loading.
     */
    fun refresh(force: Boolean = false) {
        if (!force && lastFetchTime > 0 && System.currentTimeMillis() - lastFetchTime < 5 * 60 * 1000 && _state.value is EventsState.Success) {
            return
        }
        loadEvents()
    }

    /**
     * Returns the next unreviewed event after [currentEventId] in the current displayed list order.
     * Used when the user marks an event as reviewed so the detail screen can show the next one.
     * Returns null if not in Unreviewed mode, or if [currentEventId] is last or not in the list.
     */
    fun getNextUnreviewedEventAfter(currentEventId: String): Event? {
        if (_filterMode.value != EventsFilterMode.Unreviewed) return null
        val list = _displayedEvents.value
        val index = list.indexOfFirst { it.event_id == currentEventId }
        return if (index < 0) null else list.getOrNull(index + 1)
    }

    /**
     * Returns the event at [offset] positions from [currentEventId] in the current displayed list.
     * Used for swipe-to-cycle on event detail: offset -1 = previous, offset +1 = next.
     * Works for any filter mode (Unreviewed, Reviewed, Saved). Returns null if not found or out of range.
     */
    fun getEventAtOffset(currentEventId: String, offset: Int): Event? {
        val list = _displayedEvents.value
        val index = list.indexOfFirst { it.event_id == currentEventId }
        return if (index < 0) null else list.getOrNull(index + offset)
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
        _eventsPageTitle.value = titleForMode(_filterMode.value)
        _dropdownSelectionLabel.value = dropdownLabelForMode(_filterMode.value)
        updateDisplayedEvents()
        val baseUrlValue = preferences.getBaseUrlOnce()
        if (baseUrlValue == null) {
            _state.value = EventsState.Error("No server URL", currentSuccess)
            updateDisplayedEvents()
            return
        }
        val filter = when (_filterMode.value) {
            EventsFilterMode.Unreviewed -> "unreviewed"
            EventsFilterMode.Reviewed -> "reviewed"
            EventsFilterMode.Saved -> "saved"
        }
        try {
            val service = ApiClient.createService(baseUrlValue)
            val response = service.getEvents(filter = filter)
            _state.value = EventsState.Success(response)
            updateDisplayedEvents()
            lastFetchTime = System.currentTimeMillis()
        } catch (e: Exception) {
            _state.value = EventsState.Error(
                e.message ?: "Failed to load events",
                currentSuccess
            )
            updateDisplayedEvents()
        }
        runWatchdog()
    }

    private fun restoreFilterMode(): EventsFilterMode {
        val saved = savedStateHandle.get<String>(KEY_FILTER_MODE) ?: return EventsFilterMode.Unreviewed
        return when (saved) {
            EventsFilterMode.Reviewed.name -> EventsFilterMode.Reviewed
            EventsFilterMode.Saved.name -> EventsFilterMode.Saved
            else -> EventsFilterMode.Unreviewed
        }
    }

    private fun titleForMode(mode: EventsFilterMode): String = when (mode) {
        EventsFilterMode.Unreviewed -> "Unreviewed Events"
        EventsFilterMode.Reviewed -> "Reviewed Events"
        EventsFilterMode.Saved -> "Saved Events"
    }

    private fun dropdownLabelForMode(mode: EventsFilterMode): String = when (mode) {
        EventsFilterMode.Unreviewed -> "Unreviewed"
        EventsFilterMode.Reviewed -> "Reviewed"
        EventsFilterMode.Saved -> "Saved"
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
                val existingIds = withContext(Dispatchers.Default) {
                    response.events.mapTo(mutableSetOf()) { it.event_id }
                }
                UnreadState.pruneToExistingIds(existingIds)
                updateDisplayedEvents()
            } catch (_: Exception) {
                // Ignore; watchdog will run again on next refresh
            }
        }
    }

    companion object {
        private const val KEY_FILTER_MODE = "events_filter_mode"
    }
}

/**
 * Factory for [EventsViewModel]. Uses [CreationExtras] so the owner's [SavedStateHandle]
 * (e.g. Activity's) is supplied by the framework; survives configuration changes and is owner-agnostic.
 */
class EventsViewModelFactory(
    private val sharedEventViewModel: SharedEventViewModel
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (!modelClass.isAssignableFrom(EventsViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
        }
        val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as Application
        val savedStateHandle = extras.createSavedStateHandle()
        return EventsViewModel(application, sharedEventViewModel, savedStateHandle) as T
    }
}
