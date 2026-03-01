# Android Performance Audit — Frigate Event Viewer

This document lists potential performance, responsiveness, and lifecycle issues in the Kotlin/Compose Android app. **No backend API or `MOBILE_API_CONTRACT.md` changes are suggested.** Findings are audit-only; no fix code is provided.

---

## 1. Compose Recomposition Overhead

### 1.1 Unstable or heavy parameters passed to Composables

**File:** `app/src/main/java/com/example/frigateeventviewer/ui/screens/EventsScreen.kt`  
**Location:** `EventCard(event, baseUrl, onClick)`, and `Event` usage elsewhere.

**Description:** `Event` is a data class with `List<HostedClip>`, `List<CamerasWithZones>`, `List<GenAiEntry>`, etc. In Compose, data classes containing mutable or complex generic types (e.g. `List<>`) can be treated as unstable. Passing `Event` (or lists of `Event`) as parameters can cause the compiler to skip stability inference and trigger broader recomposition than necessary when any parent state changes.

**Impact:** Entire event list items may recompose when only one item’s state changes, or when unrelated screen state (e.g. pull-to-refresh, filter toggle) updates. This increases CPU work and can contribute to list jank.

---

### 1.2 Heavy work and allocations inside Composables

**File:** `app/src/main/java/com/example/frigateeventviewer/ui/screens/EventsScreen.kt`  
**Location:** `EventCard` — `formatTimestamp()`, `formatCameraName()`, `ImageRequest.Builder(context).data(url).build()`, and `when (event.threat_level)` for icon/color.

**Description:** `formatTimestamp()` and `formatCameraName()` are invoked on every recomposition of `EventCard`. `ImageRequest` is built inline (only guarded by `thumbnailUrl?.let`), so a new request instance can be created on each recomposition. Threat icon/color derivation runs every time the card recomposes.

**Impact:** Repeated string parsing, formatter creation, and object allocation on the UI thread during scroll or when list state updates. Can cause frame drops and unnecessary allocations.

---

### 1.3 Same formatting logic duplicated and run in Composables

**File:**  
- `app/src/main/java/com/example/frigateeventviewer/ui/screens/EventsScreen.kt` — `formatTimestamp`, `formatCameraName`  
- `app/src/main/java/com/example/frigateeventviewer/ui/screens/EventDetailScreen.kt` — same names, same logic

**Description:** Timestamp and camera-name formatting are duplicated in two screen files and called directly from Composables. No caching or derivation in ViewModel/layer.

**Impact:** Redundant parsing and formatting on recomposition; any change to formatting logic must be updated in multiple places. Increases recomposition cost and maintenance risk.

---

### 1.4 Custom MarkdownTypography object created in Composable

**File:** `app/src/main/java/com/example/frigateeventviewer/ui/screens/DailyReviewScreen.kt`  
**Location:** `DailyReviewContent` — `remember(h1Style, h2Style, h3Style, paragraphStyle) { object : MarkdownTypography { ... } }`.

**Description:** A custom `MarkdownTypography` implementation is created and remembered with typography styles as keys. The object is recreated whenever any of those `TextStyle` instances change (e.g. theme or configuration change).

**Impact:** If theme or density changes, the object is recreated and the whole Markdown block may recompose. For long markdown content, this can be expensive. The pattern is correct for stability but ties heavy content to typography identity.

---

## 2. Redundant Network Calls & Lifecycle

### 2.1 Events loaded in init before user reaches main tabs

**File:** `app/src/main/java/com/example/frigateeventviewer/ui/screens/EventsViewModel.kt`  
**Location:** `init { loadEvents(); ... }` and ViewModel creation in `MainActivity.setContent`.

**Description:** `EventsViewModel` is activity-scoped and created in the root `setContent` block (same composition as `NavHost`). Its `init` calls `loadEvents()`, so the first GET /events runs as soon as the app composes, even when the start destination is Settings and the user has not opened the Events tab.

**Impact:** Unnecessary network call and processing before the user has navigated to main_tabs or Events tab. Wastes bandwidth and can race with navigation or base URL setup.

---

### 2.2 Double fetch when opening Dashboard tab

**File:** `app/src/main/java/com/example/frigateeventviewer/ui/screens/DashboardScreen.kt` and `DashboardViewModel.kt`  
**Location:** `DashboardViewModel.init { loadStats() }` and `LaunchedEffect(lifecycle, currentPage, pageIndex) { if (currentPage == pageIndex) viewModel.refresh() }`.

**Description:** When the user switches to the Dashboard tab, `DashboardScreen` is composed and `DashboardViewModel` is created; `init` runs `loadStats()`. The same screen also has a `LaunchedEffect` that calls `viewModel.refresh()` when the tab is focused and lifecycle is RESUMED. Both paths trigger a full stats (and recent-event) load.

**Impact:** Two GET /stats (and related) requests in quick succession whenever the Dashboard tab is selected. Redundant server load and possible UI flicker between loading and success.

---

### 2.3 Double fetch when opening Daily Review tab

**File:** `app/src/main/java/com/example/frigateeventviewer/ui/screens/DailyReviewScreen.kt` and `DailyReviewViewModel.kt`  
**Location:** `DailyReviewViewModel.init { fetchDailyReview() }` and `LaunchedEffect(lifecycle, currentPage, pageIndex) { if (currentPage == pageIndex) viewModel.refresh() }`.

**Description:** `DailyReviewViewModel` is created when the user first enters main_tabs (it is created in the `composable("main_tabs")` block and passed into `MainTabsScreen`). Its `init` calls `fetchDailyReview()`. When the user then switches to the Daily Review tab, the `LaunchedEffect` runs and calls `viewModel.refresh()`, which fetches again.

**Impact:** Two GET daily-review requests: one when entering main_tabs and one when switching to the Daily Review tab. Wastes bandwidth and can cause a brief loading state even when data was just loaded.

---

### 2.4 Refetch on every tab focus (Events, Dashboard, Daily Review)

**File:** `app/src/main/java/com/example/frigateeventviewer/ui/screens/EventsScreen.kt`, `DashboardScreen.kt`, `DailyReviewScreen.kt`  
**Location:** `LaunchedEffect(lifecycle, currentPage, pageIndex) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { if (currentPage == pageIndex) viewModel.refresh() } }`.

**Description:** Each of these screens calls `viewModel.refresh()` whenever the tab becomes the current page and the lifecycle is RESUMED. There is no staleness check or cache; every time the user switches to the tab, a full refetch is triggered.

**Impact:** Frequent refetches when toggling between tabs (e.g. Live ↔ Events ↔ Dashboard). Increases server load and can cause list/content flicker and unnecessary loading indicators. No benefit from “stale-while-revalidate” or time-based cache.

---

### 2.5 DailyReviewViewModel created and fetching as soon as main_tabs is entered

**File:** `app/src/main/java/com/example/frigateeventviewer/MainActivity.kt`  
**Location:** `composable("main_tabs") { ... val dailyReviewViewModel = viewModel(factory = DailyReviewViewModelFactory(application)); ... }`.

**Description:** As soon as the user navigates to main_tabs, the Daily Review ViewModel is created and its `init` runs `fetchDailyReview()`, even if the user is on Live or Dashboard.

**Impact:** Daily Review API is called before the user has opened the Daily Review tab. Combined with the tab-focus refresh, this contributes to double fetch and unnecessary network use when the user never visits that tab in that session.

---

### 2.6 Missing rememberSaveable for UI-only state across process death

**File:** `app/src/main/java/com/example/frigateeventviewer/ui/screens/MainTabsScreen.kt`  
**Location:** `showBottomBarInLandscape` and `dropdownExpanded` / `dropdownWidthDp` in LiveScreen and Settings.

**Description:** `showBottomBarInLandscape` is `var ... by remember { mutableStateOf(false) }`. Dropdown expanded state and width in LiveScreen and Settings are also plain `remember { mutableStateOf(...) }`. None use `rememberSaveable`.

**Description (impact):** After process death and restore, the bottom bar and dropdowns reset to default. No direct performance bug, but state is lost and the user may see an unexpected layout until they interact again. (Included for completeness; lower priority than recomposition/network.)

---

## 3. Threading & Coroutines

### 3.1 DataStore and API calls from Main-dispatcher scope

**File:** Multiple ViewModels and `Go2RtcStreamsRepository`.  
**Locations:**  
- `SettingsViewModel.init` — `preferences.getBaseUrlOnce()`, `getFrigateIpOnce()`, `getDefaultLiveCameraOnce()` inside `viewModelScope.launch { }`.  
- `EventsViewModel.runLoadEvents()` — `preferences.getBaseUrlOnce()` inside `viewModelScope.launch`.  
- `DashboardViewModel.loadStats()`, `DailyReviewViewModel.fetchDailyReview()`, `EventDetailViewModel.performAction()` — same pattern.  
- `Go2RtcStreamsRepository.refresh()` — `preferences.getFrigateIpOnce()` then API call; called from `viewModelScope.launch` in Settings and from MainActivity.

**Description:** `viewModelScope` uses `Dispatchers.Main.immediate` by default. DataStore’s `data` flow and `.first()` suspend and perform IO on DataStore’s internal scope, so the main thread is not blocked. However, all API calls (Retrofit suspend functions) are launched from the same scope. Retrofit/OkHttp perform the actual network work on their own threads and resume the continuation on the calling dispatcher (Main). So the pattern is generally safe but mixes main-thread continuation with any post-processing (e.g. mapping, state updates) on Main.

**Impact:** Any heavy parsing or list processing after the network call runs on the Main thread. For large payloads (e.g. many events), this can cause jank. Explicit `withContext(Dispatchers.Default)` or `Dispatchers.IO` for parsing/large work would isolate the main thread.

---

### 3.2 UnreadBadgeHelper uses GlobalScope

**File:** `app/src/main/java/com/example/frigateeventviewer/data/push/UnreadBadgeHelper.kt`  
**Location:** `updateFromServer(context)` — `GlobalScope.launch(Dispatchers.IO) { ... }`.

**Description:** The unread-count fetch and badge update are launched in `GlobalScope`, which is not tied to any lifecycle. The coroutine continues even if the Activity is destroyed and can outlive the process’s need for the result.

**Impact:** No cancellation when the user leaves the app or the Activity is destroyed. Can cause unnecessary work, and in theory a leak of context reference until the request completes. Prefer a lifecycle-bound scope (e.g. from Activity or Application).

---

## 4. Memory & Lifecycle

### 4.1 ExoPlayer lifecycle when switching away from Live tab

**File:** `app/src/main/java/com/example/frigateeventviewer/ui/screens/LiveScreen.kt`  
**Location:** `LiveVideoPlayer` — `remember(streamUrl) { ExoPlayer ... }` and `DisposableEffect(lifecycleOwner) { ... onDispose { player.release() } }`.

**Description:** With default `HorizontalPager` behavior, only the current page is composed. When the user switches from Live to another tab, `LiveScreen` leaves composition and `onDispose` runs, so the player is released. When the user returns to Live, a new ExoPlayer is created and the stream is reconnected.

**Impact:** Reconnecting to the stream on every tab switch can cause a visible “Connecting…”/buffering delay and extra network/battery use. No memory leak, but a trade-off: releasing avoids holding decoder resources when not visible; not releasing would allow instant resume but would hold memory.

---

### 4.2 ExoPlayer on Dashboard (Recent Event) when tab is not visible

**File:** `app/src/main/java/com/example/frigateeventviewer/ui/screens/DashboardScreen.kt`  
**Location:** `RecentEventVideoPlayer` — `remember(clipUrl) { ExoPlayer ... }` and `DisposableEffect(lifecycleOwner, player) { ... onDispose { player.release() } }`.

**Description:** Same pager behavior: when the user is on another tab, Dashboard is not composed, so the recent-event ExoPlayer is released. When the user returns to Dashboard, a new player is created and the clip is loaded again.

**Impact:** Same as Live tab — reconnection/reload when returning to the tab. For a short clip this may be less noticeable but still redundant if the user switches tabs often.

---

### 4.3 EventDetailScreen ExoPlayer and lifecycle

**File:** `app/src/main/java/com/example/frigateeventviewer/ui/screens/EventDetailScreen.kt`  
**Location:** `EventVideoSection` — `remember(event.camera, event.subdir, clipPath) { ExoPlayer ... }` and `DisposableEffect(lifecycleOwner) { onDispose { player.release() } }`.

**Description:** Player is released in `onDispose` when leaving the detail screen. No obvious leak.

**Impact:** None identified; included for consistency. Recreating the player when re-entering the same event would reload the clip; acceptable for a detail screen.

---

### 4.4 UnreadState uses Dispatchers.Unconfined and Eagerly

**File:** `app/src/main/java/com/example/frigateeventviewer/data/push/UnreadState.kt`  
**Location:** `scope = CoroutineScope(Dispatchers.Unconfined)` and `stateIn(..., SharingStarted.Eagerly, ...)` for `locallyMarkedReviewedEventIds` and `effectiveUnreadCount`.

**Description:** The flows are shared with `SharingStarted.Eagerly` and a global `Unconfined` scope, so collection starts immediately and never stops. The object is an application-wide singleton.

**Impact:** Flows are always active. No lifecycle-bound cancellation. For a small state object this is usually acceptable, but it means the scope never cancels and any downstream work tied to these flows runs for the app’s lifetime. Low risk; note for consistency.

---

## 5. List Performance

### 5.1 LazyColumn key and item identity

**File:** `app/src/main/java/com/example/frigateeventviewer/ui/screens/EventsScreen.kt`  
**Location:** `LazyColumn` — `items(displayEvents, key = { it.event_id }) { event -> EventCard(...) }`.

**Description:** A stable key (`event_id`) is provided, which is good for item identity and avoiding unnecessary recomposition of items when the list changes.

**Impact:** Positive; no change suggested. Noted to confirm list key usage.

---

### 5.2 Coil ImageRequest and size for event thumbnails

**File:** `app/src/main/java/com/example/frigateeventviewer/ui/screens/EventsScreen.kt`  
**Location:** `EventCard` — `ImageRequest.Builder(context).data(url).build()` and `AsyncImage(model = imageRequest!!, ... modifier = Modifier.fillMaxSize())` inside a fixed 80.dp×60.dp box.

**Description:** The request is built inline (and not necessarily remembered by URL). Coil is not given an explicit size, so it may decode the full image and then scale down to 80×60 dp, using more memory and CPU than necessary.

**Impact:** Higher memory and CPU for list thumbnails, especially with many items or large snapshots. Can contribute to scroll jank and higher GC pressure. Coil’s `size()` or equivalent should match the display size.

---

### 5.3 SnoozeScreen camera list not using LazyColumn

**File:** `app/src/main/java/com/example/frigateeventviewer/ui/screens/SnoozeScreen.kt`  
**Location:** `CameraList` — `cameras.forEach { camera -> Card(...) }` inside a `Column(verticalScroll(rememberScrollState()))`.

**Description:** All camera cards are composed at once inside a vertically scrollable Column. There is no virtualization.

**Impact:** For a large number of cameras, every card is composed and measured even when off-screen. This can cause slower first layout and more recomposition when state changes. A `LazyColumn` would limit composition to visible items.

---

## 6. State Management & Hoisting

### 6.1 landscapeTabIconAlpha drives full MainTabsScreen recomposition

**File:** `app/src/main/java/com/example/frigateeventviewer/MainActivity.kt` and `MainTabsScreen.kt`  
**Location:** In `composable("main_tabs")`, `landscapeTabIconAlpha` is read from `SettingsPreferences(application).landscapeTabIconAlpha.collectAsState(initial = 0.5f)` and passed to `MainTabsScreen`. The value is used for the floating drag handle and bottom bar icon alpha.

**Description:** When the user changes the slider in Settings, the DataStore flow emits and `landscapeTabIconAlpha` updates. The entire `MainTabsScreen` receives the new value and can recompose. The actual visual change is limited to a few alpha modifiers.

**Impact:** A small state change (one float) can trigger recomposition of the whole tab screen, including top bar, pager, and bottom bar. If the scope of recomposition is not narrowed (e.g. by passing the value only to the composables that need it or by using a more granular state), responsiveness when adjusting the slider can be worse than necessary.

---

### 6.2 eventsPageTitle collected at MainTabsScreen root

**File:** `app/src/main/java/com/example/frigateeventviewer/ui/screens/MainTabsScreen.kt`  
**Location:** `val eventsPageTitle by eventsViewModel.eventsPageTitle.collectAsState()` and `val pageTitle = when (pagerState.currentPage) { ... 2 -> eventsPageTitle ... }` used in the top bar.

**Description:** The Events tab title (e.g. “Unreviewed Events” / “Reviewed Events”) is collected at the root of `MainTabsScreen`. When the filter mode or title changes, the whole `MainTabsScreen` recomposes to update the top bar text.

**Impact:** Recomposition scope is broader than necessary. Only the title text in the top bar needs to update; the rest of the screen (pager, bottom bar) could be stable. This can cause extra work during filter toggles or other events-list updates.

---

### 6.3 SnackbarHostState and connection test state in Settings

**File:** `app/src/main/java/com/example/frigateeventviewer/ui/screens/SettingsScreen.kt`  
**Location:** `snackbarHostState = remember { SnackbarHostState() }` and `LaunchedEffect(connectionTestState) { ... snackbarHostState.showSnackbar(...) }`.

**Description:** Snackbar and connection-test handling are correctly scoped to the screen. No obvious over-hoisting.

**Impact:** None; included only to confirm state is local to Settings.

---

## 7. Other

### 7.1 DashboardViewModel created per tab visit (no cache across tab switch)

**File:** `app/src/main/java/com/example/frigateeventviewer/ui/screens/DashboardScreen.kt`  
**Location:** `viewModel = viewModel(factory = DashboardViewModelFactory(...))` inside the `HorizontalPager` page content for Dashboard.

**Description:** The ViewModel is scoped to the composition owner. With the default pager, when the user leaves the Dashboard tab, the page is disposed and the ViewModel is cleared. When the user returns, a new `DashboardViewModel` is created and `init` runs again, triggering a fresh load.

**Impact:** No cross-tab cache for Dashboard. Combined with the double-fetch (init + LaunchedEffect), this reinforces redundant network calls when switching back to Dashboard.

---

### 7.2 DailyReviewViewModel created once per main_tabs and never disposed until leaving main_tabs

**File:** `app/src/main/java/com/example/frigateeventviewer/MainActivity.kt`  
**Location:** `composable("main_tabs") { val dailyReviewViewModel = viewModel(...) ... }`.

**Description:** The Daily Review ViewModel is created when the user enters main_tabs and lives for the whole time the user stays on main_tabs. It is not recreated when switching tabs.

**Impact:** No leak; state is preserved when switching away from Daily Review. The main issue is the early fetch in init and the second fetch on tab focus, as noted in §2.3 and §2.5.

---

## Summary Table

| #   | Category        | File(s) / Area                    | Severity / note                          |
|-----|-----------------|------------------------------------|------------------------------------------|
| 1.1 | Recomposition   | EventsScreen, Event type           | Unstable Event may widen recomposition   |
| 1.2 | Recomposition   | EventsScreen EventCard             | Format + ImageRequest on every recompose |
| 1.3 | Recomposition   | EventsScreen, EventDetailScreen    | Duplicate formatting in UI               |
| 1.4 | Recomposition   | DailyReviewScreen                  | MarkdownTypography tied to theme         |
| 2.1 | Network/Lifecycle | EventsViewModel, MainActivity    | Load events before main_tabs             |
| 2.2 | Network/Lifecycle | DashboardScreen/ViewModel         | Double fetch on tab open                 |
| 2.3 | Network/Lifecycle | DailyReviewScreen/ViewModel       | Double fetch on tab open                 |
| 2.4 | Network/Lifecycle | Events, Dashboard, DailyReview    | Refetch on every tab focus              |
| 2.5 | Network/Lifecycle | MainActivity main_tabs             | Daily Review fetch on main_tabs enter    |
| 2.6 | State           | MainTabsScreen, Live, Settings    | No rememberSaveable for some UI state   |
| 3.1 | Threading      | ViewModels, Repository             | Post-network work on Main                |
| 3.2 | Threading      | UnreadBadgeHelper                  | GlobalScope for badge fetch              |
| 4.1 | Memory/Lifecycle | LiveScreen ExoPlayer              | Reconnect on every tab switch            |
| 4.2 | Memory/Lifecycle | DashboardScreen ExoPlayer         | Same as 4.1 for recent clip             |
| 4.3 | Memory/Lifecycle | EventDetailScreen                 | OK                                       |
| 4.4 | Memory/Lifecycle | UnreadState                       | Eagerly + Unconfined scope               |
| 5.1 | List           | EventsScreen LazyColumn            | Key present; OK                           |
| 5.2 | List           | EventsScreen EventCard Coil        | No size(); decode at display size        |
| 5.3 | List           | SnoozeScreen CameraList            | Column instead of LazyColumn             |
| 6.1 | State          | MainTabsScreen landscape alpha    | Full screen recompose on alpha change    |
| 6.2 | State          | MainTabsScreen pageTitle           | Full screen recompose on title change    |
| 7.1 | Other          | DashboardViewModel                 | New VM each time + double fetch          |
| 7.2 | Other          | DailyReviewViewModel               | Single VM; early + tab-focus fetch       |

---

*Audit complete. No modifications were made to the backend API or `MOBILE_API_CONTRACT.md`.*
