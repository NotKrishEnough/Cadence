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
import com.notkrishenough.cadence.model.Track
import com.notkrishenough.cadence.player.PlayerManager

@Composable
fun CadenceRoot(vm: CadenceViewModel = viewModel()) {
    val tracks by vm.tracks.collectAsState()
    val query by vm.query.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var current by remember { mutableStateOf<Track?>(null) }
    var expanded by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember { PlayerManager(context) }

    LaunchedEffect(Unit) { vm.refresh() }
    DisposableEffect(Unit) { onDispose { player.release() } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            when (tab) {
                0 -> LibraryHome(tracks, query, vm::setQuery) { index ->
                    current = tracks.getOrNull(index)
                    if (tracks.isNotEmpty()) player.playQueue(tracks, index)
                }
                1 -> AlbumsScreen(tracks) { index ->
                    current = tracks.getOrNull(index)
                    if (tracks.isNotEmpty()) player.playQueue(tracks, index)
                }
                2 -> SettingsScreen()
            }
        }

        if (current != null && !expanded) {
            MiniPlayer(
                current!!,
                playing = player.player.isPlaying,
                onExpand = { expanded = true },
                onPlay = { if (player.player.isPlaying) player.player.pause() else player.player.play() }
            )
        }

        NavigationBar(Modifier.align(Alignment.BottomCenter)) {
            val items = listOf(Icons.Default.Home to "Home", Icons.Default.Album to "Albums", Icons.Default.Settings to "Settings")
            items.forEachIndexed { i, item ->
                NavigationBarItem(tab == i, { tab = i }, icon = { Icon(item.first, null) }, label = { Text(item.second) })
            }
        }

        if (expanded && current != null) {
            FullPlayer(current!!, player, onClose = { expanded = false })
        }
    }
}

@Composable
private fun LibraryHome(tracks: List<Track>, query: String, setQuery: (String) -> Unit, onPlay: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).padding(top = 52.dp, bottom = 90.dp)) {
        Text("Your library", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = query, onValueChange = setQuery, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search songs, artists, albums") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        if (tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No music found on this device.")
            }
        } else {
            val filtered = tracks.filter {
                query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                itemsIndexed(filtered) { _, track ->
                    TrackRow(track) { onPlay(tracks.indexOf(track)) }
                }
            }
        }
    }
}

@Composable
private fun AlbumsScreen(tracks: List<Track>, onPlay: (Int) -> Unit) {
    val albums = tracks.distinctBy { it.albumId }
    Column(Modifier.fillMaxSize().padding(18.dp).padding(top = 52.dp, bottom = 90.dp)) {
        Text("Albums", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
    Surface(
        Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 72.dp),
        shape = MaterialTheme.shapes.large, tonalElevation = 6.dp
    ) {
        Row(Modifier.padding(7.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(track.title, Modifier.size(48.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(track.title, maxLines = 1)
                Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onPlay) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null) }
            IconButton(onClick = onExpand) { Icon(Icons.Default.ExpandLess, null) }
        }
    }
}

@Composable
private fun TrackRow(track: Track, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(track.title, maxLines = 1) },
        supportingContent = { Text(track.artist, maxLines = 1) },
        leadingContent = { Artwork(track.title, Modifier.size(56.dp)) },
        trailingContent = { IconButton(onClick) { Icon(Icons.Default.PlayArrow, null) } },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun Artwork(title: String, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, tonalElevation = 2.dp) {
        Box(contentAlignment = Alignment.Center) {
            Text(title.take(1), style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun FullPlayer(track: Track, manager: PlayerManager, onClose: () -> Unit) {
    val player = manager.player
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(1L) }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0)
            duration = player.duration.takeIf { it > 0 } ?: 1
            kotlinx.coroutines.delay(500)
        }
    }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth()) {
                IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, "Minimize") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, "More") }
            }
            Spacer(Modifier.weight(1f))
            Artwork(track.title, Modifier.size(310.dp))
            Spacer(Modifier.height(28.dp))
            Text(track.title, style = MaterialTheme.typography.headlineSmall)
            Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            Slider(
                value = position.toFloat().coerceIn(0f, duration.toFloat()),
                onValueChange = { player.seekTo(it.toLong()) },
                valueRange = 0f..duration.toFloat()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(format(position)); Text(format(duration))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {}) { Icon(Icons.Default.Shuffle, null) }
                IconButton(onClick = { player.seekToPrevious() }) { Icon(Icons.Default.SkipPrevious, null) }
                FilledIconButton(
                    onClick = { if (player.isPlaying) player.pause() else player.play() },
                    modifier = Modifier.size(68.dp)
                ) { Icon(if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null) }
                IconButton(onClick = { player.seekToNext() }) { Icon(Icons.Default.SkipNext, null) }
                IconButton(onClick = { player.repeatMode = if (player.repeatMode == 0) 1 else 0 }) { Icon(Icons.Default.Repeat, null) }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

private fun format(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}
