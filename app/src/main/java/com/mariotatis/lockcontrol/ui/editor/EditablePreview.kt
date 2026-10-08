package com.mariotatis.lockcontrol.ui.editor

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mariotatis.lockcontrol.data.BackgroundType
import com.mariotatis.lockcontrol.data.LockConfig
import com.mariotatis.lockcontrol.data.WidgetLimits
import com.mariotatis.lockcontrol.ui.lock.ClockPlacement
import com.mariotatis.lockcontrol.ui.lock.ClockWidget
import com.mariotatis.lockcontrol.ui.lock.DateWidget
import com.mariotatis.lockcontrol.ui.lock.KeypadStyle
import com.mariotatis.lockcontrol.ui.lock.LockBackground
import com.mariotatis.lockcontrol.ui.lock.PasscodePanel
import com.mariotatis.lockcontrol.ui.lock.PromptStyle
import com.mariotatis.lockcontrol.ui.lock.StatusRow
import com.mariotatis.lockcontrol.ui.lock.UnlockHint
import com.mariotatis.lockcontrol.ui.lock.clampBackgroundOffset
import com.mariotatis.lockcontrol.ui.lock.rememberNow
import com.mariotatis.lockcontrol.ui.theme.AccentDeep
import kotlin.math.abs
import kotlin.math.min

private const val SNAP = 0.02f

/** Which screen the editor shows: the idle lock screen or the passcode pad. */
enum class EditorView { LOCK, PASSCODE }

/** What the editor is currently customizing; drives the floating panel. */
enum class EditTarget { NONE, CLOCK, DATE, BACKGROUND, HINT, KEYS, PROMPT }

enum class LockWidget { CLOCK, DATE }

/** Where a widget sits and how big it is; what a drag or resize commits. */
data class WidgetFrame(val position: Offset, val size: Float, val stretch: Float)

/** Background framing: zoom plus an offset as a fraction of the screen. */
data class BackgroundFrame(val scale: Float, val offset: Offset)

/** Full display size in dp, so the preview is a pixel-exact miniature. */
@Composable
fun rememberScreenSize(): DpSize {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    return remember(configuration, density) {
        val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        with(density) { DpSize(bounds.width().toDp(), bounds.height().toDp()) }
    }
}

/**
 * Renders the real lock screen at full display size and scales it down, so
 * what you see (and where you drop things) is exactly what you get.
 */
@Composable
fun EditablePreview(
    config: LockConfig,
    playing: Boolean,
    view: EditorView,
    target: EditTarget,
    onTarget: (EditTarget) -> Unit,
    onWidgetChanged: (LockWidget, WidgetFrame) -> Unit,
    onBackgroundChanged: (BackgroundFrame) -> Unit,
    modifier: Modifier = Modifier,
) {
    val screen = rememberScreenSize()
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier
            .aspectRatio(screen.width / screen.height)
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.1f), shape),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val scale = min(maxWidth / screen.width, maxHeight / screen.height)
            Box(
                Modifier
                    .requiredSize(screen)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            ) { PreviewScene(config, playing, view, target, onTarget, onWidgetChanged, onBackgroundChanged) }
        }
    }
}

@Composable
private fun PreviewScene(
    config: LockConfig,
    playing: Boolean,
    view: EditorView,
    target: EditTarget,
    onTarget: (EditTarget) -> Unit,
    onWidgetChanged: (LockWidget, WidgetFrame) -> Unit,
    onBackgroundChanged: (BackgroundFrame) -> Unit,
) {
    val now = rememberNow(config.showSeconds, playing)
    val select by rememberUpdatedState(onTarget)
    val currentTarget by rememberUpdatedState(target)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenPx = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        // Position of whichever widget is being moved, used to draw the snap guides.
        var dragging by remember { mutableStateOf<Offset?>(null) }
        // Live background framing while pinching; committed when the fingers lift.
        var liveFrame by remember { mutableStateOf<BackgroundFrame?>(null) }
        val frame = liveFrame ?: BackgroundFrame(config.bgScale, Offset(config.bgOffsetX, config.bgOffsetY))
        val shown = config.copy(bgScale = frame.scale, bgOffsetX = frame.offset.x, bgOffsetY = frame.offset.y)

        val passcodeView = view == EditorView.PASSCODE
        LockBackground(
            shown, playing,
            blur = if (passcodeView) config.passcodeBlur else 0f,
            dim = if (passcodeView) config.dim + (0.3f - config.dim * 0.3f) else config.dim,
        )

        // The background itself: a single tap opens its options (or closes whatever is open);
        // pinch and drag frame a photo/video directly, without opening anything.
        val media = config.backgroundType != BackgroundType.GRADIENT && config.backgroundFile != null
        val committed by rememberUpdatedState(frame)
        val commit by rememberUpdatedState(onBackgroundChanged)
        Box(
            Modifier.fillMaxSize().pointerInput(media) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var moved = false
                    var travel = Offset.Zero
                    var working = committed
                    do {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        if (pan.isValid()) travel += pan
                        val multiTouch = event.changes.count { it.pressed } > 1
                        if (!moved && (multiTouch || abs(zoom - 1f) > 0.02f || travel.getDistance() > viewConfiguration.touchSlop)) {
                            moved = true
                        }
                        if (moved && media) {
                            working = pinch(working, zoom, pan, event.calculateCentroid(useCurrent = false), screenPx)
                            liveFrame = working
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (!moved) {
                        select(if (currentTarget == EditTarget.NONE) EditTarget.BACKGROUND else EditTarget.NONE)
                    } else if (media) {
                        commit(working)
                    }
                    liveFrame = null
                }
            },
        )

        StatusRow(config, unlocking = false, idleAlpha = if (passcodeView) 0f else 1f)

        if (passcodeView) {
            PasscodePanel(
                title = config.promptLabel(),
                pinLength = config.pinLength.takeIf { it > 0 } ?: 4,
                entered = 0,
                shakeTick = 0,
                onKey = { select(EditTarget.KEYS) },
                keys = KeypadStyle.of(config),
                prompt = PromptStyle.of(config),
                ghosts = true,
                onPromptClick = { select(EditTarget.PROMPT) },
            )
            return@BoxWithConstraints
        }

        UnlockHint(config, Modifier.align(Alignment.BottomCenter), ghosts = true, onClick = { select(EditTarget.HINT) })

        val guide = dragging
        if (guide?.x == 0.5f) Box(Modifier.align(Alignment.Center).width(2.dp).fillMaxHeight().background(GuideColor))
        if (guide?.y == 0.5f) Box(Modifier.align(Alignment.Center).height(2.dp).fillMaxWidth().background(GuideColor))

        // Hide widget outlines while framing the background so the photo is easy to judge.
        val outlined = liveFrame == null
        // Hidden widgets stay in the editor as faint ghosts, so they can be tapped and shown again.
        EditableWidget(
            frame = WidgetFrame(Offset(config.dateX, config.dateY), config.dateSize, config.dateStretch),
            sizeRange = WidgetLimits.dateSize,
            screenPx = screenPx,
            selected = target == EditTarget.DATE,
            outlined = outlined,
            ghost = !config.showDate,
            onSelect = { select(EditTarget.DATE) },
            onDrag = { dragging = it },
            onChanged = { onWidgetChanged(LockWidget.DATE, it) },
        ) { size, stretch -> DateWidget(config.copy(dateSize = size, dateStretch = stretch), now) }
        EditableWidget(
            frame = WidgetFrame(Offset(config.clockX, config.clockY), config.clockSize, config.clockStretch),
            sizeRange = WidgetLimits.clockSize,
            screenPx = screenPx,
            selected = target == EditTarget.CLOCK,
            outlined = outlined,
            ghost = !config.showClock,
            onSelect = { select(EditTarget.CLOCK) },
            onDrag = { dragging = it },
            onChanged = { onWidgetChanged(LockWidget.CLOCK, it) },
        ) { size, stretch -> ClockWidget(config.copy(clockSize = size, clockStretch = stretch), now) }
    }
}

/** Zoom about the pinch centroid, then pan, keeping the screen covered. */
private fun pinch(start: BackgroundFrame, zoom: Float, pan: Offset, centroid: Offset, screen: Size): BackgroundFrame {
    // The centroid is unspecified (NaN) on events where no pointer was previously down.
    val center = Offset(screen.width / 2f, screen.height / 2f)
    val focus = if (centroid.isSpecified && centroid.isValid()) centroid else center
    val safeZoom = if (zoom.isFinite() && zoom > 0f) zoom else 1f
    val safePan = if (pan.isValid()) pan else Offset.Zero
    val scale = (start.scale * safeZoom).coerceIn(WidgetLimits.bgScale)
    val z = scale / start.scale
    val c = focus - center
    val t = Offset(start.offset.x * screen.width, start.offset.y * screen.height)
    val next = c - (c - t) * z + safePan
    val (x, y) = clampBackgroundOffset(scale, next.x / screen.width, next.y / screen.height)
    return BackgroundFrame(scale, Offset(x, y))
}

private val HandleSize = 34.dp
private val EditPadding = HandleSize

/**
 * A lock screen widget that can be tapped to select, dragged to move, and,
 * once selected, resized from its corner handles. Height maps to font size and
 * width to horizontal stretch, so it can be made tall or wide; the opposite
 * corner stays put. Changes preview locally and commit when the gesture ends.
 */
@Composable
private fun EditableWidget(
    frame: WidgetFrame,
    sizeRange: ClosedFloatingPointRange<Float>,
    screenPx: Size,
    selected: Boolean,
    outlined: Boolean,
    ghost: Boolean,
    onSelect: () -> Unit,
    onDrag: (Offset?) -> Unit,
    onChanged: (WidgetFrame) -> Unit,
    content: @Composable (size: Float, stretch: Float) -> Unit,
) {
    val committed by rememberUpdatedState(frame)
    val drag by rememberUpdatedState(onDrag)
    val changed by rememberUpdatedState(onChanged)
    val select by rememberUpdatedState(onSelect)
    val screen by rememberUpdatedState(screenPx)
    var editing by remember { mutableStateOf<WidgetFrame?>(null) }
    var moving by remember { mutableStateOf(false) }
    var outerSize by remember { mutableStateOf(IntSize.Zero) }
    var contentSize by remember { mutableStateOf(IntSize.Zero) }
    var resizeBase by remember { mutableStateOf(IntSize.Zero) } // content size when a resize began

    val shown = editing?.let { if (moving) it.copy(position = snapped(it.position)) else it } ?: frame

    fun finish() {
        editing?.let { changed(if (moving) it.copy(position = snapped(it.position)) else it) }
        editing = null
        moving = false
        drag(null)
    }

    ClockPlacement(shown.position.x, shown.position.y, Modifier.fillMaxSize()) {
        Box(
            Modifier
                .onSizeChanged { outerSize = it }
                .drawBehind {
                    if (!outlined) return@drawBehind
                    val inset = EditPadding.toPx() / 2
                    val active = selected || editing != null
                    drawRoundRect(
                        color = Color.White.copy(alpha = if (active) 0.9f else 0.35f),
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2),
                        cornerRadius = CornerRadius(14.dp.toPx()),
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = if (active) null else PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 10.dp.toPx())),
                        ),
                    )
                }
                .pointerInput(Unit) { detectTapGestures { select() } }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            select()
                            editing = committed
                            moving = true
                        },
                        onDragEnd = ::finish,
                        onDragCancel = ::finish,
                    ) { change, amount ->
                        change.consume()
                        val current = editing ?: return@detectDragGestures
                        val halfW = (outerSize.width / 2f / screen.width).coerceAtMost(0.5f)
                        val halfH = (outerSize.height / 2f / screen.height).coerceAtMost(0.5f)
                        val next = Offset(
                            (current.position.x + amount.x / screen.width).coerceIn(halfW, 1f - halfW),
                            (current.position.y + amount.y / screen.height).coerceIn(halfH, 1f - halfH),
                        )
                        editing = current.copy(position = next)
                        drag(snapped(next))
                    }
                },
        ) {
            Box(
                Modifier
                    .padding(EditPadding)
                    .onSizeChanged { contentSize = it }
                    .alpha(if (ghost) 0.28f else 1f),
            ) { content(shown.size, shown.stretch) }
            // Corner handles live inside the padded box so they stay within its touch bounds.
            if (selected) {
                Box(Modifier.matchParentSize()) {
                    listOf(
                        Alignment.TopStart to Offset(-1f, -1f),
                        Alignment.TopEnd to Offset(1f, -1f),
                        Alignment.BottomStart to Offset(-1f, 1f),
                        Alignment.BottomEnd to Offset(1f, 1f),
                    ).forEach { (alignment, corner) ->
                        ResizeHandle(
                            Modifier.align(alignment),
                            onStart = {
                                editing = committed
                                resizeBase = contentSize
                            },
                            onEnd = ::finish,
                        ) { total ->
                            val start = committed
                            val base = resizeBase
                            if (base.width == 0 || base.height == 0) return@ResizeHandle
                            // Grow away from the anchored opposite corner.
                            val wantH = (base.height + corner.y * total.y).coerceAtLeast(8f)
                            val wantW = (base.width + corner.x * total.x).coerceAtLeast(8f)
                            val size = (start.size * wantH / base.height).coerceIn(sizeRange)
                            val hRatio = size / start.size
                            val stretch = (start.stretch * (wantW / base.width) / hRatio).coerceIn(WidgetLimits.stretch)
                            val wRatio = stretch / start.stretch * hRatio
                            val center = Offset(
                                start.position.x + corner.x * (wRatio - 1f) * base.width / 2f / screen.width,
                                start.position.y + corner.y * (hRatio - 1f) * base.height / 2f / screen.height,
                            )
                            editing = WidgetFrame(center, size, stretch)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResizeHandle(
    modifier: Modifier,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onResize: (total: Offset) -> Unit,
) {
    val start by rememberUpdatedState(onStart)
    val end by rememberUpdatedState(onEnd)
    val resize by rememberUpdatedState(onResize)
    Box(
        modifier
            .size(HandleSize)
            .pointerInput(Unit) {
                var total = Offset.Zero
                detectDragGestures(
                    onDragStart = {
                        total = Offset.Zero
                        start()
                    },
                    onDragEnd = { end() },
                    onDragCancel = { end() },
                ) { change, amount ->
                    change.consume()
                    total += amount
                    resize(total)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(HandleSize * 0.6f)
                .background(Color.White, CircleShape)
                .border(2.dp, AccentDeep, CircleShape),
        )
    }
}

private val GuideColor = Color(0xFF8C9BFF).copy(alpha = 0.8f)

private fun snapped(p: Offset) = Offset(
    if (abs(p.x - 0.5f) < SNAP) 0.5f else p.x,
    if (abs(p.y - 0.5f) < SNAP) 0.5f else p.y,
)
