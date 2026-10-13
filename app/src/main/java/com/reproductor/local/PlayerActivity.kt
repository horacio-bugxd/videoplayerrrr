package com.reproductor.local

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconToggleButton
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
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
        val title = queryTitle(uri)

        val exo = ExoPlayer.Builder(this).build().also {
            // Saltar al fotograma clave más cercano: adelantar/atrasar es mucho más rápido en vídeos largos
            it.setSeekParameters(SeekParameters.CLOSEST_SYNC)
            it.setMediaItem(MediaItem.fromUri(uri))
            it.prepare()
            it.playWhenReady = true
        }
        exoPlayer = exo

        setContent {
            // Siempre tema oscuro: los controles van encima del vídeo
            AppTheme(darkTheme = true) {
                PlayerScreen(exo = exo, title = title, onBack = { finish() })
            }
        }
    }

    /** Lee el nombre del archivo desde MediaStore. */
    private fun queryTitle(uri: Uri): String {
        val name = try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
        return name ?: "Vídeo"
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
private fun PlayerScreen(exo: ExoPlayer, title: String, onBack: () -> Unit) {
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
    var speed by remember { mutableFloatStateOf(1f) }
    var repeatOn by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var brightness by remember {
        mutableFloatStateOf(activity.window.attributes.screenBrightness.let { if (it < 0f) 0.5f else it })
    }
    var volume by remember {
        mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat())
    }
    // true mientras hay 2 dedos en pantalla (pellizco), para ignorar los deslizamientos
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

    fun cycleFit() {
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
                            if (!locked) {
                                val third = size.width / 3f
                                when {
                                    offset.x < third -> seekBy(-10_000L)
                                    offset.x > 2 * third -> seekBy(10_000L)
                                    else -> togglePlay()
                                }
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
                            if (!multiTouch[0] && !locked) {
                                exo.seekTo(target)
                                position = target
                            }
                        },
                        onHorizontalDrag = { change, dx ->
                            if (!multiTouch[0] && !locked) {
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
                            if (!multiTouch[0] && !locked) {
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
                // Pellizco: separar dedos = zoom (rellenar), juntar = ajustar
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
                        if (!locked) {
                            if (zoomTotal > 1.15f) {
                                fitMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                hud = "Zoom"
                            } else if (zoomTotal < 0.87f) {
                                fitMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                hud = "Ajustar"
                            }
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

        // 4) Controles (o solo el botón de desbloqueo si está bloqueado)
        AnimatedVisibility(visible = controlsVisible, modifier = Modifier.fillMaxSize()) {
            if (locked) {
                Box(Modifier.fillMaxSize()) {
                    FilledTonalIconButton(
                        onClick = {
                            locked = false
                            hud = "Desbloqueado"
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(start = 24.dp)
                            .size(56.dp),
                        shape = CircleShape,
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = "Desbloquear")
                    }
                }
            } else {
                PlayerControls(
                    title = title,
                    isPlaying = isPlaying,
                    position = position,
                    duration = duration,
                    speed = speed,
                    repeatOn = repeatOn,
                    fitChanged = fitMode != AspectRatioFrameLayout.RESIZE_MODE_FIT,
                    onBack = onBack,
                    onSeekBy = { seekBy(it) },
                    onTogglePlay = { togglePlay() },
                    onScrub = {
                        seeking = true
                        position = it
                    },
                    onScrubFinished = {
                        exo.seekTo(position)
                        seeking = false
                    },
                    onSpeedSelected = {
                        speed = it
                        exo.setPlaybackSpeed(it)
                        hud = "Velocidad ${it}x"
                    },
                    onCycleFit = { cycleFit() },
                    onToggleRepeat = {
                        repeatOn = !repeatOn
                        exo.repeatMode = if (repeatOn) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                        hud = if (repeatOn) "Repetir: activado" else "Repetir: desactivado"
                    },
                    onLock = {
                        locked = true
                        hud = "Bloqueado"
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private val GroupStart = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 8.dp, bottomEnd = 8.dp)
private val GroupMiddle = RoundedCornerShape(8.dp)
private val GroupEnd = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 24.dp, bottomEnd = 24.dp)

/** Controles con el estilo de la pantalla "Reproduciendo ahora" de Metrolist, adaptados a horizontal. */
@Composable
private fun PlayerControls(
    title: String,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    speed: Float,
    repeatOn: Boolean,
    fitChanged: Boolean,
    onBack: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onScrub: (Long) -> Unit,
    onScrubFinished: () -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onCycleFit: () -> Unit,
    onToggleRepeat: () -> Unit,
    onLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        // Degradados para que el texto se lea sobre el vídeo
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(120.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))))
        )

        // Arriba: botón atrás + "Reproduciendo ahora"
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            FilledTonalIconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart).size(48.dp),
                shape = CircleShape,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Atrás")
            }
            Text(
                "Reproduciendo ahora",
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        // Abajo: título, progreso, botones y grupo de opciones
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 32.dp, end = 32.dp, bottom = 20.dp)
        ) {
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            val maxPos = duration.coerceAtLeast(1L).toFloat()
            Slider(
                value = position.toFloat().coerceIn(0f, maxPos),
                onValueChange = { onScrub(it.toLong()) },
                onValueChangeFinished = onScrubFinished,
                valueRange = 0f..maxPos,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(formatDuration(position), color = Color.White, style = MaterialTheme.typography.labelLarge)
                Text(formatDuration(duration), color = Color.White, style = MaterialTheme.typography.labelLarge)
            }

            Spacer(Modifier.height(8.dp))

            // Botones principales: -10 s, Reproducir/Pausar (píldora), +10 s
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = { onSeekBy(-10_000L) },
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                ) {
                    Icon(Icons.Rounded.Replay10, contentDescription = "Atrasar 10 segundos", modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = onTogglePlay,
                    modifier = Modifier.height(64.dp).widthIn(min = 220.dp),
                ) {
                    Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isPlaying) "Pausar" else "Reproducir", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.width(12.dp))
                FilledTonalIconButton(
                    onClick = { onSeekBy(10_000L) },
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                ) {
                    Icon(Icons.Rounded.Forward10, contentDescription = "Adelantar 10 segundos", modifier = Modifier.size(28.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            // Grupo de botones: velocidad, ajustar pantalla, repetir, bloquear
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                var speedMenu by remember { mutableStateOf(false) }
                Box {
                    GroupButton(checked = speed != 1f, shape = GroupStart, onClick = { speedMenu = true }) {
                        Icon(Icons.Rounded.Speed, contentDescription = "Velocidad")
                    }
                    DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                            DropdownMenuItem(
                                text = { Text(if (s == 1f) "Normal" else "${s}x") },
                                onClick = {
                                    onSpeedSelected(s)
                                    speedMenu = false
                                },
                            )
                        }
                    }
                }
                GroupButton(checked = fitChanged, shape = GroupMiddle, onClick = onCycleFit) {
                    Icon(Icons.Rounded.AspectRatio, contentDescription = "Ajustar pantalla")
                }
                GroupButton(checked = repeatOn, shape = GroupMiddle, onClick = onToggleRepeat) {
                    Icon(Icons.Rounded.Repeat, contentDescription = "Repetir")
                }
                GroupButton(checked = false, shape = GroupEnd, onClick = onLock) {
                    Icon(Icons.Rounded.Lock, contentDescription = "Bloquear controles")
                }
            }
        }
    }
}

@Composable
private fun GroupButton(
    checked: Boolean,
    shape: Shape,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    OutlinedIconToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        modifier = Modifier.size(width = 56.dp, height = 48.dp),
        shape = shape,
    ) {
        content()
    }
}
