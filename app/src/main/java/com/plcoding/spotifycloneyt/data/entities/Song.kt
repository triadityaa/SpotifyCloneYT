package com.plcoding.spotifycloneyt.data.entities

/**
 * A song stored as a document in the Firestore `songs` collection.
 *
 * Document fields: `mediaId`, `title`, `subtitle`, `songUrl` and `imageUrl`.
 * `songUrl` and `imageUrl` can be https download URLs or `gs://` Cloud Storage URLs.
 */
data class Song(
    val mediaId: String = "",
    val title: String = "",
    val subtitle: String = "",
    val songUrl: String = "",
    val imageUrl: String = ""
) {
    companion object {
        /**
         * Parses a Firestore document. Fields are read manually instead of with
         * `DocumentSnapshot.toObject()` so the mapping does not rely on reflection and tolerates
         * numeric ids. Returns null when the document has no `songUrl`, because it can't be played.
         */
        fun fromFirestore(documentId: String, data: Map<String, Any?>): Song? {
            val songUrl = data.text("songUrl")
            if (songUrl.isEmpty()) return null
            return Song(
                mediaId = data.text("mediaId").ifEmpty { documentId },
                title = data.text("title"),
                subtitle = data.text("subtitle"),
                songUrl = songUrl,
                imageUrl = data.text("imageUrl")
            )
        }

        private fun Map<String, Any?>.text(key: String): String = this[key]?.toString()?.trim().orEmpty()
    }
}
