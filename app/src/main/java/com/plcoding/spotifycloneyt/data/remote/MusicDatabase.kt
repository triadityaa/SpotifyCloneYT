package com.plcoding.spotifycloneyt.data.remote

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.plcoding.spotifycloneyt.data.entities.Song
import com.plcoding.spotifycloneyt.other.Constants.SONG_COLLECTION
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class MusicDatabase @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun getAllSongs(): List<Song> {
        // Without app/google-services.json the default FirebaseApp is never initialized and
        // FirebaseFirestore.getInstance() would crash the app with an IllegalStateException.
        if (FirebaseApp.getApps(context).isEmpty()) throw FirebaseNotConfiguredException()

        val documents = FirebaseFirestore.getInstance()
            .collection(SONG_COLLECTION)
            .get()
            .await()
            .documents
        val songs = documents
            .mapNotNull { document -> document.data?.let { Song.fromFirestore(document.id, it) } }
            .distinctBy { it.mediaId }
        return coroutineScope {
            songs.map { song -> async { song.withResolvedStorageUrls() } }
                .awaitAll()
                .filterNotNull()
        }
    }

    /** Replaces gs:// URLs with https download URLs. Returns null if the audio file is missing. */
    private suspend fun Song.withResolvedStorageUrls(): Song? {
        val resolvedSongUrl = resolveStorageUrl(songUrl) ?: return null
        return copy(songUrl = resolvedSongUrl, imageUrl = resolveStorageUrl(imageUrl).orEmpty())
    }

    private suspend fun resolveStorageUrl(url: String): String? {
        if (!url.startsWith(GS_SCHEME)) return url
        return try {
            val bucket = GS_SCHEME + url.removePrefix(GS_SCHEME).substringBefore('/')
            FirebaseStorage.getInstance(bucket).getReferenceFromUrl(url).downloadUrl.await().toString()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Could not resolve Cloud Storage URL %s", url)
            null
        }
    }

    private companion object {
        const val GS_SCHEME = "gs://"
    }
}

class FirebaseNotConfiguredException :
    IllegalStateException("FirebaseApp is not initialized: app/google-services.json is missing")
