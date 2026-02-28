package com.example.frigateeventviewer.data.push

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
 * When displaying notifications, use [PushConstants.CHANNEL_ID_SECURITY_ALERTS]
 * so they appear in the "Security Alerts" channel and so ce_id can be hashed
 * into a deterministic notification ID.
 */
class FrigateFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            FcmTokenManager(applicationContext).registerToken(token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        // No-op for now; Rich Handler can post notifications using PushConstants.CHANNEL_ID_SECURITY_ALERTS
    }
}
