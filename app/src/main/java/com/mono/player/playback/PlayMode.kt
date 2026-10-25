package com.mono.player.playback
import androidx.media3.common.Player

enum class PlayMode { REPEAT_ALL, REPEAT_ONE, IN_ORDER, SHUFFLE;
    fun next() = entries[(ordinal + 1) % entries.size]
    /** Repeat One overrides next-track; In Order stops at end; Shuffle uses ExoPlayers
     *  no-repeat shuffle order and loops so the queue never dead-ends. */
    fun apply(p: Player) {
        p.shuffleModeEnabled = this == SHUFFLE
        p.repeatMode = when (this) {
            REPEAT_ALL, SHUFFLE -> Player.REPEAT_MODE_ALL
            REPEAT_ONE -> Player.REPEAT_MODE_ONE
            IN_ORDER -> Player.REPEAT_MODE_OFF
        }
    }
}
