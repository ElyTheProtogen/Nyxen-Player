package com.mono.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mono.player.EmbossedIconButton
import com.mono.player.GlassCard
import com.mono.player.LocalAccent
import com.mono.player.LocalHaptics
import com.mono.player.Reveal

/** Text is always white. Light-ended gradients get a dark tint (scrim) so white text stays readable. */
data class AppTheme(val name: String, val top: Color, val mid: Color, val bottom: Color, val scrim: Float, val accent: Color)

val Themes = listOf(
    AppTheme("Monochrome", Color.Black, Color(0xFF3A3A3A), Color(0xFFBDBDBD), 0.18f, Color(0xFFC4C4C4)),
    AppTheme("Purple", Color(0xFF7B1FA2), Color(0xFF3A0D52), Color.Black, 0f, Color(0xFFB14BFF)),
    AppTheme("Blue", Color(0xFF0D47A1), Color(0xFF1E88E5), Color(0xFF81D4FA), 0.35f, Color(0xFF42A5F5)),
    AppTheme("Green", Color(0xFF1B5E20), Color(0xFF43A047), Color(0xFFA5D6A7), 0.38f, Color(0xFF4CD964)),
    AppTheme("Dark red", Color(0xFF8B0000), Color(0xFF3D0000), Color.Black, 0f, Color(0xFFFF3B3B)),
    AppTheme("Midnight", Color(0xFF0B1026), Color(0xFF151B4D), Color.Black, 0f, Color(0xFF5C7CFF)),
    AppTheme("Sunset", Color(0xFFFF7E5F), Color(0xFFB33A6E), Color(0xFF2B0B3F), 0.30f, Color(0xFFFF8A5B)),
    AppTheme("Ocean", Color(0xFF0F9B8E), Color(0xFF0B4F6C), Color(0xFF020B1A), 0.15f, Color(0xFF1DE9B6)),
    AppTheme("Rose", Color(0xFFE91E63), Color(0xFF7B1038), Color.Black, 0.10f, Color(0xFFFF4081)),
    AppTheme("Neon", Color(0xFF006C7A), Color(0xFF012A33), Color.Black, 0f, Color(0xFF00E5FF)),
    AppTheme("Aurora", Color(0xFF0F8F5F), Color(0xFF3A1C71), Color(0xFF08050F), 0.10f, Color(0xFF7CFFCB)),
    AppTheme("Gold", Color(0xFF7A5C00), Color(0xFF3A2A00), Color.Black, 0f, Color(0xFFFFC53D))
)

@Composable
private fun MenuRow(icon: ImageVector, title: String, info: String, delay: Int, onClick: () -> Unit) {
    val a = LocalAccent.current
    val shape = RoundedCornerShape(22.dp)
    Reveal(delay) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(shape).background(Color.Black.copy(alpha = .40f))
                .border(1.dp, Brush.linearGradient(listOf(a.copy(alpha = .6f), Color.White.copy(alpha = .06f))), shape)
                .clickable(onClick = onClick).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(46.dp).shadow(8.dp, CircleShape, ambientColor = a, spotColor = a).clip(CircleShape)
                .background(Brush.linearGradient(listOf(lerp(a, Color.White, .2f), lerp(a, Color.Black, .5f)))), Alignment.Center) {
                Icon(icon, null, tint = Color.White)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(info, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .7f))
            }
            Icon(Icons.Default.KeyboardArrowRight, null)
        }
    }
}

@Composable
private fun ThemeOrb(t: AppTheme, selected: Boolean, delay: Int, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val hapticsOn = LocalHaptics.current
    val sc by animateFloatAsState(if (selected) 1.12f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "sc")
    val ring by animateFloatAsState(if (selected) 1f else 0f, tween(300), label = "ring")
    Reveal(delay) {
        Column(Modifier.width(78.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(64.dp).graphicsLayer { scaleX = sc; scaleY = sc }
                    .shadow((12 * ring).dp, CircleShape, ambientColor = t.accent, spotColor = t.accent).clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(t.top, t.mid, t.bottom))).background(Color.Black.copy(alpha = t.scrim))
                    .border((2 + 2 * ring).dp, Color.White.copy(alpha = .15f + .85f * ring), CircleShape)
                    .clickable { if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
                Alignment.Center
            ) {
                AnimatedVisibility(selected, enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(), exit = scaleOut() + fadeOut()) {
                    Icon(Icons.Default.Check, "Selected theme", tint = Color.White)
                }
            }
            Text(t.name, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    sub: Int, onSub: (Int) -> Unit, themeIdx: Int, onTheme: (Int) -> Unit,
    autoImport: Boolean, onAuto: (Boolean) -> Unit, scanning: Boolean, onRescan: () -> Unit,
    haptics: Boolean, onHaptics: (Boolean) -> Unit, speed: Float, onSpeed: (Float) -> Unit,
    sleepAt: String, onSleep: (Int) -> Unit
) {
    BackHandler(sub != 0) { onSub(0) }
    val titles = listOf("Settings", "Appearance", "", "Library & import", "Playback", "About")
    AnimatedContent(sub, transitionSpec = {
        if (targetState > initialState)
            (slideInHorizontally(tween(300)) { it } + fadeIn(tween(300))) togetherWith (slideOutHorizontally(tween(300)) { -it / 3 } + fadeOut(tween(300)))
        else
            (slideInHorizontally(tween(300)) { -it / 3 } + fadeIn(tween(300))) togetherWith (slideOutHorizontally(tween(300)) { it } + fadeOut(tween(300)))
    }, label = "settings") { s ->
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (s != 0) { EmbossedIconButton({ onSub(0) }, Modifier.size(44.dp)) { Icon(Icons.Default.ArrowBack, "Back to settings") }; Spacer(Modifier.width(12.dp)) }
                if (titles[s].isNotBlank()) Text(titles[s], style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            when (s) {
                0 -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    MenuRow(Icons.Default.Palette, "Appearance", "Theme: ${Themes[themeIdx].name}", 40) { onSub(1) }
                    MenuRow(Icons.Default.Equalizer, "Audio Studio", "Equalizer, bass, reverb", 90) { onSub(2) }
                    MenuRow(Icons.Default.Speed, "Playback", "Speed, sleep timer, haptics", 140) { onSub(4) }
                    MenuRow(Icons.Default.LibraryMusic, "Library & import", "Auto-import and rescan", 190) { onSub(3) }
                    MenuRow(Icons.Default.Info, "About", "NyxenPlayer", 240) { onSub(5) }
                }
                1 -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    Reveal(0) { Text("Choose a theme", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .8f)) }
                    Spacer(Modifier.height(12.dp))
                    FlowRow(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Arrangement.spacedBy(16.dp), maxItemsInEachRow = 4) {
                        Themes.forEachIndexed { i, t -> ThemeOrb(t, i == themeIdx, i * 45) { onTheme(i) } }
                    }
                }
                2 -> Studio()
                4 -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Reveal(0) {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Text("Playback speed", fontWeight = FontWeight.SemiBold)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { v -> FilterChip(speed == v, { onSpeed(v) }, { Text("${v}x") }) }
                            }
                        }
                    }
                    Reveal(70) {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Text("Sleep timer", fontWeight = FontWeight.SemiBold)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(0, 15, 30, 45, 60).forEach { m -> AssistChip({ onSleep(m) }, { Text(if (m == 0) "Off" else "$m min") }) }
                            }
                            AnimatedVisibility(sleepAt.isNotBlank()) { Text("Music stops at $sleepAt", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    Reveal(140) {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text("Haptic feedback", fontWeight = FontWeight.SemiBold); Text("Small vibrations on button presses", style = MaterialTheme.typography.bodySmall) }
                                Switch(haptics, onHaptics)
                            }
                        }
                    }
                }
                3 -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Reveal(0) {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Auto-import music", fontWeight = FontWeight.SemiBold)
                                    Text("Finds .mp3, .m4a, .ogg and .wav files on this device", style = MaterialTheme.typography.bodySmall)
                                }
                                Switch(autoImport, onAuto)
                            }
                        }
                    }
                    Reveal(80) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            EmbossedIconButton(onRescan, Modifier.height(44.dp), RoundedCornerShape(22.dp)) { Text("Rescan now", Modifier.padding(horizontal = 18.dp)) }
                            AnimatedVisibility(scanning) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
                        }
                    }
                    Reveal(140) { Text("Songs you remove from the library come back on the next scan. Files you imported by hand are kept.", style = MaterialTheme.typography.bodySmall) }
                }
                else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    Reveal(0) {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Text("NyxenPlayer", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("Version 1.0", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(10.dp))
                            Text("A local-first music player. Your music stays on your device and is never uploaded.")
                        }
                    }
                }
            }
        }
    }
}
