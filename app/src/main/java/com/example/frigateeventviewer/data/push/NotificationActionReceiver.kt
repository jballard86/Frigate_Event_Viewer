package com.example.frigateeventviewer.data.push

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.frigateeventviewer.R
import com.example.frigateeventviewer.data.api.ApiClient
import com.example.frigateeventviewer.data.preferences.SettingsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Handles notification action button clicks for CLIP_READY notifications.
 * Mark Reviewed: calls POST /viewed/events/{ce_id}, cancels the notification, shows Toast.
 * Keep: calls POST /keep/events/{ce_id}, updates the notification to "Saved", shows Toast.
 *
 * Registered with [android:exported="false"] so only this app can trigger it (PendingIntents
 * from the app are still delivered). Prevents malicious apps from spoofing Mark Reviewed/Keep.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val ceId = intent.getStringExtra(EXTRA_CE_ID)?.takeIf { it.isNotBlank() }
            ?: run {
                Toast.makeText(context, "Invalid notification action", Toast.LENGTH_SHORT).show()
                return
            }
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val baseUrl = runBlocking { SettingsPreferences(context).getBaseUrlOnce() }
        if (baseUrl.isNullOrBlank()) {
            Toast.makeText(context, "Not configured", Toast.LENGTH_SHORT).show()
            return
        }
        val eventPath = "events/$ceId"

        when (intent.action) {
            ACTION_MARK_REVIEWED -> handleMarkReviewed(context, baseUrl, eventPath, notificationId)
            ACTION_KEEP -> handleKeep(context, baseUrl, eventPath, notificationId)
            else -> { /* ignore */ }
        }
    }

    private fun handleMarkReviewed(
        context: Context,
        baseUrl: String,
        eventPath: String,
        notificationId: Int
    ) {
        val success = runBlocking(Dispatchers.IO) {
            try {
                ApiClient.createService(baseUrl).markViewed(eventPath)
                true
            } catch (_: Exception) {
                false
            }
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
        if (success) {
            Toast.makeText(context, "Marked reviewed", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Failed to mark reviewed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleKeep(
        context: Context,
        baseUrl: String,
        eventPath: String,
        notificationId: Int
    ) {
        val success = runBlocking(Dispatchers.IO) {
            try {
                ApiClient.createService(baseUrl).keepEvent(eventPath)
                true
            } catch (_: Exception) {
                false
            }
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (success) {
            val builder = NotificationCompat.Builder(context, PushConstants.CHANNEL_ID_SECURITY_ALERTS)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Saved")
                .setContentText("Event kept.")
            notificationManager.notify(notificationId, builder.build())
            Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Failed to save", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val ACTION_MARK_REVIEWED = "com.example.frigateeventviewer.MARK_REVIEWED"
        const val ACTION_KEEP = "com.example.frigateeventviewer.KEEP"
        const val EXTRA_CE_ID = "com.example.frigateeventviewer.extra.CE_ID"
        const val EXTRA_NOTIFICATION_ID = "com.example.frigateeventviewer.extra.NOTIFICATION_ID"
    }
}
