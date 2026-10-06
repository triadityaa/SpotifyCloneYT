package com.plcoding.spotifycloneyt

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.plcoding.spotifycloneyt.exoplayer.MusicService
import com.plcoding.spotifycloneyt.ui.MainActivity
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Plays a local audio file through [MusicService] the same way the UI does (via a
 * [MediaController]) and checks that the service runs as a `mediaPlayback` foreground service.
 * This covers the Android 12+ foreground service rules and the Android 14+ service type checks.
 */
@RunWith(AndroidJUnit4::class)
class MusicServicePlaybackTest {

    @get:Rule
    val notificationPermission: TestRule = grantNotificationPermission()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private var controller: MediaController? = null

    @After
    fun releaseController() {
        val mediaController = controller ?: return
        instrumentation.runOnMainSync {
            mediaController.stop()
            mediaController.clearMediaItems()
            mediaController.release()
        }
    }

    @Test
    fun playsAudioInForegroundService() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val audioUri = Uri.fromFile(copyTestAudioToCache())
            val mediaController = MediaController.Builder(
                context,
                SessionToken(context, ComponentName(context, MusicService::class.java))
            )
                .setApplicationLooper(Looper.getMainLooper())
                .buildAsync()
                .get(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            controller = mediaController

            instrumentation.runOnMainSync {
                mediaController.setMediaItem(
                    MediaItem.Builder()
                        .setMediaId("test_tone")
                        .setUri(audioUri)
                        .setRequestMetadata(
                            MediaItem.RequestMetadata.Builder().setMediaUri(audioUri).build()
                        )
                        .setMediaMetadata(MediaMetadata.Builder().setTitle("Test tone").build())
                        .build()
                )
                mediaController.repeatMode = Player.REPEAT_MODE_ONE
                mediaController.prepare()
                mediaController.play()
            }

            assertTrue("Player did not start playing", waitUntil { mediaController.isPlaying })
            assertTrue(
                "MusicService is not running in the foreground",
                waitUntil { isMusicServiceInForeground() }
            )
        }
    }

    private fun copyTestAudioToCache(): File {
        val file = File(context.cacheDir, TEST_AUDIO_FILE)
        instrumentation.context.assets.open(TEST_AUDIO_FILE).use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return file
    }

    @Suppress("DEPRECATION") // Still returns the caller's own services.
    private fun isMusicServiceInForeground(): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return activityManager.getRunningServices(Int.MAX_VALUE).any { service ->
            service.service.className == MusicService::class.java.name && service.foreground
        }
    }

    /** Polls [condition] on the main thread, where the MediaController must be used. */
    private fun waitUntil(condition: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            var isSatisfied = false
            instrumentation.runOnMainSync { isSatisfied = condition() }
            if (isSatisfied) return true
            SystemClock.sleep(100)
        }
        return false
    }

    private companion object {
        const val TEST_AUDIO_FILE = "test_tone.wav"
        const val TIMEOUT_MS = 15_000L
    }
}
