package com.example.frigateeventviewer.data.model

/**
 * Response for GET /events.
 * Property names match API contract §1.2 (snake_case for Gson).
 */
data class EventsResponse(
    val cameras: List<String> = emptyList(),
    val total_count: Int = 0,
    val events: List<Event> = emptyList()
)
