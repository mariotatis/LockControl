package com.mariotatis.lockcontrol.media

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What's playing right now, from any app with a media session. */
data class NowPlaying(
    val title: String,
    val artist: String,
    val album: String = "",
    val art: ImageBitmap? = null,
    val appName: String = "",
    val appIcon: ImageBitmap? = null,
    val playing: Boolean = false,
    val durationMs: Long = 0,
    /** Position at [positionTime] (elapsedRealtime); extrapolate while playing. */
    val positionMs: Long = 0,
    val positionTime: Long = 0,
    val speed: Float = 1f,
    val canSeek: Boolean = false,
) {
    fun positionAt(now: Long = SystemClock.elapsedRealtime()): Long {
        val elapsed = if (playing) ((now - positionTime) * speed).toLong() else 0L
        val pos = positionMs + elapsed
        return if (durationMs > 0) pos.coerceIn(0, durationMs) else pos.coerceAtLeast(0)
    }
}

/** Transport controls for [NowPlaying]; no-ops for the editor's sample data. */
interface MediaActions {
    fun playPause() {}
    fun next() {}
    fun previous() {}
    fun seekTo(positionMs: Long) {}

    companion object {
        val None = object : MediaActions {}
    }
}

data class LockNotification(
    val key: String,
    val appName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val icon: ImageBitmap? = null,
    /** Avatar tint used when there's no icon (sample data). */
    val tint: Long = 0xFF8C9BFF,
)

/**
 * Live media + notifications, fed by [LockNotificationListener]. The lock
 * screen and editor read from here; both are empty until notification access
 * is granted.
 */
object LiveData {
    private val nowPlayingState = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = nowPlayingState.asStateFlow()

    private val notificationsState = MutableStateFlow<List<LockNotification>>(emptyList())
    val notifications: StateFlow<List<LockNotification>> = notificationsState.asStateFlow()

    @Volatile
    var actions: MediaActions = MediaActions.None
        internal set

    /** Dismisses the notifications currently shown (like "Clear all"); null without access. */
    @Volatile
    internal var clearAction: (() -> Unit)? = null

    fun clearNotifications() {
        clearAction?.invoke()
        // Optimistic: empty the list now; the listener confirms as the removals arrive.
        notificationsState.value = emptyList()
    }

    internal fun publishMedia(value: NowPlaying?, controls: MediaActions) {
        actions = controls
        nowPlayingState.value = value
    }

    internal fun publishNotifications(list: List<LockNotification>) {
        notificationsState.value = list
    }

    internal fun clear() {
        clearAction = null
        publishMedia(null, MediaActions.None)
        publishNotifications(emptyList())
    }

    fun hasAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun listenerComponent(context: Context) = ComponentName(context, LockNotificationListener::class.java)
}

/** Placeholder content so widgets can be placed in the editor when nothing is live. */
object SampleData {
    val nowPlaying = NowPlaying(
        title = "Song Title",
        artist = "Artist Name",
        album = "Album",
        appName = "Music",
        playing = true,
        durationMs = 214_000,
        positionMs = 81_000,
        positionTime = SystemClock.elapsedRealtime(),
        canSeek = true,
    )

    fun notifications(now: Long = System.currentTimeMillis()) = listOf(
        LockNotification("s1", "Messages", "Alex", "Are we still on for tonight?", now - 2 * 60_000, tint = 0xFF34C759),
        LockNotification("s2", "Calendar", "Team sync", "Starts in 15 minutes", now - 18 * 60_000, tint = 0xFFFF6B6B),
        LockNotification("s3", "Mail", "Your order has shipped", "Arriving Friday", now - 3 * 3_600_000, tint = 0xFF8AB4FF),
        LockNotification("s4", "Discord", "Odin crew", "Who's up for a match?", now - 5 * 3_600_000, tint = 0xFF8C9BFF),
    )
}
