package com.mariotatis.lockcontrol.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mariotatis.lockcontrol.ui.theme.TextMuted

val PaletteColors = listOf(
    0xFFFFFFFF, 0xFFFFF4E0, 0xFFFFD86B, 0xFFFF9F5A, 0xFFFF6B6B, 0xFFFF8FAB, 0xFFE58CFF,
    0xFFC3A6FF, 0xFFA8C0FF, 0xFF8AB4FF, 0xFF7FE3FF, 0xFF7CF2C8, 0xFF9BE38B, 0xFF8E8E93, 0xFF1C1C1E,
).map { it.toInt() }

private val GlassFill = Color(0xFF101016).copy(alpha = 0.74f)
private val GlassLine = Color.White.copy(alpha = 0.12f)

/** Translucent panel floating over the preview, iOS customization style. */
@Composable
fun GlassPanel(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(GlassFill)
            .border(1.dp, GlassLine, RoundedCornerShape(28.dp))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.align(Alignment.CenterStart), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                leading?.invoke()
            }
            Box(Modifier.align(Alignment.CenterEnd)) { GlassIconButton(Icons.Rounded.Check, "Done", onClose) }
        }
        content()
    }
}

@Composable
fun GlassIconButton(icon: ImageVector, description: String, onClick: () -> Unit, selected: Boolean = false) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (selected) 0.22f else 0.1f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = Color.White, modifier = Modifier.size(20.dp)) }
}

/** Eye toggle in panel headers: shows or hides the element being edited. */
@Composable
fun VisibilityButton(visible: Boolean, onToggle: () -> Unit) {
    GlassIconButton(
        if (visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
        if (visible) "Hide" else "Show",
        onToggle,
        selected = !visible,
    )
}

@Composable
fun GlassSlider(
    icon: ImageVector,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    steps: Int = 0,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Slider(
            value = value.coerceIn(range), onValueChange = onChange, valueRange = range, steps = steps,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White.copy(alpha = 0.85f),
                inactiveTrackColor = Color.White.copy(alpha = 0.22f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

@Composable
fun GlassDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(GlassLine))
}

@Composable
fun GlassLabel(text: String) {
    Text(text.uppercase(), color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
}

/** Horizontally scrolling swatches; the selected one gets a ring. */
@Composable
fun SwatchScroller(brushes: List<Brush>, selected: Int, onSelect: (Int) -> Unit, size: Int = 36) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
        itemsIndexed(brushes) { i, brush ->
            val isSelected = i == selected
            Box(
                Modifier
                    .size((size + 10).dp)
                    .clip(CircleShape)
                    .border(2.dp, if (isSelected) Color.White else Color.Transparent, CircleShape)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(size.dp).clip(CircleShape).background(brush).border(1.dp, GlassLine, CircleShape))
            }
        }
    }
}

@Composable
fun ColorScroller(colors: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    SwatchScroller(
        brushes = colors.map { SolidColor(Color(it)) },
        selected = colors.indexOf(selected),
        onSelect = { onSelect(colors[it]) },
    )
}

/** Two-or-more option toggle, like the "Glass | Solid" switch on iOS. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).padding(4.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Text(
                label,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (isSelected) Color.White.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 9.dp),
            )
        }
    }
}

@Composable
fun ChipToggle(label: String, checked: Boolean, onToggle: () -> Unit) {
    Text(
        label,
        color = if (checked) Color.Black else Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (checked) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Selectable sample tile, e.g. "12" drawn in each typeface. */
@Composable
fun SampleTile(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color.White.copy(alpha = 0.1f) else Color.Transparent)
            .border(1.5.dp, if (selected) Color.White.copy(alpha = 0.55f) else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ---- Settings modal rows ----

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, color = TextMuted, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

fun contrastOn(color: Color): Color = if (color.luminance() > 0.5f) Color.Black else Color.White
