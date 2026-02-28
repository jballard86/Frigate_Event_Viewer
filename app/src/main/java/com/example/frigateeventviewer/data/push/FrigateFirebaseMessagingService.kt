package com.example.frigateeventviewer.data.push

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import androidx.core.app.NotificationCompat
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.frigateeventviewer.MainActivity
import com.example.frigateeventviewer.R
import com.example.frigateeventviewer.data.preferences.SettingsPreferences
import com.example.frigateeventviewer.ui.util.buildMediaUrl
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles FCM messages and token rotation. When Firebase issues a new token,
 * [onNewToken] registers it with the Frigate Event Buffer so the backend can
 * send push notifications.
 *
 * Notification content follows Material 3 typography semantics: concise title
 * and body text (setContentTitle / setContentText, or BigPictureStyle where used).
 * All notifications use [PushConstants.CHANNEL_ID_SECURITY_ALERTS] and
 * [notificationId] so updates for the same event overwrite the previous alert.
 */
class FrigateFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        /** Intent extra for content/Play intents so the app can open event detail. */
        const val EXTRA_CE_ID = "com.example.frigateeventviewer.extra.CE_ID"
        /** Intent extra for Play action: path to clip for playback. */
        const val EXTRA_HOSTED_CLIP = "com.example.frigateeventviewer.extra.HOSTED_CLIP"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            FcmTokenManager(applicationContext).registerToken(token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data ?: return
        if (data.isEmpty()) return

        serviceScope.launch {
            val baseUrl = SettingsPreferences(applicationContext).getBaseUrlOnce()
            if (baseUrl == null) return@launch

            val parsed = EventNotification.from(data)
            val nid = notificationId(parsed.ce_id)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (parsed.clear_notification || parsed.phase == NotificationPhase.DISCARDED) {
                notificationManager.cancel(nid)
                return@launch
            }

            when (parsed.phase) {
                NotificationPhase.NEW -> handleNew(applicationContext, baseUrl, nid, parsed, notificationManager)
                NotificationPhase.SNAPSHOT_READY -> handleSnapshotReady(applicationContext, baseUrl, nid, parsed, notificationManager)
                NotificationPhase.CLIP_READY -> handleClipReady(applicationContext, baseUrl, nid, parsed, notificationManager)
                NotificationPhase.DISCARDED -> { /* already cancelled above */ }
                NotificationPhase.UNKNOWN -> { /* no-op; could show generic alert if desired */ }
            }
        }
    }

    /**
     * NEW phase: "Motion Detected" with live frame as large icon only (no BigPictureStyle).
     * Media is loaded on the calling thread (already [Dispatchers.IO]); allowHardware(false) is required for notification bitmaps.
     */
    private fun handleNew(
        context: Context,
        baseUrl: String,
        notificationId: Int,
        eventNotification: EventNotification,
        notificationManager: NotificationManager
    ) {
        val liveFrameUrl = buildMediaUrl(baseUrl, eventNotification.live_frame_proxy)

        val bitmap = if (!liveFrameUrl.isNullOrBlank()) {
            val request = ImageRequest.Builder(context)
                .data(liveFrameUrl)
                .allowHardware(false)
                .build()
            val result = Coil.imageLoader(context).execute(request)
            (result as? SuccessResult)?.drawable?.let { (it as? BitmapDrawable)?.bitmap }
        } else {
            null
        }

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CE_ID, eventNotification.ce_id)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val subText = eventNotification.camera?.let { "Camera: $it" } ?: "Security alert"
        val builder = NotificationCompat.Builder(context, PushConstants.CHANNEL_ID_SECURITY_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Motion Detected")
            .setContentText(subText)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
        bitmap?.let { builder.setLargeIcon(it) }

        notificationManager.notify(notificationId, builder.build())
    }

    /**
     * SNAPSHOT_READY phase: update the same slot with BigPictureStyle cropped snapshot (visual "expansion" from large icon).
     * Media is loaded on the calling thread (already [Dispatchers.IO]); allowHardware(false) is required for notification bitmaps.
     */
    private fun handleSnapshotReady(
        context: Context,
        baseUrl: String,
        notificationId: Int,
        eventNotification: EventNotification,
        notificationManager: NotificationManager
    ) {
        val snapshotUrl = buildMediaUrl(baseUrl, eventNotification.hosted_snapshot)

        val bitmap = if (!snapshotUrl.isNullOrBlank()) {
            val request = ImageRequest.Builder(context)
                .data(snapshotUrl)
                .allowHardware(false)
                .build()
            val result = Coil.imageLoader(context).execute(request)
            (result as? SuccessResult)?.drawable?.let { (it as? BitmapDrawable)?.bitmap }
        } else {
            null
        }

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CE_ID, eventNotification.ce_id)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = eventNotification.camera?.let { "Snapshot: $it" } ?: "Snapshot ready"
        val bodyText = "Cropped snapshot available"
        val builder = NotificationCompat.Builder(context, PushConstants.CHANNEL_ID_SECURITY_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(bodyText)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
        if (bitmap != null) {
            builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(bitmap))
        }

        notificationManager.notify(notificationId, builder.build())
    }

    /**
     * CLIP_READY phase: AI title/description, "Play" action, teaser first frame as large icon.
     * When notification.gif is available, Coil decodes the first frame for the large icon.
     * Media is loaded on the calling thread (already [Dispatchers.IO]); allowHardware(false) is required for notification bitmaps.
     */
    private fun handleClipReady(
        context: Context,
        baseUrl: String,
        notificationId: Int,
        eventNotification: EventNotification,
        notificationManager: NotificationManager
    ) {
        val teaserUrl = buildMediaUrl(baseUrl, eventNotification.notification_gif)

        val bitmap = if (!teaserUrl.isNullOrBlank()) {
            val request = ImageRequest.Builder(context)
                .data(teaserUrl)
                .allowHardware(false)
                .build()
            val result = Coil.imageLoader(context).execute(request)
            (result as? SuccessResult)?.drawable?.let { (it as? BitmapDrawable)?.bitmap }
        } else {
            null
        }

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CE_ID, eventNotification.ce_id)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CE_ID, eventNotification.ce_id)
            eventNotification.hosted_clip?.let { putExtra(EXTRA_HOSTED_CLIP, it) }
        }
        val playPendingIntent = PendingIntent.getActivity(
            context,
            notificationId + 1,
            playIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val markReviewedIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_REVIEWED
            setPackage(context.packageName)
            putExtra(EXTRA_CE_ID, eventNotification.ce_id)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val markReviewedPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 2,
            markReviewedIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val keepIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_KEEP
            setPackage(context.packageName)
            putExtra(EXTRA_CE_ID, eventNotification.ce_id)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val keepPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 3,
            keepIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = eventNotification.title?.takeIf { it.isNotBlank() } ?: "Event ready"
        val bodyText = eventNotification.description?.takeIf { it.isNotBlank() } ?: "Tap to view"
        val builder = NotificationCompat.Builder(context, PushConstants.CHANNEL_ID_SECURITY_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(bodyText)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_media_play, "Play", playPendingIntent)
            .addAction(android.R.drawable.ic_menu_check_holo_dark, "Mark Reviewed", markReviewedPendingIntent)
            .addAction(android.R.drawable.ic_menu_save, "Keep", keepPendingIntent)
        bitmap?.let { builder.setLargeIcon(it) }

        notificationManager.notify(notificationId, builder.build())
    }
}
