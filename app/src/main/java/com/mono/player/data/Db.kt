package com.mono.player.data
import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tracks", indices = [Index("uri", unique = true)])
data class Track(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String, val title: String, val artist: String, val album: String,
    val genre: String, val durationMs: Long, val addedAt: Long,
    val playCount: Int = 0, val lastPlayed: Long = 0, val favorite: Boolean = false,
    val artwork: ByteArray? = null
)
@Entity(tableName = "playlists") data class Playlist(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)
@Entity(tableName = "playlist_tracks", primaryKeys = ["playlistId", "trackId"])
data class PlaylistTrack(val playlistId: Long, val trackId: Long, val position: Int)

@Dao interface MusicDao {
    @Query("SELECT id, uri, title, artist, album, genre, durationMs, addedAt, playCount, lastPlayed, favorite, NULL AS artwork FROM tracks") fun tracks(): Flow<List<Track>>
    @Query("SELECT artwork FROM tracks WHERE id=:id") suspend fun artwork(id: Long): ByteArray?
    @Query("UPDATE tracks SET favorite=:f WHERE id=:id") suspend fun setFav(id: Long, f: Boolean)
    @Query("SELECT title || '|' || artist || '|' || durationMs FROM tracks") suspend fun keys(): List<String>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(t: Track): Long // -1 = duplicate
    @Update suspend fun update(t: Track)
    @Delete suspend fun delete(t: Track) // library entry only, never the file
    @Query("UPDATE tracks SET playCount = playCount + 1, lastPlayed = :now WHERE id = :id")
    suspend fun played(id: Long, now: Long)
    @Query("SELECT t.* FROM tracks t JOIN playlist_tracks p ON t.id=p.trackId WHERE p.playlistId=:id ORDER BY p.position")
    suspend fun playlistTracks(id: Long): List<Track>
    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId=:id") suspend fun count(id: Long): Int
    @Query("SELECT COUNT(*) FROM tracks WHERE title=:t AND artist=:a AND durationMs=:d") suspend fun similar(t: String, a: String, d: Long): Int
    @Query("DELETE FROM tracks") suspend fun wipeTracks()
    @Query("DELETE FROM playlist_tracks") suspend fun wipePlaylistTracks()
    @Query("DELETE FROM playlists") suspend fun wipePlaylists()
    @Insert suspend fun addPlaylist(p: Playlist): Long
    @Query("SELECT * FROM playlists") fun playlists(): Flow<List<Playlist>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addToPlaylist(pt: PlaylistTrack)
    @Query("DELETE FROM playlists WHERE id=:id") suspend fun deletePlaylist(id: Long)
    @Query("DELETE FROM playlist_tracks WHERE playlistId=:id") suspend fun clearPlaylist(id: Long)
}
@Database(entities = [Track::class, Playlist::class, PlaylistTrack::class], version = 1, exportSchema = false)
abstract class Db : RoomDatabase() {
    abstract fun dao(): MusicDao
    companion object {
        @Volatile private var i: Db? = null
        fun get(c: Context) = i ?: synchronized(this) {
            i ?: Room.databaseBuilder(c.applicationContext, Db::class.java, "mono.db").build().also { i = it }
        }
    }
}
