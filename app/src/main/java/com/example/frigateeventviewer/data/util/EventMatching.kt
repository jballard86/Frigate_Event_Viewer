package com.example.frigateeventviewer.data.util

import com.example.frigateeventviewer.data.model.Event

/**
 * Shared logic for matching an event to a ce_id from a deep link or FCM.
 * Backend returns consolidated events with event_id/subdir = folder name (no ce_ prefix);
 * FCM sends ce_id with the prefix (e.g. ce_1772252671_bf1c91a6). So we match both exact
 * and stripped forms.
 */
object EventMatching {

    /**
     * Returns true if this event corresponds to the given ce_id from a deep link or FCM.
     */
    fun eventMatchesCeId(event: Event, ceId: String): Boolean {
        if (ceId.isBlank()) return false
        val folderName = ceId.removePrefix("ce_")
        return event.event_id == ceId ||
            (event.camera == "events" && event.subdir == ceId) ||
            event.event_id == folderName ||
            (event.camera == "events" && event.subdir == folderName)
    }

    /**
     * Finds the first event in the list that matches the given ce_id.
     */
    fun findEventByCeId(events: List<Event>, ceId: String): Event? =
        events.firstOrNull { eventMatchesCeId(it, ceId) }
}
