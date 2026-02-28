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
