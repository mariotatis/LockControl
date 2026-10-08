package com.mariotatis.lockcontrol.ui.editor

import android.app.KeyguardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.ui.graphics.Brush
import com.mariotatis.lockcontrol.ui.theme.Accent
import com.mariotatis.lockcontrol.ui.theme.AccentDeep
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.mariotatis.lockcontrol.data.BackgroundFiles
import com.mariotatis.lockcontrol.data.BackgroundType
import com.mariotatis.lockcontrol.data.ConfigRepository
import com.mariotatis.lockcontrol.media.LiveData
import android.content.Intent
import android.provider.Settings
import com.mariotatis.lockcontrol.lock.LockAccessibilityService
import com.mariotatis.lockcontrol.ui.theme.Ink
import com.mariotatis.lockcontrol.ui.theme.Warning
import kotlinx.coroutines.launch

@Composable
fun EditorScreen(onPreview: () -> Unit) {
    val rawConfig by ConfigRepository.config.collectAsStateWithLifecycle()
    // Portrait and landscape keep separate layouts; edit whichever one the device is in.
    val configuration = LocalConfiguration.current
    val portrait = configuration.screenHeightDp > configuration.screenWidthDp
    SideEffect { ConfigRepository.editingPortrait = portrait }
    val config = remember(rawConfig, portrait) { rawConfig.forOrientation(portrait) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var serviceEnabled by remember { mutableStateOf(LockAccessibilityService.isEnabled(context)) }
    var systemLockOn by remember { mutableStateOf(isSystemLockSecure(context)) }
    var notificationAccess by remember { mutableStateOf(LiveData.hasAccess(context)) }
    LifecycleResumeEffect(Unit) {
        serviceEnabled = LockAccessibilityService.isEnabled(context)
        systemLockOn = isSystemLockSecure(context)
        notificationAccess = LiveData.hasAccess(context)
        onPauseOrDispose { }
    }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val setupIncomplete = !serviceEnabled || systemLockOn

    var view by rememberSaveable { mutableStateOf(EditorView.LOCK) }
    var target by rememberSaveable { mutableStateOf(EditTarget.NONE) }
    // Slide the panel away (right in landscape, down in portrait) to work on the full preview.
    var panelCollapsed by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var passcodeMode by remember { mutableStateOf<PasscodeMode?>(null) }
    var importing by remember { mutableStateOf<BackgroundType?>(null) }
    BackHandler(target != EditTarget.NONE) { target = EditTarget.NONE }

    fun import(uri: Uri?, type: BackgroundType) {
        if (uri == null) return
        scope.launch {
            importing = type
            runCatching { BackgroundFiles.import(context, uri, type) }
                .onSuccess { path ->
                    ConfigRepository.edit {
                        it.copy(backgroundType = type, backgroundFile = path, bgScale = 1f, bgOffsetX = 0f, bgOffsetY = 0f)
                    }
                    BackgroundFiles.cleanup(context, path)
                }
                .onFailure { Toast.makeText(context, "Couldn't import that file", Toast.LENGTH_SHORT).show() }
            importing = null
        }
    }
    val imagePicker = rememberLauncherForActivityResult(PickVisualMedia()) { import(it, BackgroundType.IMAGE) }
    val videoPicker = rememberLauncherForActivityResult(PickVisualMedia()) { import(it, BackgroundType.VIDEO) }

    Surface(color = Ink, contentColor = Color.White, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TopBar(
                target = target,
                setupIncomplete = setupIncomplete,
                view = view,
                onView = {
                    view = it
                    target = EditTarget.NONE
                },
                onTryIt = onPreview,
                onSettings = { showSettings = true },
            )
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                val wide = maxWidth > maxHeight
                // Landscape: the panel docks on the right and the preview slides left so it's never
                // covered. Portrait: the panel floats at the bottom.
                // Docked panel: ~30% of the width, clamped so it stays usable on any screen.
                val panelWidth = (maxWidth * 0.3f).coerceIn(250.dp, 340.dp)
                val panelOpen = wide && target != EditTarget.NONE && !panelCollapsed
                val reserve by animateDpAsState(if (panelOpen) panelWidth + 10.dp else 0.dp, label = "reserve")
                EditablePreview(
                    config = config,
                    playing = resumed,
                    view = view,
                    target = target,
                    onTarget = { target = it },
                    onWidgetChanged = { widget, f ->
                        ConfigRepository.edit {
                            when (widget) {
                                LockWidget.CLOCK -> it.copy(
                                    clockX = f.position.x, clockY = f.position.y, clockSize = f.size, clockStretch = f.stretch,
                                )
                                LockWidget.DATE -> it.copy(
                                    dateX = f.position.x, dateY = f.position.y, dateSize = f.size, dateStretch = f.stretch,
                                )
                                LockWidget.LOCK_ICON -> it.copy(
                                    lockIconX = f.position.x, lockIconY = f.position.y, lockIconSize = f.size,
                                )
                                LockWidget.MEDIA -> it.copy(mediaX = f.position.x, mediaY = f.position.y, mediaScale = f.size)
                                LockWidget.NOTIFICATIONS -> it.copy(
                                    notifX = f.position.x, notifY = f.position.y, notifScale = f.size,
                                )
                            }
                        }
                    },
                    onBackgroundChanged = { f ->
                        ConfigRepository.edit { it.copy(bgScale = f.scale, bgOffsetX = f.offset.x, bgOffsetY = f.offset.y) }
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(end = reserve),
                )

                val panelModifier = if (wide) {
                    Modifier
                        .align(Alignment.CenterEnd)
                        .width(panelWidth)
                        .heightIn(max = maxHeight)
                } else {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(10.dp)
                        .fillMaxWidth()
                        .heightIn(max = maxHeight * 0.55f)
                }
                if (panelCollapsed && target != EditTarget.NONE) {
                    if (wide) {
                        PanelTab(Modifier.align(Alignment.CenterEnd)) { panelCollapsed = false }
                    } else {
                        BottomPanelTab(Modifier.align(Alignment.BottomCenter)) { panelCollapsed = false }
                    }
                }
                CompositionLocalProvider(
                    LocalPanelDensity provides if (wide) PanelDensity.Compact else PanelDensity(),
                    LocalPanelCollapse provides { panelCollapsed = true },
                    LocalPanelCollapseIcon provides
                        if (wide) Icons.AutoMirrored.Rounded.KeyboardArrowRight else Icons.Rounded.KeyboardArrowDown,
                ) {
                    AnimatedContent(
                        targetState = if (panelCollapsed) EditTarget.NONE else target,
                        transitionSpec = {
                            (fadeIn() + slideInVertically { it / 6 }) togetherWith (fadeOut() + slideOutVertically { it / 6 })
                        },
                        modifier = panelModifier,
                        label = "panel",
                    ) { t ->
                        val close = { target = EditTarget.NONE }
                        when (t) {
                            EditTarget.CLOCK -> ClockPanel(config, close)
                            EditTarget.DATE -> DatePanel(config, close)
                            EditTarget.BACKGROUND -> BackgroundPanel(
                                config, importing,
                                onPickImage = { imagePicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
                                onPickVideo = { videoPicker.launch(PickVisualMediaRequest(PickVisualMedia.VideoOnly)) },
                                onClose = close,
                                showKeypadBlur = view == EditorView.PASSCODE,
                            )
                            EditTarget.KEYS -> KeysPanel(
                                config,
                                onSetPasscode = { passcodeMode = PasscodeMode.SET },
                                onRemovePasscode = { passcodeMode = PasscodeMode.REMOVE },
                                onClose = close,
                            )
                            EditTarget.PROMPT -> PromptPanel(config, close)
                            EditTarget.HINT -> HintPanel(config, close)
                            EditTarget.LOCK_ICON -> LockIconPanel(config, close)
                            EditTarget.MEDIA -> MediaPanel(config, notificationAccess, { openNotificationAccess(context) }, close)
                            EditTarget.NOTIFICATIONS -> NotificationsPanel(
                                config, notificationAccess, { openNotificationAccess(context) }, close,
                            )
                            EditTarget.NONE -> Box(Modifier)
                        }
                    }
                }
            }
        }
    }

    if (showSettings) SettingsDialog(config, serviceEnabled, systemLockOn, notificationAccess) { showSettings = false }
    passcodeMode?.let { mode -> PasscodeSetupDialog(mode, config) { passcodeMode = null } }
}

/** Handle at the bottom edge that brings the collapsed portrait panel back. */
@Composable
private fun BottomPanelTab(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .width(96.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .background(Color(0xFF101016).copy(alpha = 0.85f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.KeyboardArrowUp, "Show panel", tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/** Thin handle on the right edge that brings the collapsed panel back. */
@Composable
private fun PanelTab(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .width(26.dp)
            .height(86.dp)
            .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
            .background(Color(0xFF101016).copy(alpha = 0.85f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Show panel", tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun TopBar(
    view: EditorView,
    onView: (EditorView) -> Unit,
    target: EditTarget,
    setupIncomplete: Boolean,
    onTryIt: () -> Unit,
    onSettings: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // On narrow (portrait) screens the gear's orange dot alone signals unfinished setup.
        val roomy = maxWidth > 560.dp
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppBadge()
            Text(
                "Lock Control", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (setupIncomplete && roomy) {
                Text(
                    "Finish setup", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Warning)
                        .clickable(onClick = onSettings)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            ViewSwitcher(view, onView)
            GlassIconButton(Icons.Rounded.PlayArrow, "Try it", onTryIt)
            Box {
                GlassIconButton(Icons.Outlined.Settings, "Settings", onSettings)
                if (setupIncomplete) {
                    Box(Modifier.align(Alignment.TopEnd).offset(x = 1.dp, y = (-1).dp).size(10.dp).clip(CircleShape).background(Warning))
                }
            }
        }
    }
}

/** Lock screen / Passcode switch: which screen the preview shows and edits. */
@Composable
private fun ViewSwitcher(view: EditorView, onView: (EditorView) -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            Triple(EditorView.LOCK, Icons.Outlined.Lock, "Lock screen"),
            Triple(EditorView.PASSCODE, Icons.Outlined.Dialpad, "Passcode"),
        ).forEach { (v, icon, label) ->
            val selected = v == view
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (selected) Color.White.copy(alpha = 0.2f) else Color.Transparent)
                    .clickable { onView(v) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(18.dp)) }
        }
    }
}

/** The app mark: a blue circle with a white shield, same as the launcher icon. */
@Composable
private fun AppBadge() {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Accent, AccentDeep))),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.Shield, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
}

fun openNotificationAccess(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, LiveData.listenerComponent(context).flattenToString()),
    )
}

fun isSystemLockSecure(context: Context): Boolean =
    context.getSystemService(KeyguardManager::class.java).isDeviceSecure
