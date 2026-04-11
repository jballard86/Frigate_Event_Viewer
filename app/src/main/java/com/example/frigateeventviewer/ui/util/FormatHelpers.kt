package com.example.frigateeventviewer.ui.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Parses API timestamp strings to epoch seconds.
 * Handles integer strings and fractional seconds (e.g. "1234567890.5") that [String.toLongOrNull] rejects.
 */
fun parseTimestampToEpochSeconds(timestamp: String): Long {
    if (timestamp.isBlank()) return 0L
    return timestamp.toDoubleOrNull()?.toLong()
        ?: timestamp.substringBefore('.').toLongOrNull()
        ?: 0L
}

/**
 * Formats Unix timestamp string to readable date/time using java.time (12-hour format).
 */
fun formatTimestamp(timestamp: String): String {
    val seconds = parseTimestampToEpochSeconds(timestamp)
    val instant = Instant.ofEpochSecond(seconds)
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}

/**
 * Formats camera name for display (e.g. "front_door" -> "Front door").
 */
fun formatCameraName(camera: String): String {
    return camera
        .replace('_', ' ')
        .split(' ')
        .joinToString(" ") { word ->
            word.replaceFirstChar { c ->
                if (c.isLowerCase()) c.uppercase() else c.toString()
            }
        }
}
