package com.mono.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.mono.player.EmbossedIconButton
import com.mono.player.Reveal

/** Text is always white. Light-ended gradients get a dark tint (scrim) so white text stays readable. */
data class AppTheme(val name: String, val top: Color, val mid: Color, val bottom: Color, val scrim: Float)

val Themes = listOf(
    AppTheme("Monochrome", Color.Black, Color(0xFF3A3A3A), Color(0xFFBDBDBD), 0.18f),
    AppTheme("Purple", Color(0xFF7B1FA2), Color(0xFF3A0D52), Color.Black, 0f),
    AppTheme("Blue", Color(0xFF0D47A1), Color(0xFF1E88E5), Color(0xFF81D4FA), 0.35f),
    AppTheme("Green", Color(0xFF1B5E20), Color(0xFF43A047), Color(0xFFA5D6A7), 0.38f),
    AppTheme("Dark red", Color(0xFF8B0000), Color(0xFF3D0000), Color.Black, 0f)
)

@Composable
private fun MenuRow(icon: ImageVector, title: String, info: String, delay: Int, onClick: () -> Unit) {
    Reveal(delay) {
        Surface(onClick, Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(18.dp), color = Color.White.copy(.10f)) {
            ListItem({ Text(title) }, supportingContent = { Text(info) }, leadingContent = { Icon(icon, null) },
                trailingContent = { Icon(Icons.Default.KeyboardArrowRight, null) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        }
    }
}

@Composable
private fun ThemeCard(t: AppTheme, selected: Boolean, delay: Int, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val borderA by animateFloatAsState(if (selected) 1f else 0.15f, tween(300), label = "b")
    val sc by animateFloatAsState(if (selected) 1.02f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "sc")
    Reveal(delay) {
        Box(Modifier.fillMaxWidth().padding(vertical = 5.dp).scale(sc).height(84.dp).clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(t.top, t.mid, t.bottom))).background(Color.Black.copy(alpha = t.scrim))
            .border(2.dp, Color.White.copy(alpha = borderA), RoundedCornerShape(20.dp))
            .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }.padding(horizontal = 20.dp),
            Alignment.CenterStart) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = Color.White)
                AnimatedVisibility(selected, enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(), exit = scaleOut() + fadeOut()) {
                    Icon(Icons.Default.CheckCircle, "Selected theme", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    sub: Int, onSub: (Int) -> Unit, themeIdx: Int, onTheme: (Int) -> Unit,
    autoImport: Boolean, onAuto: (Boolean) -> Unit, scanning: Boolean, onRescan: () -> Unit
) {
    BackHandler(sub != 0) { onSub(0) }
    val titles = listOf("Settings", "Appearance", "", "Library & import")
    AnimatedContent(sub, transitionSpec = {
        if (targetState > initialState)
            (slideInHorizontally(tween(300)) { it } + fadeIn(tween(300))) togetherWith (slideOutHorizontally(tween(300)) { -it / 3 } + fadeOut(tween(300)))
        else
            (slideInHorizontally(tween(300)) { -it / 3 } + fadeIn(tween(300))) togetherWith (slideOutHorizontally(tween(300)) { it } + fadeOut(tween(300)))
    }, label = "settings") { s ->
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (s != 0) { EmbossedIconButton({ onSub(0) }, Modifier.size(44.dp)) { Icon(Icons.Default.ArrowBack, "Back to settings") }; Spacer(Modifier.width(12.dp)) }
                if (titles[s].isNotBlank()) Text(titles[s], style = MaterialTheme.typography.headlineSmall)
            }
            when (s) {
                0 -> Column {
                    MenuRow(Icons.Default.Palette, "Appearance", "Theme: ${Themes[themeIdx].name}", 60) { onSub(1) }
                    MenuRow(Icons.Default.Equalizer, "Audio Studio", "Equalizer, bass, reverb", 120) { onSub(2) }
                    MenuRow(Icons.Default.LibraryMusic, "Library & import", "Auto-import and rescan", 180) { onSub(3) }
                }
                1 -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    Themes.forEachIndexed { i, t -> ThemeCard(t, i == themeIdx, i * 70) { onTheme(i) } }
                }
                2 -> Studio()
                else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Reveal(0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Auto-import music")
                                Text("Finds .mp3, .m4a, .ogg and .wav files on this device", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(autoImport, onAuto)
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
            }
        }
    }
}
