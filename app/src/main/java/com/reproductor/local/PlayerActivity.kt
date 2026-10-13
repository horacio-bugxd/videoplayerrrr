package com.reproductor.local

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.reproductor.local.ui.theme.AppTheme
import com.reproductor.local.util.formatDuration
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Pantalla de reproducción: siempre horizontal, a pantalla completa y con controles propios. */
class PlayerActivity : ComponentActivity() {

    private var exoPlayer: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        val uri = intent.data
        if (uri == null) {
            finish()
            return
        }

        val exo = ExoPlayer.Builder(this).build().also {
            // Saltar al fotograma clave más cercano: hace que adelantar/atrasar sea mucho más rápido en vídeos largos
            it.setSeekParameters(SeekParameters.CLOSEST_SYNC)
            it.setMediaItem(MediaItem.fromUri(uri))
            it.prepare()
            it.playWhenReady = true
        }
        exoPlayer = exo

        setContent {
            AppTheme {
                PlayerScreen(exo = exo, onBack = { finish() })
            }
        }
    }

    override fun onStop() {
        super.onStop()
        exoPlayer?.pause()
    }

    override fun onDestroy() {
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroy()
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun PlayerScreen(exo: ExoPlayer, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    var isPlaying by remember { mutableStateOf(exo.isPlaying) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var seeking by remember { mutableStateOf(false) }
    var fitMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var hud by remember { mutableStateOf<String?>(null) }
    var brightness by remember {
        mutableFloatStateOf(activity.window.attributes.screenBrightness.let { if (it < 0f) 0.5f else it })
    }
    var volume by remember {
        mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat())
    }
    // Se pone en true mientras hay 2 dedos en pantalla (pellizco), para ignorar los deslizamientos
    val multiTouch = remember { BooleanArray(1) }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exo.addListener(listener)
        onDispose { exo.removeListener(listener) }
    }

    // Actualiza posición y duración 4 veces por segundo
    LaunchedEffect(exo) {
        while (true) {
            if (!seeking) {
                position = exo.currentPosition
                duration = exo.duration.coerceAtLeast(0L)
            }
            delay(250)
        }
    }

    // Oculta los controles a los 3,5 s si se está reproduciendo
    LaunchedEffect(controlsVisible, isPlaying, seeking) {
        if (controlsVisible && isPlaying && !seeking) {
            delay(3500)
            controlsVisible = false
        }
    }

    // Borra el aviso central (brillo, volumen...) tras un momento
    LaunchedEffect(hud) {
        if (hud != null) {
            delay(900)
            hud = null
        }
    }

    fun seekBy(deltaMs: Long) {
        val total = exo.duration.coerceAtLeast(0L)
        val target = (exo.currentPosition + deltaMs).coerceIn(0L, total)
        exo.seekTo(target)
        position = target
        hud = if (deltaMs > 0) "+${deltaMs / 1000} s" else "−${-deltaMs / 1000} s"
    }

    fun togglePlay() {
        if (exo.isPlaying) {
            exo.pause()
        } else {
            if (exo.playbackState == Player.STATE_ENDED) exo.seekTo(0)
            exo.play()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        // 1) El vídeo
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = exo
                    useController = false // usamos nuestros propios controles
                }
            },
            update = { it.resizeMode = fitMode },
        )

        // 2) Capa de gestos
        Box(
            Modifier
                .fillMaxSize()
                // Toque: mostrar/ocultar controles. Doble toque: izquierda -10 s, derecha +10 s, centro pausa
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { offset ->
                            val third = size.width / 3f
                            when {
                                offset.x < third -> seekBy(-10_000L)
                                offset.x > 2 * third -> seekBy(10_000L)
                                else -> togglePlay()
                            }
                        },
                    )
                }
                // Deslizar en horizontal: adelantar / atrasar (el ancho completo = 90 s)
                .pointerInput(Unit) {
                    var startPos = 0L
                    var totalDx = 0f
                    var target = 0L
                    detectHorizontalDragGestures(
                        onDragStart = {
                            startPos = exo.currentPosition
                            totalDx = 0f
                            target = startPos
                        },
                        onDragEnd = {
                            if (!multiTouch[0]) {
                                exo.seekTo(target)
                                position = target
                            }
                        },
                        onHorizontalDrag = { change, dx ->
                            if (!multiTouch[0]) {
                                change.consume()
                                totalDx += dx
                                val total = exo.duration.coerceAtLeast(0L)
                                target = (startPos + (totalDx / size.width * 90_000f).toLong())
                                    .coerceIn(0L, total)
                                hud = "${formatDuration(target)} / ${formatDuration(total)}"
                            }
                        },
                    )
                }
                // Deslizar en vertical: izquierda = brillo, derecha = volumen
                .pointerInput(Unit) {
                    var leftSide = true
                    detectVerticalDragGestures(
                        onDragStart = { offset -> leftSide = offset.x < size.width / 2f },
                        onVerticalDrag = { change, dy ->
                            if (!multiTouch[0]) {
                                change.consume()
                                val fraction = -dy / size.height * 1.3f
                                if (leftSide) {
                                    brightness = (brightness + fraction).coerceIn(0.02f, 1f)
                                    val lp = activity.window.attributes
                                    lp.screenBrightness = brightness
                                    activity.window.attributes = lp
                                    hud = "Brillo ${(brightness * 100).roundToInt()}%"
                                } else {
                                    volume = (volume + fraction * maxVolume).coerceIn(0f, maxVolume.toFloat())
                                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume.roundToInt(), 0)
                                    hud = "Volumen ${(volume / maxVolume * 100).roundToInt()}%"
                                }
                            }
                        },
                    )
                }
                // Pellizco: separar dedos = rellenar pantalla (zoom), juntar = ajustar
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var zoomTotal = 1f
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.size > 1) {
                                multiTouch[0] = true
                                zoomTotal *= event.calculateZoom()
                            }
                        } while (event.changes.any { it.pressed })
                        multiTouch[0] = false
                        if (zoomTotal > 1.15f) {
                            fitMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            hud = "Zoom"
                        } else if (zoomTotal < 0.87f) {
                            fitMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            hud = "Ajustar"
                        }
                    }
                }
        )

        // 3) Aviso central (brillo, volumen, salto...)
        hud?.let { text ->
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.align(Alignment.Center),
            ) {
                Text(
                    text,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }

        // 4) Controles
        AnimatedVisibility(visible = controlsVisible, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {

                // Barra superior
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {
                        fitMode = when (fitMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        hud = when (fitMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Estirar"
                            else -> "Ajustar"
                        }
                    }) {
                        Icon(Icons.Rounded.AspectRatio, contentDescription = "Ajustar pantalla", tint = Color.White)
                    }
                }

                // Centro: -10 s, pausa/reproducir, +10 s
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { seekBy(-10_000L) }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Rounded.Replay10, contentDescription = "Atrasar 10 segundos", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    FilledIconButton(onClick = { togglePlay() }, modifier = Modifier.size(72.dp)) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    IconButton(onClick = { seekBy(10_000L) }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Rounded.Forward10, contentDescription = "Adelantar 10 segundos", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }

                // Barra inferior con el progreso (con margen para no chocar con el gesto de Android)
                val maxPos = duration.coerceAtLeast(1L).toFloat()
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(formatDuration(position), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = position.toFloat().coerceIn(0f, maxPos),
                        onValueChange = {
                            seeking = true
                            position = it.toLong()
                        },
                        onValueChangeFinished = {
                            exo.seekTo(position)
                            seeking = false
                        },
                        valueRange = 0f..maxPos,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    Text(formatDuration(duration), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
