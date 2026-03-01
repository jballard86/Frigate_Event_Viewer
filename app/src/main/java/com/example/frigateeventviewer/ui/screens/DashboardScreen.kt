package com.example.frigateeventviewer.ui.screens

import android.app.Application
import android.content.res.Configuration
import android.net.Uri
import android.text.format.DateUtils
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.frigateeventviewer.data.model.Event
import com.example.frigateeventviewer.data.model.StatsResponse
import com.example.frigateeventviewer.ui.util.buildMediaUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    currentPage: Int,
    pageIndex: Int,
    viewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModelFactory(
            LocalContext.current.applicationContext as Application
        )
    )
) {
    val state by viewModel.state.collectAsState()
    val isRefreshing = state is DashboardState.Loading
    val recentEvent by viewModel.recentEvent.collectAsState()
    val baseUrl by viewModel.baseUrl.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(lifecycle, currentPage, pageIndex) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (currentPage == pageIndex) {
                viewModel.refresh()
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val configuration = LocalConfiguration.current
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val availableHeightDp: Dp? = if (isLandscape) maxHeight else null

            when (val s = state) {
            is DashboardState.Loading -> {
                if (s.previous != null) {
                    DashboardContent(
                        stats = s.previous,
                        recentEvent = recentEvent,
                        baseUrl = baseUrl,
                        onRetry = { viewModel.refresh() },
                        availableHeightDp = availableHeightDp
                    )
                } else {
                    BoxWithProgress()
                }
            }
            is DashboardState.Success -> {
                DashboardContent(
                    stats = s.stats,
                    recentEvent = recentEvent,
                    baseUrl = baseUrl,
                    onRetry = { viewModel.refresh() },
                    availableHeightDp = availableHeightDp
                )
            }
            is DashboardState.Error -> {
                DashboardError(message = s.message, onRetry = { viewModel.refresh() })
            }
        }
        }
    }
}

@Composable
private fun BoxWithProgress() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Text(
            text = "Loading…",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Composable
private fun DashboardError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("Retry")
        }
    }
}

@Composable
private fun DashboardContent(
    stats: StatsResponse,
    recentEvent: Event?,
    baseUrl: String?,
    onRetry: () -> Unit,
    availableHeightDp: Dp? = null
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        RecentEventCard(
            event = recentEvent,
            baseUrl = baseUrl,
            availableHeightDp = availableHeightDp
        )

        val events = stats.events
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "Today",
                value = (events?.today ?: 0).toString(),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "This Week",
                value = (events?.this_week ?: 0).toString(),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "This Month",
                value = (events?.this_month ?: 0).toString(),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Unreviewed",
                value = (events?.total_unreviewed ?: 0).toString(),
                modifier = Modifier.weight(1f)
            )
        }

        val storage = stats.storage?.total_display
        val storageText = if (storage != null) {
            "${storage.value} ${storage.unit}"
        } else {
            "—"
        }
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Storage usage",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 32.dp)
                        .padding(top = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = storageText,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    OutlinedCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 32.dp)
                    .padding(top = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}

@Composable
private fun RecentEventCard(
    event: Event?,
    baseUrl: String?,
    availableHeightDp: Dp? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        if (event == null || !event.has_clip) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No recent video events",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Card
        }

        val clipPath = event.hosted_clip?.takeIf { it.isNotBlank() }
            ?: event.hosted_clips.firstOrNull()?.url
        val clipUrl = buildMediaUrl(baseUrl, clipPath)

        if (clipUrl == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No recent video events",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Card
        }

        RecentEventVideoPlayer(
            clipUrl = clipUrl,
            availableHeightDp = availableHeightDp
        )
        RecentEventTextSection(event = event)
    }
}

@Composable
private fun RecentEventVideoPlayer(
    clipUrl: String,
    availableHeightDp: Dp? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val player = remember(clipUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(clipUrl)))
            prepare()
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = false
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.pause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    val isLandscape = availableHeightDp != null
    val resizeMode = if (isLandscape) {
        AspectRatioFrameLayout.RESIZE_MODE_FIT
    } else {
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    }
    val videoModifier = if (availableHeightDp != null) {
        Modifier.fillMaxWidth().height(availableHeightDp)
    } else {
        Modifier.fillMaxWidth().aspectRatio(16f / 9f)
    }.clip(RoundedCornerShape(12.dp))

    AndroidView(
        factory = {
            PlayerView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setResizeMode(resizeMode)
                controllerShowTimeoutMs = 1000
                this.player = player
            }
        },
        update = { playerView ->
            playerView.player = player
            playerView.setResizeMode(resizeMode)
        },
        modifier = videoModifier,
        onRelease = { playerView ->
            playerView.player = null
        }
    )
}

@Composable
private fun RecentEventTextSection(event: Event) {
    val relativeTime = remember(event.timestamp) {
        val seconds = event.timestamp.toDoubleOrNull()?.toLong()
            ?: event.timestamp.substringBefore('.').toLongOrNull()
            ?: 0L
        val timeMillis = seconds * 1000L
        DateUtils.getRelativeTimeSpanString(
            timeMillis,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
    }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = event.title ?: "Recent Event",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Happened $relativeTime",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
