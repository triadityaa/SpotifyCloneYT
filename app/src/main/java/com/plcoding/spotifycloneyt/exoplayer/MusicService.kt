package com.plcoding.spotifycloneyt.exoplayer

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.plcoding.spotifycloneyt.ui.MainActivity

/**
 * Plays music in the background. Media3 posts the media notification and moves the service in
 * and out of the foreground (type `mediaPlayback`) based on the player state, which is what
 * Android 12+ foreground service rules and the Android 17 background audio hardening require.
 */
class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus= */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .setCallback(SessionCallback())
            .build()
    }

    // MediaSessionService.onTaskRemoved() is not overridden: by default the service keeps running
    // when the app is swiped away during playback and is stopped otherwise.
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private class SessionCallback : MediaSession.Callback {

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            // Restore the playable URI if it was stripped on the way from the controller.
            val playableItems = mediaItems.mapNotNull { item ->
                when {
                    item.localConfiguration != null -> item
                    item.requestMetadata.mediaUri != null ->
                        item.buildUpon().setUri(item.requestMetadata.mediaUri).build()
                    else -> null
                }
            }
            return Futures.immediateFuture(playableItems)
        }
    }
}
