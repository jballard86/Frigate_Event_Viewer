package com.example.frigateeventviewer.data.push

/**
 * Shared constants for FCM and notifications.
 * Used by [FrigateEventViewerApplication] when creating the channel and by
 * [FrigateFirebaseMessagingService] when posting notifications (e.g. for deterministic
 * notification IDs based on ce_id).
 */
object PushConstants {
    /** Notification channel ID for security-alert push messages. Importance is set in Application. */
    const val CHANNEL_ID_SECURITY_ALERTS = "security_alerts"
}

/**
 * Returns a deterministic, non-negative notification ID for the given [ce_id].
 * Same ce_id always yields the same ID so updates for the same event overwrite the previous
 * notification instead of creating duplicates. Safe for use with [NotificationManager.notify] and [NotificationManager.cancel].
 */
fun notificationId(ce_id: String): Int {
    val hash = ce_id.fold(0) { acc, c -> 31 * acc + c.code }
    return (hash and 0x7FFF_FFFF).let { if (it == 0) 1 else it }
}
