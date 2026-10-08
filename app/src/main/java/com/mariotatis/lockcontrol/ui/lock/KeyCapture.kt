package com.mariotatis.lockcontrol.ui.lock

import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.flow.MutableSharedFlow

private val passThrough = setOf(
    KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_POWER,
)

/**
 * Grabs focus and forwards every key press (except volume/power) to [sink],
 * consuming it so nothing else reacts.
 */
@Composable
fun Modifier.captureKeys(sink: MutableSharedFlow<Int>): Modifier {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    return this
        .focusRequester(focus)
        .focusable()
        .onPreviewKeyEvent { event ->
            val native = event.nativeKeyEvent
            if (native.keyCode in passThrough) return@onPreviewKeyEvent false
            val isDpad = native.keyCode in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT
            if (event.type == KeyEventType.KeyDown && (native.repeatCount == 0 || isDpad)) sink.tryEmit(native.keyCode)
            true
        }
}
