package com.notkrishenough.cadence.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import com.notkrishenough.cadence.model.Track
import com.notkrishenough.cadence.player.CadenceMediaController
import kotlinx.coroutines.delay

@Composable
fun CadenceRoot(vm: CadenceViewModel = viewModel()) {
    val tracks by vm.tracks.collectAsState()
    val query by vm.query.collectAsState()
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var current by remember { mutableStateOf<Track?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        val future = CadenceMediaController.connect(context)
        val listener = Runnable { runCatching { controller = future.get() } }
        future.addListener(listener, ContextCompat.getMainExecutor(context))
        onDispose { if (future.isDone) runCatching { future.get().release() } }
    }

    LaunchedEffect(Unit) { vm.refresh() }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        when (tab) {
            0 -> HomeScreen(tracks, query, vm::setQuery) { track ->
                current = track
                playTrack(controller, track, tracks)
            }
            1 -> SongsScreen(tracks, query, vm::setQuery) { track ->
                current = track
                playTrack(controller, track, tracks)
            }
            2 -> AlbumsScreen(tracks) { track ->
                current = track
                playTrack(controller, track, tracks)
            }
            else -> SettingsScreen()
        }

        if (current != null && !expanded) {
            MiniPlayer(
                current!!,
                controller?.isPlaying == true,
                { expanded = true }
            ) {
                if (controller?.isPlaying == true) controller?.pause() else controller?.play()
            }
        }

        FloatingNav(tab, { tab = it })

        if (expanded && current != null) {
            FullPlayer(current!!, controller) { expanded = false }
        }
    }
}

private fun playTrack(controller: MediaController?, track: Track, queue: List<Track>) {
    if (controller == null || queue.isEmpty()) return
    val items = queue.map { t ->
        MediaItem.Builder()
            .setUri(t.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(t.title)
                    .setArtist(t.artist)
                    .setAlbumTitle(t.album)
                    .setArtworkUri(t.artworkUri?.let(android.net.Uri::parse))
                    .build()
            ).build()
    }
    val index = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
    controller.setMediaItems(items, index, 0L)
    controller.prepare()
    controller.play()
}

@Composable
private fun HomeScreen(
    tracks: List<Track>,
    query: String,
    setQuery: (String) -> Unit,
    onPlay: (Track) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 170.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("CADENCE", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text("Your library", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            }
        }
        item { SearchBar(query, setQuery, Modifier.fillMaxWidth()) }

        if (tracks.isEmpty()) {
            item { EmptyLibrary() }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Songs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(tracks.size.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(tracks.take(12), key = { it.id }) { track -> TrackRow(track) { onPlay(track) } }
        }
    }
}

@Composable
private fun SongsScreen(
    tracks: List<Track>,
    query: String,
    setQuery: (String) -> Unit,
    onPlay: (Track) -> Unit
) {
    val filtered = tracks.filter {
        query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true)
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 170.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Text("Songs", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            SearchBar(query, setQuery, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
        }
        if (filtered.isEmpty()) item { EmptyLibrary(true) }
        else items(filtered, key = { it.id }) { track -> TrackRow(track) { onPlay(track) } }
    }
}

@Composable
private fun AlbumsScreen(tracks: List<Track>, onPlay: (Track) -> Unit) {
    val albums = tracks.distinctBy { it.albumId }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 170.dp)
    ) {
        item {
            Text("Albums", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(18.dp))
        }
        if (albums.isEmpty()) {
            item { EmptyLibrary(true) }
        } else {
            item {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.height(((albums.size + 1) / 2 * 230).dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    userScrollEnabled = false
                ) {
                    items(albums, key = { it.albumId }) { track -> AlbumCard(track) { onPlay(track) } }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen() {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 150.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))
        }
        item { SettingsHeader("Playback") }
        item { SettingsToggle("Gapless playback", "Keep albums flowing between tracks", true) }
        item { SettingsToggle("Resume playback", "Continue where you left off", true) }
        item { SettingsHeader("Appearance") }
        item { SettingsToggle("Dynamic colors", "Use your device wallpaper colors", true) }
        item { SettingsToggle("Dark theme", "Follow the system appearance", false) }
        item { SettingsHeader("About") }
        item {
            ListItem(
                headlineContent = { Text("Cadence") },
                supportingContent = { Text("Accord-style local music player • v0.1.0") },
                leadingContent = {
                    Surface(Modifier.size(48.dp), CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun SettingsHeader(text: String) {
    Text(text, Modifier.padding(top = 12.dp, bottom = 2.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
}

@Composable
private fun SettingsToggle(title: String, subtitle: String, initial: Boolean) {
    var checked by remember { mutableStateOf(initial) }
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(subtitle) },
            trailingContent = { Switch(checked, { checked = it }) }
        )
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("Search your music") },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = if (query.isNotEmpty()) {
            { IconButton({ onQueryChange("") }) { Icon(Icons.Default.Close, "Clear") } }
        } else null,
        singleLine = true,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun EmptyLibrary(compact: Boolean = false) {
    Surface(
        Modifier.fillMaxWidth().padding(top = if (compact) 20.dp else 60.dp),
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(Modifier.size(76.dp), CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.LibraryMusic, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("Your library is empty", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Put some music on your device and Cadence will find it automatically.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun TrackRow(track: Track, onPlay: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onPlay).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(track.artworkUri, track.title, Modifier.size(58.dp))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(track.title, maxLines = 1, fontWeight = FontWeight.Medium)
            Text(
                track.artist + " • " + track.album,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        IconButton(onPlay) { Icon(Icons.Default.PlayCircleFilled, null, tint = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun AlbumCard(track: Track, onPlay: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onPlay)) {
        Artwork(track.artworkUri, track.album, Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(8.dp))
        Text(track.album, maxLines = 1, fontWeight = FontWeight.SemiBold)
        Text(track.artist, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Artwork(uri: String?, fallback: String, modifier: Modifier) {
    Surface(modifier.clip(RoundedCornerShape(18.dp)), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        if (!uri.isNullOrBlank()) {
            AsyncImage(model = uri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer)
                    )
                ),
                contentAlignment = Alignment.Center
            ) {
                Text(fallback.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MiniPlayer(track: Track, playing: Boolean, onExpand: () -> Unit, onPlay: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 82.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onExpand),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp
    ) {
        Column {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(track.artworkUri, track.title, Modifier.size(48.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(track.title, maxLines = 1, fontWeight = FontWeight.SemiBold)
                    Text(track.artist, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onPlay) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null) }
                IconButton(onExpand) { Icon(Icons.Default.KeyboardArrowUp, "Open player") }
            }
            LinearProgressIndicator(progress = { 0f }, Modifier.fillMaxWidth().height(2.dp), trackColor = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}

@Composable
private fun FloatingNav(selected: Int, onSelected: (Int) -> Unit) {
    val items = listOf(
        Icons.Default.Home to "Home",
        Icons.Default.MusicNote to "Songs",
        Icons.Default.Album to "Albums",
        Icons.Default.Settings to "Settings"
    )
    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { onSelected(index) },
                        icon = { Icon(item.first, null) },
                        label = { Text(item.second) },
                        alwaysShowLabel = false
                    )
                }
            }
        }
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
            delay(400)
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp).windowInsetsPadding(WindowInsets.safeDrawing),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClose) { Icon(Icons.Default.KeyboardArrowDown, "Minimize") }
                Text("NOW PLAYING", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                IconButton({}) { Icon(Icons.Default.MoreVert, "More") }
            }
            Spacer(Modifier.weight(1f))
            Artwork(track.artworkUri, track.title, Modifier.fillMaxWidth().aspectRatio(1f).padding(horizontal = 8.dp))
            Spacer(Modifier.height(28.dp))
            Column(Modifier.fillMaxWidth()) {
                Text(track.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(track.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Spacer(Modifier.height(18.dp))
            Slider(
                value = position.toFloat().coerceIn(0f, duration.toFloat()),
                onValueChange = { controller?.seekTo(it.toLong()) },
                valueRange = 0f..duration.toFloat()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(format(position), style = MaterialTheme.typography.labelSmall)
                Text(format(duration), style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton({ controller?.seekToPrevious() }) { Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(34.dp)) }
                FilledIconButton({ if (controller?.isPlaying == true) controller.pause() else controller?.play() }, Modifier.size(68.dp)) {
                    Icon(if (controller?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow, null, Modifier.size(34.dp))
                }
                IconButton({ controller?.seekToNext() }) { Icon(Icons.Default.SkipNext, "Next", Modifier.size(34.dp)) }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

private fun format(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}
