package com.example.frigateeventviewer.ui.screens

import android.net.Uri
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.frigateeventviewer.R
import com.example.frigateeventviewer.data.model.Event
import com.example.frigateeventviewer.ui.util.EventMediaPath
import com.example.frigateeventviewer.ui.util.formatCameraName
import com.example.frigateeventviewer.ui.util.formatTimestamp
import com.example.frigateeventviewer.ui.util.buildMediaUrl
import com.example.frigateeventviewer.ui.util.SwipeBackBox
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    selectedEvent: Event?,
    onBack: () -> Unit,
    eventListLabel: String? = null,
    onCycleEvent: ((Int) -> Unit)? = null,
    canNavigatePrevious: Boolean = false,
    canNavigateNext: Boolean = false,
    onCyclePrevious: () -> Unit = {},
    onCycleNext: () -> Unit = {},
    onUnmarkCompleted: () -> Unit = {},
    onEventActionCompleted: (markedReviewedEventId: String?, deletedEventId: String?, advanceFromCurrent: Boolean) -> Unit = { _, _, _ -> },
    viewModel: EventDetailViewModel = viewModel<EventDetailViewModel>(
        factory = EventDetailViewModelFactory(
            LocalContext.current.applicationContext as android.app.Application
        )
    )
) {
    val baseUrl by viewModel.baseUrl.collectAsState()
    val operationState by viewModel.operationState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(operationState) {
        when (val opState = operationState) {
            is EventDetailOperationState.Success -> when (opState.action) {
                EventDetailAction.DELETE -> {
                    onEventActionCompleted(null, selectedEvent?.event_id, false)
                    onBack()
                }
                EventDetailAction.KEEP -> {
                    onEventActionCompleted(null, null, true)
                    viewModel.resetOperationState()
                }
                EventDetailAction.MARK_VIEWED -> {
                    onEventActionCompleted(selectedEvent?.event_id, null, false)
                    snackbarHostState.showSnackbar("Marked as reviewed")
                    viewModel.resetOperationState()
                }
                EventDetailAction.UNMARK_VIEWED -> {
                    onUnmarkCompleted()
                    snackbarHostState.showSnackbar("Unmarked as reviewed")
                    viewModel.resetOperationState()
                }
            }
            is EventDetailOperationState.Error -> {
                snackbarHostState.showSnackbar(opState.message)
                viewModel.resetOperationState()
            }
            else -> {}
        }
    }

    val baseTitle = "Event Detail"
    val fullTitle = eventListLabel?.let { "$baseTitle - $it" } ?: baseTitle

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fullTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        SwipeBackBox(
            enabled = false,
            onBack = onBack,
            modifier = Modifier.fillMaxSize()
        ) {
        if (selectedEvent == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "No event selected",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(onClick = onBack) { Text("Back") }
                }
            }
        } else {
        val event = selectedEvent
        val eventPath = "${event.camera}/${event.subdir}"
        val isOperationLoading = operationState is EventDetailOperationState.Loading

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    EventVideoSection(
                        event = event,
                        baseUrl = baseUrl,
                        onVerticalSwipe = onCycleEvent
                    )
                }
                if (canNavigatePrevious || canNavigateNext) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Swipe on video or use buttons to move between events in this list",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = onCyclePrevious,
                                enabled = canNavigatePrevious && !isOperationLoading
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Previous event"
                                )
                            }
                            IconButton(
                                onClick = onCycleNext,
                                enabled = canNavigateNext && !isOperationLoading
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Next event"
                                )
                            }
                        }
                    }
                }
                EventActionsSection(
                    eventPath = eventPath,
                    saved = event.saved == true,
                    viewed = event.viewed,
                    isLoading = isOperationLoading,
                    onMarkReviewed = { viewModel.markViewed(eventPath) },
                    onUnmarkReviewed = { viewModel.unmarkViewed(eventPath) },
                    onKeep = { viewModel.keepEvent(eventPath) },
                    onDelete = { viewModel.deleteEvent(eventPath) }
                )
                EventMetadataSection(event = event)
            }
        }
        }
        }
    }
}

@Composable
private fun EventVideoSection(
    event: Event,
    baseUrl: String?,
    onVerticalSwipe: ((Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current.density
    var videoAspectRatio by remember(event.camera, event.subdir) { mutableStateOf(16f / 9f) }

    val clipCandidateUrls = remember(event, baseUrl) {
        EventMediaPath.getClipPathCandidates(event).mapNotNull { buildMediaUrl(baseUrl, it) }.distinct()
    }
    val placeholderCandidateUrls = remember(event, baseUrl) {
        EventMediaPath.getPlaceholderPathCandidates(event).mapNotNull { buildMediaUrl(baseUrl, it) }.distinct()
    }

    val videoModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(videoAspectRatio)
        .clip(RoundedCornerShape(12.dp))

    if (clipCandidateUrls.isEmpty()) {
        if (placeholderCandidateUrls.isNotEmpty()) {
            EventPlaceholderImage(
                candidateUrls = placeholderCandidateUrls,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No video or image available for this event",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val firstClipUrl = clipCandidateUrls.first()
    val player = remember(event.camera, event.subdir, firstClipUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(firstClipUrl.toUri()))
            prepare()
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = false
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, lifecycleEvent ->
            when (lifecycleEvent) {
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

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val h = videoSize.height
                if (h > 0) {
                    videoAspectRatio = videoSize.width.toFloat() / h
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    AndroidView(
        factory = {
            EventVideoGestureFrameLayout(context, density).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                val playerView = LayoutInflater.from(context).inflate(
                    R.layout.event_detail_player_view,
                    this,
                    false
                ) as PlayerView
                playerView.layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
                addView(playerView)
            }
        },
        update = { container ->
            val frame = container as EventVideoGestureFrameLayout
            frame.onVerticalSwipe = onVerticalSwipe
            val playerView = frame.getChildAt(0) as PlayerView
            playerView.player = player
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)
            playerView.controllerShowTimeoutMs = 1000
        },
        modifier = videoModifier,
        onRelease = { container ->
            val frame = container as EventVideoGestureFrameLayout
            frame.onVerticalSwipe = null
            (frame.getChildAt(0) as PlayerView).player = null
        }
    )
}

/**
 * Sits above [PlayerView] in the view hierarchy and uses [onInterceptTouchEvent] so vertical drags
 * are recognized even when the video surface would otherwise consume the stream before
 * [View.setOnTouchListener] runs.
 */
private class EventVideoGestureFrameLayout(
    context: android.content.Context,
    private val density: Float
) : FrameLayout(context) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var intercepting = false

    var onVerticalSwipe: ((Int) -> Unit)? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (onVerticalSwipe == null) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                intercepting = false
                parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = ev.y - downY
                val dx = ev.x - downX
                if (!intercepting && abs(dy) > touchSlop && abs(dy) > abs(dx) * 1.05f) {
                    intercepting = true
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> intercepting = false
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val cb = onVerticalSwipe
        if (cb == null) return super.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (intercepting) return true
            MotionEvent.ACTION_UP -> {
                if (intercepting) {
                    val dy = ev.y - downY
                    val dx = ev.x - downX
                    intercepting = false
                    val minPx = 48f * density
                    if (abs(dy) >= minPx && abs(dy) > abs(dx) * 1.05f) {
                        cb(if (dy < 0) 1 else -1)
                    }
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                if (intercepting) {
                    intercepting = false
                    return true
                }
            }
        }
        return super.onTouchEvent(ev)
    }
}

/**
 * Tries each candidate URL in order; shows first successful image or "No preview" when all fail.
 */
@Composable
private fun EventPlaceholderImage(
    candidateUrls: List<String>,
    modifier: Modifier,
    contentScale: ContentScale
) {
    val context = LocalContext.current
    var currentIndex by remember { mutableStateOf(0) }
    val url = candidateUrls.getOrNull(currentIndex)
    val imageRequest = url?.let {
        remember(it) { ImageRequest.Builder(context).data(it).build() }
    }

    if (url != null && currentIndex < candidateUrls.size && imageRequest != null) {
        AsyncImage(
            model = imageRequest,
            contentDescription = "Event snapshot",
            modifier = modifier,
            contentScale = contentScale,
            onState = { state ->
                if (state is AsyncImagePainter.State.Error) {
                    if (currentIndex + 1 < candidateUrls.size) {
                        currentIndex += 1
                    } else {
                        currentIndex = candidateUrls.size
                    }
                }
            }
        )
    } else {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No video or image available for this event",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EventActionsSection(
    eventPath: String,
    saved: Boolean,
    viewed: Boolean,
    isLoading: Boolean,
    onMarkReviewed: () -> Unit,
    onUnmarkReviewed: () -> Unit,
    onKeep: () -> Unit,
    onDelete: () -> Unit
) {
    val actionShape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onDelete,
            enabled = !isLoading,
            modifier = Modifier.weight(1f).height(40.dp),
            shape = actionShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("Delete", maxLines = 1)
        }
        if (viewed) {
            Button(
                onClick = onUnmarkReviewed,
                enabled = !isLoading,
                modifier = Modifier.weight(1.4f).height(40.dp),
                shape = actionShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Text("Unmark reviewed", maxLines = 1)
            }
        } else {
            Button(
                onClick = onMarkReviewed,
                enabled = !isLoading,
                modifier = Modifier.weight(1.4f).height(40.dp),
                shape = actionShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Mark Reviewed", maxLines = 1)
            }
        }
        Button(
            onClick = onKeep,
            enabled = !isLoading && !saved,
            modifier = Modifier.weight(1f).height(40.dp),
            shape = actionShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary
            )
        ) {
            Text(if (saved) "Saved" else "Keep", maxLines = 1)
        }
    }
}

@Composable
private fun EventMetadataSection(event: Event) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        event.title?.let { title ->
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        }
        event.description?.takeIf { it.isNotBlank() }?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        event.scene?.let { scene ->
            Text(
                text = scene,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "Camera: ${formatCameraName(event.camera)}",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "Date: ${formatTimestamp(event.timestamp)}",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "Threat level: ${event.threat_level}",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

