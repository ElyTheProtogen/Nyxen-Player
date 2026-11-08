package com.mono.player.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mono.player.EmbossedIconButton
import com.mono.player.GlassCard
import com.mono.player.LocalAccent
import com.mono.player.Reveal
import com.mono.player.playback.AudioFx
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Smooth line + soft fill showing the current equalizer shape. Reads the animated values only while drawing. */
@Composable
private fun EqCurve(anims: List<Animatable<Float, AnimationVector1D>>, lo: Float, hi: Float, accent: Color, modifier: Modifier) {
    Canvas(modifier) {
        val n = anims.size; if (n < 2) return@Canvas
        val w = size.width; val h = size.height
        fun x(i: Int) = w * (i + 0.5f) / n
        fun y(v: Float) = h * (hi - v) / (hi - lo)
        val line = Path().apply {
            moveTo(x(0), y(anims[0].value))
            for (i in 1 until n) { val mx = (x(i - 1) + x(i)) / 2f; cubicTo(mx, y(anims[i - 1].value), mx, y(anims[i].value), x(i), y(anims[i].value)) }
        }
        val fill = Path().apply { addPath(line); lineTo(x(n - 1), h); lineTo(x(0), h); close() }
        drawPath(fill, Brush.verticalGradient(listOf(accent.copy(alpha = .38f), Color.Transparent)))
        drawLine(Color.White.copy(alpha = .15f), Offset(0f, y(0f)), Offset(w, y(0f)), 1.dp.toPx())
        drawPath(line, accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Mixer-style vertical fader: drag or tap anywhere on the track. Top is the maximum boost. */
@Composable
private fun Fader(value: Float, lo: Float, hi: Float, label: String, enabled: Boolean, onChange: (Float) -> Unit) {
    val accent = LocalAccent.current
    Column(Modifier.width(48.dp).graphicsLayer { alpha = if (enabled) 1f else 0.4f }, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("%+d".format((value / 100f).toInt()), fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
        Box(
            Modifier.height(190.dp).width(48.dp)
                .pointerInput(lo, hi) {
                    detectVerticalDragGestures(onDragStart = { o -> onChange(hi - (o.y.coerceIn(0f, size.height.toFloat()) / size.height) * (hi - lo)) }) { change, _ ->
                        change.consume(); onChange(hi - (change.position.y.coerceIn(0f, size.height.toFloat()) / size.height) * (hi - lo))
                    }
                }
                .pointerInput(lo, hi) { detectTapGestures { o -> onChange(hi - (o.y.coerceIn(0f, size.height.toFloat()) / size.height) * (hi - lo)) } }
                .drawBehind {
                    val cx = size.width / 2f; val tw = 6.dp.toPx()
                    val zeroY = size.height * (hi / (hi - lo)); val thumbY = size.height * ((hi - value) / (hi - lo))
                    drawRoundRect(Color.White.copy(alpha = .15f), Offset(cx - tw / 2, 0f), Size(tw, size.height), CornerRadius(tw / 2))
                    drawRoundRect(accent, Offset(cx - tw / 2, minOf(zeroY, thumbY)), Size(tw, kotlin.math.abs(zeroY - thumbY)), CornerRadius(tw / 2))
                    drawCircle(accent.copy(alpha = .35f), 18.dp.toPx(), Offset(cx, thumbY))
                    drawCircle(Color.White, 11.dp.toPx(), Offset(cx, thumbY))
                }
        )
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = .7f))
    }
}

/** Rotary-style control: drag up or down on it. Shows its value as a ring. */
@Composable
private fun Knob(label: String, value: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    val accent = LocalAccent.current
    val cur by rememberUpdatedState(value)
    Column(Modifier.graphicsLayer { alpha = if (enabled) 1f else 0.4f }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(96.dp).pointerInput(Unit) {
                detectVerticalDragGestures { change, dy -> change.consume(); onChange((cur - dy * 4f).coerceIn(0f, 1000f)) }
            },
            Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                val st = Stroke(10.dp.toPx(), cap = StrokeCap.Round)
                drawArc(Color.White.copy(alpha = .15f), 135f, 270f, false, style = st)
                drawArc(accent, 135f, 270f * (value / 1000f), false, style = st)
            }
            Text("${(value / 10).toInt()}%", fontWeight = FontWeight.Bold, color = Color.White)
        }
        Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = .75f))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun Studio() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var en by remember { mutableStateOf(AudioFx.enabled) }
    var cur by remember { mutableStateOf(AudioFx.current) }
    var customs by remember { mutableStateOf(AudioFx.customs(ctx)) }
    var saveDlg by remember { mutableStateOf(false) }
    var applied by remember { mutableStateOf(false) }
    var bass by remember { mutableFloatStateOf(AudioFx.bassStr.toFloat()) }
    var virt by remember { mutableFloatStateOf(AudioFx.virtStr.toFloat()) }
    var rev by remember { mutableIntStateOf(AudioFx.revPreset) }
    val eq = AudioFx.eq
    val n = eq?.numberOfBands?.toInt() ?: 5
    val (lo, hi) = AudioFx.range()
    val anims = remember(n) { List(n) { Animatable(AudioFx.levels[it].toFloat()) } }
    DisposableEffect(Unit) { onDispose { AudioFx.save(ctx) } }
    fun glideToLevels() { scope.launch { anims.forEachIndexed { i, a -> launch { a.animateTo(AudioFx.levels[i].toFloat(), tween(450)) } } } }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Reveal(0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Audio Studio", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text(if (en) "Effects on · $cur" else "Effects off", fontSize = 13.sp, color = Color.White.copy(alpha = .65f))
                }
                EmbossedIconButton({ en = !en; AudioFx.setFxEnabled(en) }, Modifier.size(52.dp).graphicsLayer { alpha = if (en) 1f else 0.5f }) {
                    Icon(Icons.Default.PowerSettingsNew, if (en) "Turn effects off" else "Turn effects on")
                }
            }
        }
        if (eq == null) { Reveal(60) { Text("Start playing a track once to initialise the effects, or this device does not support them.") }; return }
        Reveal(60) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioFx.presets.keys.forEach { p -> FilterChip(cur == p, { AudioFx.usePreset(p); cur = p; glideToLevels() }, { Text(p) }) }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    customs.keys.forEach { p ->
                        FilterChip(cur == p, { customs[p]?.let { AudioFx.useLevels(it) }; AudioFx.current = p; cur = p; glideToLevels() }, { Text(p) },
                            trailingIcon = { Icon(Icons.Default.Close, "Delete preset $p", Modifier.size(16.dp).clickable { AudioFx.deleteCustom(ctx, p); customs = AudioFx.customs(ctx) }) })
                    }
                    AssistChip({ saveDlg = true }, { Text("+ Save current preset") })
                }
            }
        }
        Reveal(120) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Equalizer", fontWeight = FontWeight.SemiBold)
                EqCurve(anims, lo.toFloat(), hi.toFloat(), LocalAccent.current, Modifier.fillMaxWidth().height(90.dp).padding(vertical = 6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    for (b in 0 until n) {
                        Fader(anims[b].value, lo.toFloat(), hi.toFloat(), "${eq.getCenterFreq(b.toShort()) / 1000}Hz", en) { v ->
                            AudioFx.setLevel(b, v.toInt()); cur = "Custom"; scope.launch { anims[b].snapTo(v) }
                        }
                    }
                }
            }
        }
        Reveal(180) {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Knob("Bass boost", bass, en) { bass = it; AudioFx.bassStr = it.toInt(); AudioFx.apply() }
                    Knob("Stereo widening", virt, en) { virt = it; AudioFx.virtStr = it.toInt(); AudioFx.apply() }
                }
                Text("Drag a knob up or down. Widening depends on your phone.", fontSize = 11.sp, color = Color.White.copy(alpha = .6f), modifier = Modifier.padding(top = 6.dp))
            }
        }
        Reveal(240) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Reverb", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioFx.reverbNames.forEachIndexed { i, nm -> FilterChip(rev == i, { rev = i; AudioFx.revPreset = i; AudioFx.apply() }, { Text(nm) }) }
                }
            }
        }
        Reveal(300) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                EmbossedIconButton({
                    AudioFx.usePreset("Flat"); AudioFx.bassStr = 0; AudioFx.virtStr = 0; AudioFx.revPreset = 0; AudioFx.apply()
                    cur = "Flat"; bass = 0f; virt = 0f; rev = 0; glideToLevels()
                }, Modifier.height(52.dp), RoundedCornerShape(26.dp)) { Text("Reset", Modifier.padding(horizontal = 22.dp)) }
                EmbossedIconButton({ AudioFx.save(ctx); applied = true; scope.launch { delay(1500); applied = false } },
                    Modifier.weight(1f).height(52.dp), RoundedCornerShape(26.dp)) {
                    Crossfade(applied, label = "apply") { ok -> Text(if (ok) "Applied" else "Apply", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
    if (saveDlg) {
        var name by remember { mutableStateOf("") }
        AlertDialog({ saveDlg = false }, title = { Text("Save preset") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("Preset name") }) },
            confirmButton = { TextButton({ val nm = AudioFx.saveCustom(ctx, name); if (nm.isNotEmpty()) { customs = AudioFx.customs(ctx); AudioFx.current = nm; cur = nm }; saveDlg = false }) { Text("Save") } },
            dismissButton = { TextButton({ saveDlg = false }) { Text("Cancel") } })
    }
}
