package com.mariotatis.lockcontrol.ui.editor

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mariotatis.lockcontrol.data.ConfigRepository
import com.mariotatis.lockcontrol.data.LockConfig
import com.mariotatis.lockcontrol.data.PinHasher
import com.mariotatis.lockcontrol.ui.lock.LockBackground
import com.mariotatis.lockcontrol.ui.lock.KeypadStyle
import com.mariotatis.lockcontrol.ui.lock.PromptStyle
import com.mariotatis.lockcontrol.ui.lock.PadKeys
import com.mariotatis.lockcontrol.ui.lock.PasscodePanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PasscodeMode { SET, REMOVE }

private enum class Step { VERIFY, CREATE, CONFIRM }

/** Set, change or remove the passcode, using the same pad as the lock screen. */
@Composable
fun PasscodeSetupDialog(mode: PasscodeMode, config: LockConfig, onDismiss: () -> Unit) {
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val scope = rememberCoroutineScope()
        val view = LocalView.current
        var step by remember { mutableStateOf(if (config.hasPin) Step.VERIFY else Step.CREATE) }
        var length by remember { mutableIntStateOf(if (config.pinLength == 6) 6 else 4) }
        var entry by remember { mutableStateOf("") }
        var first by remember { mutableStateOf("") }
        var shake by remember { mutableIntStateOf(0) }
        var message by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
        val targetLength = if (step == Step.VERIFY) config.pinLength else length

        fun fail(text: String) {
            message = text
            shake++
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
            busy = true
            scope.launch {
                delay(420)
                entry = ""
                busy = false
            }
        }

        fun complete(pin: String) {
            when (step) {
                Step.VERIFY -> {
                    busy = true
                    scope.launch {
                        val ok = withContext(Dispatchers.Default) { PinHasher.verify(pin, config) }
                        busy = false
                        when {
                            !ok -> fail("Wrong passcode")
                            mode == PasscodeMode.REMOVE -> {
                                ConfigRepository.update { it.copy(pinHash = null, pinSalt = null, pinLength = 0) }
                                onDismiss()
                            }
                            else -> {
                                entry = ""
                                message = null
                                step = Step.CREATE
                            }
                        }
                    }
                }
                Step.CREATE -> {
                    first = pin
                    entry = ""
                    message = null
                    step = Step.CONFIRM
                }
                Step.CONFIRM -> if (pin == first) {
                    busy = true
                    scope.launch {
                        val salt = PinHasher.newSalt()
                        val hash = withContext(Dispatchers.Default) { PinHasher.hash(pin, salt) }
                        ConfigRepository.update { it.copy(pinHash = hash, pinSalt = salt, pinLength = pin.length) }
                        onDismiss()
                    }
                } else {
                    step = Step.CREATE
                    first = ""
                    fail("Passcodes didn't match. Try again.")
                }
            }
        }

        fun onKey(index: Int) {
            when (index) {
                PadKeys.CANCEL -> onDismiss()
                PadKeys.DELETE -> entry = entry.dropLast(1)
                else -> {
                    val digit = PadKeys.digitOf(index) ?: return
                    if (busy || entry.length >= targetLength) return
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    entry += digit
                    if (entry.length == targetLength) complete(entry)
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            LockBackground(config, playing = true, blur = config.passcodeBlur, dim = 0.45f)
            PasscodePanel(
                title = when (step) {
                    Step.VERIFY -> if (mode == PasscodeMode.REMOVE) "Enter passcode to remove it" else "Enter current passcode"
                    Step.CREATE -> "Choose a new passcode"
                    Step.CONFIRM -> "Confirm your passcode"
                },
                subtitle = message,
                pinLength = targetLength,
                entered = entry.length,
                shakeTick = shake,
                onKey = ::onKey,
                keys = KeypadStyle.of(config),
                prompt = PromptStyle(config.promptStyle),
                extra = {
                    if (step == Step.CREATE) {
                        Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(4, 6).forEach { n ->
                                val selected = n == length
                                Text(
                                    "$n digits",
                                    color = if (selected) Color.Black else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = if (selected) 0.95f else 0.18f))
                                        .clickable {
                                            length = n
                                            entry = ""
                                        }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                },
            )
        }
    }
}
