package com.example.frigateeventviewer.ui.screens

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import com.example.frigateeventviewer.FrigateEventViewerApplication
import com.example.frigateeventviewer.data.Go2RtcStreamsState
import com.example.frigateeventviewer.data.Go2RtcStreamsRepository
import com.example.frigateeventviewer.data.preferences.SettingsPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import coil.compose.AsyncImage

/**
 * UI state for the Live tab (go2rtc streams).
 */
sealed class LiveState {
    data object Loading : LiveState()
    data class Success(val streamNames: List<String>) : LiveState()
    data class Error(val message: String) : LiveState()
}

/**
 * ViewModel for the Live tab. Uses shared [Go2RtcStreamsRepository] state; no per-tab fetch.
 * Selected camera is stored in [SavedStateHandle] so it survives configuration changes (e.g. rotation).
 */
class LiveViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val preferences = SettingsPreferences(application)
    private val go2RtcRepository = (application as? FrigateEventViewerApplication)?.go2RtcStreamsRepository

    /** Maps shared repository state to LiveState for UI (Error message for Unavailable). */
    val state: StateFlow<LiveState> = (go2RtcRepository?.state ?: MutableStateFlow(Go2RtcStreamsState.Unavailable(message = null)).asStateFlow())
        .map { repoState ->
            when (repoState) {
                is Go2RtcStreamsState.Loading -> LiveState.Loading
                is Go2RtcStreamsState.Success -> LiveState.Success(repoState.streamNames)
                is Go2RtcStreamsState.Unavailable -> LiveState.Error(repoState.message ?: "Set Frigate IP in Settings")
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LiveState.Loading
        )

    private val _selectedStreamName = MutableStateFlow<String?>(null)
    val selectedStreamName: StateFlow<String?> = _selectedStreamName.asStateFlow()

    /** Stream names for the dropdown: default camera (from Settings) first, then the rest. Same items as repository, reordered. */
    private val _displayStreamNames = MutableStateFlow<List<String>>(emptyList())
    val displayStreamNames: StateFlow<List<String>> = _displayStreamNames.asStateFlow()

    /** MSE/MP4 stream URL for the selected camera via Frigate proxy (port 5000). Null when no selection or no Frigate IP. */
    private val _liveStreamUrl = MutableStateFlow<String?>(null)
    val liveStreamUrl: StateFlow<String?> = _liveStreamUrl.asStateFlow()

    /** Frigate API base URL (port 5000) for building stream and snapshot URLs. Null when no Frigate IP. */
    private val _liveFrigateBaseUrl = MutableStateFlow<String?>(null)
    val liveFrigateBaseUrl: StateFlow<String?> = _liveFrigateBaseUrl.asStateFlow()

    init {
        viewModelScope.launch {
            go2RtcRepository?.state?.collect { repoState ->
                if (repoState is Go2RtcStreamsState.Success) {
                    val names = repoState.streamNames
                    val savedSelection = savedStateHandle.get<String>(KEY_SELECTED_STREAM_NAME)
                    val savedDefault = preferences.getDefaultLiveCameraOnce()
                    _selectedStreamName.value = when {
                        savedSelection != null && names.contains(savedSelection) -> savedSelection
                        !savedDefault.isNullOrBlank() && names.contains(savedDefault) -> savedDefault
                        names.isNotEmpty() -> names[0]
                        else -> null
                    }
                    val selected = _selectedStreamName.value
                    _displayStreamNames.value = if (selected != null && selected in names) {
                        listOf(selected) + names.filter { it != selected }
                    } else if (!savedDefault.isNullOrBlank() && savedDefault in names) {
                        listOf(savedDefault) + names.filter { it != savedDefault }
                    } else {
                        names
                    }
                    updateLiveStreamUrl()
                }
            }
        }
    }

    private suspend fun updateLiveStreamUrl() {
        val frigateIp = preferences.getFrigateIpOnce()
        val name = _selectedStreamName.value
        val base = if (frigateIp != null) SettingsPreferences.buildFrigateApiBaseUrl(frigateIp) else null
        _liveFrigateBaseUrl.value = base
        _liveStreamUrl.value = if (base != null && !name.isNullOrBlank()) {
            "${base}api/go2rtc/api/stream.mp4?src=$name"
        } else {
            null
        }
    }

    /** Re-fetches go2rtc streams (shared cache). Called on Retry. */
    fun refresh() {
        viewModelScope.launch {
            go2RtcRepository?.refresh()
        }
    }

    fun setSelectedStreamName(name: String?) {
        _selectedStreamName.value = name
        savedStateHandle[KEY_SELECTED_STREAM_NAME] = name
        viewModelScope.launch { updateLiveStreamUrl() }
    }

    companion object {
        private const val KEY_SELECTED_STREAM_NAME = "live_selected_stream_name"
    }
}

/**
 * Factory for [LiveViewModel]. Uses [CreationExtras] so the owner's [SavedStateHandle]
 * is supplied by the framework; selected camera survives configuration changes (e.g. rotation).
 */
class LiveViewModelFactory : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (!modelClass.isAssignableFrom(LiveViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
        }
        val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as Application
        val savedStateHandle = extras.createSavedStateHandle()
        return LiveViewModel(application, savedStateHandle) as T
    }
}

/**
 * Live tab screen: "Select Camera" dropdown (from go2rtc streams) and live video player.
 * Styling matches other main tab screens (16.dp padding, 12.dp shapes). Per UI_MAP: video 16:9, RoundedCornerShape(12.dp), RESIZE_MODE_ZOOM, controller 1s.
 */
@Composable
private fun LiveVideoPlayer(
    streamUrl: String?,
    streamName: String?,
    baseUrl: String?,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val videoModifier = modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f)
        .clip(RoundedCornerShape(12.dp))

    if (streamUrl.isNullOrBlank()) {
        Box(
            modifier = videoModifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Select a camera",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val player = remember(streamUrl) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 2_000,
                /* maxBufferMs = */ 10_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 1_000
            )
            .build()
        ExoPlayer.Builder(context)
            .setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
            .setLoadControl(loadControl)
            .build()
            .apply {
                val mediaItem = MediaItem.Builder()
                    .setUri(streamUrl.toUri())
                    .build()
                setMediaItem(mediaItem)
                prepare()
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = true
            }
    }

    var streamError by remember { mutableStateOf<String?>(null) }
    var streamStatus by remember { mutableStateOf<String?>(null) }
    var hasStartedPlaying by remember(streamUrl) { mutableStateOf(false) }
    var isVideoReady by remember(streamUrl) { mutableStateOf(false) }
    LaunchedEffect(streamUrl) {
        streamError = null
        streamStatus = "Connecting..."
        isVideoReady = false
    }
    LaunchedEffect(isVisible) {
        if (!isVisible) {
            player.stop()
            player.clearMediaItems()
            isVideoReady = false
        } else {
            streamStatus = "Connecting..."
            if (player.mediaItemCount == 0) {
                val mediaItem = MediaItem.Builder()
                    .setUri(streamUrl!!.toUri())
                    .build()
                player.setMediaItem(mediaItem)
                player.prepare()
                player.playWhenReady = true
                player.play()
            }
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val primaryMessage = when (val cause = error.cause) {
                    is HttpDataSource.InvalidResponseCodeException -> {
                        val code = cause.responseCode
                        val msg = cause.responseMessage?.takeIf { it.isNotBlank() }
                        if (msg != null) "HTTP $code $msg" else "HTTP $code"
                    }
                    else -> {
                        when (error.errorCode) {
                            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                                "HTTP error"
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                                cause?.message ?: error.message ?: "Connection failed"
                            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                            PlaybackException.ERROR_CODE_DECODING_FAILED,
                            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ->
                                "Unsupported video format on this device."
                            else ->
                                cause?.message ?: error.message ?: "Stream error"
                        }
                    }
                }
                val code = error.errorCode
                val isDecodingOrFormat = code == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                    code == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                    code == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ||
                    code == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES
                streamError = if (isDecodingOrFormat && error.message != null) {
                    "$primaryMessage\n${error.message}"
                } else {
                    primaryMessage
                }
                streamStatus = null
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                streamStatus = when (playbackState) {
                    Player.STATE_IDLE -> "Loading..."
                    Player.STATE_BUFFERING -> if (hasStartedPlaying) null else "Connecting..."
                    Player.STATE_READY -> {
                        hasStartedPlaying = true
                        isVideoReady = true
                        player.play()
                        null
                    }
                    else -> null
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    DisposableEffect(lifecycleOwner) {
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

    Column(Modifier.fillMaxWidth()) {
        Box(modifier = videoModifier) {
            AndroidView(
                factory = {
                    PlayerView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
                        controllerShowTimeoutMs = 1000
                        this.player = player
                    }
                },
                update = { playerView ->
                    playerView.player = player
                    playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
                },
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                onRelease = { playerView ->
                    playerView.player = null
                }
            )
            val snapshotUrl = if (!baseUrl.isNullOrBlank() && !streamName.isNullOrBlank()) {
                "${baseUrl.trimEnd('/')}/api/cameras/$streamName/latest.jpg"
            } else {
                null
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = !isVideoReady,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            ) {
                if (snapshotUrl != null) {
                    AsyncImage(
                        model = snapshotUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .padding(top = 8.dp)
        ) {
            if (streamError != null) {
                Text(
                    text = streamError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (streamStatus != null) {
                Text(
                    text = streamStatus!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun LiveScreen(
    currentPage: Int,
    pageIndex: Int,
    viewModel: LiveViewModel
) {
    val state by viewModel.state.collectAsState()
    val selectedStreamName by viewModel.selectedStreamName.collectAsState()
    val displayStreamNames by viewModel.displayStreamNames.collectAsState()
    val liveStreamUrl by viewModel.liveStreamUrl.collectAsState()
    val liveFrigateBaseUrl by viewModel.liveFrigateBaseUrl.collectAsState()
    val isVisible = currentPage == pageIndex
    var dropdownExpanded by remember { mutableStateOf(false) }
    var dropdownWidthDp by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    val dropdownLabel = when (val s = state) {
        is LiveState.Loading -> "Loading…"
        is LiveState.Success -> selectedStreamName ?: displayStreamNames.firstOrNull() ?: "No cameras"
        is LiveState.Error -> "No camera"
    }
    val isDropdownEnabled = state is LiveState.Success

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Select Camera",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        dropdownWidthDp = with(density) { coordinates.size.width.toDp() }
                    }
            ) {
                OutlinedTextField(
                    value = dropdownLabel,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.outline,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                if (isDropdownEnabled) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { dropdownExpanded = true }
                    )
                }
                if (state is LiveState.Success) {
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = if (dropdownWidthDp > 0.dp) Modifier.width(dropdownWidthDp) else Modifier,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (displayStreamNames.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No cameras") },
                                onClick = { dropdownExpanded = false }
                            )
                        } else {
                            displayStreamNames.forEach { name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        viewModel.setSelectedStreamName(name)
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            when (val s = state) {
                is LiveState.Error -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = s.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = { viewModel.refresh() }) {
                            Text("Retry")
                        }
                    }
                }
                is LiveState.Success -> {
                    LiveVideoPlayer(
                        streamUrl = liveStreamUrl,
                        streamName = selectedStreamName,
                        baseUrl = liveFrigateBaseUrl,
                        isVisible = isVisible
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Loading…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
