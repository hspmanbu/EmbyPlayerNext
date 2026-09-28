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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
    return this.onPreviewKeyEvent { event ->
        val native = event.nativeKeyEvent
        if (native.action != android.view.KeyEvent.ACTION_DOWN || native.repeatCount != 0) return@onPreviewKeyEvent false
        val (direction, sign, canScroll) = when (native.keyCode) {
            android.view.KeyEvent.KEYCODE_DPAD_UP -> Triple(FocusDirection.Up, -1f, state.canScrollBackward)
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> Triple(FocusDirection.Down, 1f, state.canScrollForward)
            else -> return@onPreviewKeyEvent false
        }
        if (!canScroll) return@onPreviewKeyEvent false
        if (scrollJob?.isActive == true) return@onPreviewKeyEvent true
        val amount = state.layoutInfo.viewportSize.height.coerceAtLeast(1) * 0.58f * sign
        scrollJob = scope.launch {
            state.animateScrollBy(amount)
            focusManager.moveFocus(direction)
        }
        true
    }
}

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
    return this.onPreviewKeyEvent { event ->
        val native = event.nativeKeyEvent
        if (native.action != android.view.KeyEvent.ACTION_DOWN || native.repeatCount != 0) return@onPreviewKeyEvent false
        val (direction, sign, canScroll) = when (native.keyCode) {
            android.view.KeyEvent.KEYCODE_DPAD_UP -> Triple(FocusDirection.Up, -1f, state.canScrollBackward)
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> Triple(FocusDirection.Down, 1f, state.canScrollForward)
            else -> return@onPreviewKeyEvent false
        }
        if (!canScroll) return@onPreviewKeyEvent false
        if (scrollJob?.isActive == true) return@onPreviewKeyEvent true
        val amount = state.layoutInfo.viewportSize.height.coerceAtLeast(1) * 0.58f * sign
        scrollJob = scope.launch {
            state.animateScrollBy(amount)
            focusManager.moveFocus(direction)
        }
        true
    }
}
