package com.example.frigateeventviewer.ui.screens

import androidx.lifecycle.ViewModel
import com.example.frigateeventviewer.data.model.Event
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Activity-scoped ViewModel that holds the event selected for the detail screen.
 * Used to avoid passing [Event] through NavController routes.
 * Clear selection when the user pops back from event_detail so we don't hold data longer than needed.
 */
class SharedEventViewModel : ViewModel() {

    private val _selectedEvent = MutableStateFlow<Event?>(null)
    val selectedEvent: StateFlow<Event?> = _selectedEvent.asStateFlow()

    /** Sets the event to show on the detail screen. Pass null when leaving the detail screen. */
    fun selectEvent(event: Event?) {
        _selectedEvent.value = event
    }
}
