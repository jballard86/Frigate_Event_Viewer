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

## 3. App source structure

Package base: `com.example.frigateeventviewer`.

```
app/src/main/java/com/example/frigateeventviewer/
├── FrigateEventViewerApplication.kt   # Application + Coil ImageLoaderFactory (StreamingVideoFetcher)
├── MainActivity.kt                    # Single Activity; Compose; NavHost + bottom bar
├── data/
│   ├── api/
│   │   ├── ApiClient.kt               # Retrofit/OkHttp factory; createService(baseUrl)
│   │   └── FrigateApiService.kt       # Retrofit: getEvents, getStats, getStatus, getCurrentDailyReview, generateDailyReview, markViewed, keepEvent, deleteEvent
│   ├── model/                         # DTOs for API responses (Event, EventsResponse, StatsResponse, DailyReviewResponse, GenerateReportResponse, etc.)
│   └── preferences/
│       └── SettingsPreferences.kt     # DataStore: baseUrl flow, saveBaseUrl, normalizeBaseUrl
└── ui/
    ├── screens/                       # One screen = one *Screen.kt + one *ViewModel.kt (and optional *ViewModelFactory)
    │   ├── DashboardScreen.kt         # Dashboard UI + DashboardViewModel/Factory
    │   ├── DailyReviewScreen.kt      # Daily review Markdown UI + DailyReviewViewModel/Factory
    │   ├── EventDetailScreen.kt       # Event detail: video (Media3), actions, metadata + EventDetailViewModel/Factory
    │   ├── EventsScreen.kt            # Events list UI + EventsViewModel/Factory
    │   ├── SharedEventViewModel.kt    # Activity-scoped: selectedEvent for event_detail (cleared on back)
    │   └── SettingsScreen.kt          # Server URL input + SettingsViewModel/Factory
    ├── theme/
    │   ├── Theme.kt
    │   ├── Color.kt
    │   └── Type.kt
    └── util/
        ├── MediaUrl.kt                # buildMediaUrl(baseUrl, path) for thumbnails/snapshots
        └── StreamingVideoFetcher.kt  # Coil Fetcher for .mp4 URIs; streams via MediaMetadataRetriever, frame at 2s
```

- **Screens:** Each feature screen lives in `ui/screens/` with its ViewModel (and factory if it needs `Application`) in the same file or adjacent. Do not put screens in root or in `data/`.
- **Data:** API client and DTOs under `data/`; preferences under `data/preferences/`. No UI code in `data/`.
- **Theme:** All theme/typography/color under `ui/theme/`.
- **Shared UI helpers:** Pure, stateless URL/build helpers under `ui/util/`.

---

## 4. Data flow

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

4. **Media URLs**
   - API returns paths (e.g. `hosted_snapshot`). Full URL = `{baseUrl}{path}`.
   - Use `com.example.frigateeventviewer.ui.util.buildMediaUrl(baseUrl, path)` so double slashes are avoided.

5. **Coil**
   - `FrigateEventViewerApplication` implements `ImageLoaderFactory` and registers `StreamingVideoFetcher.Factory()` so .mp4 thumbnails are fetched by streaming (MediaMetadataRetriever, frame at 2s) without full-file download. Use the default Coil `ImageLoader`; do not create ad-hoc loaders that bypass the fetcher.

---

## 5. Navigation

Navigation (routes, start destination, launch decision, bottom bar) is documented in `docs/UI_MAP.md`. Keep that document updated when adding or changing screens or routes.

---

## 6. Naming and conventions

- **ViewModels:** `*ViewModel`; factories `*ViewModelFactory` when the ViewModel needs `Application`.
- **Screen state:** Sealed classes in the same file as the ViewModel (e.g. `EventsState`, `DashboardState`, `ConnectionTestState`).
- **API:** All endpoints and response shapes follow `docs/MOBILE_API_CONTRACT.md`. New endpoints or DTOs go in `data/api/` and `data/model/` and must be documented there if they change the contract.

---

## 7. Tests

- **Unit tests:** `app/src/test/` — keep tests simple: Setup → Execute → Verify; no complex logic in tests.
- **Instrumented tests:** `app/src/androidTest/` — for UI or integration tests that need the device/emulator.

---

## 8. Testing & Linting

- **ktlint:** We use ktlint for Kotlin formatting. Run `./gradlew ktlintCheck` and `./gradlew ktlintFormat`. Running `ktlintFormat` before completing a task that touches Kotlin is part of the mandatory workflow (see root `.cursorrules`).
- **Unit tests:** We use **JUnit** for unit tests and **MockK** / **kotlinx-coroutines-test** where needed for mocks and coroutines. Run `./gradlew test` before completing work that touches business logic, ViewModels, or networking (see `.cursorrules`).
- **Windows (PowerShell):** If `JAVA_HOME` is not set and `.\gradlew.bat` fails, use the helper script from the project root: `.\run-gradle.ps1 test` and `.\run-gradle.ps1 ktlintFormat`. The script finds a JDK (e.g. Android Studio’s bundled JBR) and runs the Gradle wrapper. Alternatively, set `JAVA_HOME` to your JDK root and run `.\gradlew.bat` as usual.

---

## 9. When to update this map

Update `map.md` when you:

- Create or delete any source file or directory under `app/src/` or `docs/`.
- Rename files or packages that affect the structure above.
- Change how base URL is stored, how API is called, or how navigation works.
- Add a new screen, ViewModel, or data layer component.
- For UI/screen/navigation changes, also update `docs/UI_MAP.md` per that document's rules.

Treat map updates as part of the definition of done for any such change.
