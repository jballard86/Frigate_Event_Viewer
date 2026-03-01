package com.example.frigateeventviewer.data

import android.app.Application
import com.example.frigateeventviewer.data.api.ApiClient
import com.example.frigateeventviewer.data.preferences.SettingsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Shared state for the go2rtc stream list (camera names). Used by Settings (default camera
 * dropdown) and Live tab (Select Camera dropdown). Fetched once on app load and when Frigate IP
 * changes in Settings to avoid repeated API calls.
 */
sealed class Go2RtcStreamsState {
    data object Loading : Go2RtcStreamsState()
    data class Success(val streamNames: List<String>) : Go2RtcStreamsState()
    data class Unavailable(val message: String? = null) : Go2RtcStreamsState()
}

/**
 * Single source of truth for the go2rtc camera list. Fetch on app load and when Frigate IP
 * changes; Settings and Live tab read from [state] and do not trigger their own fetches.
 */
class Go2RtcStreamsRepository(private val application: Application) {

    private val preferences = SettingsPreferences(application)

    private val _state = MutableStateFlow<Go2RtcStreamsState>(Go2RtcStreamsState.Loading)
    val state: StateFlow<Go2RtcStreamsState> = _state.asStateFlow()

    /**
     * Fetches GET /api/go2rtc/streams using the current Frigate IP from preferences.
     * Call on app load (MainActivity) and when the user saves a new Frigate IP (Settings).
     */
    suspend fun refresh() {
        _state.value = Go2RtcStreamsState.Loading
        val ip = preferences.getFrigateIpOnce()
        val baseUrl = SettingsPreferences.buildFrigateApiBaseUrl(ip)
        if (baseUrl == null) {
            _state.value = Go2RtcStreamsState.Unavailable(message = null)
            return
        }
        try {
            val service = ApiClient.createService(baseUrl)
            val response = service.getGo2RtcStreams()
            val names = withContext(Dispatchers.Default) {
                response.keys.toList().sorted()
            }
            _state.value = Go2RtcStreamsState.Success(names)
        } catch (e: Exception) {
            _state.value = Go2RtcStreamsState.Unavailable(message = e.message ?: "Failed to load streams")
        }
    }
}
