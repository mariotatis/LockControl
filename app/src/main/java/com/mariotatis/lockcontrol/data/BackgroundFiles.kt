package com.mariotatis.lockcontrol.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * Picked media is copied into private storage so the lock screen never depends
 * on a content URI permission that can be revoked or a file that gets moved.
 */
object BackgroundFiles {
    private const val DIR = "backgrounds"

    suspend fun import(context: Context, uri: Uri, type: BackgroundType): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val suffix = if (type == BackgroundType.VIDEO) "video" else "image"
        val target = File(dir, "bg_${System.currentTimeMillis()}.$suffix")
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open $uri")
        input.use { src -> target.outputStream().use { src.copyTo(it, bufferSize = 1 shl 16) } }
        target.absolutePath
    }

    /** Deletes every stored background except [keep]. */
    fun cleanup(context: Context, keep: String?) {
        File(context.filesDir, DIR).listFiles()?.forEach { if (it.absolutePath != keep) it.delete() }
    }

    private var cachedPath: String? = null
    private var cachedBitmap: Bitmap? = null

    /** Decodes (and caches) a wallpaper downsampled to roughly [maxDimension] px. */
    suspend fun loadBitmap(path: String, maxDimension: Int = 2400): Bitmap? = withContext(Dispatchers.IO) {
        synchronized(this@BackgroundFiles) {
            if (cachedPath == path) cachedBitmap?.let { return@withContext it }
        }
        val file = File(path)
        if (!file.exists()) return@withContext null
        val bitmap = runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                var sample = 1
                while (longest / (sample * 2) >= maxDimension) sample *= 2
                decoder.setTargetSampleSize(sample)
            }
        }.getOrNull()
        synchronized(this@BackgroundFiles) {
            cachedPath = path
            cachedBitmap = bitmap
        }
        bitmap
    }
}
