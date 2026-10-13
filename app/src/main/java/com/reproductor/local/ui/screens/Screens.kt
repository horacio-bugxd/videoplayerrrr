package com.reproductor.local.ui.screens

import android.net.Uri
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedScroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reproductor.local.data.VideoItem
import com.reproductor.local.util.formatDuration
import com.reproductor.local.util.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------- Pantalla: Videos ----------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VideosScreen(
    videos: List<VideoItem>,
    loading: Boolean,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onOpen: (VideoItem) -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text("Videos") }, scrollBehavior = scroll) },
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
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(videos, key = { it.id }) { video ->
                        VideoRow(video, onClick = { onOpen(video) })
                    }
                }
            }
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

@Composable
private fun VideoThumbnail(uri: Uri, durationMs: Long) {
    val context = LocalContext.current
    // Carga la miniatura en segundo plano para no bloquear la pantalla
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = uri) {
        value = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.loadThumbnail(uri, Size(320, 180), null).asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    Box(
        Modifier
            .size(width = 120.dp, height = 68.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        val img = bitmap
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
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val folders = videos.groupBy { it.folder }.toList().sortedBy { it.first.lowercase() }
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text("Carpetas") }, scrollBehavior = scroll) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (!hasPermission) {
                PermissionPrompt(onRequestPermission)
            } else {
                LazyColumn(
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
                            modifier = Modifier.clip(MaterialTheme.shapes.large),
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
