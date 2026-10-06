package com.notkrishenough.cadence

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

data class Track(val title: String, val artist: String, val artwork: String = "")

private val demoTracks = listOf(
    Track("Midnight Drive", "Cadence"),
    Track("Afterglow", "Cadence"),
    Track("Northern Lights", "Cadence"),
    Track("Ocean Eyes", "Cadence"),
    Track("City Lights", "Cadence")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CadenceApp() }
    }
}

@Composable
private fun CadenceApp() {
    val player = remember { ExoPlayer.Builder(androidx.compose.ui.platform.LocalContext.current).build() }
    var selected by remember { mutableIntStateOf(0) }
    var current by remember { mutableStateOf<Track?>(null) }
    var expanded by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { player.release() } }

    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    ) {
        Surface(Modifier.fillMaxSize()) {
            Box {
                AnimatedContent(targetState = selected, label = "tab") { tab ->
                    when (tab) {
                        0 -> HomeScreen(demoTracks, current) { track ->
                            current = track
                            player.setMediaItem(MediaItem.fromUri(
                                "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4"
                            ))
                            player.prepare()
                            player.play()
                        }
                        1 -> LibraryScreen(demoTracks) { current = it; expanded = true }
                        else -> SettingsScreen()
                    }
                }

                AnimatedVisibility(
                    visible = current != null && !expanded,
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    MiniPlayer(current!!, onExpand = { expanded = true }, onPlay = {
                        if (player.isPlaying) player.pause() else player.play()
                    })
                }

                NavigationBar(
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    listOf(Icons.Default.Home to "Home", Icons.Default.LibraryMusic to "Library", Icons.Default.Settings to "Settings")
                        .forEachIndexed { index, item ->
                            NavigationBarItem(
                                selected = selected == index,
                                onClick = { selected = index },
                                icon = { Icon(item.first, null) },
                                label = { Text(item.second) }
                            )
                        }
                }

                if (expanded && current != null) {
                    FullPlayer(current!!, player) { expanded = false }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(tracks: List<Track>, current: Track?, onPlay: (Track) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 64.dp, end = 20.dp, bottom = 150.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Text("Good evening", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Your music, beautifully simple.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AssistChip(onClick = {}, label = { Text("Songs") }, leadingIcon = { Icon(Icons.Default.MusicNote, null) })
                AssistChip(onClick = {}, label = { Text("Albums") }, leadingIcon = { Icon(Icons.Default.Album, null) })
            }
        }
        item { Text("Recently played", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        items(tracks) { track -> TrackRow(track, onPlay) }
    }
}

@Composable
private fun LibraryScreen(tracks: List<Track>, onOpen: (Track) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 64.dp)) {
        Text("Library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 150.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tracks) { TrackRow(it, onOpen) }
        }
    }
}

@Composable
private fun SettingsScreen() {
    Column(Modifier.fillMaxSize().padding(20.dp).padding(top = 64.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        listOf("Dynamic colors", "Gapless playback", "Normalize volume", "Show explicit content").forEach {
            var checked by remember { mutableStateOf(true) }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(it, Modifier.weight(1f))
                Switch(checked = checked, onCheckedChange = { checked = it })
            }
        }
    }
}

@Composable
private fun TrackRow(track: Track, onClick: (Track) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { onClick(track) }.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(track.title, Modifier.size(58.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, fontWeight = FontWeight.SemiBold)
            Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { onClick(track) }) { Icon(Icons.Default.PlayArrow, null) }
    }
}

@Composable
private fun Artwork(title: String, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(14.dp)).background(
            Brush.linearGradient(listOf(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.tertiaryContainer
            ))
        ),
        contentAlignment = Alignment.Center
    ) {
        Text(title.take(1), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MiniPlayer(track: Track, onExpand: () -> Unit, onPlay: () -> Unit) {
    Surface(
        modifier = Modifier.padding(bottom = 76.dp, start = 12.dp, end = 12.dp).fillMaxWidth().clickable { onExpand() },
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 5.dp
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(track.title, Modifier.size(48.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            IconButton(onClick = onPlay) { Icon(Icons.Default.PlayArrow, null) }
        }
    }
}

@Composable
private fun FullPlayer(track: Track, player: ExoPlayer, onClose: () -> Unit) {
    val playing = player.isPlaying
    val scale by animateFloatAsState(if (playing) 1f else .94f, label = "art")
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, "Minimize") }
                Text("NOW PLAYING", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, "More") }
            }
            Spacer(Modifier.weight(1f))
            Artwork(track.title, Modifier.size(300.dp).scale(scale))
            Spacer(Modifier.height(30.dp))
            Text(track.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Slider(value = if (playing) .55f else .15f, onValueChange = {})
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0:00"); Text("3:42")
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {}) { Icon(Icons.Default.Shuffle, null) }
                IconButton(onClick = {}) { Icon(Icons.Default.SkipPrevious, null) }
                FilledIconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }, modifier = Modifier.size(68.dp)) {
                    Icon(if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                }
                IconButton(onClick = {}) { Icon(Icons.Default.SkipNext, null) }
                IconButton(onClick = {}) { Icon(Icons.Default.Repeat, null) }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}
