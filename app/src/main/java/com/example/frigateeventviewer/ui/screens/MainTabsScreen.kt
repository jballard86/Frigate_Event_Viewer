package com.example.frigateeventviewer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.frigateeventviewer.data.model.Event
import kotlinx.coroutines.launch

@Composable
fun MainTabsScreen(
    navController: NavHostController,
    sharedEventViewModel: SharedEventViewModel,
    dailyReviewViewModel: DailyReviewViewModel
) {
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { 3 }
    )
    val coroutineScope = rememberCoroutineScope()
    val pageTitle = when (pagerState.currentPage) {
        0 -> "Dashboard"
        1 -> "Events"
        2 -> "Daily Review"
        else -> ""
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
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
                    if (pagerState.currentPage == 0) {
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
            NavigationBar {
                NavigationBarItem(
                    selected = pagerState.currentPage == 0,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(0)
                        }
                    },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") }
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 1,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(1)
                        }
                    },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Events") },
                    label = { Text("Events") }
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 2,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(2)
                        }
                    },
                    icon = { Icon(Icons.Default.Description, contentDescription = "Daily Review") },
                    label = { Text("Daily Review") }
                )
            }
        }
    ) { innerPadding ->
        val currentPage = pagerState.currentPage
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { page ->
            when (page) {
                0 -> DashboardScreen(
                    currentPage = currentPage,
                    pageIndex = 0
                )
                1 -> EventsScreen(
                    onEventClick = { event: Event ->
                        sharedEventViewModel.selectEvent(event)
                        navController.navigate("event_detail")
                    },
                    currentPage = currentPage,
                    pageIndex = 1,
                    sharedEventViewModel = sharedEventViewModel
                )
                2 -> DailyReviewScreen(
                    viewModel = dailyReviewViewModel,
                    currentPage = currentPage,
                    pageIndex = 2
                )
            }
        }
    }
}

