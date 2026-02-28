package com.example.frigateeventviewer.data.model

/**
 * Request body for POST /api/snooze/&lt;camera&gt;. Contract §8.1.
 */
data class SnoozeRequest(
    val duration_minutes: Int,
    val snooze_notifications: Boolean = true,
    val snooze_ai: Boolean = true
)
