package com.example.frigateeventviewer.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.frigateeventviewer.FrigateEventViewerApplication
import com.example.frigateeventviewer.data.api.ApiClient
import com.example.frigateeventviewer.data.Go2RtcStreamsState
import com.example.frigateeventviewer.data.Go2RtcStreamsRepository
import com.example.frigateeventviewer.data.preferences.SettingsPreferences
import com.example.frigateeventviewer.data.push.FcmTokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the connection test (GET /status).
 */
sealed class ConnectionTestState {
    data object Idle : ConnectionTestState()
    data object Loading : ConnectionTestState()
    data object Success : ConnectionTestState()
    data class Error(val message: String) : ConnectionTestState()
}

/**
 * ViewModel for the Settings/onboarding screen.
 * Owns DataStore read/save, connection test via API, and exposes state to the UI.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = SettingsPreferences(application)
    private val go2RtcRepository = (application as? FrigateEventViewerApplication)?.go2RtcStreamsRepository

    /** Current saved base URL from DataStore, or null. */
    val savedBaseUrl: StateFlow<String?> = preferences.baseUrl
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /** User's current input in the URL field (for two-way binding). */
    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    /** User's current input in the Frigate IP field (for two-way binding). */
    private val _frigateIpInput = MutableStateFlow("")
    val frigateIpInput: StateFlow<String> = _frigateIpInput.asStateFlow()

    /** Result of the last connection test. */
    private val _connectionTestState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val connectionTestState: StateFlow<ConnectionTestState> = _connectionTestState.asStateFlow()

    /** Landscape tab bar icon alpha (0f..1f). Default 0.5f. */
    val landscapeTabIconAlpha: StateFlow<Float> = preferences.landscapeTabIconAlpha
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.5f
        )

    /** State of the default camera dropdown (shared go2rtc cache). Fetched on app load and when Frigate IP is saved. */
    val defaultCameraListState: StateFlow<Go2RtcStreamsState> =
        go2RtcRepository?.state ?: MutableStateFlow(Go2RtcStreamsState.Unavailable(message = null)).asStateFlow()

    /** Currently selected default camera (stream name), or null for "None". */
    private val _defaultCameraSelection = MutableStateFlow<String?>(null)
    val defaultCameraSelection: StateFlow<String?> = _defaultCameraSelection.asStateFlow()

    init {
        viewModelScope.launch {
            _urlInput.value = preferences.getBaseUrlOnce()?.trimEnd('/') ?: ""
            _frigateIpInput.value = preferences.getFrigateIpOnce() ?: ""
            _defaultCameraSelection.value = preferences.getDefaultLiveCameraOnce()
        }
    }

    fun setDefaultCameraSelection(streamName: String?) {
        _defaultCameraSelection.value = streamName
    }

    fun updateUrlInput(value: String) {
        _urlInput.value = value
    }

    fun updateFrigateIpInput(value: String) {
        _frigateIpInput.value = value
    }

    /**
     * Saves the current URL input and Frigate IP to DataStore after normalizing URL.
     * @return true if URL saved successfully, false if invalid (Frigate IP is always saved)
     */
    fun saveBaseUrl(onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            preferences.saveFrigateIp(_frigateIpInput.value)
            preferences.saveDefaultLiveCamera(_defaultCameraSelection.value)
            val normalized = preferences.saveBaseUrl(_urlInput.value)
            if (normalized != null) {
                _urlInput.update { normalized.trimEnd('/') }
                FcmTokenManager(getApplication()).registerIfPossible()
                onSaved()
            }
            go2RtcRepository?.refresh()
        }
    }

    /**
     * Tests the connection by calling GET /status on the given or saved URL.
     * Uses [urlToTest] if non-null, otherwise the current [urlInput] (normalized).
     */
    fun testConnection(urlToTest: String? = null) {
        viewModelScope.launch {
            val url = urlToTest?.trim()?.ifEmpty { null }
                ?: _urlInput.value.trim().ifEmpty { null }
            if (url == null) {
                _connectionTestState.value = ConnectionTestState.Error("Enter a server URL")
                return@launch
            }
            val baseUrl = SettingsPreferences.normalizeBaseUrl(url)
                ?: run {
                    _connectionTestState.value = ConnectionTestState.Error("Invalid URL")
                    return@launch
                }
            _connectionTestState.value = ConnectionTestState.Loading
            try {
                val service = ApiClient.createService(baseUrl)
                service.getStatus()
                _connectionTestState.value = ConnectionTestState.Success
            } catch (e: Exception) {
                _connectionTestState.value = ConnectionTestState.Error(
                    e.message ?: "Connection failed"
                )
            }
        }
    }

    fun clearConnectionTestState() {
        _connectionTestState.value = ConnectionTestState.Idle
    }

    /**
     * Saves the landscape tab bar icon alpha (0f..1f). Used for the "show tab bar" icon in landscape.
     */
    fun setLandscapeTabIconAlpha(value: Float) {
        viewModelScope.launch {
            preferences.saveLandscapeTabIconAlpha(value)
        }
    }
}

/**
 * Factory for [SettingsViewModel] so it receives [Application] for DataStore.
 */
class SettingsViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
