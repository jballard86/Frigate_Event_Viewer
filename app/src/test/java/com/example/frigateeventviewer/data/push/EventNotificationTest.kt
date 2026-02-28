package com.example.frigateeventviewer.data.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [EventNotification.from] and [notificationId].
 * Setup → Execute → Verify; no complex logic in tests.
 */
class EventNotificationTest {

    @Test
    fun from_fullPayload_parsesAllFields() {
        val data = mapOf(
            "ce_id" to "ce_123",
            "phase" to "NEW",
            "clear_notification" to "false",
            "threat_level" to "2",
            "camera" to "front_door",
            "live_frame_proxy" to "/api/cameras/front_door/latest.jpg",
            "hosted_snapshot" to "/files/events/ce_123/snapshot.jpg",
            "notification_gif" to "/files/events/ce_123/teaser.gif",
            "title" to "Person at door",
            "description" to "Someone approached.",
            "hosted_clip" to "/files/events/ce_123/clip.mp4"
        )

        val result = EventNotification.from(data)

        assertEquals("ce_123", result.ce_id)
        assertEquals(NotificationPhase.NEW, result.phase)
        assertFalse(result.clear_notification)
        assertEquals(2, result.threat_level)
        assertEquals("front_door", result.camera)
        assertEquals("/api/cameras/front_door/latest.jpg", result.live_frame_proxy)
        assertEquals("/files/events/ce_123/snapshot.jpg", result.hosted_snapshot)
        assertEquals("/files/events/ce_123/teaser.gif", result.notification_gif)
        assertEquals("Person at door", result.title)
        assertEquals("Someone approached.", result.description)
        assertEquals("/files/events/ce_123/clip.mp4", result.hosted_clip)
    }

    @Test
    fun from_clearNotificationTrue_parsesTrue() {
        val data = mapOf("ce_id" to "x", "clear_notification" to "true")

        val result = EventNotification.from(data)

        assertTrue(result.clear_notification)
    }

    @Test
    fun from_clearNotificationTrueCaseInsensitive_parsesTrue() {
        val data = mapOf("ce_id" to "x", "clear_notification" to "TRUE")

        val result = EventNotification.from(data)

        assertTrue(result.clear_notification)
    }

    @Test
    fun from_clearNotificationFalse_parsesFalse() {
        val data = mapOf("ce_id" to "x", "clear_notification" to "false")

        val result = EventNotification.from(data)

        assertFalse(result.clear_notification)
    }

    @Test
    fun from_threatLevelInvalid_defaultsToZero() {
        val data = mapOf("ce_id" to "x", "threat_level" to "x")

        val result = EventNotification.from(data)

        assertEquals(0, result.threat_level)
    }

    @Test
    fun from_threatLevelMissing_defaultsToZero() {
        val data = mapOf("ce_id" to "x")

        val result = EventNotification.from(data)

        assertEquals(0, result.threat_level)
    }

    @Test
    fun from_phaseSnapshotReady_parsesEnum() {
        val data = mapOf("ce_id" to "x", "phase" to "SNAPSHOT_READY")

        val result = EventNotification.from(data)

        assertEquals(NotificationPhase.SNAPSHOT_READY, result.phase)
    }

    @Test
    fun from_phaseClipReady_parsesEnum() {
        val data = mapOf("ce_id" to "x", "phase" to "CLIP_READY")

        val result = EventNotification.from(data)

        assertEquals(NotificationPhase.CLIP_READY, result.phase)
    }

    @Test
    fun from_phaseDiscarded_parsesEnum() {
        val data = mapOf("ce_id" to "x", "phase" to "DISCARDED")

        val result = EventNotification.from(data)

        assertEquals(NotificationPhase.DISCARDED, result.phase)
    }

    @Test
    fun from_phaseUnknown_parsesAsUnknown() {
        val data = mapOf("ce_id" to "x", "phase" to "INVALID")

        val result = EventNotification.from(data)

        assertEquals(NotificationPhase.UNKNOWN, result.phase)
    }

    @Test
    fun from_ceIdMissing_usesFallbackNonBlank() {
        val data = mapOf<String, String>("phase" to "NEW")

        val result = EventNotification.from(data)

        assertNotNull(result.ce_id)
        assertTrue(result.ce_id.isNotBlank())
    }

    @Test
    fun from_notificationGifAltKey_usesNotificationGifPath() {
        val data = mapOf("ce_id" to "x", "notification.gif" to "/path/teaser.gif")

        val result = EventNotification.from(data)

        assertEquals("/path/teaser.gif", result.notification_gif)
    }

    @Test
    fun notificationId_sameCeId_returnsSameInt() {
        val id1 = notificationId("ce_123")
        val id2 = notificationId("ce_123")

        assertEquals(id1, id2)
    }

    @Test
    fun notificationId_differentCeId_returnsDifferentInt() {
        val id1 = notificationId("ce_123")
        val id2 = notificationId("ce_456")

        assertTrue(id1 != id2)
    }

    @Test
    fun notificationId_returnsPositive() {
        val id = notificationId("ce_any")

        assertTrue(id > 0)
    }

    @Test
    fun notificationId_deterministic_multipleCallsSameResult() {
        val ids = (1..10).map { notificationId("ce_stable") }

        ids.forEach { assertEquals(ids[0], it) }
    }
}
