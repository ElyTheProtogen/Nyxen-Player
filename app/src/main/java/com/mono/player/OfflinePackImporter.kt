package com.mono.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.mono.player.data.MusicDao
import com.mono.player.data.Track
import java.io.ByteArrayOutputStream

object OfflinePackImporter {
    suspend fun addToLibrary(context: Context, dao: MusicDao): Int {
        val samples = listOf(
            Sample("nyxen_sample_01", "Obsidian Drift", "Dark Ambient", R.drawable.nyxen_cover_01),
            Sample("nyxen_sample_02", "Neon Pulse", "Electronic Ambient", R.drawable.nyxen_cover_02),
            Sample("nyxen_sample_03", "Aurora Sleep", "Atmospheric", R.drawable.nyxen_cover_03)
        )
        var added = 0
        for (sample in samples) {
            val rawId = context.resources.getIdentifier(sample.resource, "raw", context.packageName)
            if (rawId == 0) continue
            val uri = Uri.parse("android.resource://${context.packageName}/$rawId")
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 48_000L
                val bitmap = BitmapFactory.decodeResource(context.resources, sample.cover)
                val artwork = ByteArrayOutputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
                    out.toByteArray()
                }
                bitmap.recycle()
                val id = dao.insert(
                    Track(
                        uri = uri.toString(),
                        title = sample.title,
                        artist = "Nyxen Studio",
                        album = "Offline Soundscapes",
                        genre = sample.genre,
                        durationMs = duration,
                        addedAt = System.currentTimeMillis(),
                        artwork = artwork
                    )
                )
                if (id != -1L) added++
            } finally {
                retriever.release()
            }
        }
        return added
    }

    private data class Sample(val resource: String, val title: String, val genre: String, val cover: Int)
}
