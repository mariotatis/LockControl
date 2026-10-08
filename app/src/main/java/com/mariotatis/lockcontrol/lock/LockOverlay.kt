package com.mariotatis.lockcontrol.lock

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.mariotatis.lockcontrol.data.ConfigRepository
import com.mariotatis.lockcontrol.ui.lock.LockScreen
import com.mariotatis.lockcontrol.ui.lock.captureKeys
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Hosts the Compose lock screen in a TYPE_ACCESSIBILITY_OVERLAY window. That
 * window type sits above the status bar, notification shade and navigation
 * bar, so nothing underneath can be reached while it is showing.
 */
class LockOverlay(private val service: AccessibilityService) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var view: ComposeView? = null
    private var owner: OverlayLifecycleOwner? = null

    val screenOn = MutableStateFlow(true)
    val keyEvents = MutableSharedFlow<Int>(extraBufferCapacity = 32)
    val isShowing: Boolean get() = view != null

    fun show() {
        if (view != null) return
        val lifecycleOwner = OverlayLifecycleOwner().apply { create() }
        val composeView = ComposeView(service).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setContent {
                val config by ConfigRepository.config.collectAsState()
                val active by screenOn.collectAsState()
                LockScreen(
                    config = config,
                    active = active,
                    keyEvents = keyEvents,
                    onUnlocked = { mainHandler.post { hide() } },
                    // Fallback when the accessibility key filter doesn't deliver: the overlay is the focused window.
                    modifier = Modifier.captureKeys(keyEvents),
                )
            }
        }
        windowManager.addView(composeView, layoutParams())
        lifecycleOwner.resume()
        view = composeView
        owner = lifecycleOwner
    }

    fun hide() {
        val v = view ?: return
        view = null
        v.disposeComposition()
        runCatching { windowManager.removeViewImmediate(v) }
        owner?.destroy()
        owner = null
    }

    @Suppress("DEPRECATION")
    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    ).apply {
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        fitInsetsTypes = 0
        title = "LockControl"
    }
}

/** Minimal lifecycle/saved-state owner so Compose can run outside an Activity. */
private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    fun create() {
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    fun resume() {
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
