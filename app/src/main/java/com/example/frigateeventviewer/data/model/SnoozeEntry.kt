package com.example.frigateeventviewer.data.model

/**
 * One camera's snooze entry in GET /api/snooze response. Contract §8.2.
 */
data class SnoozeEntry(
    val expiration_time: Double,
    val snooze_notifications: Boolean,
    val snooze_ai: Boolean
)
