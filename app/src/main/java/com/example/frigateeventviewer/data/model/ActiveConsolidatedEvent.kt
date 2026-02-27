package com.example.frigateeventviewer.data.model

/**
 * Single consolidated event entry in status metrics.
 * Property names match API contract §3.2 (snake_case for Gson).
 */
data class ActiveConsolidatedEvent(
    val id: String? = null,
    val state: String? = null,
    val cameras: List<String>? = null,
    val start_time: Double? = null
)
