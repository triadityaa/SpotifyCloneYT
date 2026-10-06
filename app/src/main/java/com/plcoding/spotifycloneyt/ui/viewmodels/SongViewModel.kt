package com.plcoding.spotifycloneyt.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.plcoding.spotifycloneyt.exoplayer.MusicServiceConnection
import com.plcoding.spotifycloneyt.other.Constants.UPDATE_PLAYER_POSITION_INTERVAL
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

data class PlaybackProgress(val positionMs: Long, val durationMs: Long)

@HiltViewModel
class SongViewModel @Inject constructor(
    private val musicServiceConnection: MusicServiceConnection
) : ViewModel() {

    /** Emits the playback position while collected. Must be collected on the main thread. */
    val playbackProgress: Flow<PlaybackProgress> = flow {
        while (true) {
            emit(
                PlaybackProgress(
                    positionMs = musicServiceConnection.currentPosition,
                    durationMs = musicServiceConnection.duration
                )
            )
            delay(UPDATE_PLAYER_POSITION_INTERVAL)
        }
    }.distinctUntilChanged()
}
