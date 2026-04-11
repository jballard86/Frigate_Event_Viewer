package com.example.frigateeventviewer.ui.screens

import android.content.Intent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mikepenz.markdown.compose.LocalMarkdownTypography
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.MarkdownTypography
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Human-readable label for a report day (Today / Yesterday / short date).
 */
internal fun formatDailyReviewDateLabel(isoDate: String, zoneId: ZoneId = ZoneId.systemDefault()): String {
    val date = LocalDate.parse(isoDate)
    val today = LocalDate.now(zoneId)
    val yesterday = today.minusDays(1)
    return when (date) {
        today -> "Today"
        yesterday -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyReviewScreen(
    viewModel: DailyReviewViewModel,
    currentPage: Int,
    pageIndex: Int,
) {
    val state by viewModel.state.collectAsState()
    val datesLoading by viewModel.datesLoading.collectAsState()
    val availableDates by viewModel.availableDates.collectAsState()
    val selectedDateIso by viewModel.selectedDateIso.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    /** Prefer the date from [DailyReviewState.Success] so the field never goes blank while loading. */
    val displayDateIso = when (val s = state) {
        is DailyReviewState.Success -> s.selectedDateIso
        else -> selectedDateIso
    } ?: availableDates.firstOrNull()

    LaunchedEffect(lifecycle, currentPage, pageIndex) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (currentPage == pageIndex) {
                viewModel.refresh(force = false)
            }
        }
    }

    val isRefreshing = state is DailyReviewState.Loading

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refresh(force = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        val scrollState = rememberScrollState()
        var longPressActive by remember { mutableStateOf(false) }
        val isAtTop = remember(state, scrollState.value, scrollState.maxValue) {
            when (state) {
                is DailyReviewState.Success -> {
                    val buffer = (scrollState.maxValue * 0.03f).toInt().coerceAtLeast(0)
                    scrollState.value <= buffer
                }
                else -> true
            }
        }
        val showButton = isAtTop && !longPressActive
        val coroutineScope = rememberCoroutineScope()

        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp)
                        .pointerInput(Unit, coroutineScope) {
                            awaitPointerEventScope {
                                var pressJob: Job? = null
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    when (event.type) {
                                        PointerEventType.Press -> {
                                            pressJob?.cancel()
                                            pressJob = coroutineScope.launch {
                                                delay(500)
                                                longPressActive = true
                                            }
                                        }
                                        PointerEventType.Release -> {
                                            pressJob?.cancel()
                                            pressJob = null
                                            longPressActive = false
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        },
                ) {
                    var dropdownExpanded by remember { mutableStateOf(false) }
                    var dropdownWidthDp by remember { mutableStateOf(0.dp) }
                    val density = LocalDensity.current

                    val canOpenDropdown = !datesLoading && availableDates.isNotEmpty()
                    val triggerLabel = when {
                        datesLoading -> "Loading dates…"
                        displayDateIso != null -> formatDailyReviewDateLabel(displayDateIso)
                        else -> "Select report day"
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .padding(bottom = 8.dp)
                            .onGloballyPositioned { coordinates ->
                                dropdownWidthDp = with(density) { coordinates.size.width.toDp() }
                            },
                    ) {
                        OutlinedTextField(
                            value = triggerLabel,
                            onValueChange = {},
                            readOnly = true,
                            enabled = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp),
                            trailingIcon = {
                                if (datesLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.outline,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            singleLine = true,
                        )
                        if (canOpenDropdown) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                    ) { dropdownExpanded = true },
                            )
                        }
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = if (dropdownWidthDp > 0.dp) Modifier.width(dropdownWidthDp) else Modifier,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            availableDates.forEach { iso ->
                                DropdownMenuItem(
                                    text = { Text(formatDailyReviewDateLabel(iso)) },
                                    onClick = {
                                        viewModel.selectDate(iso)
                                        dropdownExpanded = false
                                    },
                                )
                            }
                        }
                    }

                    when (val s = state) {
                        is DailyReviewState.Success -> {
                            if (s.isViewingToday) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(bottom = 8.dp),
                                ) {
                                    Text(
                                        text = "In progress — report updates as events occur",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            DailyReviewContent(
                                markdownText = s.markdownText,
                                scrollState = scrollState,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        is DailyReviewState.Idle,
                        is DailyReviewState.Loading,
                        -> {
                            DailyReviewLoading(
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        is DailyReviewState.Empty -> {
                            DailyReviewEmpty(
                                message = s.message,
                                onGenerate = { viewModel.generateNewReview() },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        is DailyReviewState.Error -> {
                            DailyReviewError(
                                message = s.message,
                                onRetry = { viewModel.fetchDailyReview() },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
            val successForActions = state as? DailyReviewState.Success
            DailyReviewBottomActions(
                visible = showButton && successForActions != null,
                markdownText = successForActions?.markdownText,
                reportDateIso = successForActions?.selectedDateIso,
                onGenerate = { viewModel.generateNewReview() },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun DailyReviewBottomActions(
    visible: Boolean,
    markdownText: String?,
    reportDateIso: String?,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val edgeInset = 16.dp
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    val md = markdownText ?: return@Button
                    val d = reportDateIso ?: return@Button
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, md)
                        putExtra(Intent.EXTRA_SUBJECT, "Daily report $d")
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Save report"))
                },
                enabled = !markdownText.isNullOrBlank() && !reportDateIso.isNullOrBlank(),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = edgeInset)
                    .height(40.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Save",
                        maxLines = 1,
                    )
                }
            }
            Button(
                onClick = onGenerate,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = edgeInset)
                    .height(40.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Generate New Report",
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyReviewLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            text = "Loading…",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun DailyReviewEmpty(
    message: String,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onGenerate,
            modifier = Modifier.height(40.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Generate New Report",
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun DailyReviewContent(
    markdownText: String,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val h1Style = MaterialTheme.typography.titleLarge
    val h2Style = MaterialTheme.typography.titleMedium
    val h3Style = MaterialTheme.typography.titleSmall
    val paragraphStyle = MaterialTheme.typography.bodyMedium
    val customTypography = remember(h1Style, h2Style, h3Style, paragraphStyle) {
        object : MarkdownTypography {
            override val h1: TextStyle get() = h1Style
            override val h2: TextStyle get() = h2Style
            override val h3: TextStyle get() = h3Style
            override val h4: TextStyle get() = paragraphStyle
            override val h5: TextStyle get() = paragraphStyle
            override val h6: TextStyle get() = paragraphStyle
            override val paragraph: TextStyle get() = paragraphStyle
            override val code: TextStyle get() = paragraphStyle
            override val bullet: TextStyle get() = paragraphStyle
            override val list: TextStyle get() = paragraphStyle
            override val ordered: TextStyle get() = paragraphStyle
            override val quote: TextStyle get() = paragraphStyle
            override val text: TextStyle get() = paragraphStyle
        }
    }
    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(bottom = 80.dp),
    ) {
        CompositionLocalProvider(
            LocalMarkdownTypography provides customTypography,
        ) {
            Markdown(
                content = markdownText,
                typography = customTypography,
                modifier = Modifier,
            )
        }
    }
}

@Composable
private fun DailyReviewError(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text("Retry")
        }
    }
}
