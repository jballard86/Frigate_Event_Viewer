package com.example.frigateeventviewer.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.frigateeventviewer.data.api.ApiClient
import com.example.frigateeventviewer.data.model.EventsResponse
import com.example.frigateeventviewer.data.preferences.SettingsPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI state for the events list (GET /events?filter=unreviewed).
 */
sealed class EventsState {
    data object Loading : EventsState()
    data class Success(val response: EventsResponse) : EventsState()
    data class Error(val message: String) : EventsState()
}

/**
 * ViewModel for the Events screen.
 * Loads unreviewed events; exposes baseUrl for thumbnail URL building and Loading / Success / Error.
 * Subscribes to [SharedEventViewModel.eventsRefreshRequested] to refresh when event-detail actions complete.
 */
class EventsViewModel(
    application: Application,
    sharedEventViewModel: SharedEventViewModel
) : AndroidViewModel(application) {

    private val preferences = SettingsPreferences(application)

    /** Base URL for the UI to build media URLs via [com.example.frigateeventviewer.ui.util.buildMediaUrl]. */
    val baseUrl: StateFlow<String?> = preferences.baseUrl
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _state = MutableStateFlow<EventsState>(EventsState.Loading)
    val state: StateFlow<EventsState> = _state.asStateFlow()

    init {
        loadEvents()
        viewModelScope.launch {
            sharedEventViewModel.eventsRefreshRequested.collect {
                loadEvents()
            }
        }
    }

    /**
     * Re-fetches GET /events?filter=unreviewed.
     */
    fun refresh() {
        loadEvents()
    }

    private fun loadEvents() {
        viewModelScope.launch {
            _state.value = EventsState.Loading
            val baseUrlValue = preferences.getBaseUrlOnce()
            if (baseUrlValue == null) {
                _state.value = EventsState.Error("No server URL")
                return@launch
            }
            try {
                val service = ApiClient.createService(baseUrlValue)
                val response = service.getEvents(filter = "unreviewed")
                _state.value = EventsState.Success(response)
            } catch (e: Exception) {
                _state.value = EventsState.Error(
                    e.message ?: "Failed to load events"
                )
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
