package com.example.frigateeventviewer.data.push

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.frigateeventviewer.R
import com.example.frigateeventviewer.data.api.ApiClient
import com.example.frigateeventviewer.data.preferences.SettingsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Updates the app icon badge from [UnreadState]. Fetches GET /api/events/unread_count on resume
 * and applies the badge; callers record mark-reviewed/delete in UnreadState then call [applyBadge].
 *
 * Does not hold its own count; single source of truth is [UnreadState].
 */
object UnreadBadgeHelper {

    /**
     * Fetches GET /api/events/unread_count, records the count in [UnreadState], then applies
     * the badge. Call from MainActivity onResume().
     */
    fun updateFromServer(context: Context) {
        ProcessLifecycleOwner.get().lifecycleScope.launch(Dispatchers.IO) {
            val baseUrl = SettingsPreferences(context).getBaseUrlOnce()
            if (baseUrl.isNullOrBlank()) return@launch
            try {
                val response = ApiClient.createService(baseUrl).getUnreadCount()
                UnreadState.recordFetchedUnreadCount(response.unread_count)
                withContext(Dispatchers.Main) {
                    applyBadge(context, UnreadState.currentEffectiveUnreadCount())
                }
            } catch (_: Exception) {
                // Ignore; badge will update on next resume
            }
        }
    }

    /**
     * Posts or cancels the silent badge notification with the given count.
     * Call after [UnreadState.recordMarkedReviewed] / [recordDeleted] (e.g. from MainActivity
     * or NotificationActionReceiver) so the badge updates immediately.
     */
    fun applyBadge(context: Context, count: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (count <= 0) {
            notificationManager.cancel(PushConstants.BADGE_NOTIFICATION_ID)
        } else {
            val builder = NotificationCompat.Builder(context, PushConstants.CHANNEL_ID_BADGE)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Unreviewed events")
                .setContentText(if (count == 1) "1 event" else "$count events")
                .setNumber(count)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setSilent(true)
            notificationManager.notify(PushConstants.BADGE_NOTIFICATION_ID, builder.build())
        }
    }
}
