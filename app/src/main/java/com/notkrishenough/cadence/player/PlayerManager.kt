package com.notkrishenough.cadence.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import com.notkrishenough.cadence.model.Track

class PlayerManager(context: Context) {
    val player = ExoPlayer.Builder(context.applicationContext).build()

    fun playQueue(tracks: List<Track>, start: Int) {
        player.setMediaItems(tracks.map {
            MediaItem.Builder()
                .setUri(it.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(it.title)
                        .setArtist(it.artist)
                        .setAlbumTitle(it.album)
                        .build()
                ).build()
        }, start, 0L)
        player.prepare()
        player.play()
    }

    fun release() = player.release()
}
