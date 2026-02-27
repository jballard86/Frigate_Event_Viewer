package com.example.frigateeventviewer.data.model

/**
 * Most recent event reference in GET /stats. Nullable per contract §3.1.
 * Property names match API contract (snake_case for Gson).
 */
data class MostRecent(
    val event_id: String? = null,
    val camera: String? = null,
    val url: String? = null,
    val timestamp: Double? = null
)
