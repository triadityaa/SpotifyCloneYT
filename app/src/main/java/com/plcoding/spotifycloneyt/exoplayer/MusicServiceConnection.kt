package com.plcoding.spotifycloneyt.exoplayer

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.plcoding.spotifycloneyt.data.entities.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Connects the UI to [MusicService] through a Media3 [MediaController].
 *
 * Call [connect] in `onStart` and [disconnect] in `onStop` of the activity. All methods must be
 * called on the main thread.
 */
@Singleton
class MusicServiceConnection @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pendingCommands = mutableListOf<(MediaController) -> Unit>()

    private val _currentMediaItem = MutableStateFlow<MediaItem?>(null)
    val currentMediaItem: StateFlow<MediaItem?> = _currentMediaItem.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)

    /** True while playback is requested (also while buffering), i.e. when to show a pause button. */
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playerErrors = MutableSharedFlow<PlaybackException>(extraBufferCapacity = 1)
    val playerErrors: SharedFlow<PlaybackException> = _playerErrors.asSharedFlow()

    val currentPosition: Long
        get() = controller?.currentPosition ?: 0L

    val duration: Long
        get() = controller?.duration?.takeIf { it != C.TIME_UNSET } ?: 0L

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncState(player)
        }

        override fun onPlayerError(error: PlaybackException) {
            Timber.e(error, "Playback error")
            _playerErrors.tryEmit(error)
        }
    }

    fun connect() {
        if (controllerFuture != null) return
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener({
            // disconnect() was called before the connection was established.
            if (controllerFuture !== future) return@addListener
            val mediaController = try {
                future.get()
            } catch (e: Exception) {
                Timber.e(e, "Could not connect to MusicService")
                controllerFuture = null
                pendingCommands.clear()
                return@addListener
            }
            controller = mediaController
            mediaController.addListener(playerListener)
            syncState(mediaController)
            pendingCommands.forEach { command -> command(mediaController) }
            pendingCommands.clear()
        }, ContextCompat.getMainExecutor(context))
    }

    fun disconnect() {
        controller?.removeListener(playerListener)
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        pendingCommands.clear()
    }

    /**
     * Plays [song] as part of [playlist]. If [song] is already the current song, playback is
     * paused/resumed when [toggleIfCurrent] is true, otherwise it is just resumed.
     */
    fun playSong(song: Song, playlist: List<Song>, toggleIfCurrent: Boolean) = withController { player ->
        val index = playlist.indexOfFirst { it.mediaId == song.mediaId }
        if (index == -1) return@withController
        when {
            !player.hasPlaylist(playlist) -> {
                player.setMediaItems(playlist.map(Song::toMediaItem), index, C.TIME_UNSET)
                player.prepare()
                player.play()
            }
            player.currentMediaItemIndex != index -> {
                player.seekToDefaultPosition(index)
                player.resume()
            }
            toggleIfCurrent -> player.pauseOrResume()
            else -> player.resume()
        }
    }

    /** Switches to [song] but keeps the current play/pause state (used by the swipeable mini player). */
    fun skipToSong(song: Song, playlist: List<Song>) = withController { player ->
        val index = playlist.indexOfFirst { it.mediaId == song.mediaId }
        if (index == -1) return@withController
        if (player.hasPlaylist(playlist)) {
            player.seekToDefaultPosition(index)
        } else {
            player.setMediaItems(playlist.map(Song::toMediaItem), index, C.TIME_UNSET)
            player.prepare()
        }
    }

    /** Pauses/resumes playback, or starts [fallbackPlaylist] when nothing has been played yet. */
    fun togglePlayPause(fallbackPlaylist: List<Song>) = withController { player ->
        if (player.mediaItemCount > 0) {
            player.pauseOrResume()
        } else if (fallbackPlaylist.isNotEmpty()) {
            player.setMediaItems(fallbackPlaylist.map(Song::toMediaItem))
            player.prepare()
            player.play()
        }
    }

    fun skipToNext() = withController { player -> player.seekToNextMediaItem() }

    fun skipToPrevious() = withController { player -> player.seekToPrevious() }

    fun seekTo(positionMs: Long) = withController { player -> player.seekTo(positionMs) }

    private fun withController(command: (MediaController) -> Unit) {
        val mediaController = controller
        when {
            mediaController != null -> command(mediaController)
            controllerFuture != null -> pendingCommands += command
            else -> Timber.w("Not connected to MusicService, command ignored")
        }
    }

    private fun syncState(player: Player) {
        _currentMediaItem.value = player.currentMediaItem
        _isPlaying.value = player.isPlaybackRequested()
    }

    private fun Player.isPlaybackRequested(): Boolean =
        playWhenReady && playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED

    private fun Player.hasPlaylist(playlist: List<Song>): Boolean =
        mediaItemCount == playlist.size &&
            playlist.indices.all { getMediaItemAt(it).mediaId == playlist[it].mediaId }

    private fun Player.resume() {
        if (playbackState == Player.STATE_IDLE) prepare()
        if (playbackState == Player.STATE_ENDED) seekToDefaultPosition()
        play()
    }

    private fun Player.pauseOrResume() {
        if (isPlaybackRequested()) pause() else resume()
    }
}
