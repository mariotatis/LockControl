package com.mariotatis.lockcontrol.ui.lock

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons as MaterialIcons
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.mariotatis.lockcontrol.data.WidgetLimits
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.mariotatis.lockcontrol.data.LockConfig
import com.mariotatis.lockcontrol.data.NotifStyle
import com.mariotatis.lockcontrol.media.LockNotification

private const val WIDTH = 340

/** The notification group in the configured [NotifStyle]; draws nothing when [items] is empty. */
@Composable
fun NotificationsWidget(
    config: LockConfig,
    items: List<LockNotification>,
    modifier: Modifier = Modifier,
    /** Clears the shown notifications; null renders the button as a non-interactive preview. */
    onClear: (() -> Unit)? = null,
) {
    if (items.isEmpty()) return
    val glass = Color(0xFF101016).copy(alpha = config.notifBgAlpha.coerceIn(0f, 1f))
    val shown = items.take(config.notifMax.coerceIn(1, 6))
    val hidden = items.size - shown.size
    val now = System.currentTimeMillis()
    val t = config.notifTextScale.coerceIn(WidgetLimits.innerScale)
    CompositionLocalProvider(LocalNotifText provides t) {
        Box(modifier.scaledBy(config.notifScale / 100f)) {
            when (config.notifStyle) {
                NotifStyle.LIST -> Column(Modifier.width(cardWidth()), verticalArrangement = Arrangement.spacedBy(d(8f))) {
                    shown.forEach { NotificationCard(it, config.notifHideContent, glass, now) }
                    if (hidden > 0) MoreLabel(hidden)
                    if (config.notifShowClear) ClearButton(glass, onClear)
                }
                NotifStyle.STACK -> Column(Modifier.width(cardWidth()), verticalArrangement = Arrangement.spacedBy(d(8f))) {
                    Stack(shown, hidden, config.notifHideContent, glass, now)
                    if (config.notifShowClear) ClearButton(glass, onClear)
                }
                NotifStyle.ICONS -> Icons(items, glass)
            }
        }
    }
}

/** Text size multiplier for the notification widget; app icons grow along with it. */
private val LocalNotifText = staticCompositionLocalOf { 1f }

@Composable
private fun ts(base: Int) = (base * LocalNotifText.current).sp

/** A dp value that grows and shrinks with the text, so spacing stays in proportion. */
@Composable
private fun d(base: Float): Dp = (base * LocalNotifText.current).dp

/**
 * Text style whose line height tracks its font size. Without it, Text inherits
 * the theme's fixed 24sp line height, which leaves big gaps at small sizes.
 */
@Composable
private fun line(base: Int, color: Color, weight: FontWeight = FontWeight.Normal) = TextStyle(
    color = color,
    fontSize = ts(base),
    lineHeight = ts(base) * 1.28f,
    fontWeight = weight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

@Composable
private fun cardWidth(): Dp = (WIDTH * (0.7f + 0.3f * LocalNotifText.current)).dp

/** iOS-style stack: the newest card on top, older ones peeking out underneath. */
@Composable
private fun Stack(items: List<LockNotification>, hidden: Int, hideContent: Boolean, glass: Color, now: Long) {
    val behind = (items.size - 1).coerceAtMost(2)
    Column(Modifier.width(cardWidth()), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.padding(bottom = d(behind * 8f))) {
            for (layer in behind downTo 1) {
                Box(
                    Modifier
                        .matchParentSize()
                        .zIndex(-layer.toFloat())
                        .offset(y = d(layer * 8f))
                        .graphicsLayer {
                            val s = 1f - layer * 0.05f
                            scaleX = s
                        }
                        .clip(RoundedCornerShape(d(20f)))
                        .background(glass.copy(alpha = glass.alpha * (1f - layer * 0.25f))),
                )
            }
            NotificationCard(items.first(), hideContent, glass, now)
        }
        val more = items.size - 1 + hidden
        if (more > 0) {
            Spacer(Modifier.height(d(6f)))
            MoreLabel(more)
        }
    }
}

/** Compact row of app icons with a count, like an always-on display. */
@Composable
private fun Icons(items: List<LockNotification>, glass: Color) {
    val apps = items.distinctBy { it.appName }
    Row(
        Modifier.clip(CircleShape).background(glass).padding(horizontal = d(12f), vertical = d(8f)),
        horizontalArrangement = Arrangement.spacedBy(d(8f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        apps.take(6).forEach { AppIcon(it, d(28f)) }
        if (apps.size > 6) Text("+${apps.size - 6}", style = line(13, Color.White, FontWeight.SemiBold))
    }
}

@Composable
private fun NotificationCard(item: LockNotification, hideContent: Boolean, glass: Color, now: Long) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(d(20f)))
            .background(glass)
            .padding(horizontal = d(12f), vertical = d(11f)),
        // Icon centered against the whole text block, like iOS.
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(item, d(36f))
        Spacer(Modifier.width(d(11f)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(d(1.5f))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.appName, style = line(12, Color.White.copy(alpha = 0.7f)), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(d(8f)))
                Text(ago(item.postTime, now), style = line(12, Color.White.copy(alpha = 0.55f)), maxLines = 1)
            }
            if (hideContent) {
                Text("New notification", style = line(14, Color.White, FontWeight.SemiBold), maxLines = 1)
            } else {
                if (item.title.isNotBlank()) {
                    Text(
                        item.title, style = line(14, Color.White, FontWeight.SemiBold),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.text.isNotBlank()) {
                    Text(
                        item.text, style = line(13, Color.White.copy(alpha = 0.85f)),
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppIcon(item: LockNotification, size: Dp) {
    val icon = item.icon
    if (icon != null) {
        Image(icon, item.appName, modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)))
    } else {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(Color(item.tint)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                item.appName.take(1).uppercase(),
                style = TextStyle(
                    color = Color.White,
                    fontSize = (size.value * 0.45f).sp,
                    lineHeight = (size.value * 0.45f).sp,
                    fontWeight = FontWeight.Bold,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
            )
        }
    }
}

/** "Clear all" pill under the list. Consumes the tap, so it never unlocks the screen. */
@Composable
private fun ClearButton(glass: Color, onClear: (() -> Unit)?) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(glass)
                .then(if (onClear != null) Modifier.clickable(onClick = onClear) else Modifier)
                .padding(horizontal = d(14f), vertical = d(7f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(MaterialIcons.Rounded.ClearAll, null, tint = Color.White, modifier = Modifier.size(d(16f)))
            Spacer(Modifier.width(d(6f)))
            Text("Clear all", style = line(12, Color.White, FontWeight.Medium))
        }
    }
}

@Composable
private fun MoreLabel(count: Int) {
    Text(
        "+$count more", style = line(12, Color.White.copy(alpha = 0.75f), FontWeight.Medium),
        modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

private fun ago(time: Long, now: Long): String {
    val minutes = ((now - time) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 24 * 60 -> "${minutes / 60}h"
        else -> "${minutes / (24 * 60)}d"
    }
}
