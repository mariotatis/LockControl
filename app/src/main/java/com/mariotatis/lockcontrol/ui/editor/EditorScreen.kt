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
import androidx.compose.ui.text.font.FontWeight
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
import com.mariotatis.lockcontrol.lock.LockAccessibilityService
import com.mariotatis.lockcontrol.ui.theme.Ink
import com.mariotatis.lockcontrol.ui.theme.Warning
import kotlinx.coroutines.launch

@Composable
fun EditorScreen(onPreview: () -> Unit) {
    val config by ConfigRepository.config.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var serviceEnabled by remember { mutableStateOf(LockAccessibilityService.isEnabled(context)) }
    var systemLockOn by remember { mutableStateOf(isSystemLockSecure(context)) }
    LifecycleResumeEffect(Unit) {
        serviceEnabled = LockAccessibilityService.isEnabled(context)
        systemLockOn = isSystemLockSecure(context)
        onPauseOrDispose { }
    }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val setupIncomplete = !serviceEnabled || systemLockOn

    var view by rememberSaveable { mutableStateOf(EditorView.LOCK) }
    var target by rememberSaveable { mutableStateOf(EditTarget.NONE) }
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
                    ConfigRepository.update {
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
                val panelOpen = wide && target != EditTarget.NONE
                val reserve by animateDpAsState(if (panelOpen) PanelWidth + 12.dp else 0.dp, label = "reserve")
                EditablePreview(
                    config = config,
                    playing = resumed,
                    view = view,
                    target = target,
                    onTarget = { target = it },
                    onWidgetChanged = { widget, f ->
                        ConfigRepository.update {
                            when (widget) {
                                LockWidget.CLOCK -> it.copy(
                                    clockX = f.position.x, clockY = f.position.y, clockSize = f.size, clockStretch = f.stretch,
                                )
                                LockWidget.DATE -> it.copy(
                                    dateX = f.position.x, dateY = f.position.y, dateSize = f.size, dateStretch = f.stretch,
                                )
                            }
                        }
                    },
                    onBackgroundChanged = { f ->
                        ConfigRepository.update { it.copy(bgScale = f.scale, bgOffsetX = f.offset.x, bgOffsetY = f.offset.y) }
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(end = reserve),
                )

                val panelModifier = if (wide) {
                    Modifier
                        .align(Alignment.CenterEnd)
                        .width(PanelWidth)
                        .heightIn(max = maxHeight)
                } else {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(10.dp)
                        .fillMaxWidth()
                        .heightIn(max = maxHeight * 0.55f)
                }
                AnimatedContent(
                    targetState = target,
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
                        EditTarget.NONE -> Box(Modifier)
                    }
                }
            }
        }
    }

    if (showSettings) SettingsDialog(config, serviceEnabled, systemLockOn) { showSettings = false }
    passcodeMode?.let { mode -> PasscodeSetupDialog(mode, config) { passcodeMode = null } }
}

private val PanelWidth = 360.dp

@Composable
private fun TopBar(
    view: EditorView,
    onView: (EditorView) -> Unit,
    target: EditTarget,
    setupIncomplete: Boolean,
    onTryIt: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppBadge()
        Text("Lock Control", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (setupIncomplete) {
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

fun isSystemLockSecure(context: Context): Boolean =
    context.getSystemService(KeyguardManager::class.java).isDeviceSecure
