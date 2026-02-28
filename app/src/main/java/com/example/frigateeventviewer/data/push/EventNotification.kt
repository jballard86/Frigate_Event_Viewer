package com.example.frigateeventviewer.data.push

import java.util.UUID

/**
 * Phase of a consolidated event as sent in FCM data payload.
 * Drives notification behavior: NEW (motion), SNAPSHOT_READY (crop), CLIP_READY (play), DISCARDED (cancel).
 */
enum class NotificationPhase {
    NEW,
    SNAPSHOT_READY,
    CLIP_READY,
    DISCARDED,
    UNKNOWN
}

/**
 * Typed representation of FCM data payload for event notifications.
 * Parsed from [RemoteMessage.getData] Map<String, String>; used by
 * [FrigateFirebaseMessagingService] for phase-aware notification handling.
 *
 * All fields except [ce_id] and [phase] are nullable. [ce_id] is never blank
 * after parsing (fallback to random UUID when missing so every message gets a slot).
 */
data class EventNotification(
    val ce_id: String,
    val phase: NotificationPhase,
    val clear_notification: Boolean,
    val threat_level: Int,
    val camera: String?,
    val live_frame_proxy: String?,
    val hosted_snapshot: String?,
    val notification_gif: String?,
    /** Cropped snapshot path sent by backend in FCM; only media key currently sent. Used as fallback for notification image when phase-specific keys are absent. */
    val cropped_image_url: String?,
    /** Full URL to the notification image (e.g. Firebase Storage or other public URL). When set, app loads from this first with no delay; avoids VPN/private-network issues on cellular. */
    val image_url: String?,
    val title: String?,
    val description: String?,
    val hosted_clip: String?
) {
    companion object {
        private const val KEY_CE_ID = "ce_id"
        private const val KEY_PHASE = "phase"
        private const val KEY_CLEAR_NOTIFICATION = "clear_notification"
        private const val KEY_THREAT_LEVEL = "threat_level"
        private const val KEY_CAMERA = "camera"
        private const val KEY_LIVE_FRAME_PROXY = "live_frame_proxy"
        private const val KEY_HOSTED_SNAPSHOT = "hosted_snapshot"
        private const val KEY_NOTIFICATION_GIF = "notification_gif"
        private const val KEY_NOTIFICATION_GIF_ALT = "notification.gif"
        private const val KEY_CROPPED_IMAGE_URL = "cropped_image_url"
        private const val KEY_IMAGE_URL = "image_url"
        private const val KEY_TITLE = "title"
        private const val KEY_DESCRIPTION = "description"
        private const val KEY_HOSTED_CLIP = "hosted_clip"

        /**
         * Parses FCM data map into [EventNotification]. Safe for missing or invalid values:
         * booleans and integers are cast from strings; unknown phase becomes [NotificationPhase.UNKNOWN];
         * missing [ce_id] is replaced with a random UUID so the notification can still be slotted.
         */
        fun from(data: Map<String, String>): EventNotification {
            val ceId = data[KEY_CE_ID].takeIf { !it.isNullOrBlank() } ?: UUID.randomUUID().toString()
            val phase = parsePhase(data[KEY_PHASE])
            val clearNotification = data[KEY_CLEAR_NOTIFICATION].let { v ->
                v != null && v.equals("true", ignoreCase = true)
            }
            val threatLevel = data[KEY_THREAT_LEVEL]?.toIntOrNull() ?: 0
            val camera = data[KEY_CAMERA].takeIf { !it.isNullOrBlank() }
            val liveFrameProxy = data[KEY_LIVE_FRAME_PROXY].takeIf { !it.isNullOrBlank() }
            val hostedSnapshot = data[KEY_HOSTED_SNAPSHOT].takeIf { !it.isNullOrBlank() }
            val notificationGif = data[KEY_NOTIFICATION_GIF].takeIf { !it.isNullOrBlank() }
                ?: data[KEY_NOTIFICATION_GIF_ALT].takeIf { !it.isNullOrBlank() }
            val croppedImageUrl = data[KEY_CROPPED_IMAGE_URL].takeIf { !it.isNullOrBlank() }
            val imageUrl = data[KEY_IMAGE_URL].takeIf { !it.isNullOrBlank() }
            val title = data[KEY_TITLE].takeIf { !it.isNullOrBlank() }
            val description = data[KEY_DESCRIPTION].takeIf { !it.isNullOrBlank() }
            val hostedClip = data[KEY_HOSTED_CLIP].takeIf { !it.isNullOrBlank() }

            return EventNotification(
                ce_id = ceId,
                phase = phase,
                clear_notification = clearNotification,
                threat_level = threatLevel,
                camera = camera,
                live_frame_proxy = liveFrameProxy,
                hosted_snapshot = hostedSnapshot,
                notification_gif = notificationGif,
                cropped_image_url = croppedImageUrl,
                image_url = imageUrl,
                title = title,
                description = description,
                hosted_clip = hostedClip
            )
        }

        private fun parsePhase(value: String?): NotificationPhase = when (value?.uppercase()) {
            "NEW" -> NotificationPhase.NEW
            "SNAPSHOT_READY" -> NotificationPhase.SNAPSHOT_READY
            "CLIP_READY" -> NotificationPhase.CLIP_READY
            "DISCARDED" -> NotificationPhase.DISCARDED
            else -> NotificationPhase.UNKNOWN
        }
    }
}
