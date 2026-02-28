package com.example.frigateeventviewer.data.model

/**
 * Response for POST /api/snooze/&lt;camera&gt;. Contract §8.1.
 */
data class SnoozeResponse(
    val expiration_time: Double,
    val camera: String,
    val snooze_notifications: Boolean,
    val snooze_ai: Boolean
)
