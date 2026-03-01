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
- **`run-pre-commit.ps1`** — PowerShell script that runs `.\gradlew ktlintFormat` then `.\gradlew test` from the project root. Use before committing when you changed Kotlin or business logic. See §9 for the full command (use Option A on Windows).
- **`.gitignore`** — Git ignore rules for Android/Gradle builds, IDE metadata, and local environment files.
- **`docs/`** — Project documentation (e.g. `MOBILE_API_CONTRACT.md`, `UI_MAP.md`). No app source code here.
  - **`UI_MAP.md`** — Compose UI and navigation flow (routes, screens, ViewModels). Keep it updated when adding or changing screens or routes.
  - **`CODEBASE_ANALYSIS.md`** — Codebase analysis: file list with LOC, totals (with/without tests), test-coverage gaps, and refactoring/duplication notes. Update when doing large-scale analysis or when the structure changes significantly.
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
| | AndroidX Media3 ExoPlayer HLS | 1.2.1 |
| | Multiplatform Markdown Renderer (Android + M3) | 0.24.0 |
| **Unit tests** | JUnit | 4.13.2 |
| | MockK | 1.13.9 |
| | kotlinx-coroutines-test | 1.7.3 |
| **Android tests** | AndroidX JUnit | 1.1.5 |
| | AndroidX Espresso Core | 3.5.1 |
| | AndroidX Compose UI Test JUnit4 | (BOM) |
| **Debug** | AndroidX Compose UI Tooling / Test Manifest | (BOM) |

Gson is pulled in transitively by Retrofit (version in catalog: 2.10.1). Compose BOM pins many Compose artifacts; only non-BOM versions are listed explicitly above. The Google Services plugin is applied in the app module. A placeholder `app/google-services.json` is included so the project builds; replace it with the file from the Firebase Console for FCM push to work.

---

## 4. App source structure

Package base: `com.example.frigateeventviewer`.

```
app/src/main/java/com/example/frigateeventviewer/
├── FrigateEventViewerApplication.kt   # Application: Coil ImageLoaderFactory (StreamingVideoFetcher); go2RtcStreamsRepository (shared go2rtc cache); "Security Alerts" notification channel
├── MainActivity.kt                    # Single Activity; Compose; on load triggers go2RtcStreamsRepository.refresh(); NavHost (settings, main_tabs, event_not_found, event_detail, snooze); handles deep link buffer://event_detail/{ce_id} via DeepLinkViewModel and EventMatching.findEventByCeId
├── data/
│   ├── Go2RtcStreamsRepository.kt    # Shared cache: go2rtc stream names (Go2RtcStreamsState); refresh() on app load and when Frigate IP saved; used by Settings and Live
│   ├── api/
│   │   ├── ApiClient.kt               # Retrofit/OkHttp factory; createService(baseUrl)
│   │   └── FrigateApiService.kt       # Retrofit: getEvents, getCameras, getStats, getStatus, getCurrentDailyReview, generateDailyReview, markViewed, keepEvent, deleteEvent, registerDevice, getSnoozeList, setSnooze, clearSnooze, getUnreadCount, getGo2RtcStreams
│   ├── model/                         # DTOs for API responses (Event, EventsResponse, StatsResponse, CamerasResponse, SnoozeRequest, SnoozeResponse, SnoozeEntry, UnreadCountResponse, DailyReviewResponse, etc.)
│   ├── preferences/
│   │   └── SettingsPreferences.kt     # DataStore: baseUrl, frigateIp, buildFrigateApiBaseUrl (5000); defaultLiveCamera; landscapeTabIconAlpha
│   ├── push/
│   │       ├── PushConstants.kt           # CHANNEL_ID_SECURITY_ALERTS; notificationId(ce_id) for deterministic slotting; used by Application and FrigateFirebaseMessagingService
│   │       ├── UnreadState.kt             # Single source of truth: last server unread count + locally marked reviewed IDs; badge and Events list both read from it
│   │       ├── UnreadBadgeHelper.kt       # Fetches GET unread_count, applies badge from UnreadState; updateFromServer(onResume), applyBadge(after mark reviewed/delete)
│   │       ├── EventNotification.kt       # FCM payload model (NotificationPhase, EventNotification) and EventNotification.from(data) parser
│       ├── FcmTokenManager.kt         # FCM token fetch + POST /api/mobile/register via SettingsPreferences baseUrl; registerIfPossible(), registerToken(token) for onNewToken
│       ├── FrigateFirebaseMessagingService.kt   # FirebaseMessagingService: onNewToken → registerToken; onMessageReceived parses EventNotification; notification image from GET /events snapshot (same as events tab) when available, else FCM paths; handleNew, handleSnapshotReady, handleClipReady
│       ├── NotificationImageCache.kt  # In-memory LruCache of scaled notification bitmaps by ce_id; hard memory ceiling (1/8th maxMemory) + 72h TTL; removeForEventPath() called from EventDetailViewModel on delete
│       └── NotificationActionReceiver.kt       # BroadcastReceiver: MARK_REVIEWED (POST /viewed, UnreadState.recordMarkedReviewed, applyBadge, cancel notification, Toast), KEEP (POST /keep, update notification "Saved", Toast); exported=false
│   └── util/
│       └── EventMatching.kt           # eventMatchesCeId(event, ceId), findEventByCeId(events, ceId); shared by MainActivity deep link and FrigateFirebaseMessagingService notification image
└── ui/
    ├── screens/                       # One screen = one *Screen.kt + one *ViewModel.kt (and optional *ViewModelFactory)
    │   ├── DashboardScreen.kt         # Dashboard UI + DashboardViewModel/Factory; 5m refresh throttle
    │   ├── DailyReviewScreen.kt       # Daily review Markdown UI + DailyReviewViewModel/Factory; 5m refresh throttle
    │   ├── DeepLinkViewModel.kt       # Pending deep-link ce_id and resolve trigger; used by MainActivity for buffer://event_detail/{ce_id}
    │   ├── EventDetailScreen.kt       # Event detail: video (Media3) or snapshot placeholder when no clip; actions, metadata + EventDetailViewModel/Factory
    │   ├── EventNotFoundScreen.kt     # Shown when deep link cannot resolve to an event; Refresh retries resolution
    │   ├── EventsScreen.kt            # Events list: two modes (Reviewed/Unreviewed), full-width toggle; dynamic title; filters by UnreadState.locallyMarkedReviewedEventIds; EventsViewModel activity-scoped (MainActivity), filter mode in Activity SavedStateHandle via CreationExtras; LazyColumn uses stable key (event_id) and EventCardItem display model for list performance; 5m refresh throttle; watchdog prunes UnreadState
│   ├── LiveScreen.kt              # Live tab: Select Camera dropdown from shared Go2RtcStreamsRepository state, preselects default from Settings when in list; live video player (stream URL: api/go2rtc/api/stream.mp4 via Frigate proxy 5000 only; 16:9, 12.dp; ExoPlayer with low-latency LoadControl and VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING; Connecting/Loading status; error below player shows exact reason e.g. HTTP 404); LiveViewModel/Factory (activity-scoped, created in MainActivity, passed via MainTabsScreen)
│   ├── MainTabsScreen.kt          # HorizontalPager + bottom navigation hosting Live/Dashboard/Events/DailyReview; header title from tab; Snooze (Dashboard only) + Settings; tab index from MainTabsViewModel (SavedStateHandle) for rotation; default tab Dashboard (index 1); landscape: bottom bar hidden by default, AnimatedVisibility (expand/shrink) for show/hide; floating drag handle (circle+chevron, zIndex+offset) when bar closed; handle inside bottomBar Column above NavigationBar when bar open (transparent strip); alpha from SettingsPreferences
    │   ├── MainTabsViewModel.kt       # Activity-scoped: selectedTabIndex in SavedStateHandle so main-tabs page survives configuration change
    │   ├── SharedEventViewModel.kt    # Activity-scoped: selectedEvent for event_detail; requestEventsRefresh(markedReviewedEventId?, deletedEventId?) for events list + local designation
    │   ├── SettingsScreen.kt          # Server URL + Frigate IP + Default camera (Live tab) dropdown from go2rtc streams; SettingsViewModel/Factory
    │   ├── SnoozeScreen.kt            # Per-camera snooze: presets 30m/1h/2h, AI vs Notification toggles, camera list with Snooze/Clear
    │   └── SnoozeViewModel.kt         # SnoozeViewModel/Factory: getCameras, getSnoozeList, setSnooze, clearSnooze
    ├── theme/
    │   ├── Theme.kt
    │   ├── Color.kt
    │   └── Type.kt
    └── util/
        ├── EventMediaPath.kt          # Candidate paths for event thumbnails/clips: primary (API path) + fallback (subdir/event_id swap for single-camera, ce_ prefix for consolidated); used by EventsScreen, EventDetailScreen, DashboardScreen
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
   - **Live tab (go2rtc streams):** Uses **Frigate IP** from Settings, not the Event Buffer base URL. **Camera list:** Fetched once on app load (MainActivity calls `go2RtcStreamsRepository.refresh()`) and when Frigate IP is saved in Settings; Settings and Live tab read from `Go2RtcStreamsRepository.state` (no per-screen fetch). `SettingsPreferences.buildFrigateApiBaseUrl(frigateIp)` returns `http://<frigate_ip>:5000/`; repository uses that to call `getGo2RtcStreams()` (GET api/go2rtc/streams). **MP4 playback:** The app **exclusively uses the Frigate proxy (port 5000)**. Stream URL = `{base}api/go2rtc/api/stream.mp4?src={streamName}`. Player uses ExoPlayer with low-latency LoadControl and VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING; shows "Connecting..." / "Loading..." while buffering; error text below player shows exact reason (e.g. HTTP 404, connection refused).
   - Daily Review uses the same base URL and API client for `api/daily-review/current` and `api/daily-review/generate`.
   - No API calls from Composables; all from ViewModels.

3. **Shared event selection**
   - The event selected for the detail screen is held in `SharedEventViewModel.selectedEvent` (activity-scoped).
   - EventsScreen sets it via `selectEvent(event)` when the user taps an event card, then navigates to `"event_detail"`.
   - When the user leaves the detail screen (back), MainActivity calls `selectEvent(null)` so the app does not hold the event in memory longer than needed.
   - When the user completes an action on EventDetailScreen (Mark Reviewed, Keep, or Delete), MainActivity invokes `onEventActionCompleted(markedReviewedEventId, deletedEventId)`, which calls `SharedEventViewModel.requestEventsRefresh(markedReviewedEventId, deletedEventId)`. EventsViewModel subscribes to `eventsRefreshRequested`, records in [UnreadState](app/src/main/java/com/example/frigateeventviewer/data/push/UnreadState.kt) (mark reviewed / delete), and refetches the events list so the UI stays in sync. MainActivity also updates the app icon badge via [UnreadBadgeHelper.applyBadge](app/src/main/java/com/example/frigateeventviewer/data/push/UnreadBadgeHelper.kt) so the count updates immediately.

4. **Media URLs**
   - API returns paths (e.g. `hosted_snapshot`). Full URL = `{baseUrl}{path}`.
   - Use `com.example.frigateeventviewer.ui.util.buildMediaUrl(baseUrl, path)` so double slashes are avoided.

5. **Coil**
   - `FrigateEventViewerApplication` implements `ImageLoaderFactory` and registers `StreamingVideoFetcher.Factory()` so .mp4 thumbnails are fetched by streaming (MediaMetadataRetriever, frame at 2s) without full-file download. Use the default Coil `ImageLoader`; do not create ad-hoc loaders that bypass the fetcher.

6. **FCM registration**
   - On app start (MainActivity `LaunchedEffect`) and after first-run Save (SettingsViewModel.saveBaseUrl), `FcmTokenManager` fetches the FCM token and, if a base URL is set in SettingsPreferences, POSTs it to `POST /api/mobile/register`. On token rotation, `FrigateFirebaseMessagingService.onNewToken` calls `FcmTokenManager.registerToken(newToken)`. Server URL is never hardcoded.

7. **FCM phase-aware notifications**
   - `FrigateFirebaseMessagingService.onMessageReceived` parses FCM data into `EventNotification`; if base URL is missing, no notification is posted. Notifications use `notificationId(ce_id)` so the same event updates in place. For the notification image, the app first checks [NotificationImageCache](app/src/main/java/com/example/frigateeventviewer/data/push/NotificationImageCache.kt) (72h TTL; evicted on get if expired); on cache miss, if FCM data includes `image_url` (full public URL), the app loads from that first with no delay (works on cellular/VPN). Otherwise it tries the same source as the events tab: GET /events, find event by ce_id ([EventMatching.findEventByCeId](app/src/main/java/com/example/frigateeventviewer/data/util/EventMatching.kt)), then load bitmap from `event.hosted_snapshot` (else `hosted_clip`). Loaded/scaled bitmaps are cached by ce_id. If that fails, it falls back to FCM payload paths (live_frame_proxy, hosted_snapshot, notification_gif, cropped_image_url). On event delete, [EventDetailViewModel](app/src/main/java/com/example/frigateeventviewer/ui/screens/EventDetailViewModel.kt) calls `NotificationImageCache.removeForEventPath(eventPath)` so the cache does not retain the image. NEW: large icon; SNAPSHOT_READY: BigPictureStyle; CLIP_READY: AI title/description, Play action, Mark Reviewed and Keep actions (via [NotificationActionReceiver](app/src/main/java/com/example/frigateeventviewer/data/push/NotificationActionReceiver.kt)), large icon. FCM paths that look like absolute are normalized. Media URLs built with `buildMediaUrl(baseUrl, path)`; image loads use Coil with `allowHardware(false)` on the service's IO scope.
8. **Notification quick actions**
   - CLIP_READY notifications include "Mark Reviewed" and "Keep" buttons. Taps are handled by `NotificationActionReceiver` (registered with `android:exported="false"`). Event path is reconstructed as `events/{ce_id}`. Mark Reviewed calls POST /viewed and cancels the notification; Keep calls POST /keep and updates the same notification to "Saved". Toasts give feedback; on API failure a Toast shows the error.

9. **AndroidManifest**
   - `android:usesCleartextTraffic="true"` on `<application>` for local HTTP backends. FCM service is declared with `com.google.firebase.MESSAGING_EVENT` so FCM can deliver messages and invoke `onNewToken`.

10. **Presence and badge**
   - The app icon badge (unreviewed-events count) and the events list share a single source of truth: [UnreadState](app/src/main/java/com/example/frigateeventviewer/data/push/UnreadState.kt) (last server unread count + locally marked reviewed event IDs). [UnreadBadgeHelper](app/src/main/java/com/example/frigateeventviewer/data/push/UnreadBadgeHelper.kt) fetches GET /api/events/unread_count on MainActivity `onResume()`, records the count in UnreadState, and applies the silent badge notification (channel [PushConstants.CHANNEL_ID_BADGE](app/src/main/java/com/example/frigateeventviewer/data/push/PushConstants.kt)); when the user marks an event as reviewed or deletes it (in-app or via notification action), callers record in UnreadState and call `UnreadBadgeHelper.applyBadge(context, UnreadState.currentEffectiveUnreadCount())` so the badge updates immediately without waiting for the next resume.

---

## 6. Navigation

Navigation (routes, start destination, launch decision, bottom bar) is documented in `docs/UI_MAP.md`. Keep that document updated when adding or changing screens or routes. The Events screen has two filter states (Reviewed / Unreviewed) and a dynamic title per UI_MAP. Tab selection is preserved across configuration changes (e.g. rotation) via **MainTabsViewModel** (activity-scoped) and **SavedStateHandle**; the selected tab index is stored in saved state, so it survives Activity recreation regardless of NavHost. In landscape the bottom tab bar is hidden by default; show/hide is animated (expand/shrink). A semi-transparent drag handle (circle + chevron, opacity from `SettingsPreferences.landscapeTabIconAlpha`) floats at bottom-right when the bar is closed; when the bar is open the handle sits inside the bottom bar above the NavigationBar. Drag up to show, drag down to hide.

**Deep link:** MainActivity handles `buffer://event_detail/{ce_id}`. It also handles notification taps: when the user taps a notification body or "Play", the app is launched with intent extra `EXTRA_CE_ID` (from [FrigateFirebaseMessagingService](app/src/main/java/com/example/frigateeventviewer/data/push/FrigateFirebaseMessagingService.kt)); MainActivity treats this like a deep link by building `buffer://event_detail/{ce_id}` and running the same resolution. It parses the URI (or ce_id from extra), fetches events (GET /events?filter=all), finds the event via [EventMatching.findEventByCeId](app/src/main/java/com/example/frigateeventviewer/data/util/EventMatching.kt) (matches both full ce_id and folder name without `ce_` prefix), sets `SharedEventViewModel.selectedEvent` and navigates to `event_detail`. If not found, navigates to `event_not_found/{ce_id}` which shows "Event not found" and a Refresh button that retries resolution. `onNewIntent` updates the intent and triggers the same resolution so opening a second link or tapping another notification works.

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

- **ktlint:** We use ktlint for Kotlin formatting and style. Run `ktlintFormat` (fix) then `ktlintCheck` (fail on any remaining violations, e.g. unused imports). Running these before completing a task that touches Kotlin is part of the mandatory workflow (see root `.cursorrules`).
- **Unit tests:** We use **JUnit** for unit tests and **MockK** / **kotlinx-coroutines-test** where needed. Run `.\gradlew test` before completing work that touches business logic, ViewModels, or networking (see `.cursorrules`).

**Pre-commit script (recommended).** One script runs format, style check, compile, and unit tests. On Windows, execution policy often blocks unsigned scripts, so **use Option A** (bypass) as the standard command:

**Option A (recommended — works without changing execution policy):**

```powershell
cd "z:\Code Projects\Frigate Event Viewer"; powershell -ExecutionPolicy Bypass -File .\run-pre-commit.ps1
```

If you have already allowed local scripts (`Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser`), you can use:

```powershell
cd "z:\Code Projects\Frigate Event Viewer"; .\run-pre-commit.ps1
```

The script (`run-pre-commit.ps1` in the project root) changes to the project directory, runs `.\gradlew ktlintFormat`, then `.\gradlew ktlintCheck`, then `.\gradlew compileDebugKotlin`, then `.\gradlew test`, and exits with failure if any step fails.

**What the script runs (one script fits all for pre-commit):**

- **ktlintFormat** — Formats all Kotlin source (fixes style; no separate per-module step).
- **ktlintCheck** — Fails the build if any ktlint rule is still violated (e.g. unused imports). Run after format so only unfixable or remaining violations fail.
- **compileDebugKotlin** — Compiles main Kotlin code; fails on compile errors (unresolved reference, type inference, etc.) before tests run.
- **test** — Runs **all** unit tests in `app/src/test/`. Gradle discovers every test class there; there is no separate list. Current unit test classes: `EventNotificationTest`, `SharedEventViewModelTest`, `ExampleUnitTest`, `EventDetailViewModelTest`, `MediaUrlTest`.

Instrumented tests in `app/src/androidTest/` are **not** run by this script (they require a device or emulator). Run them with `.\gradlew connectedDebugAndroidTest` when needed.

You can also run the steps manually:

```powershell
cd "z:\Code Projects\Frigate Event Viewer"
.\gradlew ktlintFormat
.\gradlew ktlintCheck
.\gradlew compileDebugKotlin
.\gradlew test
```

- **ktlintFormat** — Fix Kotlin style; run before committing when you changed Kotlin files.
- **ktlintCheck** — Verify no style violations remain; fails build on e.g. unused imports.
- **compileDebugKotlin** — Compile main code; fails on unresolved reference, type inference, etc.
- **test** — Run unit tests; run before committing when you changed business logic, ViewModels, or networking.

Optional: If JAVA_HOME is not set, use `.\run-gradle.ps1` with the same task names when that helper exists.


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
