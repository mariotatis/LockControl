package com.mariotatis.lockcontrol.ui.editor

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mariotatis.lockcontrol.data.ConfigRepository
import com.mariotatis.lockcontrol.data.LockConfig
import com.mariotatis.lockcontrol.ui.theme.Panel
import com.mariotatis.lockcontrol.ui.theme.PanelHigh
import com.mariotatis.lockcontrol.ui.theme.TextMuted
import com.mariotatis.lockcontrol.ui.theme.Warning

private fun update(transform: (LockConfig) -> LockConfig) = ConfigRepository.edit(transform)

/** Gear menu: setup status, what's shown on the lock screen, and behavior. */
@Composable
fun SettingsDialog(
    config: LockConfig,
    serviceEnabled: Boolean,
    systemLockOn: Boolean,
    notificationAccess: Boolean,
    onDismiss: () -> Unit,
) {
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalContentColor provides Color.White) { SettingsContent(config, serviceEnabled, systemLockOn, notificationAccess, onDismiss) }
    }
}

@Composable
private fun SettingsContent(
    config: LockConfig,
    serviceEnabled: Boolean,
    systemLockOn: Boolean,
    notificationAccess: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    run {
        Column(
            Modifier
                .padding(24.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Panel)
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                GlassIconButton(Icons.Rounded.Close, "Close", onDismiss)
            }

            if (!serviceEnabled) {
                StatusLine(false, "Lock service is off", "Turn on \"Lock Control lock screen\" in Accessibility. It draws over the system and catches buttons.")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Accessibility") }
                    OutlinedButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    }) { Text("App info") }
                }
                Text(
                    "Toggle greyed out? Sideloaded apps need: App info → ⋮ (top right) → Allow restricted settings.",
                    color = TextMuted, fontSize = 12.sp,
                )
            } else {
                StatusLine(true, "Lock service is on", null)
            }
            if (systemLockOn) {
                StatusLine(false, "System screen lock is still on", "You'd see two lock screens. Set Screen lock to None so this one replaces it.")
                OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }) { Text("Security settings") }
            }

            if (notificationAccess) {
                StatusLine(true, "Notification access is on", null)
            } else {
                StatusLine(false, "Notification access is off", "Needed to show music and notifications on the lock screen.")
                OutlinedButton(onClick = { openNotificationAccess(context) }) { Text("Allow access") }
            }

            SectionLabel("Lock screen")
            SwitchRow("Use custom lock screen", config.enabled, { on -> update { it.copy(enabled = on) } }, "Shows every time the screen turns off")
            SwitchRow("Show clock", config.showClock, { on -> update { it.copy(showClock = on) } })
            SwitchRow("Show date", config.showDate, { on -> update { it.copy(showDate = on) } })
            SwitchRow("Show music", config.showMedia, { on -> update { it.copy(showMedia = on) } })
            SwitchRow("Show notifications", config.showNotifications, { on -> update { it.copy(showNotifications = on) } })
            SwitchRow("Lock icon", config.showLockIcon, { on -> update { it.copy(showLockIcon = on) } })
            SwitchRow("Battery indicator", config.showBattery, { on -> update { it.copy(showBattery = on) } })
            SwitchRow("Unlock hint", config.showHint, { on -> update { it.copy(showHint = on) } })

            SectionLabel("Behavior")
            SwitchRow("Lock after restart", config.lockOnBoot, { on -> update { it.copy(lockOnBoot = on) } })

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(PanelHigh.copy(alpha = 0.6f)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("If you're ever stuck", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Booting into Safe Mode disables this lock. Over USB: adb shell settings delete secure enabled_accessibility_services",
                    color = TextMuted, fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(), color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun StatusLine(ok: Boolean, title: String, body: String?) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber, null,
            tint = if (ok) Color(0xFF34C759) else Warning, modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (body != null) Text(body, color = TextMuted, fontSize = 13.sp)
        }
    }
}
