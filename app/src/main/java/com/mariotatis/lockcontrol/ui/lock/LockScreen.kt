package com.mariotatis.lockcontrol.ui.lock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mariotatis.lockcontrol.data.LockConfig
import com.mariotatis.lockcontrol.data.LockoutTracker
import com.mariotatis.lockcontrol.data.PinHasher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LockStage { IDLE, PASSCODE, UNLOCKING }

@Stable
class LockState(private val scope: CoroutineScope) {
    var config by mutableStateOf(LockConfig())
    var stage by mutableStateOf(LockStage.IDLE); private set
    var entry by mutableStateOf(""); private set
    var focus by mutableStateOf<Int?>(null); private set
    var flash by mutableStateOf<Int?>(null); private set
    var shakeTick by mutableIntStateOf(0); private set
    var lockoutSeconds by mutableIntStateOf(0)
    private var verifying = false
    var onFeedback: (Boolean) -> Unit = {}

    private val acceptsInput get() = stage == LockStage.PASSCODE && !verifying && lockoutSeconds == 0

    fun requestUnlock() {
        if (stage != LockStage.IDLE) return
        stage = if (config.hasPin) LockStage.PASSCODE else LockStage.UNLOCKING
    }

    fun reset() {
        if (stage == LockStage.UNLOCKING) return
        stage = LockStage.IDLE
        entry = ""
        focus = null
    }

    fun press(index: Int) {
        when (index) {
            PadKeys.CANCEL -> reset()
            PadKeys.DELETE -> if (entry.isNotEmpty()) entry = entry.dropLast(1)
            else -> PadKeys.digitOf(index)?.let(::digit)
        }
    }

    private fun digit(d: Int) {
        if (!acceptsInput || entry.length >= config.pinLength) return
        onFeedback(false)
        entry += d
        if (entry.length == config.pinLength) verify()
    }

    private fun verify() {
        verifying = true
        val attempt = entry
        val cfg = config
        scope.launch {
            val ok = withContext(Dispatchers.Default) { PinHasher.verify(attempt, cfg) }
            if (ok) {
                LockoutTracker.recordSuccess()
                stage = LockStage.UNLOCKING
            } else {
                LockoutTracker.recordFailure()
                onFeedback(true)
                shakeTick++
                delay(420)
                entry = ""
            }
            verifying = false
        }
    }

    /** Hardware key / gamepad input. */
    fun onKey(code: Int) {
        when (stage) {
            LockStage.IDLE -> requestUnlock()
            LockStage.UNLOCKING -> Unit
            LockStage.PASSCODE -> when (code) {
                in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> flashPress(PadKeys.indexOf(code - KeyEvent.KEYCODE_0))
                in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 ->
                    flashPress(PadKeys.indexOf(code - KeyEvent.KEYCODE_NUMPAD_0))
                KeyEvent.KEYCODE_DPAD_UP -> moveFocus(-1, 0)
                KeyEvent.KEYCODE_DPAD_DOWN -> moveFocus(1, 0)
                KeyEvent.KEYCODE_DPAD_LEFT -> moveFocus(0, -1)
                KeyEvent.KEYCODE_DPAD_RIGHT -> moveFocus(0, 1)
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER ->
                    focus?.let(::flashPress) ?: run { focus = 4 }
                KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DEL,
                KeyEvent.KEYCODE_ESCAPE -> if (entry.isEmpty()) reset() else press(PadKeys.DELETE)
            }
        }
    }

    private fun moveFocus(dRow: Int, dCol: Int) {
        val current = focus ?: run { focus = 4; return }
        val row = (current / 3 + dRow).coerceIn(0, 3)
        val col = (current % 3 + dCol).coerceIn(0, 2)
        focus = row * 3 + col
    }

    private fun flashPress(index: Int) {
        flash = index
        press(index)
        scope.launch {
            delay(110)
            if (flash == index) flash = null
        }
    }
}

/**
 * The full lock screen: wallpaper, positioned clock, status row and the
 * passcode pad. Used by the overlay window and by the in-app preview.
 *
 * @param active whether the display is on (drives video playback, clock ticking
 *   and resetting back to the idle clock when the screen goes off).
 * @param keyEvents key codes (ACTION_DOWN) from hardware buttons / gamepad.
 */
@Composable
fun LockScreen(
    config: LockConfig,
    active: Boolean,
    keyEvents: Flow<Int>,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val state = remember { LockState(scope) }
    val view = LocalView.current
    SideEffect {
        state.config = config
        state.onFeedback = { error ->
            view.performHapticFeedback(if (error) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }
    val unlocked by rememberUpdatedState(onUnlocked)

    LaunchedEffect(active) { if (!active) state.reset() }
    LaunchedEffect(keyEvents) { keyEvents.collect { state.onKey(it) } }
    LaunchedEffect(state.stage, state.shakeTick) {
        while (true) {
            state.lockoutSeconds = ((LockoutTracker.remainingMs() + 999) / 1000).toInt()
            if (state.lockoutSeconds == 0) break
            delay(250)
        }
    }

    val passcode by animateFloatAsState(
        if (state.stage == LockStage.PASSCODE) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "passcode",
    )
    val exit = remember { Animatable(0f) }
    LaunchedEffect(state.stage) {
        if (state.stage == LockStage.UNLOCKING) {
            exit.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            unlocked()
        }
    }
    val drag = remember { Animatable(0f) }
    val swipeThreshold = with(LocalDensity.current) { 90.dp.toPx() }
    val now = rememberNow(config.showSeconds, active)

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .graphicsLayer {
                alpha = 1f - exit.value
                val s = 1f + 0.06f * exit.value
                scaleX = s
                scaleY = s
            }
            .then(
                if (state.stage == LockStage.IDLE) {
                    Modifier
                        .pointerInput(Unit) { detectTapGestures { state.requestUnlock() } }
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (drag.value < -swipeThreshold) state.requestUnlock()
                                    scope.launch { drag.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                },
                                onDragCancel = { scope.launch { drag.animateTo(0f) } },
                            ) { change, amount ->
                                change.consume()
                                scope.launch { drag.snapTo((drag.value + amount).coerceAtMost(0f)) }
                            }
                        }
                } else Modifier,
            ),
    ) {
        LockBackground(
            config = config,
            playing = active,
            blur = config.passcodeBlur * passcode,
            dim = config.dim + (0.3f - config.dim * 0.3f) * passcode,
        )

        if (passcode < 0.999f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = (1f - passcode) * (1f + drag.value / (swipeThreshold * 3f)).coerceIn(0f, 1f)
                        translationY = drag.value * 0.5f - passcode * 40.dp.toPx()
                    },
            ) {
                if (config.showDate) {
                    ClockPlacement(config.dateX, config.dateY, Modifier.fillMaxSize()) { DateWidget(config, now) }
                }
                if (config.showClock) {
                    ClockPlacement(config.clockX, config.clockY, Modifier.fillMaxSize()) { ClockWidget(config, now) }
                }
                if (config.showHint || config.showHintChevron || config.showHintBar) {
                    UnlockHint(config, Modifier.align(Alignment.BottomCenter))
                }
            }
        }

        StatusRow(config, unlocking = state.stage == LockStage.UNLOCKING, idleAlpha = 1f - passcode)

        if (passcode > 0.001f) {
            PasscodePanel(
                title = if (state.lockoutSeconds > 0) "Too many attempts" else config.promptLabel(),
                subtitle = if (state.lockoutSeconds > 0) "Try again in ${state.lockoutSeconds}s" else null,
                pinLength = config.pinLength,
                entered = state.entry.length,
                shakeTick = state.shakeTick,
                enabled = state.lockoutSeconds == 0,
                focusedKey = state.focus,
                flashKey = state.flash,
                onKey = state::press,
                keys = KeypadStyle.of(config),
                prompt = PromptStyle.of(config),
                modifier = Modifier.graphicsLayer {
                    alpha = passcode
                    val s = 1.08f - 0.08f * passcode
                    scaleX = s
                    scaleY = s
                },
            )
        }
    }
}

@Composable
internal fun StatusRow(config: LockConfig, unlocking: Boolean, idleAlpha: Float) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 18.dp)) {
        Icon(
            if (unlocking) Icons.Filled.LockOpen else Icons.Filled.Lock,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f * idleAlpha),
            modifier = Modifier.align(Alignment.TopCenter).size(22.dp),
        )
        if (config.showBattery) BatteryIndicator(Modifier.align(Alignment.TopEnd))
    }
}

@Composable
internal fun UnlockHint(
    config: LockConfig,
    modifier: Modifier,
    ghosts: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    // In the editor, hidden parts are drawn faintly so they can still be tapped and re-enabled.
    fun Modifier.part(shown: Boolean) = if (shown) this else if (ghosts) alpha(0.25f) else alpha(0f)
    val bob by rememberInfiniteTransition(label = "hint").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob",
    )
    val style = config.hintStyle
    val tint = Color(style.color).copy(alpha = style.alpha.coerceIn(0f, 1f))
    Column(
        modifier
            .padding(bottom = 14.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.KeyboardArrowUp, null, tint = tint,
            modifier = Modifier
                .part(config.showHintChevron)
                .size((style.size * 1.85f).dp)
                .graphicsLayer { translationY = -bob * 6.dp.toPx() },
        )
        Text(config.hintLabel(), style = style.toTextStyle(), modifier = Modifier.part(config.showHint))
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .part(config.showHintBar)
                .width(134.dp)
                .height(5.dp)
                .background(tint.copy(alpha = (style.alpha + 0.05f).coerceAtMost(1f)), RoundedCornerShape(3.dp)),
        )
    }
}

@Composable
private fun BatteryIndicator(modifier: Modifier = Modifier) {
    val (level, charging) = rememberBattery()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("$level%", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(6.dp))
        Canvas(Modifier.width(27.dp).height(13.dp)) {
            val stroke = 1.2.dp.toPx()
            val nub = 2.dp.toPx()
            val bodyW = size.width - nub - 1.dp.toPx()
            drawRoundRect(
                Color.White.copy(alpha = 0.5f), Offset(stroke / 2, stroke / 2), Size(bodyW - stroke, size.height - stroke),
                CornerRadius(3.5.dp.toPx()), style = Stroke(stroke),
            )
            val inset = stroke + 1.2.dp.toPx()
            val fill = when {
                charging -> Color(0xFF34C759)
                level <= 20 -> Color(0xFFFF453A)
                else -> Color.White
            }
            drawRoundRect(
                fill, Offset(inset, inset),
                Size((bodyW - inset * 2) * (level / 100f), size.height - inset * 2), CornerRadius(2.dp.toPx()),
            )
            drawRoundRect(
                Color.White.copy(alpha = 0.5f), Offset(size.width - nub, size.height * 0.33f),
                Size(nub, size.height * 0.34f), CornerRadius(1.dp.toPx()),
            )
        }
    }
}

@Composable
private fun rememberBattery(): Pair<Int, Boolean> {
    val context = LocalContext.current
    var battery by remember { mutableStateOf(readBattery(stickyBattery(context))) }
    androidx.compose.runtime.DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                battery = readBattery(intent)
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    return battery
}

private fun stickyBattery(context: Context): Intent? = ContextCompat.registerReceiver(
    context, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
)

private fun readBattery(intent: Intent?): Pair<Int, Boolean> {
    if (intent == null) return 100 to false
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    return (level * 100 / scale) to charging
}
