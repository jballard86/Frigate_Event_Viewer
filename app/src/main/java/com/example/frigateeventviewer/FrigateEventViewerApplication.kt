package com.example.frigateeventviewer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.frigateeventviewer.data.push.PushConstants
import com.example.frigateeventviewer.ui.util.StreamingVideoFetcher

/**
 * Application entry point. Provides a global Coil [ImageLoader] configured with
 * [StreamingVideoFetcher] so that .mp4 thumbnail URLs are streamed via
 * MediaMetadataRetriever (frame at 2s) without full-file download. This avoids
 * ProtocolException and network saturation. Use the default Coil ImageLoader app-wide.
 *
 * Creates the "Security Alerts" notification channel (IMPORTANCE_HIGH) for FCM so
 * push notifications display with correct importance. Channel ID is [PushConstants.CHANNEL_ID_SECURITY_ALERTS].
 */
class FrigateEventViewerApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PushConstants.CHANNEL_ID_SECURITY_ALERTS,
                "Security Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
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
