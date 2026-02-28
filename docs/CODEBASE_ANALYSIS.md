# Frigate Event Viewer — Codebase Analysis

Generated from a full pass over the project. Covers line counts, test-coverage gaps, and refactoring/duplication opportunities.

---

## 1. Code files and lines of code

### 1.1 Main source (`app/src/main`)

| File | Lines |
|------|------:|
| **Application** | |
| `FrigateEventViewerApplication.kt` | 52 |
| `MainActivity.kt` | 219 |
| **data/api** | |
| `ApiClient.kt` | 39 |
| `FrigateApiService.kt` | 82 |
| **data/model** | |
| `ActiveConsolidatedEvent.kt` | 11 |
| `ActiveEventsStatus.kt` | 10 |
| `CamerasResponse.kt` | 8 |
| `CamerasWithZones.kt` | 9 |
| `DailyReviewResponse.kt` | 9 |
| `Event.kt` | 32 |
| `EventsResponse.kt` | 10 |
| `GenAiEntry.kt` | 12 |
| `GenerateReportResponse.kt` | 10 |
| `HaHelpers.kt` | 9 |
| `HostedClip.kt` | 9 |
| `LastCleanup.kt` | 9 |
| `MostRecent.kt` | 11 |
| `RegisterDeviceRequest.kt` | 8 |
| `RegisterDeviceResponse.kt` | 8 |
| `SnoozeEntry.kt` | 9 |
| `SnoozeRequest.kt` | 9 |
| `SnoozeResponse.kt` | 10 |
| `StatsEvents.kt` | 13 |
| `StatsResponse.kt` | 14 |
| `StatsStorage.kt` | 10 |
| `StatsSystem.kt` | 15 |
| `StatusConfig.kt` | 12 |
| `StatusMetrics.kt` | 11 |
| `StatusResponse.kt` | 15 |
| `StorageBreakdown.kt` | 10 |
| `StorageValue.kt` | 9 |
| `SystemError.kt` | 10 |
| `UnreadCountResponse.kt` | 7 |
| **data/preferences** | |
| `SettingsPreferences.kt` | 62 |
| **data/push** | |
| `EventNotification.kt` | 90 |
| `FcmTokenManager.kt` | 52 |
| `FrigateFirebaseMessagingService.kt` | 237 |
| `NotificationActionReceiver.kt` | 95 |
| `PushConstants.kt` | 24 |
| **ui/screens** | |
| `DailyReviewScreen.kt` | 197 |
| `DailyReviewViewModel.kt` | 119 |
| `DashboardScreen.kt` | 370 |
| `DashboardViewModel.kt` | 95 |
| `DeepLinkViewModel.kt` | 44 |
| `EventDetailScreen.kt` | 336 |
| `EventDetailViewModel.kt` | 108 |
| `EventNotFoundScreen.kt` | 56 |
| `EventsScreen.kt` | 289 |
| `EventsViewModel.kt` | 91 |
| `MainTabsScreen.kt` | 147 |
| `SettingsScreen.kt` | 136 |
| `SettingsViewModel.kt` | 111 |
| `SharedEventViewModel.kt` | 34 |
| `SnoozeScreen.kt` | 323 |
| `SnoozeViewModel.kt` | 144 |
| **ui/theme** | |
| `Color.kt` | 8 |
| `Theme.kt` | 51 |
| `Type.kt` | 32 |
| **ui/util** | |
| `MediaUrl.kt` | 15 |
| `StreamingVideoFetcher.kt` | 50 |
| `SwipeBack.kt` | 95 |

**Main source subtotal:** 58 files, **3,911** lines.

### 1.2 Unit tests (`app/src/test`)

| File | Lines |
|------|------:|
| `ExampleUnitTest.kt` | 14 |
| `data/push/EventNotificationTest.kt` | 129 |
| `ui/screens/EventDetailViewModelTest.kt` | 29 |
| `ui/screens/SharedEventViewModelTest.kt` | 46 |
| `ui/util/MediaUrlTest.kt` | 26 |

**Unit test subtotal:** 5 files, **244** lines.

### 1.3 Instrumented tests (`app/src/androidTest`)

| File | Lines |
|------|------:|
| `ExampleInstrumentedTest.kt` | 20 |

**Instrumented test subtotal:** 1 file, **20** lines.

### 1.4 Totals

| Scope | Files | Lines |
|-------|------:|------:|
| **Main source only** | 58 | **3,911** |
| **Tests only** (unit + instrumented) | 6 | **264** |
| **Total (main + tests)** | 64 | **4,175** |

---

## 2. Items that should be covered by tests

### 2.1 High priority (business logic / parsing)

- **`SettingsPreferences.normalizeBaseUrl`** — URL normalization and validation (trim, scheme, trailing slash). Pure function; easy to unit test. Used everywhere for base URL; bugs would break API and FCM.
- **`DeepLinkViewModel.parseDeepLinkCeId`** — Parses `buffer://event_detail/{ce_id}`. Pure function; should have tests for valid URI, invalid scheme/host, missing path, blank segment.
- **`PushConstants.notificationId(ce_id)`** — Deterministic ID from string. Already referenced in `EventNotificationTest`; ensure tests cover collision avoidance (e.g. `ce_id` → non-zero, different inputs → different IDs).

### 2.2 ViewModels (state and API flow)

- **`EventsViewModel`** — `loadEvents()`, state transitions (Loading → Success/Error), behavior when `getBaseUrlOnce()` is null, and refresh on `eventsRefreshRequested`.
- **`DashboardViewModel`** — `loadStats()`, `recentEvent` derivation from getEvents, keepPrevious on refresh, error with previous state.
- **`DailyReviewViewModel`** — Load current report, generate report, error handling.
- **`SnoozeViewModel`** — `load()`, `setSnooze()`, `clearSnooze()`, preset index and duration, operation-in-progress and error state.
- **`SettingsViewModel`** — `saveBaseUrl`, `testConnection` (success and error paths), `normalizeBaseUrl` integration via validation.

### 2.3 Data / API layer

- **`ApiClient.createService`** — At least a smoke test that it returns a non-null service for a valid base URL (optional if covered indirectly).
- **`FrigateApiService`** — Contract tests or mocked response tests for key endpoints (e.g. getEvents, getStats, markViewed, keepEvent) to catch serialization/contract drift.
- **`EventNotification.from`** — Already well covered in `EventNotificationTest`; keep edge cases (missing keys, `notification.gif` fallback, UNKNOWN phase) in scope.

### 2.4 Lower priority / integration

- **`FcmTokenManager`** — Token fetch and register flow is hard to unit test without mocking Firebase; consider integration or instrumented tests if token registration becomes critical.
- **`NotificationActionReceiver`** — Mark Reviewed / Keep flows with mocked `ApiClient` or a test API could be covered by unit tests that verify intents and calls.
- **Deep-link resolution in MainActivity** — Effectively integration (NavController + API); better suited for UI/instrumented tests or manual testing unless extracted to a testable use case.

---

## 3. Refactoring, splitting, and duplication

### 3.1 Duplication: “get base URL → create service”

**Pattern:** Every ViewModel and several other components do:

```kotlin
val baseUrl = preferences.getBaseUrlOnce()  // or SettingsPreferences(context).getBaseUrlOnce()
if (baseUrl == null) { /* handle no URL */ return }
val service = ApiClient.createService(baseUrl)
// then use service.*
```

**Seen in:** MainActivity (`updateUnreadBadge`, deep-link resolution), EventsViewModel, DashboardViewModel, DailyReviewViewModel, EventDetailViewModel, SettingsViewModel, SnoozeViewModel, NotificationActionReceiver, FcmTokenManager, FrigateFirebaseMessagingService (indirectly via baseUrl).

**Suggestion:** Introduce a small abstraction that encapsulates “get current base URL and optional FrigateApiService” so call sites don’t repeat the same null-check and factory call. For example:

- A **repository or use case** that exposes `suspend fun getApiService(): FrigateApiService?` (or `Result<FrigateApiService>`) using `SettingsPreferences` + `ApiClient`, or
- An **extension** or **helper** that takes `SettingsPreferences` and returns `FrigateApiService?` after `getBaseUrlOnce()`.

Then ViewModels and other callers use this single path. This does not require a large architectural change and keeps `map.md` and existing patterns intact.

### 3.2 Duplication: Bitmap loading in FrigateFirebaseMessagingService

**Pattern:** In `handleNew`, `handleSnapshotReady`, and `handleClipReady`, the same logic appears:

- Build URL with `buildMediaUrl(baseUrl, somePath)`.
- If URL non-blank: build Coil `ImageRequest` with `allowHardware(false)`, execute, extract `Bitmap` from `SuccessResult` / `BitmapDrawable`.
- Use bitmap for large icon or BigPictureStyle.

**Suggestion:** Extract a private helper, e.g. `loadBitmapForNotification(context: Context, url: String?): Bitmap?`, and call it from all three handlers. Reduces duplication and keeps notification bitmap loading in one place.

### 3.3 Duplication: Content intent / PendingIntent for event

**Pattern:** In `FrigateFirebaseMessagingService`, the same `Intent` for opening the app to an event (MainActivity + `EXTRA_CE_ID`) and the same `PendingIntent.getActivity(…)` pattern are repeated in `handleNew`, `handleSnapshotReady`, and `handleClipReady`.

**Suggestion:** Extract a private helper, e.g. `contentIntentForEvent(context: Context, ceId: String, notificationId: Int): PendingIntent`, and reuse it in all three handlers.

### 3.4 Large screens (candidates to split or simplify)

- **`DashboardScreen.kt`** (370 lines) — Contains dashboard layout, pull-to-refresh, stats cards, recent-event card with ExoPlayer. Consider extracting composables for: “Stats section”, “Recent event card with player”, “Connection/error state”, so the main screen is a thin composition of these.
- **`EventDetailScreen.kt`** (336 lines) — Detail UI, video, actions, metadata. Consider extracting: “Video section”, “Action buttons”, “Metadata section”, into separate composables for readability and reuse.
- **`SnoozeScreen.kt`** (323 lines) — Presets, toggles, camera list, snooze/clear actions. Consider extracting: “Preset selector”, “Camera list item”, “Snooze controls” into named composables.
- **`EventsScreen.kt`** (289 lines) — Events list and item layout. Consider extracting “Event card” (and any filter/empty state) into a dedicated composable.

Splitting does not require new files in every case; extracting private composables within the same file already improves clarity and testability of UI structure.

### 3.5 MainActivity

**`MainActivity.kt`** (219 lines) hosts NavHost, deep-link resolution, badge updates, and all route composables. Acceptable for a single-Activity app, but you could:

- Move **unread badge update** into a small use case or helper that takes `Context`, `baseUrl`, and `NotificationManager`, so MainActivity only calls “update badge” and stays focused on navigation and composition.
- Keep deep-link resolution as-is unless you introduce a dedicated “deep-link use case” that returns a resolution result; then unit test that and keep MainActivity thin.

### 3.6 NotificationActionReceiver

**`handleMarkReviewed`** and **`handleKeep`** share the same structure: runBlocking + ApiClient + try/catch, then update notification/Toast. The only differences are the API call and the success UI. A small private helper (e.g. `runApiAction(block): Boolean`) could reduce duplication and make adding new actions easier.

### 3.7 Example tests

- **`ExampleUnitTest.kt`** — Placeholder (e.g. `addition_isCorrect`). Remove or replace with a real unit test (e.g. for `normalizeBaseUrl` or `parseDeepLinkCeId`) so the default test suite is meaningful.
- **`ExampleInstrumentedTest.kt`** — Only checks package name. Keep for sanity or replace with a minimal UI/smoke test (e.g. launch MainActivity and assert a composable is displayed).

---

## 4. Summary

| Metric | Value |
|--------|--------|
| Total main source LOC | 3,911 |
| Total test LOC | 264 |
| Total LOC (main + tests) | 4,175 |
| Main source files | 58 |
| Test files | 6 |

**Test coverage:** Focus next on `SettingsPreferences.normalizeBaseUrl`, `DeepLinkViewModel.parseDeepLinkCeId`, and the main ViewModels (Events, Dashboard, DailyReview, Snooze, Settings). `EventNotification.from` and `MediaUrl` are already covered; keep `notificationId` under test.

**Refactoring:** Highest impact with minimal structural change: (1) centralize “get base URL + create API service” usage, (2) extract bitmap loading and content-intent helpers in `FrigateFirebaseMessagingService`, (3) extract composables in the largest screens (Dashboard, EventDetail, Snooze, Events) to reduce file size and improve clarity.
