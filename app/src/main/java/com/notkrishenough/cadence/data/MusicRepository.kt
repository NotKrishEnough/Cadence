package com.notkrishenough.cadence.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.notkrishenough.cadence.model.Track

class MusicRepository(private val context: Context) {
    fun loadTracks(): List<Track> {
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION
        )
        val tracks = mutableListOf<Track>()
        resolver.query(
            collection, projection,
            MediaStore.Audio.Media.IS_MUSIC + " != 0",
            null,
            MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC"
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (cursor.moveToNext()) {
                val trackId = cursor.getLong(id)
                val aid = cursor.getLong(albumId)
                tracks += Track(
                    trackId,
                    cursor.getString(title) ?: "Unknown title",
                    cursor.getString(artist) ?: "Unknown artist",
                    cursor.getString(album) ?: "Unknown album",
                    aid,
                    cursor.getLong(duration),
                    ContentUris.withAppendedId(collection, trackId).toString(),
                    ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, aid).toString()
                )
            }
        }
        return tracks
    }
}
