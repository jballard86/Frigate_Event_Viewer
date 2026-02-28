package com.example.frigateeventviewer.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.frigateeventviewer.data.api.ApiClient
import com.example.frigateeventviewer.data.preferences.SettingsPreferences
import com.example.frigateeventviewer.data.push.NotificationImageCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Identifies which action succeeded so the UI can navigate back (Delete/Keep) or show Snackbar (Mark Reviewed).
 */
enum class EventDetailAction {
    MARK_VIEWED,
    KEEP,
    DELETE
}

/**
 * UI state for event detail actions (mark viewed, keep, delete).
 * [Success] carries the action so the UI knows whether to pop back or show a Snackbar.
 */
sealed class EventDetailOperationState {
    data object Idle : EventDetailOperationState()
    data object Loading : EventDetailOperationState()
    data class Success(val action: EventDetailAction) : EventDetailOperationState()
    data class Error(val message: String) : EventDetailOperationState()
}

/**
 * ViewModel for the Event Detail screen.
 * Performs mark viewed, keep, and delete via the API; exposes [operationState] and [baseUrl].
 * UI should pop back on [Success] for [EventDetailAction.DELETE] or [EventDetailAction.KEEP];
 * on [EventDetailAction.MARK_VIEWED], show a Snackbar and reset to Idle.
 */
class EventDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = SettingsPreferences(application)

    /** Base URL for building media URLs (e.g. clip). Same pattern as [EventsViewModel]. */
    val baseUrl: StateFlow<String?> = preferences.baseUrl
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _operationState = MutableStateFlow<EventDetailOperationState>(EventDetailOperationState.Idle)
    val operationState: StateFlow<EventDetailOperationState> = _operationState.asStateFlow()

    /**
     * Marks the event as viewed. On success, UI shows Snackbar and stays on screen.
     */
    fun markViewed(eventPath: String) {
        performAction(EventDetailAction.MARK_VIEWED) { baseUrlValue ->
            ApiClient.createService(baseUrlValue).markViewed(eventPath)
        }
    }

    /**
     * Keeps the event (moves to saved). On success, UI should pop back (path changes on server).
     */
    fun keepEvent(eventPath: String) {
        performAction(EventDetailAction.KEEP) { baseUrlValue ->
            ApiClient.createService(baseUrlValue).keepEvent(eventPath)
        }
    }

    /**
     * Deletes the event folder. On success, UI should pop back.
     */
    fun deleteEvent(eventPath: String) {
        performAction(EventDetailAction.DELETE, eventPath) { baseUrlValue ->
            ApiClient.createService(baseUrlValue).deleteEvent(eventPath)
        }
    }

    /** Resets operation state to Idle (e.g. after showing Snackbar for Mark Reviewed). */
    fun resetOperationState() {
        _operationState.value = EventDetailOperationState.Idle
    }

    private fun performAction(action: EventDetailAction, eventPathForCache: String? = null, block: suspend (String) -> Unit) {
        viewModelScope.launch {
            _operationState.value = EventDetailOperationState.Loading
            val baseUrlValue = preferences.getBaseUrlOnce()
            if (baseUrlValue == null) {
                _operationState.value = EventDetailOperationState.Error("No server URL")
                return@launch
            }
            try {
                block(baseUrlValue)
                if (action == EventDetailAction.DELETE && eventPathForCache != null) {
                    NotificationImageCache.removeForEventPath(eventPathForCache)
                }
                _operationState.value = EventDetailOperationState.Success(action)
            } catch (e: Exception) {
                _operationState.value = EventDetailOperationState.Error(
                    e.message ?: "Request failed"
                )
            }
        }
    }
}

/**
 * Factory for [EventDetailViewModel] so it receives [Application].
 */
class EventDetailViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EventDetailViewModel::class.java)) {
            return EventDetailViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
