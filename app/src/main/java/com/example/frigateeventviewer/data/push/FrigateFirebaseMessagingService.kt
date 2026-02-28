package com.example.frigateeventviewer.data.push

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.graphics.drawable.BitmapDrawable
import androidx.core.app.NotificationCompat
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.request.ErrorResult
import com.example.frigateeventviewer.MainActivity
import com.example.frigateeventviewer.R
import com.example.frigateeventviewer.data.api.ApiClient
import com.example.frigateeventviewer.data.preferences.SettingsPreferences
import com.example.frigateeventviewer.data.util.EventMatching
import com.example.frigateeventviewer.ui.util.buildMediaUrl
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

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
        private const val TAG = "FrigateFCM"

        /** Intent extra for content/Play intents so the app can open event detail. */
        const val EXTRA_CE_ID = "com.example.frigateeventviewer.extra.CE_ID"
        /** Intent extra for Play action: path to clip for playback. */
        const val EXTRA_HOSTED_CLIP = "com.example.frigateeventviewer.extra.HOSTED_CLIP"
    }

    /**
     * If the backend sends an absolute path for CE (e.g. under /files/), extract the segment
     * from "events/" so the result is /files/events/<folder_name>/... and matches the server route.
     */
    private fun normalizeFcmMediaPath(path: String?): String? {
        if (path.isNullOrBlank()) return null
        val eventsIndex = path.indexOf("events/")
        return if (eventsIndex >= 0 && !path.startsWith("/files/events/")) "/files/" + path.substring(eventsIndex) else path
    }

    /**
     * Scales [bitmap] to fit within the platform's notification large icon dimensions so the
     * system displays it reliably. Uses android.R.dimen.notification_large_icon_* with a 256dp
     * fallback. Returns the same bitmap if already within bounds.
     */
    private fun scaleBitmapForNotification(bitmap: Bitmap, context: Context): Bitmap {
        val res = context.resources
        val fallbackPx = (256 * res.displayMetrics.density).toInt().coerceAtLeast(1)
        val maxW = try {
            val px = res.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
            if (px > 0) px else fallbackPx
        } catch (_: Exception) { fallbackPx }
        val maxH = try {
            val px = res.getDimensionPixelSize(android.R.dimen.notification_large_icon_height)
            if (px > 0) px else fallbackPx
        } catch (_: Exception) { fallbackPx }
        if (bitmap.width <= maxW && bitmap.height <= maxH) return bitmap
        val scale = min(maxW.toFloat() / bitmap.width, maxH.toFloat() / bitmap.height).coerceAtMost(1f)
        val newW = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val newH = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    /**
     * Loads a bitmap from a full URL (e.g. from FCM image_url when backend serves a public URL).
     * Use for [EventNotification.image_url] so the image loads with no delay and works on cellular.
     */
    private suspend fun loadNotificationBitmapFromUrl(context: Context, url: String): android.graphics.Bitmap? {
        if (url.isBlank()) return null
        return try {
            val result = Coil.imageLoader(context).execute(
                ImageRequest.Builder(context).data(url).allowHardware(false).build()
            )
            (result as? SuccessResult)?.drawable?.let { (it as? BitmapDrawable)?.bitmap }
        } catch (_: Exception) { null }
    }

    /**
     * Loads a bitmap for notification use. Tries [primaryPath] first, then [fallbackPath].
     * [fallbackPath] is normalized via [normalizeFcmMediaPath] so absolute FCM paths work.
     * Runs on caller's context (use Dispatchers.IO); allowHardware(false) for notification bitmaps.
     */
    private suspend fun loadNotificationBitmap(
        context: Context,
        baseUrl: String,
        primaryPath: String?,
        fallbackPath: String?
    ): android.graphics.Bitmap? {
        val primaryUrl = buildMediaUrl(baseUrl, primaryPath)
        if (!primaryUrl.isNullOrBlank()) {
            val result = Coil.imageLoader(context).execute(
                ImageRequest.Builder(context).data(primaryUrl).allowHardware(false).build()
            )
            (result as? SuccessResult)?.drawable?.let { (it as? BitmapDrawable)?.bitmap }?.let { return it }
        }
        val fallbackNormalized = normalizeFcmMediaPath(fallbackPath)
        val fallbackUrl = buildMediaUrl(baseUrl, fallbackNormalized)
        if (!fallbackUrl.isNullOrBlank()) {
            val result = Coil.imageLoader(context).execute(
                ImageRequest.Builder(context).data(fallbackUrl).allowHardware(false).build()
            )
            (result as? SuccessResult)?.drawable?.let { (it as? BitmapDrawable)?.bitmap }?.let { return it }
        }
        return null
    }

    /**
     * Tries to load the notification image from the same source as the events tab: GET /events,
     * find event by ce_id, then use event.hosted_snapshot (else hosted_clip). Returns null on
     * fetch failure, event not found, or bitmap load failure. Logs the failure reason to aid debugging.
     */
    private suspend fun loadNotificationBitmapFromApi(
        context: Context,
        baseUrl: String,
        ceId: String
    ): android.graphics.Bitmap? {
        return try {
            val service = ApiClient.createService(baseUrl)
            val response = service.getEvents("all")
            val event = EventMatching.findEventByCeId(response.events, ceId)
            if (event == null) {
                Log.w(TAG, "Notification image: event not found for ce_id=$ceId")
                return null
            }
            val snapshotPath = event.hosted_snapshot?.takeIf { it.isNotBlank() }
                ?: event.hosted_clip?.takeIf { it.isNotBlank() }
            if (snapshotPath == null) {
                Log.w(TAG, "Notification image: no snapshot/clip path for ce_id=$ceId")
                return null
            }
            val url = buildMediaUrl(baseUrl, snapshotPath)
            if (url == null) {
                Log.w(TAG, "Notification image: buildMediaUrl returned null for ce_id=$ceId")
                return null
            }
            val result = Coil.imageLoader(context).execute(
                ImageRequest.Builder(context).data(url).allowHardware(false).build()
            )
            val bitmap = (result as? SuccessResult)?.drawable?.let { (it as? BitmapDrawable)?.bitmap }
            if (bitmap == null) {
                when (result) {
                    is ErrorResult -> Log.w(TAG, "Notification image: Coil load failed for url=$url", result.throwable)
                    else -> Log.w(TAG, "Notification image: Coil load failed for url=$url (result=${result::class.simpleName})")
                }
                return null
            }
            Log.d(TAG, "Notification image loaded from API for ce_id=$ceId")
            bitmap
        } catch (e: Exception) {
            Log.w(TAG, "Notification image from API failed", e)
            null
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            FcmTokenManager(applicationContext).registerToken(token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "onMessageReceived: from=${remoteMessage.from}, data keys=${remoteMessage.data?.keys?.joinToString()}")
        val data = remoteMessage.data
        if (data == null || data.isEmpty()) {
            Log.w(TAG, "onMessageReceived: no data payload, ignoring (notification payload is handled by system when app in background)")
            return
        }

        serviceScope.launch {
            val baseUrl = SettingsPreferences(applicationContext).getBaseUrlOnce()
            if (baseUrl == null) {
                Log.w(TAG, "Skipping FCM: no base URL set. Set server URL in Settings to receive event notifications.")
                return@launch
            }

            val parsed = EventNotification.from(data)
            Log.d(TAG, "Parsed FCM: ce_id=${parsed.ce_id}, phase=${parsed.phase}")
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
                NotificationPhase.UNKNOWN -> {
                    Log.w(TAG, "FCM phase UNKNOWN for ce_id=${parsed.ce_id}; ensure server sends phase: NEW|SNAPSHOT_READY|CLIP_READY|DISCARDED (data keys are case-sensitive in FCM)")
                }
            }
        }
    }

    /**
     * NEW phase: "Motion Detected" with live frame as large icon only (no BigPictureStyle).
     * First tries API snapshot (same source as events tab), then FCM paths.
     */
    private suspend fun handleNew(
        context: Context,
        baseUrl: String,
        notificationId: Int,
        eventNotification: EventNotification,
        notificationManager: NotificationManager
    ) {
        var scaledBitmap = NotificationImageCache.get(eventNotification.ce_id)
        if (scaledBitmap == null) {
        var bitmap: android.graphics.Bitmap? = null
        val imageUrl = eventNotification.image_url
        if (!imageUrl.isNullOrBlank() && (imageUrl.startsWith("http://") || imageUrl.startsWith("https://"))) {
            bitmap = loadNotificationBitmapFromUrl(context, imageUrl)
        }
        if (bitmap == null) {
        delay(2000) // Let VPN become default network after FCM wake (e.g. Tailscale on cellular)
        bitmap = loadNotificationBitmapFromApi(context, baseUrl, eventNotification.ce_id)
        if (bitmap == null) {
            delay(1500)
            bitmap = loadNotificationBitmapFromApi(context, baseUrl, eventNotification.ce_id)
        }
        if (bitmap == null) {
            bitmap = loadNotificationBitmap(
                context,
                baseUrl,
                eventNotification.live_frame_proxy,
                eventNotification.cropped_image_url
            )
        }
        }
        if (bitmap == null) {
            Log.w(TAG, "Notification image: no bitmap (API + fallback failed) for ce_id=${eventNotification.ce_id}")
        }
        scaledBitmap = bitmap?.let { scaleBitmapForNotification(it, context) }
        scaledBitmap?.let { NotificationImageCache.put(eventNotification.ce_id, it) }
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
        if (scaledBitmap != null) builder.setLargeIcon(scaledBitmap)
        builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Motion Detected")
            .setContentText(subText)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(notificationId, builder.build())
    }

    /**
     * SNAPSHOT_READY phase: update the same slot with BigPictureStyle cropped snapshot.
     * First tries API snapshot (same source as events tab), then FCM paths.
     */
    private suspend fun handleSnapshotReady(
        context: Context,
        baseUrl: String,
        notificationId: Int,
        eventNotification: EventNotification,
        notificationManager: NotificationManager
    ) {
        var scaledBitmap = NotificationImageCache.get(eventNotification.ce_id)
        if (scaledBitmap == null) {
        var bitmap: android.graphics.Bitmap? = null
        val imageUrl = eventNotification.image_url
        if (!imageUrl.isNullOrBlank() && (imageUrl.startsWith("http://") || imageUrl.startsWith("https://"))) {
            bitmap = loadNotificationBitmapFromUrl(context, imageUrl)
        }
        if (bitmap == null) {
        delay(2000) // Let VPN become default network after FCM wake (e.g. Tailscale on cellular)
        bitmap = loadNotificationBitmapFromApi(context, baseUrl, eventNotification.ce_id)
        if (bitmap == null) {
            delay(1500)
            bitmap = loadNotificationBitmapFromApi(context, baseUrl, eventNotification.ce_id)
        }
        if (bitmap == null) {
            bitmap = loadNotificationBitmap(
                context,
                baseUrl,
                eventNotification.hosted_snapshot,
                eventNotification.cropped_image_url
            )
        }
        }
        if (bitmap == null) {
            Log.w(TAG, "Notification image: no bitmap (API + fallback failed) for ce_id=${eventNotification.ce_id}")
        }
        scaledBitmap = bitmap?.let { scaleBitmapForNotification(it, context) }
        scaledBitmap?.let { NotificationImageCache.put(eventNotification.ce_id, it) }
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
        if (scaledBitmap != null) builder.setLargeIcon(scaledBitmap)
        builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(bodyText)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
        if (scaledBitmap != null) {
            builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(scaledBitmap))
        }

        notificationManager.notify(notificationId, builder.build())
    }

    /**
     * CLIP_READY phase: AI title/description, "Play" action, teaser as large icon.
     * First tries API snapshot (same source as events tab), then FCM paths.
     */
    private suspend fun handleClipReady(
        context: Context,
        baseUrl: String,
        notificationId: Int,
        eventNotification: EventNotification,
        notificationManager: NotificationManager
    ) {
        var scaledBitmap = NotificationImageCache.get(eventNotification.ce_id)
        if (scaledBitmap == null) {
        var bitmap: android.graphics.Bitmap? = null
        val imageUrl = eventNotification.image_url
        if (!imageUrl.isNullOrBlank() && (imageUrl.startsWith("http://") || imageUrl.startsWith("https://"))) {
            bitmap = loadNotificationBitmapFromUrl(context, imageUrl)
        }
        if (bitmap == null) {
        delay(2000) // Let VPN become default network after FCM wake (e.g. Tailscale on cellular)
        bitmap = loadNotificationBitmapFromApi(context, baseUrl, eventNotification.ce_id)
        if (bitmap == null) {
            delay(1500)
            bitmap = loadNotificationBitmapFromApi(context, baseUrl, eventNotification.ce_id)
        }
        if (bitmap == null) {
            bitmap = loadNotificationBitmap(
                context,
                baseUrl,
                eventNotification.notification_gif,
                eventNotification.cropped_image_url
            )
        }
        }
        if (bitmap == null) {
            Log.w(TAG, "Notification image: no bitmap (API + fallback failed) for ce_id=${eventNotification.ce_id}")
        }
        scaledBitmap = bitmap?.let { scaleBitmapForNotification(it, context) }
        scaledBitmap?.let { NotificationImageCache.put(eventNotification.ce_id, it) }
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
        if (scaledBitmap != null) builder.setLargeIcon(scaledBitmap)
        builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(bodyText)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_media_play, "Play", playPendingIntent)
            .addAction(android.R.drawable.checkbox_on_background, "Mark Reviewed", markReviewedPendingIntent)
            .addAction(android.R.drawable.ic_menu_save, "Keep", keepPendingIntent)
        if (scaledBitmap != null) {
            builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(scaledBitmap))
        }

        notificationManager.notify(notificationId, builder.build())
    }
}
