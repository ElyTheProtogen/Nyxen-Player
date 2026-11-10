package com.mono.player.playback
import androidx.media3.common.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.mono.player.data.Db
import kotlinx.coroutines.*

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var touched: String? = null
    private var counted: String? = null
    private var noisyReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(false)
            .build()
        AudioFx.load(applicationContext)
        AudioFx.attach(player.audioSessionId)
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) = AudioFx.attach(audioSessionId)
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) { touched = null; counted = null }
            override fun onPlayerError(e: PlaybackException) {
                if (player.hasNextMediaItem()) { player.seekToNextMediaItem(); player.prepare() }
            }
        })
        session = MediaSession.Builder(this, player).build()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY &&
                    getSharedPreferences("mono", MODE_PRIVATE).getBoolean("pauseOnDisconnect", true)) {
                    session?.player?.pause()
                }
            }
        }
        noisyReceiver = receiver
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(receiver, filter)
        // "Recently played" updates after 3 s of listening; the play count goes up after half the song (max 30 s).
        mainScope.launch {
            while (true) {
                delay(1000)
                if (!player.isPlaying) continue
                val id = player.currentMediaItem?.mediaId ?: continue
                val tid = id.toLongOrNull() ?: continue
                val pos = player.currentPosition; val d = player.duration
                if (touched != id && pos >= 3000) { touched = id; scope.launch { Db.get(applicationContext).dao().touch(tid, System.currentTimeMillis()) } }
                val need = if (d > 0) minOf(d / 2, 30_000L) else 30_000L
                if (counted != id && pos >= need) { counted = id; scope.launch { Db.get(applicationContext).dao().played(tid, System.currentTimeMillis()) } }
            }
        }
    }
    override fun onGetSession(info: MediaSession.ControllerInfo) = session
    override fun onDestroy() {
        noisyReceiver?.let { runCatching { unregisterReceiver(it) } }
        noisyReceiver = null
        session?.run { player.release(); release() }
        scope.cancel(); mainScope.cancel(); AudioFx.release(); super.onDestroy()
    }
}
