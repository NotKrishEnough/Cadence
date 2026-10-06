package com.notkrishenough.cadence.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import com.notkrishenough.cadence.model.Track
import com.notkrishenough.cadence.player.CadenceMediaController

@Composable
fun CadenceRoot(vm: CadenceViewModel = viewModel()) {
    val tracks by vm.tracks.collectAsState()
    val query by vm.query.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var current by remember { mutableStateOf<Track?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        val future = CadenceMediaController.connect(context)
        val listener = Runnable {
            runCatching { controller = future.get() }
        }
        future.addListener(listener, androidx.core.content.ContextCompat.getMainExecutor(context))
        onDispose { if (future.isDone) runCatching { future.get().release() } }
    }

    LaunchedEffect(Unit) { vm.refresh() }

    Box(Modifier.fillMaxSize()) {
        when (tab) {
            0 -> LibraryHome(tracks, query, vm::setQuery) { track ->
                current = track
                playTrack(controller, track, tracks)
            }
            1 -> AlbumsScreen(tracks) { track ->
                current = track
                playTrack(controller, track, tracks)
            }
            else -> SettingsScreen()
        }

        if (current != null && !expanded) {
            MiniPlayer(current!!, controller?.isPlaying == true, { expanded = true }) {
                if (controller?.isPlaying == true) controller?.pause() else controller?.play()
            }
        }

        NavigationBar(Modifier.align(Alignment.BottomCenter)) {
            val items = listOf(Icons.Default.Home to "Home", Icons.Default.Album to "Albums", Icons.Default.Settings to "Settings")
            items.forEachIndexed { i, item ->
                NavigationBarItem(tab == i, { tab = i }, icon = { Icon(item.first, null) }, label = { Text(item.second) })
            }
        }

        if (expanded && current != null) {
            FullPlayer(current!!, controller, { expanded = false })
        }
    }
}

private fun playTrack(controller: MediaController?, track: Track, queue: List<Track>) {
    if (controller == null) return
    val items = queue.map { t ->
        MediaItem.Builder()
            .setUri(t.uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.album).build())
            .build()
    }
    val index = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
    controller.setMediaItems(items, index, 0L)
    controller.prepare()
    controller.play()
}

@Composable
private fun LibraryHome(tracks: List<Track>, query: String, setQuery: (String) -> Unit, onPlay: (Track) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).padding(top = 52.dp, bottom = 90.dp)) {
        Text("Your library", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(query, setQuery, Modifier.fillMaxWidth(), placeholder = { Text("Search songs, artists, albums") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
        Spacer(Modifier.height(12.dp))
        if (tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No music found on this device.") }
        } else {
            val filtered = tracks.filter { query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                itemsIndexed(filtered) { _, track -> TrackRow(track, onPlay = { onPlay(track) }) }
            }
        }
    }
}

@Composable
private fun AlbumsScreen(tracks: List<Track>, onPlay: (Track) -> Unit) {
    val albums = tracks.distinctBy { it.albumId }
    Column(Modifier.fillMaxSize().padding(18.dp).padding(top = 52.dp, bottom = 90.dp)) {
        Text("Albums", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(14.dp))
        LazyColumn {
            itemsIndexed(albums) { _, track ->
                ListItem(
                    headlineContent = { Text(track.album) },
                    supportingContent = { Text(track.artist) },
                    leadingContent = { Artwork(track.title, Modifier.size(56.dp)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen() {
    Column(Modifier.fillMaxSize().padding(20.dp).padding(top = 52.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))
        var dynamic by remember { mutableStateOf(true) }
        var gapless by remember { mutableStateOf(true) }
        ListItem(headlineContent = { Text("Dynamic colors") }, trailingContent = { Switch(dynamic, { dynamic = it }) })
        ListItem(headlineContent = { Text("Gapless playback") }, trailingContent = { Switch(gapless, { gapless = it }) })
    }
}

@Composable
private fun MiniPlayer(track: Track, playing: Boolean, onExpand: () -> Unit, onPlay: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp).padding(bottom = 72.dp), shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
        Row(Modifier.padding(7.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(track.title, Modifier.size(48.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(track.title, maxLines = 1)
                Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            IconButton(onPlay) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null) }
            IconButton(onExpand) { Icon(Icons.Default.ExpandLess, null) }
        }
    }
}

@Composable
private fun TrackRow(track: Track, onPlay: () -> Unit) {
    ListItem(
        headlineContent = { Text(track.title, maxLines = 1) },
        supportingContent = { Text(track.artist, maxLines = 1) },
        leadingContent = { Artwork(track.title, Modifier.size(56.dp)) },
        trailingContent = { IconButton(onPlay) { Icon(Icons.Default.PlayArrow, null) } }
    )
}

@Composable
private fun Artwork(title: String, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, tonalElevation = 2.dp) {
        Box(contentAlignment = Alignment.Center) { Text(title.take(1), style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun FullPlayer(track: Track, controller: MediaController?, onClose: () -> Unit) {
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(1L) }
    LaunchedEffect(controller) {
        while (true) {
            position = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
            duration = (controller?.duration ?: 1L).coerceAtLeast(1L)
            kotlinx.coroutines.delay(500)
        }
    }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth()) {
                IconButton(onClose) { Icon(Icons.Default.KeyboardArrowDown, "Minimize") }
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.weight(1f))
            Artwork(track.title, Modifier.size(310.dp))
            Spacer(Modifier.height(28.dp))
            Text(track.title, style = MaterialTheme.typography.headlineSmall)
            Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(value = position.toFloat().coerceIn(0f, duration.toFloat()), onValueChange = { controller?.seekTo(it.toLong()) }, valueRange = 0f..duration.toFloat())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(format(position)); Text(format(duration)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ controller?.seekToPrevious() }) { Icon(Icons.Default.SkipPrevious, null) }
                FilledIconButton({ if (controller?.isPlaying == true) controller.pause() else controller?.play() }, Modifier.size(68.dp)) {
                    Icon(if (controller?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                }
                IconButton({ controller?.seekToNext() }) { Icon(Icons.Default.SkipNext, null) }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

private fun format(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}
