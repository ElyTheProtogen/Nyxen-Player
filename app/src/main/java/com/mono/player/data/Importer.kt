package com.mono.player.data
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

data class ImportResult(val added: Int, val duplicates: Int, val failed: Int)

object Importer {
    private val ext = setOf("mp3", "wav", "ogg", "m4a")

    suspend fun import(c: Context, uris: List<Uri>): ImportResult {
        val dao = Db.get(c).dao(); var a = 0; var d = 0; var f = 0
        for (u in uris) {
            try { c.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (_: Exception) { }
            val name = DocumentFile.fromSingleUri(c, u)?.name ?: ""
            if (name.substringAfterLast(".", "").lowercase() !in ext) { f++; continue }
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(c, u)
                fun m(k: Int, def: String) = r.extractMetadata(k)?.takeIf { it.isNotBlank() } ?: def
                val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                if (dur == null) { f++; continue }
                val t = Track(
                    uri = u.toString(), title = m(MediaMetadataRetriever.METADATA_KEY_TITLE, name.substringBeforeLast(".")),
                    artist = m(MediaMetadataRetriever.METADATA_KEY_ARTIST, "Unknown artist"),
                    album = m(MediaMetadataRetriever.METADATA_KEY_ALBUM, "Unknown album"),
                    genre = m(MediaMetadataRetriever.METADATA_KEY_GENRE, "Unknown genre"),
                    durationMs = dur, addedAt = System.currentTimeMillis(), artwork = r.embeddedPicture
                )
                if (dao.insert(t) == -1L) d++ else a++
            } catch (_: Exception) { f++ } finally { r.release() }
        }
        return ImportResult(a, d, f)
    }
}
