package com.example.frigateeventviewer.data.push

import android.content.Context
import com.example.frigateeventviewer.data.api.ApiClient
import com.example.frigateeventviewer.data.model.RegisterDeviceRequest
import com.example.frigateeventviewer.data.preferences.SettingsPreferences
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Fetches the FCM device token and registers it with the Frigate Event Buffer server.
 * Base URL is always read from [SettingsPreferences]; never hardcoded.
 *
 * Use [registerIfPossible] on app start and after first-run Save. Use [registerToken]
 * from [FrigateFirebaseMessagingService.onNewToken] when Firebase rotates the token.
 */
class FcmTokenManager(private val context: Context) {

    private val preferences = SettingsPreferences(context)

    /**
     * Fetches the current FCM token, then if a base URL is set, POSTs it to
     * POST /api/mobile/register. Fire-and-forget; logs or ignores errors.
     */
    suspend fun registerIfPossible() {
        val token = fetchFcmToken() ?: return
        registerToken(token)
    }

    /**
     * POSTs the given [token] to the server if a base URL is available in preferences.
     * Call from [FrigateFirebaseMessagingService.onNewToken] so the backend keeps the
     * current token after rotation.
     */
    suspend fun registerToken(token: String) {
        if (token.isBlank()) return
        val baseUrl = preferences.getBaseUrlOnce() ?: return
        try {
            val service = ApiClient.createService(baseUrl)
            service.registerDevice(RegisterDeviceRequest(token))
        } catch (_: Exception) {
            // Ignore or log; no UI in this layer
        }
    }

    private suspend fun fetchFcmToken(): String? = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    if (!token.isNullOrBlank()) cont.resume(token) { _ -> }
                    else cont.resume(null) { _ -> }
                } else {
                    cont.resume(null) { _ -> }
                }
            }
    }
}
