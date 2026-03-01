package com.example.frigateeventviewer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.frigateeventviewer.data.push.PushConstants
import com.example.frigateeventviewer.data.Go2RtcStreamsRepository
import com.example.frigateeventviewer.ui.util.StreamingVideoFetcher

/**
 * Application entry point. Provides a global Coil [ImageLoader] configured with
 * [StreamingVideoFetcher] so that .mp4 thumbnail URLs are streamed via
 * MediaMetadataRetriever (frame at 2s) without full-file download. This avoids
 * ProtocolException and network saturation. Use the default Coil ImageLoader app-wide.
 *
 * Creates the "Security Alerts" notification channel (IMPORTANCE_HIGH) for FCM and the
 * "Unread count" badge channel (IMPORTANCE_LOW, no sound) for the app icon badge.
 * Channel IDs: [PushConstants.CHANNEL_ID_SECURITY_ALERTS], [PushConstants.CHANNEL_ID_BADGE].
 */
class FrigateEventViewerApplication : Application(), ImageLoaderFactory {

    /** Shared cache of go2rtc stream names; fetch on app load and when Frigate IP changes in Settings. Lazy so context is ready. */
    val go2RtcStreamsRepository by lazy { Go2RtcStreamsRepository(this) }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val securityChannel = NotificationChannel(
                PushConstants.CHANNEL_ID_SECURITY_ALERTS,
                "Security Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(securityChannel)
            val badgeChannel = NotificationChannel(
                PushConstants.CHANNEL_ID_BADGE,
                "Unread count",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(true)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(badgeChannel)
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(StreamingVideoFetcher.Factory())
            }
            .build()
    }
}
