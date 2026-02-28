# Phase 4 addendum — paste into plan if 4.2 bullets and 4.3–4.5 are missing

Replace the two bullets under **4.2 Invoke on app start** and add **4.3**, **4.4**, **4.5** as follows.

**4.2 Invoke on app start** — MainActivity.kt

- In the existing `LaunchedEffect(Unit)` block (or a second `LaunchedEffect(Unit)`): after the navigation logic, call `FcmTokenManager(applicationContext).registerIfPossible()`. Fire-and-forget; use application context.

**4.3 First-run: trigger after Save** — SettingsViewModel.kt

- In `saveBaseUrl(onSaved)`, after successfully saving (`if (normalized != null)`): call `FcmTokenManager(application).registerIfPossible()` (ViewModel has `application` from `AndroidViewModel`), then invoke `onSaved()`. So the first time the user enters the URL and taps Save, the device registers for push before navigating to the dashboard; no app restart required.

**4.4 Token rotation: FirebaseMessagingService** — new file FrigateFirebaseMessagingService.kt in data.push

- Extend `FirebaseMessagingService`. Override **onNewToken(newToken: String)** and call the same registration API: from a coroutine, call `FcmTokenManager(applicationContext).registerToken(newToken)` so the buffer always has the current token.
- Override **onMessageReceived(remoteMessage)** as a no-op for now so the "Rich Handler" can be added later.
- Use PushConstants.CHANNEL_ID_SECURITY_ALERTS when building notifications so the upcoming handler can hash ce_id into a deterministic notification ID on this channel.

**4.5 AndroidManifest**

- **Cleartext traffic:** Ensure `<application>` has `android:usesCleartextTraffic="true"`. It is already present; verify it is not removed.
- **FCM service:** Declare the FirebaseMessagingService: `<service android:name=".data.push.FrigateFirebaseMessagingService" android:exported="false">` with `<intent-filter>` and `<action android:name="com.google.firebase.MESSAGING_EVENT" />`.

**Outcome:** Registration runs on every cold start and immediately after first-run Save; token rotation updates the server via onNewToken; manifest supports local HTTP and FCM; channel ID is a single constant for deterministic notification handling later.
