package com.example.frigateeventviewer.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.frigateeventviewer.data.api.ApiClient
import com.example.frigateeventviewer.data.model.Event
import com.example.frigateeventviewer.data.model.StatsResponse
import com.example.frigateeventviewer.data.preferences.SettingsPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI state for the dashboard (GET /stats).
 */
sealed class DashboardState {
    data class Loading(val previous: StatsResponse? = null) : DashboardState()
    data class Success(val stats: StatsResponse) : DashboardState()
    data class Error(val message: String, val previous: StatsResponse? = null) : DashboardState()
}

/**
 * ViewModel for the Dashboard screen.
 * Loads stats from GET /stats using the saved base URL; exposes Loading / Success / Error and refresh().
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = SettingsPreferences(application)

    private val _state = MutableStateFlow<DashboardState>(DashboardState.Loading(null))
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    /** Base URL for building media URLs (e.g. recent event clip). */
    val baseUrl: StateFlow<String?> = preferences.baseUrl
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /** Most recent event with a clip, or null when unavailable. */
    private val _recentEvent = MutableStateFlow<Event?>(null)
    val recentEvent: StateFlow<Event?> = _recentEvent.asStateFlow()

    init {
        loadStats()
    }

    /**
     * Re-fetches GET /stats. Called on pull-to-refresh or retry.
     */
    fun refresh() {
        loadStats(keepPrevious = true)
    }

    private fun loadStats(keepPrevious: Boolean = false) {
        viewModelScope.launch {
            val previous = (_state.value as? DashboardState.Success)?.stats
            _state.value = DashboardState.Loading(if (keepPrevious) previous else null)
            val baseUrl = preferences.getBaseUrlOnce()
            if (baseUrl == null) {
                _state.value = DashboardState.Error("No server URL")
                return@launch
            }
            try {
                val service = ApiClient.createService(baseUrl)
                val stats = service.getStats()
                val recent = try {
                    val response = service.getEvents(filter = "unreviewed")
                    response.events
                        .asSequence()
                        .filter { it.has_clip }
                        .firstOrNull()
                } catch (_: Exception) {
                    null
                }
                _recentEvent.value = recent
                _state.value = DashboardState.Success(stats)
            } catch (e: Exception) {
                _state.value = DashboardState.Error(
                    e.message ?: "Failed to load stats",
                    previous = previous
                )
            }
        }
    }
}

/**
 * Factory for [DashboardViewModel] so it receives [Application].
 */
class DashboardViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            return DashboardViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
