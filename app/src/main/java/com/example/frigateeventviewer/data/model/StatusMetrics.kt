package com.example.frigateeventviewer.data.model

/**
 * Metrics section in GET /status response.
 * Property names match API contract §3.2 (snake_case for Gson).
 */
data class StatusMetrics(
    val notification_queue_size: Int? = null,
    val active_threads: Int? = null,
    val active_consolidated_events: List<ActiveConsolidatedEvent>? = null,
    val recent_errors: List<SystemError>? = null
)
