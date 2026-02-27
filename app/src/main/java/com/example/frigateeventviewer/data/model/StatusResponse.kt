package com.example.frigateeventviewer.data.model

/**
 * Response for GET /status (connection test and server health).
 * Property names match API contract §3.2 (snake_case for Gson).
 */
data class StatusResponse(
    val online: Boolean = true,
    val mqtt_connected: Boolean = false,
    val uptime_seconds: Double = 0.0,
    val uptime: String? = null,
    val started_at: String? = null,
    val active_events: ActiveEventsStatus? = null,
    val metrics: StatusMetrics? = null,
    val config: StatusConfig? = null
)
