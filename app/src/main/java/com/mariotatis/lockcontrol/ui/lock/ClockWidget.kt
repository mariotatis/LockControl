package com.mariotatis.lockcontrol.ui.lock

import android.graphics.Typeface
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mariotatis.lockcontrol.data.ClockFont
import com.mariotatis.lockcontrol.data.ClockLayout
import com.mariotatis.lockcontrol.data.DateFormat
import com.mariotatis.lockcontrol.data.LockConfig
import com.mariotatis.lockcontrol.data.TextSpec
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private val condensedFamily by lazy { FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL)) }

fun ClockFont.family(): FontFamily = when (this) {
    ClockFont.SANS -> FontFamily.SansSerif
    ClockFont.CONDENSED -> condensedFamily
    ClockFont.SERIF -> FontFamily.Serif
    ClockFont.MONO -> FontFamily.Monospace
}

/** Turns a stored [TextSpec] into a Compose text style. */
fun TextSpec.toTextStyle(shadow: Boolean = false): TextStyle = TextStyle(
    color = Color(color).copy(alpha = alpha.coerceIn(0f, 1f)),
    fontFamily = font.family(),
    fontWeight = FontWeight(weight.coerceIn(1, 1000)),
    fontSize = size.sp,
    shadow = if (shadow) Shadow(Color.Black.copy(alpha = 0.3f * alpha), Offset(0f, size * 0.06f), size * 0.4f) else null,
)

/** Current time, ticking on the second or minute boundary while [active]. */
@Composable
fun rememberNow(withSeconds: Boolean, active: Boolean = true): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(withSeconds, active) {
        while (true) {
            now = LocalDateTime.now()
            if (!active) break
            val ms = System.currentTimeMillis()
            delay(if (withSeconds) 1000 - ms % 1000 else 60_000 - ms % 60_000 + 5)
        }
    }
    return now
}

private fun widgetTextStyle(
    config: LockConfig,
    size: Float,
    color: Int,
    alpha: Float,
    font: ClockFont,
    weight: Int,
    stretch: Float,
) = TextStyle(
    color = Color(color).copy(alpha = alpha.coerceIn(0f, 1f)),
    shadow = if (config.clockShadow) Shadow(Color.Black.copy(alpha = 0.35f * alpha), Offset(0f, size * 0.03f), size * 0.2f) else null,
    fontFamily = font.family(),
    fontWeight = FontWeight(weight.coerceIn(1, 1000)),
    fontSize = size.sp,
    textGeometricTransform = TextGeometricTransform(scaleX = stretch),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)

@Composable
fun ClockWidget(config: LockConfig, now: LocalDateTime, modifier: Modifier = Modifier) {
    val size = config.clockSize
    val timeStyle = widgetTextStyle(
        config, size, config.clockColor, config.clockAlpha, config.clockFont, config.clockWeight, config.clockStretch,
    ).copy(
        lineHeight = (size * if (config.clockLayout == ClockLayout.STACKED) 0.9f else 1f).sp,
        letterSpacing = (-0.02).em,
        fontFeatureSettings = "tnum",
    )
    val hours = when {
        config.use24h -> if (config.clockLeadingZero) "HH" else "H"
        else -> if (config.clockLeadingZero) "hh" else "h"
    }
    val rest = if (config.showSeconds) "mm:ss" else "mm"
    // AM/PM is drawn small beside the minutes, like a watch face; 12-hour only.
    val amPm = if (config.showAmPm && !config.use24h) now.format(DateTimeFormatter.ofPattern(" a", Locale.getDefault())) else ""
    fun withAmPm(text: String) = buildAnnotatedString {
        append(text)
        if (amPm.isNotEmpty()) {
            withStyle(SpanStyle(fontSize = (size * 0.3f).sp, letterSpacing = 0.em)) { append(amPm) }
        }
    }
    Column(modifier.fitWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        when (config.clockLayout) {
            // A clock never wraps; if it's wider than the screen, resize it from the corners.
            ClockLayout.INLINE -> Text(
                withAmPm(now.format(DateTimeFormatter.ofPattern("$hours:$rest"))), style = timeStyle, softWrap = false,
            )
            ClockLayout.STACKED -> {
                Text(now.format(DateTimeFormatter.ofPattern(hours)), style = timeStyle, softWrap = false)
                Text(withAmPm(now.format(DateTimeFormatter.ofPattern(rest))), style = timeStyle, softWrap = false)
            }
        }
    }
}

fun formatDate(now: LocalDateTime, format: DateFormat, locale: Locale = Locale.getDefault()): String {
    val formatter = format.pattern?.let { DateTimeFormatter.ofPattern(it, locale) }
        ?: DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)
    return now.format(formatter)
}

@Composable
fun DateWidget(config: LockConfig, now: LocalDateTime, modifier: Modifier = Modifier) {
    val size = config.dateSize
    val date = remember(now.toLocalDate(), config.dateFormat) { formatDate(now, config.dateFormat) }
    val style = widgetTextStyle(
        config, size, config.dateColor, config.dateAlpha, config.dateFont, config.dateWeight, config.dateStretch,
    ).copy(lineHeight = (size * 1.2f).sp)
    Text(date, style = style, modifier = modifier.fitWidth(), softWrap = false)
}

/**
 * Lays the content out at its natural width; if that's wider than the space
 * available (e.g. a big clock on a portrait screen), shrinks it uniformly to
 * fit instead of clipping or wrapping.
 */
fun Modifier.fitWidth(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
    if (!constraints.hasBoundedWidth || placeable.width <= constraints.maxWidth) {
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    } else {
        val scale = constraints.maxWidth / placeable.width.toFloat()
        layout(constraints.maxWidth, (placeable.height * scale).roundToInt()) {
            placeable.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

/**
 * Places a single child so its center sits at ([x], [y]) in normalized
 * coordinates, clamped so it never leaves the screen.
 */
@Composable
fun ClockPlacement(x: Float, y: Float, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEach { p ->
                val px = (x * constraints.maxWidth - p.width / 2f).roundToInt()
                    .coerceIn(0, max(0, constraints.maxWidth - p.width))
                val py = (y * constraints.maxHeight - p.height / 2f).roundToInt()
                    .coerceIn(0, max(0, constraints.maxHeight - p.height))
                p.place(px, py)
            }
        }
    }
}
