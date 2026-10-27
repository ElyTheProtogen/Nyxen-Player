package com.mono.player.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Finds music through MediaStore (needs the audio read permission) and adds new songs to the library. */
object DeviceScan {
    private val ext = setOf("mp3", "m4a", "ogg", "wav")

    suspend fun run(c: Context): ImportResult = withContext(Dispatchers.IO) {
        val dao = Db.get(c).dao(); var added = 0; var dup = 0; var failed = 0
        val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION)
        val sel = "${MediaStore.Audio.Media.IS_RINGTONE}=0 AND ${MediaStore.Audio.Media.IS_NOTIFICATION}=0 AND ${MediaStore.Audio.Media.IS_ALARM}=0"
        try {
            c.contentResolver.query(base, proj, sel, null, null)?.use { q ->
                val iId = q.getColumnIndexOrThrow(MediaStore.Audio.Media._ID); val iName = q.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val iTitle = q.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE); val iArtist = q.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val iAlbum = q.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM); val iDur = q.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (q.moveToNext()) {
                    val name = q.getString(iName) ?: ""
                    if (name.substringAfterLast('.', "").lowercase() !in ext) continue
                    val dur = q.getLong(iDur); if (dur <= 0) { failed++; continue }
                    val uri = ContentUris.withAppendedId(base, q.getLong(iId))
                    val title = q.getString(iTitle)?.takeIf { it.isNotBlank() } ?: name.substringBeforeLast('.')
                    val artist = q.getString(iArtist)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown artist"
                    val album = q.getString(iAlbum)?.takeIf { it.isNotBlank() } ?: "Unknown album"
                    if (dao.similar(title, artist, dur) > 0) { dup++; continue }
                    val art: ByteArray? = if (Build.VERSION.SDK_INT >= 29) try {
                        val bmp = c.contentResolver.loadThumbnail(uri, Size(160, 160), null)
                        ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 70, it) }.toByteArray()
                    } catch (_: Exception) { null } else null
                    val t = Track(uri = uri.toString(), title = title, artist = artist, album = album, genre = "Unknown genre",
                        durationMs = dur, addedAt = System.currentTimeMillis(), artwork = art)
                    if (dao.insert(t) == -1L) dup++ else added++
                }
            }
        } catch (_: Exception) { failed++ }
        ImportResult(added, dup, failed)
    }
}
