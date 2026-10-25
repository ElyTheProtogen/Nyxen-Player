package com.mono.player.playback

import android.media.audiofx.*

/** Effects attach to ExoPlayer's audio session, so they change the actual output.
 *  Band count is device-dependent (often 5). State is re-applied when the session changes. */
object AudioFx {
    var eq: Equalizer? = null; var bass: BassBoost? = null
    var rev: PresetReverb? = null; var virt: Virtualizer? = null
    var enabled = true
    val levels = IntArray(32); var bassStr = 0; var virtStr = 0; var revPreset = 0
    val reverbNames = listOf("Off", "Small Room", "Medium Room", "Large Room", "Medium Hall", "Large Hall", "Plate")

    fun attach(session: Int) {
        release(); if (session == 0) return
        eq = try { Equalizer(0, session) } catch (_: Exception) { null }
        bass = try { BassBoost(0, session) } catch (_: Exception) { null }
        rev = try { PresetReverb(0, session) } catch (_: Exception) { null }
        virt = try { Virtualizer(0, session) } catch (_: Exception) { null }
        apply()
    }
    fun apply() {
        try {
            eq?.let { e -> e.enabled = enabled
                for (b in 0 until e.numberOfBands) e.setBandLevel(b.toShort(), levels[b].toShort()) }
            bass?.let { it.enabled = enabled && bassStr > 0; if (bassStr > 0 && it.strengthSupported) it.setStrength(bassStr.toShort()) }
            virt?.let { it.enabled = enabled && virtStr > 0; if (virtStr > 0 && it.strengthSupported) it.setStrength(virtStr.toShort()) }
            rev?.let { it.enabled = enabled && revPreset > 0; if (revPreset > 0) it.preset = (revPreset - 1).toShort() }
        } catch (_: Exception) { }
    }
    fun setEnabled(v: Boolean) { enabled = v; apply() }
    fun setLevel(b: Int, mB: Int) { levels[b] = mB; apply() }
    fun range(): Pair<Int, Int> = eq?.bandLevelRange?.let { it[0].toInt() to it[1].toInt() } ?: (-1500 to 1500)

    /** Curves over relative band position f in 0..1, result in -1..1 of the max boost. */
    val presets: Map<String, (Float) -> Float> = linkedMapOf(
        "Flat" to { _ -> 0f },
        "Bass Boost" to { f -> (1f - f * 3f).coerceAtLeast(0f) * 0.8f },
        "Treble Boost" to { f -> (f * 3f - 2f).coerceAtLeast(0f) * 0.8f },
        "Vocal" to { f -> (0.6f - kotlin.math.abs(f - 0.55f) * 3f).coerceAtLeast(-0.3f) },
        "Rock" to { f -> kotlin.math.abs(f - 0.5f) * 1.2f - 0.1f },
        "Pop" to { f -> (0.5f - kotlin.math.abs(f - 0.5f) * 2f) * 0.6f },
        "Electronic" to { f -> kotlin.math.abs(f - 0.5f) * 1.4f - 0.2f }
    )
    fun usePreset(name: String) {
        val e = eq ?: return; val (_, hi) = range(); val n = e.numberOfBands.toInt()
        for (b in 0 until n) levels[b] = ((presets[name]!!((b.toFloat() / (n - 1).coerceAtLeast(1))) * hi * 0.6f)).toInt()
        apply()
    }
    fun release() { listOf(eq, bass, rev, virt).forEach { try { it?.release() } catch (_: Exception) { } }
        eq = null; bass = null; rev = null; virt = null }
}
