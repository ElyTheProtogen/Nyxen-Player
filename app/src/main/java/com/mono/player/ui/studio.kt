package com.mono.player.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mono.player.playback.AudioFx

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun Studio() {
    var en by remember { mutableStateOf(AudioFx.enabled) }
    var ver by remember { mutableIntStateOf(0) }
    val eq = AudioFx.eq
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Audio Studio", style = MaterialTheme.typography.headlineSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Effects", Modifier.weight(1f)); Switch(en, { en = it; AudioFx.setEnabled(it) })
        }
        if (eq == null) { Text("Start playing a track once to initialise the effects, or this device does not support them."); return }
        Text("Presets")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AudioFx.presets.keys.forEach { n -> AssistChip({ AudioFx.usePreset(n); ver++ }, { Text(n) }) }
        }
        key(ver) {
            val (lo, hi) = AudioFx.range()
            for (b in 0 until eq.numberOfBands.toInt()) {
                var v by remember { mutableFloatStateOf(AudioFx.levels[b].toFloat()) }
                Text("${eq.getCenterFreq(b.toShort()) / 1000} Hz   ${(v / 100).toInt()} dB")
                Slider(v, { v = it; AudioFx.setLevel(b, it.toInt()) }, valueRange = lo.toFloat()..hi.toFloat())
            }
            var bs by remember { mutableFloatStateOf(AudioFx.bassStr.toFloat()) }
            Text("Bass enhancement")
            Slider(bs, { bs = it; AudioFx.bassStr = it.toInt(); AudioFx.apply() }, valueRange = 0f..1000f)
            var vs by remember { mutableFloatStateOf(AudioFx.virtStr.toFloat()) }
            Text("Stereo widening (device dependent)")
            Slider(vs, { vs = it; AudioFx.virtStr = it.toInt(); AudioFx.apply() }, valueRange = 0f..1000f)
        }
        Text("Reverb")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AudioFx.reverbNames.forEachIndexed { i, n ->
                FilterChip(AudioFx.revPreset == i, { AudioFx.revPreset = i; AudioFx.apply(); ver++ }, { Text(n) })
            }
        }
        OutlinedButton({ AudioFx.usePreset("Flat"); AudioFx.bassStr = 0; AudioFx.virtStr = 0; AudioFx.revPreset = 0; AudioFx.apply(); ver++ }) { Text("Reset") }
    }
}
