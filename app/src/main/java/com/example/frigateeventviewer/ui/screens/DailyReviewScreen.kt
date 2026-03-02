package com.example.frigateeventviewer.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.text.TextStyle
import com.mikepenz.markdown.compose.LocalMarkdownTypography
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.MarkdownTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyReviewScreen(
    viewModel: DailyReviewViewModel,
    currentPage: Int,
    pageIndex: Int
) {
    val state by viewModel.state.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(lifecycle, currentPage, pageIndex) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (currentPage == pageIndex) {
                viewModel.refresh(force = false)
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = state is DailyReviewState.Loading,
        onRefresh = { viewModel.refresh(force = true) },
        modifier = Modifier.fillMaxSize()
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
                modifier = Modifier.fillMaxSize()
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
                        }
                ) {
                    when (val s = state) {
                        is DailyReviewState.Idle,
                        is DailyReviewState.Loading -> {
                            DailyReviewLoading(
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        is DailyReviewState.Success -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            DailyReviewContent(
                                markdownText = s.markdownText,
                                scrollState = scrollState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        is DailyReviewState.Error -> {
                            DailyReviewError(
                                message = s.message,
                                onRetry = { viewModel.fetchDailyReview() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
            DailyReviewGenerateButton(
                visible = showButton,
                onClick = { viewModel.generateNewReview() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 0.dp)
            )
        }
    }
}

@Composable
private fun DailyReviewGenerateButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.height(40.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Generate New Report",
                    maxLines = 1
                )
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
private fun DailyReviewContent(
    markdownText: String,
    scrollState: ScrollState,
    modifier: Modifier = Modifier
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
            .padding(bottom = 80.dp)
    ) {
        CompositionLocalProvider(
            LocalMarkdownTypography provides customTypography
        ) {
            Markdown(
                content = markdownText,
                typography = customTypography,
                modifier = Modifier
            )
        }
    }
}

@Composable
private fun DailyReviewError(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
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
