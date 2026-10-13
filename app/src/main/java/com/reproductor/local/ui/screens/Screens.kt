package com.reproductor.local.ui.screens

import android.content.Intent
import android.net.Uri
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reproductor.local.PlayerActivity
import com.reproductor.local.data.VideoItem
import com.reproductor.local.util.formatDuration
import com.reproductor.local.util.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------- Pantalla: Videos (estilo "Inicio" de Metrolist) ----------

private enum class VideoFilter(val label: String) {
    ALL("Todos"),
    RECENT("Recientes"),
    SHORT("Cortos"),
    LONG("Largos"),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VideosScreen(
    videos: List<VideoItem>,
    loading: Boolean,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onOpen: (VideoItem) -> Unit,
) {
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    var filter by remember { mutableStateOf(VideoFilter.ALL) }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Videos", style = MaterialTheme.typography.headlineMedium) },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                !hasPermission -> PermissionPrompt(onRequestPermission)
                loading -> LoadingIndicator(Modifier.align(Alignment.Center))
                videos.isEmpty() -> Text(
                    "No se encontraron vídeos",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge,
                )
                else -> HomeContent(videos, filter, { filter = it }, onOpen)
            }
        }
    }
}

@Composable
private fun HomeContent(
    videos: List<VideoItem>,
    filter: VideoFilter,
    onFilter: (VideoFilter) -> Unit,
    onOpen: (VideoItem) -> Unit,
) {
    val filtered = when (filter) {
        VideoFilter.ALL -> videos
        VideoFilter.RECENT -> videos.take(30)
        VideoFilter.SHORT -> videos.filter { it.durationMs < 5 * 60_000L }
        VideoFilter.LONG -> videos.filter { it.durationMs > 30 * 60_000L }
    }
    // Cuadrícula de 3x3 por página (hasta 3 páginas)
    val pages = remember(filtered) { filtered.take(27).chunked(9) }
    val pagerState = rememberPagerState(pageCount = { pages.size })

    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        // Chips de filtro
        item(key = "chips") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(VideoFilter.entries.toList()) { f ->
                    FilterChip(
                        selected = f == filter,
                        onClick = { onFilter(f) },
                        label = { Text(f.label) },
                        shape = CircleShape,
                    )
                }
            }
        }

        // Cuadrícula de tarjetas
        if (pages.isNotEmpty()) {
            item(key = "grid_title") {
                SectionTitle(if (filter == VideoFilter.ALL) "Añadidos recientemente" else filter.label)
            }
            item(key = "grid") {
                Column {
                    HorizontalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        pageSpacing = 12.dp,
                    ) { pageIndex ->
                        val page = pages[pageIndex]
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(3) { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    repeat(3) { col ->
                                        val video = page.getOrNull(row * 3 + col)
                                        if (video != null) {
                                            VideoTile(video, Modifier.weight(1f)) { onOpen(video) }
                                        } else {
                                            Spacer(Modifier.weight(1f).aspectRatio(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Puntos indicadores de página
                    if (pages.size > 1) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            repeat(pages.size) { i ->
                                Box(
                                    Modifier
                                        .padding(horizontal = 4.dp)
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (i == pagerState.currentPage) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Lista completa
        item(key = "all_title") { SectionTitle("Todos los vídeos") }
        if (filtered.isEmpty()) {
            item(key = "empty") {
                Text(
                    "No hay vídeos con este filtro",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        items(filtered, key = { it.id }) { video ->
            Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                VideoRow(video, onClick = { onOpen(video) })
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

/** Tarjeta cuadrada con la miniatura y el título encima, como las de Metrolist. */
@Composable
private fun VideoTile(video: VideoItem, modifier: Modifier, onClick: () -> Unit) {
    val thumb = rememberThumbnail(video.uri)
    Box(
        modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
    ) {
        if (thumb != null) {
            Image(thumb, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.align(Alignment.Center))
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
        )
        Text(
            video.title,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 8.dp, vertical = 8.dp),
        )
    }
}

/** Lista de vídeos reutilizable (la usa el interior de cada carpeta). */
@Composable
private fun VideoList(videos: List<VideoItem>, onOpen: (VideoItem) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(videos, key = { it.id }) { video ->
            VideoRow(video, onClick = { onOpen(video) })
        }
    }
}

@Composable
private fun VideoRow(video: VideoItem, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VideoThumbnail(video.uri, video.durationMs)
            Column(Modifier.weight(1f)) {
                Text(
                    video.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${video.folder} · ${formatSize(video.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Carga la miniatura en segundo plano para no bloquear la pantalla. */
@Composable
private fun rememberThumbnail(uri: Uri): ImageBitmap? {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = uri) {
        value = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.loadThumbnail(uri, Size(320, 320), null).asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    return bitmap
}

@Composable
private fun VideoThumbnail(uri: Uri, durationMs: Long) {
    val img = rememberThumbnail(uri)
    Box(
        Modifier
            .size(width = 120.dp, height = 68.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        if (img != null) {
            Image(img, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.align(Alignment.Center))
        }
        Surface(
            color = Color.Black.copy(alpha = 0.7f),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
        ) {
            Text(
                formatDuration(durationMs),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

// ---------- Pantalla: Carpetas ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(
    videos: List<VideoItem>,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
) {
    val context = LocalContext.current
    // Carpeta abierta ahora mismo (null = se ve la lista de carpetas)
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val current = selected

    val openVideo = { video: VideoItem ->
        context.startActivity(Intent(context, PlayerActivity::class.java).setData(video.uri))
    }

    // El botón "atrás" del móvil vuelve a la lista de carpetas
    BackHandler(enabled = current != null) { selected = null }

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val folders = videos.groupBy { it.folder }.toList().sortedBy { it.first.lowercase() }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(current ?: "Carpetas", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (current != null) {
                        IconButton(onClick = { selected = null }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                        }
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                !hasPermission -> PermissionPrompt(onRequestPermission)
                current != null -> VideoList(videos.filter { it.folder == current }, openVideo)
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(folders, key = { it.first }) { (name, list) ->
                        ListItem(
                            headlineContent = { Text(name) },
                            supportingContent = { Text("${list.size} vídeos") },
                            leadingContent = { Icon(Icons.Rounded.Folder, contentDescription = null) },
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.large)
                                .clickable { selected = name },
                        )
                    }
                }
            }
        }
    }
}

// ---------- Pantalla: Ajustes ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text("Ajustes") }, scrollBehavior = scroll) },
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp)) {
            ListItem(
                headlineContent = { Text("Acerca de") },
                supportingContent = { Text("Reproductor de vídeo local · código abierto (GPL-3.0)") },
                leadingContent = { Icon(Icons.Rounded.Info, contentDescription = null) },
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.clip(MaterialTheme.shapes.large),
            )
        }
    }
}

// ---------- Aviso de permiso ----------

@Composable
private fun PermissionPrompt(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.VideoLibrary, contentDescription = null, modifier = Modifier.size(48.dp))
        Text(
            "Necesito acceso a tus vídeos para mostrarlos",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        Button(onClick = onRequest) { Text("Dar permiso") }
    }
}
