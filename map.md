# Frigate Event Viewer — Project Map

**This file is the source of truth for project structure, logic, and file placement.** Read it before making architectural changes or adding files.

---

## 1. Project overview

- **Type:** Android app (Kotlin, Jetpack Compose, single-Activity).
- **Purpose:** View events and dashboard stats from a Frigate Event Buffer server; user configures server base URL in Settings.
- **API contract:** Backend API is documented in `docs/MOBILE_API_CONTRACT.md`. All API usage must align with that contract.

---

## 2. Root layout

- **`map.md`** — This file. Keep it updated when adding/removing/renaming files or changing data flow.
- **`run-gradle.ps1`** — Optional PowerShell helper: sets JAVA_HOME from a detected JDK (or from a `local.jdk.path` file in the project root) and runs the Gradle wrapper. Use on Windows when JAVA_HOME is not set (e.g. `.\run-gradle.ps1 test`).
- **`.gitignore`** — Git ignore rules for Android/Gradle builds, IDE metadata, and local environment files.
- **`docs/`** — Project documentation (e.g. `MOBILE_API_CONTRACT.md`, `UI_MAP.md`). No app source code here.
  - **`UI_MAP.md`** — Compose UI and navigation flow (routes, screens, ViewModels). Keep it updated when adding or changing screens or routes.
- **`app/`** — Android application module (Gradle). All app source lives under `app/src/main/`.

Do not add new root-level folders (e.g. `lib/`, `core/`) without explicit permission.

---

## 3. Libraries and dependencies

**Canonical source:** Dependency declarations and versions are in `gradle/libs.versions.toml`; the app module uses them via `app/build.gradle.kts` (e.g. `libs.androidx.core.ktx`). **When you add, remove, or change any dependency, update both `gradle/libs.versions.toml` and this section** so the map stays accurate.

| Purpose | Library | Version |
|--------|--------|--------|
| **Plugins (root)** | Android Gradle Plugin | 9.0.1 |
| | Kotlin + Compose compiler | 2.0.21 |
| | ktlint | 12.2.0 |
| **App runtime** | AndroidX Core KTX | 1.10.1 |
| | AndroidX Lifecycle Runtime KTX | 2.6.1 |
| | AndroidX Activity Compose | 1.8.0 |
| | AndroidX Compose BOM | 2024.09.00 |
| | AndroidX Compose UI, Material3, Icons Extended | (BOM / 1.7.5) |
| | AndroidX DataStore Preferences | 1.0.0 |
| | AndroidX Navigation Compose | 2.7.7 |
| | AndroidX Lifecycle ViewModel Compose | 2.6.1 |
| | AndroidX Lifecycle Runtime Compose | 2.6.1 |
| **Networking** | Retrofit | 2.9.0 |
| | Retrofit Converter Gson | 2.9.0 |
| | OkHttp + Logging Interceptor | 4.12.0 |
| **Push** | Firebase BOM | 33.0.0 |
| | firebase-messaging | (BOM) |
| **Media / UI** | Coil Compose | 2.5.0 |
| | AndroidX Media3 ExoPlayer + UI | 1.2.1 |
| | Multiplatform Markdown Renderer (Android + M3) | 0.24.0 |
| **Unit tests** | JUnit | 4.13.2 |
| | MockK | 1.13.9 |
| | kotlinx-coroutines-test | 1.7.3 |
| **Android tests** | AndroidX JUnit | 1.1.5 |
| | AndroidX Espresso Core | 3.5.1 |
| | AndroidX Compose UI Test JUnit4 | (BOM) |
| **Debug** | AndroidX Compose UI Tooling / Test Manifest | (BOM) |

Gson is pulled in transitively by Retrofit (version in catalog: 2.10.1). Compose BOM pins many Compose artifacts; only non-BOM versions are listed explicitly above. The Google Services plugin is applied in the app module; the app requires `app/google-services.json` from the Firebase Console for FCM to work.

---

## 4. App source structure

Package base: `com.example.frigateeventviewer`.

```
app/src/main/java/com/example/frigateeventviewer/
├── FrigateEventViewerApplication.kt   # Application: Coil ImageLoaderFactory (StreamingVideoFetcher); "Security Alerts" notification channel (PushConstants.CHANNEL_ID_SECURITY_ALERTS)
├── MainActivity.kt                    # Single Activity; Compose; NavHost (settings, main_tabs, event_not_found, event_detail, snooze); handles deep link buffer://event_detail/{ce_id} via DeepLinkViewModel
├── data/
│   ├── api/
│   │   ├── ApiClient.kt               # Retrofit/OkHttp factory; createService(baseUrl)
│   │   └── FrigateApiService.kt       # Retrofit: getEvents, getCameras, getStats, getStatus, getCurrentDailyReview, generateDailyReview, markViewed, keepEvent, deleteEvent, registerDevice, getSnoozeList, setSnooze, clearSnooze, getUnreadCount
│   ├── model/                         # DTOs for API responses (Event, EventsResponse, StatsResponse, CamerasResponse, SnoozeRequest, SnoozeResponse, SnoozeEntry, UnreadCountResponse, DailyReviewResponse, etc.)
│   ├── preferences/
│   │   └── SettingsPreferences.kt     # DataStore: baseUrl flow, saveBaseUrl, normalizeBaseUrl
│   └── push/
│       ├── PushConstants.kt           # CHANNEL_ID_SECURITY_ALERTS; notificationId(ce_id) for deterministic slotting; used by Application and FrigateFirebaseMessagingService
│       ├── EventNotification.kt       # FCM payload model (NotificationPhase, EventNotification) and EventNotification.from(data) parser
│       ├── FcmTokenManager.kt         # FCM token fetch + POST /api/mobile/register via SettingsPreferences baseUrl; registerIfPossible(), registerToken(token) for onNewToken
│       ├── FrigateFirebaseMessagingService.kt   # FirebaseMessagingService: onNewToken → registerToken; onMessageReceived parses EventNotification, cancel (clear/DISCARDED), handleNew (large icon), handleSnapshotReady (BigPictureStyle), handleClipReady (Play + Mark Reviewed + Keep actions + teaser)
│       └── NotificationActionReceiver.kt       # BroadcastReceiver: MARK_REVIEWED (POST /viewed, cancel notification, Toast), KEEP (POST /keep, update notification "Saved", Toast); exported=false
└── ui/
    ├── screens/                       # One screen = one *Screen.kt + one *ViewModel.kt (and optional *ViewModelFactory)
    │   ├── DashboardScreen.kt         # Dashboard UI + DashboardViewModel/Factory
    │   ├── DailyReviewScreen.kt       # Daily review Markdown UI + DailyReviewViewModel/Factory
    │   ├── DeepLinkViewModel.kt       # Pending deep-link ce_id and resolve trigger; used by MainActivity for buffer://event_detail/{ce_id}
    │   ├── EventDetailScreen.kt       # Event detail: video (Media3), actions, metadata + EventDetailViewModel/Factory
    │   ├── EventNotFoundScreen.kt     # Shown when deep link cannot resolve to an event; Refresh retries resolution
    │   ├── EventsScreen.kt            # Events list UI + EventsViewModel/Factory
    │   ├── MainTabsScreen.kt          # HorizontalPager + bottom navigation hosting Dashboard/Events/DailyReview; header has Snooze (Dashboard only) + Settings
    │   ├── SharedEventViewModel.kt    # Activity-scoped: selectedEvent for event_detail; eventsRefreshRequested to trigger events list refresh after detail actions
    │   ├── SettingsScreen.kt          # Server URL input + SettingsViewModel/Factory
    │   ├── SnoozeScreen.kt            # Per-camera snooze: presets 30m/1h/2h, AI vs Notification toggles, camera list with Snooze/Clear
    │   └── SnoozeViewModel.kt         # SnoozeViewModel/Factory: getCameras, getSnoozeList, setSnooze, clearSnooze
    ├── theme/
    │   ├── Theme.kt
    │   ├── Color.kt
    │   └── Type.kt
    └── util/
        ├── MediaUrl.kt                # buildMediaUrl(baseUrl, path) for thumbnails/snapshots
        ├── StreamingVideoFetcher.kt  # Coil Fetcher for .mp4 URIs; streams via MediaMetadataRetriever, frame at 2s
        └── SwipeBack.kt               # SwipeBackBox: full-width swipe-back on nested screens (rightward swipe from anywhere; vertical scroll preserved)
```

- **Screens:** Each feature screen lives in `ui/screens/` with its ViewModel (and factory if it needs `Application`) in the same file or adjacent. Do not put screens in root or in `data/`.
- **Data:** API client and DTOs under `data/`; preferences under `data/preferences/`. No UI code in `data/`.
- **Theme:** All theme/typography/color under `ui/theme/`.
- **Shared UI helpers:** Pure, stateless URL/build helpers and gesture modifiers under `ui/util/`.

---

## 5. Data flow

1. **Base URL**
   - Stored in DataStore via `SettingsPreferences` (`baseUrl` flow, `saveBaseUrl`, `getBaseUrlOnce`).
   - Must be normalized (trailing slash, http/https) — use `SettingsPreferences.normalizeBaseUrl` before saving.
   - Retrofit base URL must end with `/`; `ApiClient.createService(baseUrl)` expects the normalized value.

2. **API calls**
   - ViewModels get base URL from `SettingsPreferences.getBaseUrlOnce()` (or collect `baseUrl` flow).
   - They create the service with `ApiClient.createService(baseUrl)` and call `FrigateApiService` suspend functions.
   - Daily Review uses the same base URL and API client for `api/daily-review/current` and `api/daily-review/generate`.
   - No API calls from Composables; all from ViewModels.

3. **Shared event selection**
   - The event selected for the detail screen is held in `SharedEventViewModel.selectedEvent` (activity-scoped).
   - EventsScreen sets it via `selectEvent(event)` when the user taps an event card, then navigates to `"event_detail"`.
   - When the user leaves the detail screen (back), MainActivity calls `selectEvent(null)` so the app does not hold the event in memory longer than needed.
   - When the user completes an action on EventDetailScreen (Mark Reviewed, Keep, or Delete), MainActivity passes `onEventActionCompleted` which calls `SharedEventViewModel.requestEventsRefresh()`. EventsViewModel subscribes to `eventsRefreshRequested` and refetches the events list so the UI stays in sync.

4. **Media URLs**
   - API returns paths (e.g. `hosted_snapshot`). Full URL = `{baseUrl}{path}`.
   - Use `com.example.frigateeventviewer.ui.util.buildMediaUrl(baseUrl, path)` so double slashes are avoided.

5. **Coil**
   - `FrigateEventViewerApplication` implements `ImageLoaderFactory` and registers `StreamingVideoFetcher.Factory()` so .mp4 thumbnails are fetched by streaming (MediaMetadataRetriever, frame at 2s) without full-file download. Use the default Coil `ImageLoader`; do not create ad-hoc loaders that bypass the fetcher.

6. **FCM registration**
   - On app start (MainActivity `LaunchedEffect`) and after first-run Save (SettingsViewModel.saveBaseUrl), `FcmTokenManager` fetches the FCM token and, if a base URL is set in SettingsPreferences, POSTs it to `POST /api/mobile/register`. On token rotation, `FrigateFirebaseMessagingService.onNewToken` calls `FcmTokenManager.registerToken(newToken)`. Server URL is never hardcoded.

7. **FCM phase-aware notifications**
   - `FrigateFirebaseMessagingService.onMessageReceived` parses FCM data into `EventNotification`; if base URL is missing, no notification is posted. Notifications use `notificationId(ce_id)` so the same event updates in place. NEW: live frame as large icon; SNAPSHOT_READY: BigPictureStyle; CLIP_READY: AI title/description, Play action, Mark Reviewed and Keep actions (via [NotificationActionReceiver](app/src/main/java/com/example/frigateeventviewer/data/push/NotificationActionReceiver.kt)), teaser first frame. Media URLs built with `buildMediaUrl(baseUrl, path)`; all image loads use Coil with `allowHardware(false)` and run on the service's IO scope.
8. **Notification quick actions**
   - CLIP_READY notifications include "Mark Reviewed" and "Keep" buttons. Taps are handled by `NotificationActionReceiver` (registered with `android:exported="false"`). Event path is reconstructed as `events/{ce_id}`. Mark Reviewed calls POST /viewed and cancels the notification; Keep calls POST /keep and updates the same notification to "Saved". Toasts give feedback; on API failure a Toast shows the error.

9. **AndroidManifest**
   - `android:usesCleartextTraffic="true"` on `<application>` for local HTTP backends. FCM service is declared with `com.google.firebase.MESSAGING_EVENT` so FCM can deliver messages and invoke `onNewToken`.

10. **Presence and badge**
   - On [MainActivity](app/src/main/java/com/example/frigateeventviewer/MainActivity.kt) `onResume()`, the app calls GET /api/events/unread_count (see [FrigateApiService.getUnreadCount](app/src/main/java/com/example/frigateeventviewer/data/api/FrigateApiService.kt)). The count is used to update the app icon badge: a silent, low-priority notification (channel [PushConstants.CHANNEL_ID_BADGE](app/src/main/java/com/example/frigateeventviewer/data/push/PushConstants.kt)) is posted with `NotificationCompat.setNumber(count)` so launchers (e.g. Pixel, Samsung) show the numeric badge. When count is 0 the notification is cancelled so the badge clears.

---

## 6. Navigation

Navigation (routes, start destination, launch decision, bottom bar) is documented in `docs/UI_MAP.md`. Keep that document updated when adding or changing screens or routes.

**Deep link:** MainActivity handles `buffer://event_detail/{ce_id}`. It parses the URI, fetches events (GET /events?filter=all), finds the event by `event_id` or `camera=="events" && subdir`, sets `SharedEventViewModel.selectedEvent` and navigates to `event_detail`. If not found, navigates to `event_not_found/{ce_id}` which shows "Event not found" and a Refresh button that retries resolution. `onNewIntent` updates the intent and triggers the same resolution so opening a second link works.

---

## 7. Naming and conventions

- **ViewModels:** `*ViewModel`; factories `*ViewModelFactory` when the ViewModel needs `Application`.
- **Screen state:** Sealed classes in the same file as the ViewModel (e.g. `EventsState`, `DashboardState`, `ConnectionTestState`).
- **API:** All endpoints and response shapes follow `docs/MOBILE_API_CONTRACT.md`. New endpoints or DTOs go in `data/api/` and `data/model/` and must be documented there if they change the contract.

---

## 8. Tests

- **Unit tests:** `app/src/test/` — keep tests simple: Setup → Execute → Verify; no complex logic in tests.
- **Instrumented tests:** `app/src/androidTest/` — for UI or integration tests that need the device/emulator.

---

## 9. Testing & Linting

- **ktlint:** We use ktlint for Kotlin formatting. Run `./gradlew ktlintCheck` and `./gradlew ktlintFormat`. Running `ktlintFormat` before completing a task that touches Kotlin is part of the mandatory workflow (see root `.cursorrules`).
- **Unit tests:** We use **JUnit** for unit tests and **MockK** / **kotlinx-coroutines-test** where needed for mocks and coroutines. Run `./gradlew test` before completing work that touches business logic, ViewModels, or networking (see `.cursorrules`).


---

## 10. When to update this map

Update `map.md` when you:

- Create or delete any source file or directory under `app/src/` or `docs/`.
- Rename files or packages that affect the structure above.
- **Add, remove, or change a dependency** — update `gradle/libs.versions.toml` and the **Libraries and dependencies** table in §3 (including versions).
- Change how base URL is stored, how API is called, or how navigation works.
- Add a new screen, ViewModel, or data layer component.
- For UI/screen/navigation changes, also update `docs/UI_MAP.md` per that document's rules.

Treat map updates as part of the definition of done for any such change.
