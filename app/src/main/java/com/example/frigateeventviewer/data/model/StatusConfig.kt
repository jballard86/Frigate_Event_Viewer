package com.example.frigateeventviewer.data.model

/**
 * Config section in GET /status response.
 * Property names match API contract §3.2 (snake_case for Gson).
 */
data class StatusConfig(
    val retention_days: Int? = null,
    val log_level: String? = null,
    val ffmpeg_timeout: Int? = null,
    val summary_padding_before: Int? = null,
    val summary_padding_after: Int? = null
)
