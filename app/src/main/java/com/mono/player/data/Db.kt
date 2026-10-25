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

@Dao interface Dao {
    @Query("SELECT * FROM tracks") fun tracks(): Flow<List<Track>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(t: Track): Long // -1 = duplicate
    @Update suspend fun update(t: Track)
    @Delete suspend fun delete(t: Track) // library entry only, never the file
    @Query("UPDATE tracks SET playCount = playCount + 1, lastPlayed = :now WHERE id = :id")
    suspend fun played(id: Long, now: Long)
    @Query("SELECT t.* FROM tracks t JOIN playlist_tracks p ON t.id=p.trackId WHERE p.playlistId=:id ORDER BY p.position")
    suspend fun playlistTracks(id: Long): List<Track>
    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId=:id") suspend fun count(id: Long): Int
    @Insert suspend fun addPlaylist(p: Playlist): Long
    @Query("SELECT * FROM playlists") fun playlists(): Flow<List<Playlist>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addToPlaylist(pt: PlaylistTrack)
    @Query("DELETE FROM playlists WHERE id=:id") suspend fun deletePlaylist(id: Long)
    @Query("DELETE FROM playlist_tracks WHERE playlistId=:id") suspend fun clearPlaylist(id: Long)
}
@Database(entities = [Track::class, Playlist::class, PlaylistTrack::class], version = 1, exportSchema = false)
abstract class Db : RoomDatabase() {
    abstract fun dao(): Dao
    companion object {
        @Volatile private var i: Db? = null
        fun get(c: Context) = i ?: synchronized(this) {
            i ?: Room.databaseBuilder(c.applicationContext, Db::class.java, "mono.db").build().also { i = it }
        }
    }
}
