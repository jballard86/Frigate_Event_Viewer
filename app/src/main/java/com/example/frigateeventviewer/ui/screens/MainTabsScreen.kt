package com.example.frigateeventviewer.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.navigation.NavHostController
import com.example.frigateeventviewer.data.model.Event
import com.example.frigateeventviewer.ui.screens.LiveViewModel
import kotlinx.coroutines.launch
import android.content.res.Configuration

@Composable
fun MainTabsScreen(
    navController: NavHostController,
    sharedEventViewModel: SharedEventViewModel,
    mainTabsViewModel: MainTabsViewModel,
    dailyReviewViewModel: DailyReviewViewModel,
    eventsViewModel: EventsViewModel,
    liveViewModel: LiveViewModel,
    landscapeTabIconAlphaProvider: () -> Float
) {
    val selectedTabIndex by mainTabsViewModel.selectedTabIndex.collectAsState()
    val pagerState = rememberPagerState(
        initialPage = selectedTabIndex,
        pageCount = { 4 }
    )
    LaunchedEffect(pagerState.settledPage) {
        mainTabsViewModel.setSelectedTabIndex(pagerState.settledPage)
    }
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var showBottomBarInLandscape by remember { mutableStateOf(false) }

    val showBottomBar = !isLandscape || showBottomBarInLandscape
    LaunchedEffect(isLandscape) {
        if (isLandscape) showBottomBarInLandscape = false
    }
    val density = LocalDensity.current
    val dragThresholdPx = with(density) { 40.dp.toPx() }
    val totalDragPx = remember { mutableStateOf(0f) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            val eventsPageTitle by eventsViewModel.eventsPageTitle.collectAsState()
            val pageTitle = when (pagerState.currentPage) {
                0 -> "Live"
                1 -> "Dashboard"
                2 -> eventsPageTitle
                3 -> "Daily Review"
                else -> ""
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = pageTitle,
                    style = MaterialTheme.typography.headlineLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    if (pagerState.currentPage == 1) {
                        IconButton(
                            onClick = { navController.navigate("snooze") }
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsOff,
                                contentDescription = "Snooze"
                            )
                        }
                    }
                    IconButton(
                        onClick = { navController.navigate("settings") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (isLandscape) {
                AnimatedVisibility(
                    visible = showBottomBarInLandscape,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Transparent)
                        ) {
                            val barMaxWidth = maxWidth
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = barMaxWidth * 0.1f)
                                        .size(48.dp)
                                        .alpha(landscapeTabIconAlphaProvider())
                                        .border(
                                            width = 2.dp,
                                            color = MaterialTheme.colorScheme.outline,
                                            shape = CircleShape
                                        )
                                        .semantics {
                                            contentDescription = "Drag down to hide tab bar"
                                        }
                                        .pointerInput(dragThresholdPx) {
                                            detectVerticalDragGestures(
                                                onVerticalDrag = { _, dy ->
                                                    totalDragPx.value += dy
                                                },
                                                onDragEnd = {
                                                    if (totalDragPx.value < -dragThresholdPx) {
                                                        showBottomBarInLandscape = true
                                                    } else if (totalDragPx.value > dragThresholdPx) {
                                                        showBottomBarInLandscape = false
                                                    }
                                                    totalDragPx.value = 0f
                                                }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                        NavigationBar {
                            NavigationBarItem(
                                selected = pagerState.currentPage == 0,
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(0)
                                    }
                                },
                                icon = { Icon(Icons.Default.Videocam, contentDescription = "Live") },
                                label = { Text("Live") }
                            )
                            NavigationBarItem(
                                selected = pagerState.currentPage == 1,
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(1)
                                    }
                                },
                                icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                                label = { Text("Dashboard") }
                            )
                            NavigationBarItem(
                                selected = pagerState.currentPage == 2,
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(2)
                                    }
                                },
                                icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Events") },
                                label = { Text("Events") }
                            )
                            NavigationBarItem(
                                selected = pagerState.currentPage == 3,
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(3)
                                    }
                                },
                                icon = { Icon(Icons.Default.Description, contentDescription = "Daily Review") },
                                label = { Text("Daily Review") }
                            )
                        }
                    }
                }
            } else {
                if (showBottomBar) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = pagerState.currentPage == 0,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(0)
                                }
                            },
                            icon = { Icon(Icons.Default.Videocam, contentDescription = "Live") },
                            label = { Text("Live") }
                        )
                        NavigationBarItem(
                            selected = pagerState.currentPage == 1,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(1)
                                }
                            },
                            icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                            label = { Text("Dashboard") }
                        )
                        NavigationBarItem(
                            selected = pagerState.currentPage == 2,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(2)
                                }
                            },
                            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Events") },
                            label = { Text("Events") }
                        )
                        NavigationBarItem(
                            selected = pagerState.currentPage == 3,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(3)
                                }
                            },
                            icon = { Icon(Icons.Default.Description, contentDescription = "Daily Review") },
                            label = { Text("Daily Review") }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val currentPage = pagerState.currentPage
            val isLiveTabVisible = pagerState.currentPage == 0
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                beyondViewportPageCount = 3
            ) { page ->
                when (page) {
                    0 -> LiveScreen(
                        currentPage = currentPage,
                        pageIndex = 0,
                        viewModel = liveViewModel,
                        isVisible = isLiveTabVisible
                    )
                    1 -> DashboardScreen(
                        currentPage = currentPage,
                        pageIndex = 1
                    )
                    2 -> EventsScreen(
                        onEventClick = { event: Event ->
                            sharedEventViewModel.selectEvent(event)
                            navController.navigate("event_detail")
                        },
                        currentPage = currentPage,
                        pageIndex = 2,
                        sharedEventViewModel = sharedEventViewModel,
                        viewModel = eventsViewModel
                    )
                    3 -> DailyReviewScreen(
                        viewModel = dailyReviewViewModel,
                        currentPage = currentPage,
                        pageIndex = 3
                    )
                }
            }
            if (isLandscape && !showBottomBarInLandscape) {
                Box(
                    modifier = Modifier
                        .zIndex(1f)
                        .align(Alignment.BottomEnd)
                        .offset(
                            x = -(maxWidth * 0.1f),
                            y = -(maxHeight * 0.1f)
                        )
                        .size(48.dp)
                        .alpha(landscapeTabIconAlphaProvider())
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                        .semantics {
                            contentDescription = "Drag up to show tab bar"
                        }
                        .pointerInput(dragThresholdPx) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { _, dy ->
                                    totalDragPx.value += dy
                                },
                                onDragEnd = {
                                    if (totalDragPx.value < -dragThresholdPx) {
                                        showBottomBarInLandscape = true
                                    } else if (totalDragPx.value > dragThresholdPx) {
                                        showBottomBarInLandscape = false
                                    }
                                    totalDragPx.value = 0f
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
        }
    }
    }
}

