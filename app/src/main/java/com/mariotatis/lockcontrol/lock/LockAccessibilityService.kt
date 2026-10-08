package com.mariotatis.lockcontrol.lock

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.mariotatis.lockcontrol.data.ConfigRepository

/**
 * Shows the custom lock screen whenever the display turns off (so it's already
 * there when the screen wakes) and swallows hardware/gamepad keys while locked.
 */
class LockAccessibilityService : AccessibilityService() {
    private lateinit var overlay: LockOverlay

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    overlay.screenOn.value = false
                    if (ConfigRepository.config.value.enabled) overlay.show()
                }
                Intent.ACTION_SCREEN_ON -> overlay.screenOn.value = true
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ConfigRepository.init(applicationContext)
        overlay = LockOverlay(this)
        overlay.screenOn.value = getSystemService(PowerManager::class.java).isInteractive
        ContextCompat.registerReceiver(
            this, screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        instance = this

        // The service binds early during boot; lock right away after a restart.
        val config = ConfigRepository.config.value
        if (config.enabled && config.lockOnBoot && SystemClock.elapsedRealtime() < BOOT_WINDOW_MS) overlay.show()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!::overlay.isInitialized || !overlay.isShowing) return false
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_POWER -> return false
        }
        val isDpad = event.keyCode in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT
        if (event.action == KeyEvent.ACTION_DOWN && (event.repeatCount == 0 || isDpad)) {
            overlay.keyEvents.tryEmit(event.keyCode)
        }
        return true // nothing reaches the app underneath while locked
    }

    fun lockNow() {
        performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        overlay.show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        runCatching { unregisterReceiver(screenReceiver) }
        if (::overlay.isInitialized) overlay.hide()
        super.onDestroy()
    }

    companion object {
        private const val BOOT_WINDOW_MS = 3 * 60_000L

        @Volatile
        var instance: LockAccessibilityService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val me = ComponentName(context, LockAccessibilityService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
