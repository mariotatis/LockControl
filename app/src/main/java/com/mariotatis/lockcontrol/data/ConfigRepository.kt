package com.mariotatis.lockcontrol.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

/**
 * Single source of truth for the lock screen configuration. The editor and the
 * accessibility service live in the same process, so edits show up live on the
 * lock screen without any extra plumbing.
 */
object ConfigRepository {
    private const val PREFS = "lock_config"
    private const val KEY = "config"

    private lateinit var prefs: SharedPreferences
    private val state = MutableStateFlow(LockConfig())
    val config: StateFlow<LockConfig> = state.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        state.value = prefs.getString(KEY, null)
            ?.let { runCatching { LockConfig.fromJson(it) }.getOrNull() }
            ?: LockConfig()
    }

    fun update(transform: (LockConfig) -> LockConfig) {
        val updated = state.updateAndGet(transform)
        prefs.edit().putString(KEY, updated.toJson()).apply()
    }
}
