package com.mariotatis.lockcontrol.ui.lock

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import com.mariotatis.lockcontrol.data.KeyStyle
import com.mariotatis.lockcontrol.data.LockConfig
import com.mariotatis.lockcontrol.data.TextSpec
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Keypad cell indices, laid out 3 wide x 4 tall:
 *   0 1 2      1 2 3
 *   3 4 5  =>  4 5 6
 *   6 7 8      7 8 9
 *   9 10 11    Cancel 0 Delete
 */
object PadKeys {
    const val CANCEL = 9
    const val ZERO = 10
    const val DELETE = 11

    fun digitOf(index: Int): Int? = when (index) {
        in 0..8 -> index + 1
        ZERO -> 0
        else -> null
    }

    fun indexOf(digit: Int): Int = if (digit == 0) ZERO else digit - 1
}

private val letters = mapOf(
    2 to "ABC", 3 to "DEF", 4 to "GHI", 5 to "JKL", 6 to "MNO", 7 to "PQRS", 8 to "TUV", 9 to "WXYZ",
)

/** How the passcode keys look; derived from the config so the editor can preview it live. */
data class KeypadStyle(
    val style: KeyStyle = KeyStyle.GLASS,
    val keyColor: Color = Color.White,
    val keyAlpha: Float = 0.16f,
    val textColor: Color = Color.White,
    val showLetters: Boolean = true,
) {
    companion object {
        fun of(config: LockConfig) = KeypadStyle(
            config.keyStyle, Color(config.keyColor), config.keyAlpha, Color(config.keyTextColor), config.showKeyLetters,
        )
    }
}

/** The "Enter Passcode" heading and the lock icon above it. */
data class PromptStyle(
    val spec: TextSpec = TextSpec(weight = 500, size = 21f),
    val showText: Boolean = true,
    val showIcon: Boolean = true,
) {
    companion object {
        fun of(config: LockConfig) = PromptStyle(config.promptStyle, config.showPrompt, config.showPromptIcon)
    }
}

@Composable
fun PasscodePanel(
    title: String,
    pinLength: Int,
    entered: Int,
    shakeTick: Int,
    onKey: (Int) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    focusedKey: Int? = null,
    flashKey: Int? = null,
    keys: KeypadStyle = KeypadStyle(),
    prompt: PromptStyle = PromptStyle(),
    ghosts: Boolean = false,
    onPromptClick: (() -> Unit)? = null,
    extra: @Composable () -> Unit = {},
) {
    val ink = keys.textColor
    val promptColor = Color(prompt.spec.color).copy(alpha = prompt.spec.alpha.coerceIn(0f, 1f))
    // Editor ghosts: hidden parts stay faintly visible (and tappable) instead of vanishing.
    fun Modifier.part(shown: Boolean) = if (shown) this else if (ghosts) alpha(0.25f) else alpha(0f)
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val wide = maxWidth > maxHeight * 1.15f
        val button = if (wide) {
            min(84.dp, (maxHeight - 112.dp) / 4.8f)
        } else {
            min(84.dp, min((maxWidth - 64.dp) / 3.8f, (maxHeight - 280.dp) / 4.7f))
        }
        val gap = button * 0.26f

        val info: @Composable () -> Unit = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(300.dp)) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = if (onPromptClick != null) {
                        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onPromptClick).padding(6.dp)
                    } else Modifier,
                ) {
                    Icon(
                        Icons.Filled.Lock, null, tint = promptColor,
                        modifier = Modifier.part(prompt.showIcon).size(26.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        title, style = prompt.spec.toTextStyle(), textAlign = TextAlign.Center,
                        // A lockout message must always show, even if the prompt is hidden.
                        modifier = Modifier.part(prompt.showText || subtitle != null),
                    )
                }
                if (subtitle != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(subtitle, color = ink.copy(alpha = 0.75f), fontSize = 14.sp, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(22.dp))
                PasscodeDots(pinLength, entered, shakeTick, ink)
                extra()
            }
        }
        val keypad: @Composable () -> Unit = {
            Keypad(button, gap, enabled, entered > 0, focusedKey, flashKey, keys, onKey)
        }

        if (wide) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(button * 1.1f, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) { info(); keypad() }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                info()
                Spacer(Modifier.height(button * 0.6f))
                keypad()
            }
        }
    }
}

@Composable
fun PasscodeDots(length: Int, entered: Int, shakeTick: Int, color: Color = Color.White) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(shakeTick) {
        if (shakeTick > 0) {
            shake.snapTo(0f)
            shake.animateTo(1f, tween(480, easing = LinearEasing))
            shake.snapTo(0f)
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.graphicsLayer {
            val p = shake.value
            translationX = if (p == 0f) 0f else sin(p * PI * 6).toFloat() * 18.dp.toPx() * (1f - p)
        },
    ) {
        repeat(length) { i ->
            val filled by animateFloatAsState(if (i < entered) 1f else 0f, tween(120), label = "dot")
            Box(
                Modifier
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = filled))
                    .border(1.4.dp, color, CircleShape),
            )
        }
    }
}

@Composable
private fun Keypad(
    button: Dp,
    gap: Dp,
    enabled: Boolean,
    canDelete: Boolean,
    focusedKey: Int?,
    flashKey: Int?,
    keys: KeypadStyle,
    onKey: (Int) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(gap),
        modifier = Modifier.alpha(if (enabled) 1f else 0.4f),
    ) {
        for (row in 0 until 4) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap * 1.35f)) {
                for (col in 0 until 3) {
                    val index = row * 3 + col
                    val focused = focusedKey == index
                    val digit = PadKeys.digitOf(index)
                    when {
                        digit != null -> DigitKey(
                            digit, button, focused, flashKey == index, enabled, keys,
                        ) { onKey(index) }
                        index == PadKeys.CANCEL -> TextKey(button, focused, enabled, keys, { onKey(index) }) {
                            Text("Cancel", color = keys.textColor, fontSize = 16.sp)
                        }
                        else -> TextKey(button, focused, enabled && canDelete, keys, { onKey(index) }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Backspace, "Delete",
                                tint = keys.textColor.copy(alpha = if (canDelete) 1f else 0.35f),
                                modifier = Modifier.size(button * 0.34f),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.pressable(enabled: Boolean, onPress: () -> Unit, onPressedChange: (Boolean) -> Unit): Modifier =
    pointerInput(enabled) {
        if (!enabled) return@pointerInput
        detectTapGestures(onPress = {
            onPressedChange(true)
            onPress() // fire on touch-down like iOS, for a snappy feel
            tryAwaitRelease()
            onPressedChange(false)
        })
    }

private fun KeyStyle.shape(): Shape = if (this == KeyStyle.SQUARE) RoundedCornerShape(26) else CircleShape

private fun Modifier.focusRing(focused: Boolean, keys: KeypadStyle): Modifier =
    if (focused) border(2.dp, keys.textColor.copy(alpha = 0.95f), keys.style.shape()) else this

/** One key in any [KeyStyle]: drawn as filled glass, a ring, a rounded square, or text only. */
@Composable
fun KeyFace(size: Dp, keys: KeypadStyle, active: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = keys.style.shape()
    val rest = when (keys.style) {
        KeyStyle.GLASS, KeyStyle.SQUARE -> keys.keyAlpha
        KeyStyle.OUTLINE, KeyStyle.MINIMAL -> 0f
    }
    val fill by animateColorAsState(
        keys.keyColor.copy(alpha = if (active) (rest + 0.45f).coerceAtMost(0.85f) else rest),
        tween(if (active) 30 else 320), label = "key",
    )
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(fill)
            .then(
                if (keys.style == KeyStyle.OUTLINE) {
                    Modifier.border(1.5.dp, keys.keyColor.copy(alpha = (keys.keyAlpha * 4f).coerceIn(0.3f, 1f)), shape)
                } else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun DigitLabel(digit: Int, size: Dp, color: Color, showLetters: Boolean = true) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "$digit", color = color, fontWeight = FontWeight.Light,
            fontSize = (size.value * 0.42f).sp, lineHeight = (size.value * 0.46f).sp,
        )
        val sub = letters[digit].takeIf { showLetters }
        if (sub != null) {
            Text(
                sub, color = color, fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.12f).sp, letterSpacing = 0.18.em, lineHeight = (size.value * 0.14f).sp,
            )
        } else if (digit != 0 && showLetters) {
            Spacer(Modifier.height((size.value * 0.14f).dp))
        }
    }
}

@Composable
private fun DigitKey(
    digit: Int,
    size: Dp,
    focused: Boolean,
    flashing: Boolean,
    enabled: Boolean,
    keys: KeypadStyle,
    onPress: () -> Unit,
) {
    val press by rememberUpdatedState(onPress)
    var pressed by remember { mutableStateOf(false) }
    KeyFace(
        size, keys, pressed || flashing,
        Modifier.focusRing(focused, keys).pressable(enabled, { press() }) { pressed = it },
    ) { DigitLabel(digit, size, keys.textColor, keys.showLetters) }
}

@Composable
private fun TextKey(
    size: Dp,
    focused: Boolean,
    enabled: Boolean,
    keys: KeypadStyle,
    onPress: () -> Unit,
    content: @Composable () -> Unit,
) {
    val press by rememberUpdatedState(onPress)
    var pressed by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(size)
            .clip(keys.style.shape())
            .focusRing(focused, keys)
            .pressable(enabled, { press() }) { pressed = it }
            .alpha(if (pressed) 0.5f else 1f),
        contentAlignment = Alignment.Center,
    ) { content() }
}
