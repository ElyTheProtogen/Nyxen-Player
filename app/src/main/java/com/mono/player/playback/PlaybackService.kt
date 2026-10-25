package com.mono.player.playback
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.mono.player.data.Db
import kotlinx.coroutines.*

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        AudioFx.attach(player.audioSessionId)
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) = AudioFx.attach(audioSessionId)
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                val id = item?.mediaId?.toLongOrNull() ?: return
                scope.launch { Db.get(applicationContext).dao().played(id, System.currentTimeMillis()) }
            }
            override fun onPlayerError(e: PlaybackException) {
                if (player.hasNextMediaItem()) { player.seekToNextMediaItem(); player.prepare() }
            }
        })
        session = MediaSession.Builder(this, player).build()
    }
    override fun onGetSession(info: MediaSession.ControllerInfo) = session
    override fun onDestroy() { session?.run { player.release(); release() }; scope.cancel(); AudioFx.release(); super.onDestroy() }
}
