package com.mariotatis.lockcontrol.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Gradient
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.AlignHorizontalCenter
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.LineWeight
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mariotatis.lockcontrol.data.BackgroundType
import com.mariotatis.lockcontrol.data.ClockFont
import com.mariotatis.lockcontrol.data.ClockLayout
import com.mariotatis.lockcontrol.data.ConfigRepository
import com.mariotatis.lockcontrol.data.DateFormat
import com.mariotatis.lockcontrol.data.KeyStyle
import com.mariotatis.lockcontrol.data.LockConfig
import com.mariotatis.lockcontrol.data.MediaStyle
import com.mariotatis.lockcontrol.data.NotifStyle
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.ViewAgenda
import com.mariotatis.lockcontrol.data.TextSpec
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import com.mariotatis.lockcontrol.data.WidgetLimits
import com.mariotatis.lockcontrol.ui.lock.DigitLabel
import com.mariotatis.lockcontrol.ui.lock.GradientPresets
import com.mariotatis.lockcontrol.ui.lock.KeyFace
import com.mariotatis.lockcontrol.ui.lock.KeypadStyle
import com.mariotatis.lockcontrol.ui.lock.family
import com.mariotatis.lockcontrol.ui.lock.formatDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

private fun update(transform: (LockConfig) -> LockConfig) = ConfigRepository.edit(transform)

/** Font tiles + weight slider, shared by the clock and date panels. */
@Composable
private fun FontPicker(sample: String, font: ClockFont, weight: Int, onFont: (ClockFont) -> Unit, onWeight: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ClockFont.entries.forEach { f ->
            SampleTile(f == font, { onFont(f) }, Modifier.weight(1f)) {
                Text(
                    sample, color = Color.White, fontSize = LocalPanelDensity.current.tile, maxLines = 1,
                    fontFamily = f.family(), fontWeight = androidx.compose.ui.text.font.FontWeight(weight),
                )
            }
        }
    }
    GlassSlider(Icons.Rounded.LineWeight, weight.toFloat(), WidgetLimits.weight, { onWeight((it / 100f).roundToInt() * 100) }, steps = 7)
}

@Composable
fun ClockPanel(config: LockConfig, onClose: () -> Unit, modifier: Modifier = Modifier) {
    GlassPanel(
        title = "Clock", onClose = onClose, modifier = modifier,
        leading = {
            GlassIconButton(Icons.Rounded.AlignHorizontalCenter, "Center", { update { it.copy(clockX = 0.5f) } })
            VisibilityButton(config.showClock) { update { it.copy(showClock = !it.showClock) } }
        },
    ) {
        FontPicker(
            "12", config.clockFont, config.clockWeight,
            onFont = { f -> update { it.copy(clockFont = f) } },
            onWeight = { w -> update { it.copy(clockWeight = w) } },
        )
        GlassDivider()
        ColorScroller(PaletteColors, config.clockColor) { c -> update { it.copy(clockColor = c) } }
        GlassSlider(Icons.Rounded.Opacity, config.clockAlpha, 0.1f..1f, { a -> update { it.copy(clockAlpha = a) } })
        Segmented(
            listOf(ClockLayout.INLINE to "Inline", ClockLayout.STACKED to "Stacked"), config.clockLayout,
        ) { l -> update { it.copy(clockLayout = l) } }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipToggle("24-hour", config.use24h) { update { it.copy(use24h = !it.use24h) } }
            if (!config.use24h) ChipToggle("AM/PM", config.showAmPm) { update { it.copy(showAmPm = !it.showAmPm) } }
            ChipToggle("Leading 0", config.clockLeadingZero) { update { it.copy(clockLeadingZero = !it.clockLeadingZero) } }
            ChipToggle("Seconds", config.showSeconds) { update { it.copy(showSeconds = !it.showSeconds) } }
            ChipToggle("Shadow", config.clockShadow) { update { it.copy(clockShadow = !it.clockShadow) } }
        }
    }
}

@Composable
fun DatePanel(config: LockConfig, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val sample = remember { formatDate(LocalDateTime.now(), DateFormat.SHORT).substringBefore(' ') }
    GlassPanel(
        title = "Date", onClose = onClose, modifier = modifier,
        leading = {
            GlassIconButton(Icons.Rounded.AlignHorizontalCenter, "Center", { update { it.copy(dateX = 0.5f) } })
            VisibilityButton(config.showDate) { update { it.copy(showDate = !it.showDate) } }
        },
    ) {
        FontPicker(
            sample, config.dateFont, config.dateWeight,
            onFont = { f -> update { it.copy(dateFont = f) } },
            onWeight = { w -> update { it.copy(dateWeight = w) } },
        )
        GlassDivider()
        ColorScroller(PaletteColors, config.dateColor) { c -> update { it.copy(dateColor = c) } }
        GlassSlider(Icons.Rounded.Opacity, config.dateAlpha, 0.1f..1f, { a -> update { it.copy(dateAlpha = a) } })
        DateFormatScroller(config.dateFormat) { f -> update { it.copy(dateFormat = f) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipToggle("Shadow", config.clockShadow) { update { it.copy(clockShadow = !it.clockShadow) } }
        }
    }
}

/** Every date style, each shown as today's date written that way. */
@Composable
private fun DateFormatScroller(selected: DateFormat, onSelect: (DateFormat) -> Unit) {
    val now = remember { LocalDateTime.now() }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(DateFormat.entries) { f ->
            val isSelected = f == selected
            Text(
                formatDate(now, f),
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                fontSize = (LocalPanelDensity.current.body.value * 1.00f).sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = if (isSelected) 0.2f else 0.07f))
                    .border(1.5.dp, if (isSelected) Color.White.copy(alpha = 0.55f) else Color.Transparent, RoundedCornerShape(50))
                    .clickable { onSelect(f) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
fun BackgroundPanel(
    config: LockConfig,
    importing: BackgroundType?,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    showKeypadBlur: Boolean = false,
) {
    val media = config.backgroundType != BackgroundType.GRADIENT && config.backgroundFile != null
    GlassPanel(
        title = "Background", onClose = onClose, modifier = modifier,
        leading = if (media && (config.bgScale != 1f || config.bgOffsetX != 0f || config.bgOffsetY != 0f)) {
            {
                GlassIconButton(Icons.Rounded.ZoomOutMap, "Reset zoom", {
                    update { it.copy(bgScale = 1f, bgOffsetX = 0f, bgOffsetY = 0f) }
                })
            }
        } else null,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceTile(Icons.Outlined.Gradient, "Gradient", config.backgroundType == BackgroundType.GRADIENT, Modifier.weight(1f)) {
                update { it.copy(backgroundType = BackgroundType.GRADIENT) }
            }
            SourceTile(Icons.Outlined.Image, "Photo", config.backgroundType == BackgroundType.IMAGE, Modifier.weight(1f), onPickImage)
            SourceTile(Icons.Outlined.Videocam, "Video", config.backgroundType == BackgroundType.VIDEO, Modifier.weight(1f), onPickVideo)
        }
        if (importing != null) {
            LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp)), color = Color.White)
        }
        if (config.backgroundType == BackgroundType.GRADIENT) {
            SwatchScroller(GradientPresets.map { it.brush }, config.gradientIndex, { i -> update { it.copy(gradientIndex = i) } })
        } else {
            Text(
                "Pinch to zoom and drag on the preview to frame it. Tap ${if (config.backgroundType == BackgroundType.VIDEO) "Video" else "Photo"} to pick another.",
                color = Color.White.copy(alpha = 0.7f), fontSize = (LocalPanelDensity.current.body.value * 1.00f).sp,
            )
        }
        GlassSlider(Icons.Rounded.Brightness6, config.dim, 0f..0.7f, { v -> update { it.copy(dim = v) } })
        if (showKeypadBlur) {
            GlassLabel("Blur behind keypad")
            GlassSlider(Icons.Rounded.BlurOn, config.passcodeBlur, 0f..80f, { v -> update { it.copy(passcodeBlur = v) } })
        }
    }
}

@Composable
private fun SourceTile(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = if (selected) 0.18f else 0.07f))
            .border(1.5.dp, if (selected) Color.White.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(LocalPanelDensity.current.icon + 2.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = (LocalPanelDensity.current.body.value * 0.92f).sp)
    }
}

@Composable
fun KeysPanel(
    config: LockConfig,
    onSetPasscode: () -> Unit,
    onRemovePasscode: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(title = "Passcode keys", onClose = onClose, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Use passcode", color = Color.White, fontSize = (LocalPanelDensity.current.body.value * 1.15f).sp)
                Text(
                    if (config.hasPin) "${config.pinLength} digits" else "Off: swipe up to unlock",
                    color = Color.White.copy(alpha = 0.6f), fontSize = (LocalPanelDensity.current.body.value * 0.92f).sp,
                )
            }
            Switch(
                checked = config.hasPin,
                onCheckedChange = { on -> if (on) onSetPasscode() else onRemovePasscode() },
                colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF34C759), checkedThumbColor = Color.White),
            )
        }
        if (config.hasPin) {
            Text(
                "Change passcode", color = Color.White, fontSize = (LocalPanelDensity.current.body.value * 1.08f).sp, fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(onClick = onSetPasscode)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        GlassDivider()
        GlassLabel("Key style")
        val keys = KeypadStyle.of(config)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyStyle.entries.forEach { style ->
                SampleTile(style == config.keyStyle, { update { it.copy(keyStyle = style) } }, Modifier.weight(1f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        KeyFace(LocalPanelDensity.current.button + 6.dp, keys.copy(style = style), active = false) {
                            DigitLabel(2, LocalPanelDensity.current.button + 6.dp, keys.textColor, keys.showLetters)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(style.name.lowercase().replaceFirstChar { it.uppercase() }, color = Color.White, fontSize = (LocalPanelDensity.current.body.value * 0.85f).sp)
                    }
                }
            }
        }
        GlassLabel("Keypad size")
        GlassSlider(Icons.Rounded.Dialpad, config.keypadScale, WidgetLimits.keypadScale, { v -> update { it.copy(keypadScale = v) } })
        GlassLabel("Keys")
        ColorScroller(PaletteColors, config.keyColor) { c -> update { it.copy(keyColor = c) } }
        GlassSlider(Icons.Rounded.Opacity, config.keyAlpha, 0f..0.6f, { a -> update { it.copy(keyAlpha = a) } })
        GlassLabel("Numbers")
        ColorScroller(PaletteColors, config.keyTextColor) { c -> update { it.copy(keyTextColor = c) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipToggle("Letters (ABC)", config.showKeyLetters) { update { it.copy(showKeyLetters = !it.showKeyLetters) } }
        }
    }
}

/**
 * Editable text + styling for a free-text element. [toggles] holds the
 * element-specific show/hide chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextPanel(
    title: String,
    text: String,
    placeholder: String,
    onText: (String) -> Unit,
    spec: TextSpec,
    onSpec: (TextSpec) -> Unit,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
    toggles: @Composable () -> Unit,
) {
    GlassPanel(
        title = title, onClose = onClose, modifier = modifier,
        leading = { VisibilityButton(visible, onToggleVisible) },
    ) {
        // Same order as the Clock and Date panels: typeface, weight, color, opacity.
        FontPicker(
            "Aa", spec.font, spec.weight,
            onFont = { onSpec(spec.copy(font = it)) },
            onWeight = { onSpec(spec.copy(weight = it)) },
        )
        GlassDivider()
        ColorScroller(PaletteColors, spec.color) { onSpec(spec.copy(color = it)) }
        GlassSlider(Icons.Rounded.Opacity, spec.alpha, 0.1f..1f, { onSpec(spec.copy(alpha = it)) })
        GlassSlider(Icons.Rounded.FormatSize, spec.size, 10f..48f, { onSpec(spec.copy(size = it)) })
        GlassDivider()
        BasicTextField(
            value = text,
            onValueChange = onText,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = (LocalPanelDensity.current.body.value * 1.15f).sp),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            decorationBox = { inner ->
                if (text.isEmpty()) Text(placeholder, color = Color.White.copy(alpha = 0.45f), fontSize = (LocalPanelDensity.current.body.value * 1.15f).sp)
                inner()
            },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            toggles()
        }
    }
}

@Composable
fun PromptPanel(config: LockConfig, onClose: () -> Unit, modifier: Modifier = Modifier) {
    TextPanel(
        title = "Passcode text",
        text = config.promptText,
        placeholder = "Enter Passcode",
        onText = { t -> update { it.copy(promptText = t) } },
        spec = config.promptStyle,
        onSpec = { sp -> update { it.copy(promptStyle = sp) } },
        visible = config.showPrompt,
        onToggleVisible = { update { it.copy(showPrompt = !it.showPrompt) } },
        onClose = onClose,
        modifier = modifier,
    ) {
        ChipToggle("Lock icon", config.showPromptIcon) { update { it.copy(showPromptIcon = !it.showPromptIcon) } }
    }
}

@Composable
fun HintPanel(config: LockConfig, onClose: () -> Unit, modifier: Modifier = Modifier) {
    TextPanel(
        title = "Unlock hint",
        text = config.hintText,
        placeholder = config.copy(hintText = "").hintLabel(),
        onText = { t -> update { it.copy(hintText = t) } },
        spec = config.hintStyle,
        onSpec = { sp -> update { it.copy(hintStyle = sp) } },
        visible = config.showHint,
        onToggleVisible = { update { it.copy(showHint = !it.showHint) } },
        onClose = onClose,
        modifier = modifier,
    ) {
        ChipToggle("Chevron", config.showHintChevron) { update { it.copy(showHintChevron = !it.showHintChevron) } }
        ChipToggle("Bar", config.showHintBar) { update { it.copy(showHintBar = !it.showHintBar) } }
    }
}

/** Shown in the music/notification panels until notification access is granted. */
@Composable
private fun AccessNotice(what: String, onGrant: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFFB648).copy(alpha = 0.16f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Showing sample $what. Allow notification access to see the real thing.",
            color = Color.White, fontSize = (LocalPanelDensity.current.body.value * 1.00f).sp,
        )
        Text(
            "Allow access", color = Color.Black, fontSize = (LocalPanelDensity.current.body.value * 1.00f).sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0xFFFFB648))
                .clickable(onClick = onGrant)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@Composable
fun MediaPanel(
    config: LockConfig,
    hasAccess: Boolean,
    onGrantAccess: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(
        title = "Music", onClose = onClose, modifier = modifier,
        leading = {
            GlassIconButton(Icons.Rounded.AlignHorizontalCenter, "Center", { update { it.copy(mediaX = 0.5f) } })
            VisibilityButton(config.showMedia) { update { it.copy(showMedia = !it.showMedia) } }
        },
    ) {
        if (!hasAccess) AccessNotice("music", onGrantAccess)
        GlassLabel("Style")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                MediaStyle.CARD to (Icons.Rounded.ViewAgenda to "Card"),
                MediaStyle.SQUARE to (Icons.Rounded.CropSquare to "Square"),
                MediaStyle.WAVE to (Icons.Rounded.GraphicEq to "Wave"),
                MediaStyle.PILL to (Icons.Rounded.Remove to "Pill"),
            ).forEach { (style, look) ->
                SampleTile(style == config.mediaStyle, { update { it.copy(mediaStyle = style) } }, Modifier.weight(1f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(look.first, null, tint = Color.White, modifier = Modifier.size(LocalPanelDensity.current.icon + 4.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(look.second, color = Color.White, fontSize = (LocalPanelDensity.current.body.value * 0.85f).sp)
                    }
                }
            }
        }
        GlassDivider()
        ColorScroller(PaletteColors, config.mediaColor) { c -> update { it.copy(mediaColor = c) } }
        GlassLabel("Text size")
        GlassSlider(Icons.Rounded.FormatSize, config.mediaTextScale, WidgetLimits.innerScale, { v -> update { it.copy(mediaTextScale = v) } })
        GlassLabel("Button size")
        GlassSlider(Icons.Rounded.PlayCircle, config.mediaButtonScale, WidgetLimits.innerScale, { v -> update { it.copy(mediaButtonScale = v) } })
        if (config.mediaStyle == MediaStyle.CARD || config.mediaStyle == MediaStyle.PILL) {
            GlassLabel("Background")
            GlassSlider(Icons.Rounded.Opacity, config.mediaBgAlpha, 0f..0.9f, { a -> update { it.copy(mediaBgAlpha = a) } })
        }
        Text(
            "Works with YouTube, YouTube Music, Spotify and any app that shows media controls. Hides itself when nothing is playing.",
            color = Color.White.copy(alpha = 0.6f), fontSize = (LocalPanelDensity.current.body.value * 0.92f).sp,
        )
    }
}

@Composable
fun NotificationsPanel(
    config: LockConfig,
    hasAccess: Boolean,
    onGrantAccess: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(
        title = "Notifications", onClose = onClose, modifier = modifier,
        leading = {
            GlassIconButton(Icons.Rounded.AlignHorizontalCenter, "Center", { update { it.copy(notifX = 0.5f) } })
            VisibilityButton(config.showNotifications) { update { it.copy(showNotifications = !it.showNotifications) } }
        },
    ) {
        if (!hasAccess) AccessNotice("notifications", onGrantAccess)
        Segmented(
            listOf(NotifStyle.LIST to "List", NotifStyle.STACK to "Stack", NotifStyle.ICONS to "Icons"), config.notifStyle,
        ) { s -> update { it.copy(notifStyle = s) } }
        if (config.notifStyle != NotifStyle.ICONS) {
            GlassLabel("Show up to ${config.notifMax}")
            GlassSlider(
                Icons.Rounded.Notifications, config.notifMax.toFloat(), 1f..6f,
                { v -> update { it.copy(notifMax = v.roundToInt()) } }, steps = 4,
            )
        }
        GlassLabel("Text size")
        GlassSlider(Icons.Rounded.FormatSize, config.notifTextScale, WidgetLimits.innerScale, { v -> update { it.copy(notifTextScale = v) } })
        GlassLabel("Background")
        GlassSlider(Icons.Rounded.Opacity, config.notifBgAlpha, 0f..0.9f, { a -> update { it.copy(notifBgAlpha = a) } })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipToggle("Hide previews", config.notifHideContent) {
                update { it.copy(notifHideContent = !it.notifHideContent) }
            }
            if (config.notifStyle != NotifStyle.ICONS) {
                ChipToggle("Clear button", config.notifShowClear) {
                    update { it.copy(notifShowClear = !it.notifShowClear) }
                }
            }
        }
        Text(
            "Silent notifications and ones that apps mark as secret never show on the lock screen. Hides itself when there are none.",
            color = Color.White.copy(alpha = 0.6f), fontSize = (LocalPanelDensity.current.body.value * 0.92f).sp,
        )
    }
}

@Composable
fun LockIconPanel(config: LockConfig, onClose: () -> Unit, modifier: Modifier = Modifier) {
    GlassPanel(
        title = "Lock icon", onClose = onClose, modifier = modifier,
        leading = {
            GlassIconButton(Icons.Rounded.AlignHorizontalCenter, "Center", { update { it.copy(lockIconX = 0.5f) } })
            VisibilityButton(config.showLockIcon) { update { it.copy(showLockIcon = !it.showLockIcon) } }
        },
    ) {
        ColorScroller(PaletteColors, config.lockIconColor) { c -> update { it.copy(lockIconColor = c) } }
        GlassSlider(Icons.Rounded.Opacity, config.lockIconAlpha, 0.1f..1f, { a -> update { it.copy(lockIconAlpha = a) } })
        GlassLabel("Size")
        GlassSlider(Icons.Rounded.FormatSize, config.lockIconSize, WidgetLimits.lockIconSize, { v -> update { it.copy(lockIconSize = v) } })
        Text(
            "Drag it anywhere on the preview. It turns into an open lock as you unlock.",
            color = Color.White.copy(alpha = 0.6f), fontSize = (LocalPanelDensity.current.body.value * 0.92f).sp,
        )
    }
}
