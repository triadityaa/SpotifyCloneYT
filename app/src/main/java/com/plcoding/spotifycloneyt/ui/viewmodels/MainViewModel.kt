package com.plcoding.spotifycloneyt.ui.viewmodels

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.plcoding.spotifycloneyt.R
import com.plcoding.spotifycloneyt.data.entities.Song
import com.plcoding.spotifycloneyt.data.remote.FirebaseNotConfiguredException
import com.plcoding.spotifycloneyt.data.remote.MusicDatabase
import com.plcoding.spotifycloneyt.exoplayer.MusicServiceConnection
import com.plcoding.spotifycloneyt.exoplayer.toSong
import com.plcoding.spotifycloneyt.other.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@HiltViewModel
class MainViewModel @Inject constructor(
    private val musicDatabase: MusicDatabase,
    private val musicServiceConnection: MusicServiceConnection
) : ViewModel() {

    private val _songs = MutableStateFlow<Resource<List<Song>>>(Resource.Loading)
    val songs: StateFlow<Resource<List<Song>>> = _songs.asStateFlow()

    val currentSong: StateFlow<Song?> = musicServiceConnection.currentMediaItem
        .map { mediaItem -> mediaItem?.toSong() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isPlaying: StateFlow<Boolean> = musicServiceConnection.isPlaying

    val playerErrors: SharedFlow<PlaybackException> = musicServiceConnection.playerErrors

    private var loadSongsJob: Job? = null

    init {
        loadSongs()
    }

    fun loadSongs() {
        if (loadSongsJob?.isActive == true) return
        loadSongsJob = viewModelScope.launch {
            _songs.value = Resource.Loading
            _songs.value = try {
                Resource.Success(musicDatabase.getAllSongs())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Could not load songs")
                Resource.Error(e.toErrorMessage())
            }
        }
    }

    fun playOrToggleSong(song: Song, toggle: Boolean = false) {
        musicServiceConnection.playSong(song, loadedSongs(), toggleIfCurrent = toggle)
    }

    fun skipToSong(song: Song) {
        musicServiceConnection.skipToSong(song, loadedSongs())
    }

    fun togglePlayPause() {
        musicServiceConnection.togglePlayPause(fallbackPlaylist = loadedSongs())
    }

    fun skipToNextSong() {
        musicServiceConnection.skipToNext()
    }

    fun skipToPreviousSong() {
        musicServiceConnection.skipToPrevious()
    }

    fun seekTo(positionMs: Long) {
        musicServiceConnection.seekTo(positionMs)
    }

    private fun loadedSongs(): List<Song> =
        (songs.value as? Resource.Success)?.data.orEmpty()

    @StringRes
    private fun Exception.toErrorMessage(): Int = when {
        this is FirebaseNotConfiguredException -> R.string.error_firebase_not_configured
        this is FirebaseFirestoreException &&
            code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> R.string.error_permission_denied
        this is FirebaseFirestoreException &&
            code == FirebaseFirestoreException.Code.UNAVAILABLE -> R.string.error_network
        else -> R.string.error_unknown
    }
}
