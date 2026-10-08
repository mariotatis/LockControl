package com.mariotatis.lockcontrol.ui.lock

import android.content.Context
import android.graphics.Matrix
import android.net.Uri
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.mariotatis.lockcontrol.data.BackgroundFiles
import com.mariotatis.lockcontrol.data.BackgroundType
import com.mariotatis.lockcontrol.data.LockConfig
import com.mariotatis.lockcontrol.data.WidgetLimits
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import java.io.File
import kotlin.math.max

data class GradientPreset(val name: String, val colors: List<Color>) {
    val brush: Brush get() = Brush.linearGradient(colors, start = Offset.Zero, end = Offset.Infinite)
}

val GradientPresets = listOf(
    GradientPreset("Aurora", listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))),
    GradientPreset("Dusk", listOf(Color(0xFF2B1055), Color(0xFF7597DE))),
    GradientPreset("Sunset", listOf(Color(0xFF355C7D), Color(0xFF6C5B7B), Color(0xFFC06C84))),
    GradientPreset("Peach", listOf(Color(0xFFFF7E5F), Color(0xFFFEB47B))),
    GradientPreset("Ocean", listOf(Color(0xFF1A2980), Color(0xFF26D0CE))),
    GradientPreset("Forest", listOf(Color(0xFF134E5E), Color(0xFF71B280))),
    GradientPreset("Plum", listOf(Color(0xFF42275A), Color(0xFF734B6D))),
    GradientPreset("Midnight", listOf(Color(0xFF07070C), Color(0xFF1F2340))),
)

/** Keeps a zoomed background covering the whole screen: the shift can't exceed the zoom slack. */
fun clampBackgroundOffset(scale: Float, x: Float, y: Float): Pair<Float, Float> {
    val slack = (scale - 1f) / 2f
    return x.coerceIn(-slack, slack) to y.coerceIn(-slack, slack)
}

fun gradientAt(index: Int): GradientPreset = GradientPresets[index.coerceIn(GradientPresets.indices)]

/**
 * The wallpaper layer. [blur] is in dp and [dim] is the black scrim alpha; both
 * are animated by the lock screen when the passcode pad opens.
 */
@Composable
fun LockBackground(
    config: LockConfig,
    playing: Boolean,
    modifier: Modifier = Modifier,
    blur: Float = 0f,
    dim: Float = config.dim,
) {
    Box(modifier.fillMaxSize()) {
        val blurModifier = if (blur > 0.5f) Modifier.blur(blur.dp, BlurredEdgeTreatment.Rectangle) else Modifier
        Box(Modifier.fillMaxSize().then(blurModifier)) {
            val file = config.backgroundFile?.takeIf { File(it).exists() }
            val media = file != null && config.backgroundType != BackgroundType.GRADIENT
            // Pinch-zoom framing for photos and videos: scale about the center, then shift.
            val framing = if (media) {
                Modifier.clipToBounds().graphicsLayer {
                    val s = config.bgScale.coerceIn(WidgetLimits.bgScale)
                    scaleX = s
                    scaleY = s
                    translationX = config.bgOffsetX * size.width
                    translationY = config.bgOffsetY * size.height
                }
            } else Modifier
            Box(Modifier.fillMaxSize().then(framing)) {
                when {
                    config.backgroundType == BackgroundType.IMAGE && file != null -> ImageBackground(file)
                    config.backgroundType == BackgroundType.VIDEO && file != null -> key(file) { VideoBackground(file, playing) }
                    else -> Box(Modifier.fillMaxSize().background(gradientAt(config.gradientIndex).brush))
                }
            }
        }
        if (dim > 0.001f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim.coerceIn(0f, 1f))))
    }
}

@Composable
private fun ImageBackground(path: String) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, path) { value = BackgroundFiles.loadBitmap(path) }
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    if (image != null) {
        Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    } else {
        Box(Modifier.fillMaxSize().background(Color.Black))
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoBackground(path: String, playing: Boolean) {
    val context = LocalContext.current
    val player = remember(path) { createSilentLoopingPlayer(context, path) }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(player, playing) { player.playWhenReady = playing }

    AndroidView(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        factory = { ctx ->
            CropTextureView(ctx).also { view ->
                player.setVideoTextureView(view)
                player.addListener(object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        view.setVideoSize(videoSize.width * videoSize.pixelWidthHeightRatio, videoSize.height.toFloat())
                    }
                })
            }
        },
    )
}

@OptIn(UnstableApi::class)
private fun createSilentLoopingPlayer(context: Context, path: String): ExoPlayer =
    ExoPlayer.Builder(context).build().apply {
        // Drop the audio track entirely: no sound, no audio focus requests.
        trackSelectionParameters = trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            .build()
        volume = 0f
        repeatMode = Player.REPEAT_MODE_ONE
        setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
        prepare()
    }

/** TextureView that center-crops the video (TextureView stretches by default). */
private class CropTextureView(context: Context) : TextureView(context) {
    private var videoWidth = 0f
    private var videoHeight = 0f

    fun setVideoSize(width: Float, height: Float) {
        videoWidth = width
        videoHeight = height
        updateTransform()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateTransform()
    }

    private fun updateTransform() {
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        if (viewW <= 0f || viewH <= 0f || videoWidth <= 0f || videoHeight <= 0f) return
        val scale = max(viewW / videoWidth, viewH / videoHeight)
        setTransform(Matrix().apply {
            setScale(videoWidth * scale / viewW, videoHeight * scale / viewH, viewW / 2f, viewH / 2f)
        })
    }
}
