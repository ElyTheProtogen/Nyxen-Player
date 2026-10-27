package com.mono.player.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mono.player.EmbossedIconButton
import com.mono.player.GlassCard
import com.mono.player.Reveal
import com.mono.player.playback.AudioFx
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun AnimSlider(left: String, right: (Float) -> String, shown: FloatArray, idx: Int, target: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    val scope = rememberCoroutineScope()
    val anim = remember { Animatable(shown[idx]) }
    LaunchedEffect(Unit) { anim.animateTo(target, tween(450)); shown[idx] = anim.value }
    Row(Modifier.fillMaxWidth()) { Text(left); Spacer(Modifier.weight(1f)); Text(right(anim.value), fontWeight = FontWeight.SemiBold) }
    Slider(anim.value, { v -> scope.launch { anim.snapTo(v) }; shown[idx] = v; onChange(v) }, valueRange = range)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun Studio() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var en by remember { mutableStateOf(AudioFx.enabled) }
    var ver by remember { mutableIntStateOf(0) }
    var cur by remember { mutableStateOf(AudioFx.current) }
    var customs by remember { mutableStateOf(AudioFx.customs(ctx)) }
    var saveDlg by remember { mutableStateOf(false) }
    var applied by remember { mutableStateOf(false) }
    val eq = AudioFx.eq
    val shownLevels = remember { FloatArray(32) { AudioFx.levels[it].toFloat() } }
    val shownMisc = remember { floatArrayOf(AudioFx.bassStr.toFloat(), AudioFx.virtStr.toFloat()) }
    DisposableEffect(Unit) { onDispose { AudioFx.save(ctx) } }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Reveal(0) {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Equalizer", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Switch(en, { en = it; AudioFx.setFxEnabled(it) })
                    Spacer(Modifier.width(10.dp))
                    EmbossedIconButton({ AudioFx.usePreset("Flat"); AudioFx.bassStr = 0; AudioFx.virtStr = 0; AudioFx.revPreset = 0; AudioFx.apply(); cur = "Flat"; ver++ },
                        Modifier.height(36.dp), RoundedCornerShape(18.dp)) { Text("Reset", Modifier.padding(horizontal = 14.dp)) }
                }
            }
        }
        if (eq == null) { Reveal(60) { Text("Start playing a track once to initialise the effects, or this device does not support them.") }; return }
        Reveal(60) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Presets", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioFx.presets.keys.forEach { n -> FilterChip(cur == n, { AudioFx.usePreset(n); cur = n; ver++ }, { Text(n) }) }
                }
                Spacer(Modifier.height(10.dp))
                Text("My presets", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    customs.keys.forEach { n ->
                        FilterChip(cur == n, { customs[n]?.let { AudioFx.useLevels(it) }; AudioFx.current = n; cur = n; ver++ }, { Text(n) },
                            trailingIcon = { Icon(Icons.Default.Close, "Delete preset $n", Modifier.size(16.dp).clickable { AudioFx.deleteCustom(ctx, n); customs = AudioFx.customs(ctx) }) })
                    }
                    AssistChip({ saveDlg = true }, { Text("+ Save current preset") })
                }
            }
        }
        Reveal(120) {
            GlassCard(Modifier.fillMaxWidth()) {
                key(ver) {
                    AnimSlider("Bass boost", { "${(it / 10).toInt()}%" }, shownMisc, 0, AudioFx.bassStr.toFloat(), 0f..1000f) { AudioFx.bassStr = it.toInt(); AudioFx.apply() }
                    AnimSlider("Stereo widening", { "${(it / 10).toInt()}%" }, shownMisc, 1, AudioFx.virtStr.toFloat(), 0f..1000f) { AudioFx.virtStr = it.toInt(); AudioFx.apply() }
                }
            }
        }
        Reveal(180) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Frequency bands", fontWeight = FontWeight.SemiBold)
                key(ver) {
                    val (lo, hi) = AudioFx.range()
                    for (b in 0 until eq.numberOfBands.toInt()) {
                        AnimSlider("${eq.getCenterFreq(b.toShort()) / 1000} Hz", { "%+d dB".format((it / 100).toInt()) }, shownLevels, b,
                            AudioFx.levels[b].toFloat(), lo.toFloat()..hi.toFloat()) { AudioFx.setLevel(b, it.toInt()); cur = "Custom" }
                    }
                }
            }
        }
        Reveal(240) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Reverb", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioFx.reverbNames.forEachIndexed { i, n -> FilterChip(AudioFx.revPreset == i, { AudioFx.revPreset = i; AudioFx.apply(); ver++ }, { Text(n) }) }
                }
            }
        }
        Reveal(300) {
            EmbossedIconButton({ AudioFx.save(ctx); applied = true; scope.launch { delay(1500); applied = false } },
                Modifier.fillMaxWidth().height(52.dp), RoundedCornerShape(26.dp)) {
                Crossfade(applied, label = "apply") { ok -> Text(if (ok) "Applied" else "Apply", fontWeight = FontWeight.Bold) }
            }
        }
    }
    if (saveDlg) {
        var name by remember { mutableStateOf("") }
        AlertDialog({ saveDlg = false }, title = { Text("Save preset") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("Preset name") }) },
            confirmButton = { TextButton({ val n = AudioFx.saveCustom(ctx, name); if (n.isNotEmpty()) { customs = AudioFx.customs(ctx); AudioFx.current = n; cur = n }; saveDlg = false }) { Text("Save") } },
            dismissButton = { TextButton({ saveDlg = false }) { Text("Cancel") } })
    }
}
