package com.mariotatis.lockcontrol.ui.lock

import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.wrapContentWidth
import com.mariotatis.lockcontrol.data.WidgetLimits
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mariotatis.lockcontrol.data.LockConfig
import com.mariotatis.lockcontrol.data.MediaStyle
import com.mariotatis.lockcontrol.media.MediaActions
import com.mariotatis.lockcontrol.media.NowPlaying
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/** Text and button size multipliers for everything inside the music widget. */
private data class MediaSizing(val text: Float = 1f, val buttons: Float = 1f)

private val LocalMediaSizing = staticCompositionLocalOf { MediaSizing() }

/**
 * Now-playing widget in the configured [MediaStyle]. [active] stops the
 * progress ticker and wave animation while the screen is off.
 */
@Composable
fun MediaWidget(
    config: LockConfig,
    media: NowPlaying,
    actions: MediaActions,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val tint = Color(config.mediaColor)
    val glass = Color(0xFF101016).copy(alpha = config.mediaBgAlpha.coerceIn(0f, 1f))
    val sizing = MediaSizing(
        config.mediaTextScale.coerceIn(WidgetLimits.innerScale),
        config.mediaButtonScale.coerceIn(WidgetLimits.innerScale),
    )
    CompositionLocalProvider(LocalMediaSizing provides sizing) {
        Box(modifier.scaledBy(config.mediaScale / 100f)) {
            when (config.mediaStyle) {
                MediaStyle.CARD -> CardStyle(media, actions, active, tint, glass)
                MediaStyle.SQUARE -> SquareStyle(media, actions, active, tint)
                MediaStyle.WAVE -> WaveStyle(media, actions, active, tint)
                MediaStyle.PILL -> PillStyle(media, actions, tint, glass)
            }
        }
    }
}

/** Widens a widget a little as its text grows, so bigger titles still have room. */
@Composable
private fun widthFor(base: Int): Dp = (base * (0.7f + 0.3f * LocalMediaSizing.current.text)).dp

@Composable
private fun CardStyle(media: NowPlaying, actions: MediaActions, active: Boolean, tint: Color, glass: Color) {
    Column(
        Modifier
            .width(widthFor(340))
            .clip(RoundedCornerShape(26.dp))
            .background(glass)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(media, 56.dp * LocalMediaSizing.current.text.coerceAtLeast(1f), RoundedCornerShape(12.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                TitleText(media.title, tint, 16)
                SubText(media.artist, tint)
            }
            AppBadge(media)
        }
        Spacer(Modifier.height(14.dp))
        Progress(media, actions, active, tint)
        Spacer(Modifier.height(6.dp))
        Controls(media, actions, tint, 36.dp, Modifier.fillMaxWidth())
    }
}

@Composable
private fun SquareStyle(media: NowPlaying, actions: MediaActions, active: Boolean, tint: Color) {
    val side = widthFor(230)
    Box(Modifier.size(side).clip(RoundedCornerShape(28.dp))) {
        Artwork(media, side, RoundedCornerShape(0.dp))
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.78f))),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            TitleText(media.title, tint, 16)
            SubText(media.artist, tint)
            Spacer(Modifier.height(8.dp))
            Progress(media, actions, active, tint, showTimes = false)
            Controls(media, actions, tint, 34.dp, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun WaveStyle(media: NowPlaying, actions: MediaActions, active: Boolean, tint: Color) {
    Column(Modifier.width(widthFor(300)), horizontalAlignment = Alignment.CenterHorizontally) {
        Wave(media.playing && active, tint, Modifier.fillMaxWidth().height(96.dp))
        Spacer(Modifier.height(10.dp))
        TitleText(media.title, tint, 17)
        SubText(media.artist, tint)
        Spacer(Modifier.height(4.dp))
        Controls(media, actions, tint, 36.dp, Modifier.fillMaxWidth())
    }
}

@Composable
private fun PillStyle(media: NowPlaying, actions: MediaActions, tint: Color, glass: Color) {
    Row(
        Modifier
            .width(widthFor(310))
            .clip(CircleShape)
            .background(glass)
            .padding(start = 8.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(media, 44.dp * LocalMediaSizing.current.text.coerceAtLeast(1f), CircleShape)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            TitleText(media.title, tint, 14)
            SubText(media.artist, tint, 12)
        }
        val b = LocalMediaSizing.current.buttons
        ControlButton(if (media.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, tint, 30.dp * b) { actions.playPause() }
        ControlButton(Icons.Rounded.SkipNext, tint, 26.dp * b) { actions.next() }
    }
}

/** Siri curves: (attenuation, opacity, stroke width in dp), back to front. */
private val SiriCurves = listOf(
    Triple(-2f, 0.1f, 1f),
    Triple(-6f, 0.2f, 1f),
    Triple(4f, 0.4f, 1f),
    Triple(2f, 0.6f, 1f),
    Triple(1f, 1f, 1.6f),
)

/**
 * Siri-style wave: sine curves sharing their nodes, tapered to flat at both
 * ends. It flows and gently "breathes" while playing and eases flat when paused.
 */
@Composable
private fun Wave(playing: Boolean, tint: Color, modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "phase",
    )
    // Two slow, unrelated cycles so the height never repeats in an obvious way.
    val breathe by transition.animateFloat(
        0f, (2 * PI).toFloat(), infiniteRepeatable(tween(5300, easing = LinearEasing)), label = "breathe",
    )
    val energy by animateFloatAsState(if (playing) 1f else 0.06f, tween(700), label = "energy")
    Canvas(modifier) {
        val mid = size.height / 2
        val amplitude = energy * (0.62f + 0.22f * sin(breathe) + 0.16f * sin(breathe * 2.7f + 1.3f))
        val steps = 140
        fun curve(attenuation: Float): androidx.compose.ui.graphics.Path {
            val path = androidx.compose.ui.graphics.Path()
            for (i in 0..steps) {
                val x = -2f + 4f * i / steps // the classic -2..2 domain
                val envelope = (2f / (2f + x * x * x * x)).let { it * it } // (K / (K + x^4))^K, K = 2
                val y = mid + mid * 0.92f * amplitude * envelope * sin(3.6f * x - phase) / attenuation
                val px = size.width * i / steps
                if (i == 0) path.moveTo(px, y) else path.lineTo(px, y)
            }
            return path
        }
        SiriCurves.forEach { (attenuation, opacity, width) ->
            val path = curve(attenuation)
            if (attenuation == 1f) {
                // Soft glow under the lead curve.
                drawPath(path, tint.copy(alpha = 0.18f), style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round))
            }
            drawPath(path, tint.copy(alpha = opacity), style = Stroke(width = width.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun Artwork(media: NowPlaying, size: Dp, shape: androidx.compose.ui.graphics.Shape) {
    val art = media.art
    if (art != null) {
        Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(shape))
    } else {
        Box(
            Modifier
                .size(size)
                .clip(shape)
                .background(Brush.linearGradient(listOf(Color(0xFF8C9BFF), Color(0xFFE58CFF), Color(0xFFFF9F5A)))),
            // Large tiles (Square) keep the note small and high, clear of the title drawn over the bottom.
            contentAlignment = if (size > 100.dp) Alignment.TopCenter else Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.MusicNote, null, tint = Color.White,
                modifier = if (size > 100.dp) Modifier.padding(top = size * 0.16f).size(size * 0.26f) else Modifier.size(size * 0.45f),
            )
        }
    }
}

@Composable
private fun AppBadge(media: NowPlaying) {
    val icon = media.appIcon ?: return
    Image(icon, media.appName, modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)))
}

@Composable
private fun TitleText(text: String, tint: Color, size: Int) {
    Text(text, color = tint, fontSize = (size * LocalMediaSizing.current.text).sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun SubText(text: String, tint: Color, size: Int = 13) {
    if (text.isBlank()) return
    Text(text, color = tint.copy(alpha = 0.7f), fontSize = (size * LocalMediaSizing.current.text).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun Controls(media: NowPlaying, actions: MediaActions, tint: Color, baseSize: Dp, modifier: Modifier) {
    val size = baseSize * LocalMediaSizing.current.buttons
    // Grouped in the middle with a modest gap; shrinks to fit if the buttons outgrow the widget.
    Row(
        modifier.wrapContentWidth().fitWidth(),
        horizontalArrangement = Arrangement.spacedBy(size * 0.35f, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlButton(Icons.Rounded.SkipPrevious, tint, size) { actions.previous() }
        ControlButton(if (media.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, tint, size * 1.3f) { actions.playPause() }
        ControlButton(Icons.Rounded.SkipNext, tint, size) { actions.next() }
    }
}

/** Tap target that consumes the touch, so pressing a control never unlocks the screen. */
@Composable
private fun ControlButton(icon: ImageVector, tint: Color, size: Dp, onClick: () -> Unit) {
    val click by rememberUpdatedState(onClick)
    var pressed by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(size + 10.dp)
            .clip(CircleShape)
            .background(if (pressed) tint.copy(alpha = 0.18f) else Color.Transparent)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { click() },
                )
            },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(size)) }
}

/** Progress bar with elapsed/remaining times; drag or tap it to seek when the app allows. */
@Composable
private fun Progress(media: NowPlaying, actions: MediaActions, active: Boolean, tint: Color, showTimes: Boolean = true) {
    if (media.durationMs <= 0) return
    var position by remember { mutableLongStateOf(media.positionAt()) }
    var scrub by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(media, active) {
        while (true) {
            position = media.positionAt(SystemClock.elapsedRealtime())
            if (!active || !media.playing) break
            delay(500)
        }
    }
    val fraction = scrub ?: (position.toFloat() / media.durationMs).coerceIn(0f, 1f)
    val current by rememberUpdatedState(media)
    var width by remember { mutableFloatStateOf(1f) }
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(18.dp)
                .then(
                    if (media.canSeek) {
                        Modifier
                            .pointerInput(Unit) {
                                detectTapGestures { o ->
                                    actions.seekTo((o.x / size.width * current.durationMs).toLong())
                                }
                            }
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragStart = { o -> scrub = (o.x / size.width).coerceIn(0f, 1f) },
                                    onDragEnd = {
                                        scrub?.let { actions.seekTo((it * current.durationMs).toLong()) }
                                        scrub = null
                                    },
                                    onDragCancel = { scrub = null },
                                ) { change, amount ->
                                    change.consume()
                                    scrub = ((scrub ?: 0f) + amount / size.width).coerceIn(0f, 1f)
                                }
                            }
                    } else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxWidth().height(if (scrub != null) 8.dp else 5.dp)) {
                width = size.width
                val r = CornerRadius(size.height / 2)
                drawRoundRect(tint.copy(alpha = 0.25f), cornerRadius = r)
                drawRoundRect(tint.copy(alpha = 0.9f), size = Size(size.width * fraction, size.height), cornerRadius = r)
            }
        }
        if (showTimes) {
            Row(Modifier.fillMaxWidth()) {
                val shown = (fraction * media.durationMs).toLong()
                val timeSize = (11 * LocalMediaSizing.current.text).sp
                Text(clock(shown), color = tint.copy(alpha = 0.65f), fontSize = timeSize, modifier = Modifier.weight(1f))
                Text("-" + clock(media.durationMs - shown), color = tint.copy(alpha = 0.65f), fontSize = timeSize)
            }
        }
    }
}

private fun clock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
