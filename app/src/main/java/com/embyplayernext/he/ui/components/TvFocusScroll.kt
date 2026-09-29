package com.embyplayernext.he.ui.components

import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.tvScrollThenFocus(state: LazyListState): Modifier {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var scrollJob by remember { mutableStateOf<Job?>(null) }
    val isTv = (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    if (!isTv) return this

    return this
        .focusProperties {
            exit = { direction ->
                if (direction == FocusDirection.Up && state.canScrollBackward) {
                    FocusRequester.Cancel
                } else if (direction == FocusDirection.Down && state.canScrollForward) {
                    FocusRequester.Cancel
                } else {
                    FocusRequester.Default
                }
            }
        }
        .onPreviewKeyEvent { event ->
            val native = event.nativeKeyEvent
            if (native.action != android.view.KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
            val (direction, sign, canScroll) = when (native.keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_UP -> Triple(FocusDirection.Up, -1f, state.canScrollBackward)
                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> Triple(FocusDirection.Down, 1f, state.canScrollForward)
                else -> return@onPreviewKeyEvent false
            }

            if (scrollJob?.isActive == true) return@onPreviewKeyEvent true

            // First, try moving focus within the currently visible page
            val moved = focusManager.moveFocus(direction)
            if (moved) {
                // Focus moved successfully to an already visible item, no page scroll needed!
                return@onPreviewKeyEvent true
            }

            // Focus could not move because it reached the boundary of the visible items.
            // If the list can scroll in that direction, scroll to bring the next items into view!
            if (!canScroll) return@onPreviewKeyEvent false

            val amount = calculateListScrollAmount(state, sign)
            scrollJob = scope.launch {
                state.animateScrollBy(amount)
                focusManager.moveFocus(direction)
            }
            true
        }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.tvScrollThenFocus(state: LazyGridState): Modifier {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var scrollJob by remember { mutableStateOf<Job?>(null) }
    val isTv = (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    if (!isTv) return this

    return this
        .focusProperties {
            exit = { direction ->
                if (direction == FocusDirection.Up && state.canScrollBackward) {
                    FocusRequester.Cancel
                } else if (direction == FocusDirection.Down && state.canScrollForward) {
                    FocusRequester.Cancel
                } else {
                    FocusRequester.Default
                }
            }
        }
        .onPreviewKeyEvent { event ->
            val native = event.nativeKeyEvent
            if (native.action != android.view.KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
            val (direction, sign, canScroll) = when (native.keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_UP -> Triple(FocusDirection.Up, -1f, state.canScrollBackward)
                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> Triple(FocusDirection.Down, 1f, state.canScrollForward)
                else -> return@onPreviewKeyEvent false
            }

            if (scrollJob?.isActive == true) return@onPreviewKeyEvent true

            // First, try moving focus within the currently visible page
            val moved = focusManager.moveFocus(direction)
            if (moved) {
                // Focus moved successfully to an already visible item, no page scroll needed!
                return@onPreviewKeyEvent true
            }

            // Focus could not move because it reached the boundary of the visible items.
            // If the grid can scroll in that direction, scroll to bring the next row into view!
            if (!canScroll) return@onPreviewKeyEvent false

            val amount = calculateGridScrollAmount(state, sign)
            scrollJob = scope.launch {
                state.animateScrollBy(amount)
                focusManager.moveFocus(direction)
            }
            true
        }
}

private fun calculateListScrollAmount(state: LazyListState, sign: Float): Float {
    val items = state.layoutInfo.visibleItemsInfo
    val viewportHeight = state.layoutInfo.viewportSize.height.toFloat()
    if (items.isEmpty()) return (viewportHeight * 0.45f).coerceAtLeast(120f) * sign
    val avgHeight = items.map { it.size }.average().toFloat()
    val step = if (avgHeight > 60f) avgHeight * 1.15f else (viewportHeight * 0.45f)
    return step.coerceIn(120f, viewportHeight * 0.75f) * sign
}

private fun calculateGridScrollAmount(state: LazyGridState, sign: Float): Float {
    val items = state.layoutInfo.visibleItemsInfo
    val viewportHeight = state.layoutInfo.viewportSize.height.toFloat()
    if (items.isEmpty()) return (viewportHeight * 0.45f).coerceAtLeast(120f) * sign
    val avgHeight = items.map { it.size.height }.average().toFloat()
    val step = if (avgHeight > 60f) avgHeight * 1.15f else (viewportHeight * 0.45f)
    return step.coerceIn(120f, viewportHeight * 0.75f) * sign
}
