package com.mariotatis.lockcontrol.media

import android.app.Notification
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import kotlin.math.max

/**
 * Notification access gives us two things: the posted notifications, and the
 * active media sessions of every app (YouTube, Spotify, YouTube Music...) with
 * their metadata and transport controls.
 */
class LockNotificationListener : NotificationListenerService() {
    private companion object {
        const val HIDE_DELAY_MS = 4_000L
    }

    private val main = Handler(Looper.getMainLooper())
    private var sessionManager: MediaSessionManager? = null
    private val callbacks = mutableMapOf<MediaController, MediaController.Callback>()
    private val appCache = mutableMapOf<String, Pair<String, ImageBitmap?>>()
    private var artKey: String? = null
    private var artCache: ImageBitmap? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        watch(controllers.orEmpty())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val manager = getSystemService(MediaSessionManager::class.java)
        sessionManager = manager
        val component = LiveData.listenerComponent(this)
        runCatching {
            manager.addOnActiveSessionsChangedListener(sessionsListener, component, main)
            watch(manager.getActiveSessions(component))
        }
        LiveData.clearAction = {
            val keys = LiveData.notifications.value.map { it.key }.toTypedArray()
            if (keys.isNotEmpty()) runCatching { cancelNotifications(keys) }
        }
        refreshNotifications()
    }

    override fun onListenerDisconnected() {
        main.removeCallbacks(hideMedia)
        runCatching { sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        unwatchAll()
        LiveData.clear()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refreshNotifications()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refreshNotifications()

    // ---- Media ----

    private fun watch(controllers: List<MediaController>) {
        unwatchAll()
        controllers.forEach { controller ->
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = publishMedia()
                override fun onMetadataChanged(metadata: MediaMetadata?) = publishMedia()
                override fun onSessionDestroyed() = publishMedia()
            }
            controller.registerCallback(callback, main)
            callbacks[controller] = callback
        }
        publishMedia()
    }

    private fun unwatchAll() {
        callbacks.forEach { (controller, callback) -> runCatching { controller.unregisterCallback(callback) } }
        callbacks.clear()
    }

    /** Transitional states (skipping, buffering, connecting) count as playing so the widget doesn't blink. */
    private fun PlaybackState?.isActive() = this?.state in setOf(
        PlaybackState.STATE_PLAYING,
        PlaybackState.STATE_BUFFERING,
        PlaybackState.STATE_CONNECTING,
        PlaybackState.STATE_SKIPPING_TO_NEXT,
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM,
        PlaybackState.STATE_FAST_FORWARDING,
        PlaybackState.STATE_REWINDING,
    )

    /** Hides the widget only after nothing has been playing for a moment, surviving quick skips. */
    private val hideMedia = Runnable { LiveData.publishMedia(null, MediaActions.None) }

    /** Prefer whatever is playing; otherwise the most recent paused session that has a title. */
    private fun publishMedia() {
        val controllers = callbacks.keys.toList()
        val chosen = controllers.firstOrNull { it.playbackState.isActive() }
            ?: controllers.firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PAUSED &&
                    !it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank()
            }
        main.removeCallbacks(hideMedia)
        if (chosen == null) {
            main.postDelayed(hideMedia, HIDE_DELAY_MS)
            return
        }
        val meta = chosen.metadata
        val state = chosen.playbackState
        val (appName, appIcon) = appInfo(chosen.packageName)
        // Mid-skip, apps often clear the metadata for a moment; keep showing the last song until the new one lands.
        val previous = LiveData.nowPlaying.value
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty()
        if (title.isBlank() && previous != null && previous.appName == appName) {
            LiveData.publishMedia(previous.copy(playing = state.isActive()), LiveData.actions)
            return
        }
        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE).orEmpty()
        val album = meta?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
        val playing = state.isActive()
        val nowPlaying = NowPlaying(
            title = title.ifBlank { appName },
            artist = artist,
            album = album,
            art = art(meta, "${chosen.packageName}|$title|$album"),
            appName = appName,
            appIcon = appIcon,
            playing = playing,
            durationMs = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0) ?: 0,
            positionMs = state?.position?.coerceAtLeast(0) ?: 0,
            positionTime = state?.lastPositionUpdateTime ?: 0,
            speed = state?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            canSeek = (state?.actions ?: 0L) and PlaybackState.ACTION_SEEK_TO != 0L,
        )
        val controls = chosen.transportControls
        LiveData.publishMedia(nowPlaying, object : MediaActions {
            override fun playPause() {
                if (chosen.playbackState.isActive()) controls.pause() else controls.play()
            }
            override fun next() = controls.skipToNext()
            override fun previous() = controls.skipToPrevious()
            override fun seekTo(positionMs: Long) = controls.seekTo(positionMs)
        })
    }

    private fun art(meta: MediaMetadata?, key: String): ImageBitmap? {
        if (meta == null) return null
        if (key == artKey) return artCache
        val bitmap = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: loadUri(
                meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                    ?: meta.getString(MediaMetadata.METADATA_KEY_ART_URI),
            )
        artKey = key
        artCache = bitmap?.let { downscale(it, 384).asImageBitmap() }
        return artCache
    }

    /** Only local content:// URIs; we never touch the network. */
    private fun loadUri(uri: String?): Bitmap? {
        if (uri == null || !uri.startsWith("content://")) return null
        return runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, Uri.parse(uri))) { d, _, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }.getOrNull()
    }

    private fun downscale(bitmap: Bitmap, max: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= max) return bitmap
        val f = max.toFloat() / longest
        return bitmap.scale((bitmap.width * f).toInt().coerceAtLeast(1), (bitmap.height * f).toInt().coerceAtLeast(1))
    }

    private fun appInfo(pkg: String): Pair<String, ImageBitmap?> = appCache.getOrPut(pkg) {
        runCatching {
            val info = packageManager.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
            val name = packageManager.getApplicationLabel(info).toString()
            val icon = packageManager.getApplicationIcon(info).toBitmap(96, 96).asImageBitmap()
            name to icon
        }.getOrElse { pkg.substringAfterLast('.') to null }
    }

    // ---- Notifications ----

    private fun refreshNotifications() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val ranking = Ranking()
        val rankingMap = runCatching { currentRanking }.getOrNull()
        val list = active.asSequence()
            .filter { it.packageName != packageName && it.isClearable }
            .filter { sbn ->
                val n = sbn.notification
                n.flags and Notification.FLAG_GROUP_SUMMARY == 0 &&
                    n.category != Notification.CATEGORY_TRANSPORT &&
                    !n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION) &&
                    // Respect apps that ask to never show content on a lock screen.
                    n.visibility != Notification.VISIBILITY_SECRET
            }
            .filter { sbn ->
                // Skip silent / minimized notifications, like the system lock screen does.
                rankingMap == null || !rankingMap.getRanking(sbn.key, ranking) ||
                    ranking.importance >= NotificationManager.IMPORTANCE_DEFAULT
            }
            .mapNotNull(::toLockNotification)
            .sortedByDescending { it.postTime }
            .take(20)
            .toList()
        LiveData.publishNotifications(list)
    }

    private fun toLockNotification(sbn: StatusBarNotification): LockNotification? {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_TEXT) ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
            ?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        val (appName, icon) = appInfo(sbn.packageName)
        return LockNotification(sbn.key, appName, title, text, sbn.postTime, icon)
    }
}
