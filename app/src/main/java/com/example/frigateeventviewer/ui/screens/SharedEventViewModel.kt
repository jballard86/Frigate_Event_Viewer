package com.example.frigateeventviewer.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.frigateeventviewer.data.model.Event
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Payload for events list refresh: optional event ids when user marks reviewed or deletes.
 */
data class EventRefreshPayload(
    val markedReviewedEventId: String? = null,
    val deletedEventId: String? = null
) {
    companion object {
        val RefreshOnly = EventRefreshPayload()
    }
}

/**
 * Activity-scoped ViewModel that holds the event selected for the detail screen.
 * Used to avoid passing [Event] through NavController routes.
 * Clear selection when the user pops back from event_detail so we don't hold data longer than needed.
 * Also exposes [eventsRefreshRequested] so EventsViewModel can refresh the list when an action completes.
 * Optional [markedReviewedEventId] and [deletedEventId] let EventsViewModel update local designation.
 */
class SharedEventViewModel : ViewModel() {

    private val _selectedEvent = MutableStateFlow<Event?>(null)
    val selectedEvent: StateFlow<Event?> = _selectedEvent.asStateFlow()

    private val _eventsRefreshRequested = MutableSharedFlow<EventRefreshPayload>(replay = 0)
    /** Emits when the events list should refresh (e.g. after Mark Reviewed / Keep / Delete). */
    val eventsRefreshRequested: SharedFlow<EventRefreshPayload> = _eventsRefreshRequested.asSharedFlow()

    /** Sets the event to show on the detail screen. Pass null when leaving the detail screen. */
    fun selectEvent(event: Event?) {
        _selectedEvent.value = event
    }

    /**
     * Requests that the events list refresh (called after event-detail actions).
     * @param markedReviewedEventId event id when user tapped Mark Reviewed (for local designation).
     * @param deletedEventId event id when user deleted the event (remove from local set).
     */
    fun requestEventsRefresh(
        markedReviewedEventId: String? = null,
        deletedEventId: String? = null
    ) {
        viewModelScope.launch {
            _eventsRefreshRequested.emit(
                EventRefreshPayload(
                    markedReviewedEventId = markedReviewedEventId,
                    deletedEventId = deletedEventId
                )
            )
        }
    }
}
