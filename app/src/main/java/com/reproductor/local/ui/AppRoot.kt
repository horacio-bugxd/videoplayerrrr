package com.reproductor.local.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.reproductor.local.PlayerActivity
import com.reproductor.local.data.VideoItem
import com.reproductor.local.data.VideoRepository
import com.reproductor.local.ui.screens.FoldersScreen
import com.reproductor.local.ui.screens.SettingsScreen
import com.reproductor.local.ui.screens.VideosScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Videos("videos", "Videos", Icons.Rounded.VideoLibrary),
    Folders("folders", "Carpetas", Icons.Rounded.Folder),
    Settings("settings", "Ajustes", Icons.Rounded.Settings),
}

/** Estructura principal: barra de navegación inferior + las 3 pantallas. */
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val navController = rememberNavController()

    // Permiso para leer los vídeos del dispositivo
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    // Lista de vídeos: se carga cuando hay permiso
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            loading = true
            videos = VideoRepository.load(context)
            loading = false
        }
    }

    val requestPermission = { permissionLauncher.launch(Manifest.permission.READ_MEDIA_VIDEO) }
    val openVideo = { video: VideoItem ->
        context.startActivity(Intent(context, PlayerActivity::class.java).setData(video.uri))
    }

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Videos.route,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Tab.Videos.route) {
                VideosScreen(videos, loading, hasPermission, requestPermission, openVideo)
            }
            composable(Tab.Folders.route) {
                FoldersScreen(videos, hasPermission, requestPermission)
            }
            composable(Tab.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
