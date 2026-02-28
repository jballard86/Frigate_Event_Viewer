package com.example.frigateeventviewer.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.frigateeventviewer.data.api.ApiClient
import com.example.frigateeventviewer.data.model.SnoozeEntry
import com.example.frigateeventviewer.data.model.SnoozeRequest
import com.example.frigateeventviewer.data.preferences.SettingsPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Preset duration in minutes for snooze. */
val SNOOZE_PRESETS = listOf(30, 60, 120)

/**
 * UI state for the Snooze screen.
 */
sealed class SnoozeState {
    data object Loading : SnoozeState()
    data class Ready(
        val cameras: List<String>,
        val activeSnoozes: Map<String, SnoozeEntry>
    ) : SnoozeState()
    data class Error(val message: String) : SnoozeState()
}

/**
 * ViewModel for the Snooze screen.
 * Loads cameras (GET /cameras) and active snoozes (GET /api/snooze);
 * setSnooze(camera) uses selected preset and toggles; clearSnooze(camera) clears.
 */
class SnoozeViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = SettingsPreferences(application)

    private val _state = MutableStateFlow<SnoozeState>(SnoozeState.Loading)
    val state: StateFlow<SnoozeState> = _state.asStateFlow()

    /** Selected preset index (0=30m, 1=1h, 2=2h). */
    private val _selectedPresetIndex = MutableStateFlow(1)
    val selectedPresetIndex: StateFlow<Int> = _selectedPresetIndex.asStateFlow()

    /** Duration in minutes from current preset. */
    val selectedDurationMinutes: Int
        get() = SNOOZE_PRESETS.getOrElse(_selectedPresetIndex.value) { 60 }

    /** Mute notifications for this camera; default true. */
    private val _snoozeNotifications = MutableStateFlow(true)
    val snoozeNotifications: StateFlow<Boolean> = _snoozeNotifications.asStateFlow()

    /** Skip AI processing for this camera; default true. */
    private val _snoozeAi = MutableStateFlow(true)
    val snoozeAi: StateFlow<Boolean> = _snoozeAi.asStateFlow()

    /** Camera currently selected for applying snooze. */
    private val _selectedCamera = MutableStateFlow<String?>(null)
    val selectedCamera: StateFlow<String?> = _selectedCamera.asStateFlow()

    /** True while a set/clear snooze request is in progress. */
    private val _operationInProgress = MutableStateFlow(false)
    val operationInProgress: StateFlow<Boolean> = _operationInProgress.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = SnoozeState.Loading
            val baseUrl = preferences.getBaseUrlOnce()
            if (baseUrl == null) {
                _state.value = SnoozeState.Error("No server URL")
                return@launch
            }
            try {
                val service = ApiClient.createService(baseUrl)
                val cameras = service.getCameras().cameras
                val snoozes = service.getSnoozeList()
                _state.value = SnoozeState.Ready(cameras = cameras, activeSnoozes = snoozes)
            } catch (e: Exception) {
                _state.value = SnoozeState.Error(e.message ?: "Failed to load")
            }
        }
    }

    fun setSelectedPresetIndex(index: Int) {
        if (index in SNOOZE_PRESETS.indices) {
            _selectedPresetIndex.value = index
        }
    }

    fun setSnoozeNotifications(value: Boolean) {
        _snoozeNotifications.value = value
    }

    fun setSnoozeAi(value: Boolean) {
        _snoozeAi.value = value
    }

    fun setSelectedCamera(camera: String?) {
        _selectedCamera.value = camera
    }

    fun setSnooze(camera: String) {
        viewModelScope.launch {
            _operationInProgress.value = true
            val baseUrl = preferences.getBaseUrlOnce()
            if (baseUrl == null) {
                _operationInProgress.value = false
                return@launch
            }
            try {
                val service = ApiClient.createService(baseUrl)
                val duration = selectedDurationMinutes
                val request = SnoozeRequest(
                    duration_minutes = duration,
                    snooze_notifications = _snoozeNotifications.value,
                    snooze_ai = _snoozeAi.value
                )
                service.setSnooze(camera, request)
                load()
            } catch (_: Exception) {
                // Keep previous state; load() will refresh
                load()
            } finally {
                _operationInProgress.value = false
            }
        }
    }

    fun clearSnooze(camera: String) {
        viewModelScope.launch {
            _operationInProgress.value = true
            val baseUrl = preferences.getBaseUrlOnce()
            if (baseUrl == null) {
                _operationInProgress.value = false
                return@launch
            }
            try {
                val service = ApiClient.createService(baseUrl)
                service.clearSnooze(camera)
                load()
            } catch (_: Exception) {
                load()
            } finally {
                _operationInProgress.value = false
            }
        }
    }
}

class SnoozeViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SnoozeViewModel::class.java)) {
            return SnoozeViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
