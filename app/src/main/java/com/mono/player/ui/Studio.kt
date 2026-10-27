package com.mono.player.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mono.player.EmbossedIconButton
import com.mono.player.Reveal
import com.mono.player.playback.AudioFx
import kotlinx.coroutines.launch

@Composable
private fun AnimSlider(label: (Float) -> String, shown: FloatArray, idx: Int, target: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    val scope = rememberCoroutineScope()
    val anim = remember { Animatable(shown[idx]) }
    LaunchedEffect(Unit) { anim.animateTo(target, tween(450)); shown[idx] = anim.value }
    Text(label(anim.value))
    Slider(anim.value, { v -> scope.launch { anim.snapTo(v) }; shown[idx] = v; onChange(v) }, valueRange = range)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun Studio() {
    var en by remember { mutableStateOf(AudioFx.enabled) }
    var ver by remember { mutableIntStateOf(0) }
    val eq = AudioFx.eq
    val shownLevels = remember { FloatArray(32) { AudioFx.levels[it].toFloat() } }
    val shownMisc = remember { floatArrayOf(AudioFx.bassStr.toFloat(), AudioFx.virtStr.toFloat()) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Reveal(0) { Text("Audio Studio", style = MaterialTheme.typography.headlineSmall) }
        Reveal(60) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Effects", Modifier.weight(1f)); Switch(en, { en = it; AudioFx.setFxEnabled(it) })
            }
        }
        if (eq == null) { Reveal(120) { Text("Start playing a track once to initialise the effects, or this device does not support them.") }; return }
        Reveal(120) {
            Column {
                Text("Presets")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioFx.presets.keys.forEach { n -> AssistChip({ AudioFx.usePreset(n); ver++ }, { Text(n) }) }
                }
            }
        }
        Reveal(180) {
            Column {
                key(ver) {
                    val (lo, hi) = AudioFx.range()
                    for (b in 0 until eq.numberOfBands.toInt()) {
                        AnimSlider({ "${eq.getCenterFreq(b.toShort()) / 1000} Hz   ${(it / 100).toInt()} dB" }, shownLevels, b,
                            AudioFx.levels[b].toFloat(), lo.toFloat()..hi.toFloat()) { AudioFx.setLevel(b, it.toInt()) }
                    }
                    AnimSlider({ "Bass enhancement" }, shownMisc, 0, AudioFx.bassStr.toFloat(), 0f..1000f) { AudioFx.bassStr = it.toInt(); AudioFx.apply() }
                    AnimSlider({ "Stereo widening (device dependent)" }, shownMisc, 1, AudioFx.virtStr.toFloat(), 0f..1000f) { AudioFx.virtStr = it.toInt(); AudioFx.apply() }
                }
            }
        }
        Reveal(240) {
            Column {
                Text("Reverb")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioFx.reverbNames.forEachIndexed { i, n ->
                        FilterChip(AudioFx.revPreset == i, { AudioFx.revPreset = i; AudioFx.apply(); ver++ }, { Text(n) })
                    }
                }
            }
        }
        Reveal(300) {
            EmbossedIconButton({ AudioFx.usePreset("Flat"); AudioFx.bassStr = 0; AudioFx.virtStr = 0; AudioFx.revPreset = 0; AudioFx.apply(); ver++ },
                Modifier.height(44.dp), RoundedCornerShape(22.dp)) { Text("Reset", Modifier.padding(horizontal = 18.dp)) }
        }
    }
}
