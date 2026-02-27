package com.example.frigateeventviewer.ui.util

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Wraps [content] in a Box and applies a full-width swipe-right-to-go-back gesture.
 * A rightward swipe from anywhere on the screen triggers [onBack], similar to swiping
 * between tabs. Vertical scroll is preserved: the gesture is only consumed when horizontal
 * movement exceeds [triggerThreshold] and dominates vertical movement, so scrollable
 * content (e.g. Settings, Event detail) still scrolls when the user drags vertically.
 *
 * @param enabled When false, the gesture is disabled and [onBack] is never called.
 * @param onBack Invoked once per gesture when the user swipes right past the threshold.
 * @param triggerThreshold Minimum rightward drag in dp to trigger [onBack] (default 100.dp).
 */
@Composable
fun SwipeBackBox(
    enabled: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    triggerThreshold: Dp = 100.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val triggerPx = with(density) { triggerThreshold.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (enabled) {
                    Modifier.pointerInput(enabled, triggerPx) {
                        awaitPointerEventScope {
                            var totalDragX = 0f
                            var totalDragY = 0f
                            var triggered = false

                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                when (event.type) {
                                    PointerEventType.Press -> {
                                        totalDragX = 0f
                                        totalDragY = 0f
                                        triggered = false
                                    }
                                    PointerEventType.Move -> {
                                        if (triggered) {
                                            event.changes.forEach { it.consume() }
                                            continue
                                        }
                                        var deltaX = 0f
                                        var deltaY = 0f
                                        event.changes.forEach { change ->
                                            deltaX += change.position.x - change.previousPosition.x
                                            deltaY += change.position.y - change.previousPosition.y
                                        }
                                        totalDragX += deltaX
                                        totalDragY += deltaY
                                        // Rightward in LTR is positive X; only trigger when horizontal dominates
                                        if (totalDragX > triggerPx &&
                                            totalDragX > abs(totalDragY)
                                        ) {
                                            triggered = true
                                            onBack()
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                    else -> {
                                        totalDragX = 0f
                                        totalDragY = 0f
                                        triggered = false
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        content()
    }
}
