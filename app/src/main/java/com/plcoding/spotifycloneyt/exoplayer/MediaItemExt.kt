package com.plcoding.spotifycloneyt.exoplayer

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.plcoding.spotifycloneyt.data.entities.Song

fun Song.toMediaItem(): MediaItem {
    val songUri = songUrl.toUri()
    return MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(songUri)
        // MediaItem.localConfiguration (the URI above) is not sent to a MediaSession running in
        // another process, so the URI is also kept in the request metadata for MusicService.
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(songUri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(subtitle)
                .setArtworkUri(imageUrl.takeIf { it.isNotEmpty() }?.toUri())
                .build()
        )
        .build()
}

fun MediaItem.toSong(): Song = Song(
    mediaId = mediaId,
    title = mediaMetadata.title?.toString().orEmpty(),
    subtitle = mediaMetadata.artist?.toString().orEmpty(),
    songUrl = (localConfiguration?.uri ?: requestMetadata.mediaUri)?.toString().orEmpty(),
    imageUrl = mediaMetadata.artworkUri?.toString().orEmpty()
)
