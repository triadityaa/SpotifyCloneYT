package com.plcoding.spotifycloneyt.data.entities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SongTest {

    @Test
    fun fromFirestore_mapsAllFields() {
        val song = Song.fromFirestore(
            documentId = "doc1",
            data = mapOf(
                "mediaId" to "1",
                "title" to "Title",
                "subtitle" to "Artist",
                "songUrl" to "https://example.com/song.mp3",
                "imageUrl" to "https://example.com/cover.jpg"
            )
        )

        assertEquals(
            Song(
                mediaId = "1",
                title = "Title",
                subtitle = "Artist",
                songUrl = "https://example.com/song.mp3",
                imageUrl = "https://example.com/cover.jpg"
            ),
            song
        )
    }

    @Test
    fun fromFirestore_usesDocumentIdWhenMediaIdIsMissing() {
        val song = Song.fromFirestore("doc1", mapOf("songUrl" to "https://example.com/song.mp3"))

        assertEquals("doc1", song?.mediaId)
        assertEquals("", song?.title)
        assertEquals("", song?.imageUrl)
    }

    @Test
    fun fromFirestore_acceptsNumericMediaId() {
        val song = Song.fromFirestore(
            "doc1",
            mapOf("mediaId" to 7L, "songUrl" to "https://example.com/song.mp3")
        )

        assertEquals("7", song?.mediaId)
    }

    @Test
    fun fromFirestore_trimsValues() {
        val song = Song.fromFirestore(
            "doc1",
            mapOf("title" to "  Title ", "songUrl" to " https://example.com/song.mp3\n")
        )

        assertEquals("Title", song?.title)
        assertEquals("https://example.com/song.mp3", song?.songUrl)
    }

    @Test
    fun fromFirestore_returnsNullWithoutSongUrl() {
        assertNull(Song.fromFirestore("doc1", mapOf("title" to "Title")))
        assertNull(Song.fromFirestore("doc1", mapOf("title" to "Title", "songUrl" to "  ")))
    }
}
