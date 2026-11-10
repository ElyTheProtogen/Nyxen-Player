package com.mono.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.mono.player.R
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mono.player.EmbossedIconButton
import com.mono.player.GlassCard
import com.mono.player.LocalAccent
import com.mono.player.LocalHaptics
import com.mono.player.Reveal
import com.mono.player.data.Track
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Text is always white. Light-ended gradients get a dark tint (scrim) so white text stays readable. */
data class AppTheme(val name: String, val top: Color, val mid: Color, val bottom: Color, val scrim: Float, val accent: Color)

private val BuiltInThemes = listOf(
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
    AppTheme("Gold", Color(0xFF7A5C00), Color(0xFF3A2A00), Color.Black, 0f, Color(0xFFFFC53D)),
    AppTheme("Cyberpunk", Color(0xFFFF2E93), Color(0xFF3B0A5E), Color(0xFF020B1F), 0.10f, Color(0xFF00F0FF)),
    AppTheme("Mint", Color(0xFF2BB39A), Color(0xFF0B3D3A), Color.Black, 0.12f, Color(0xFF6CFFD9)),
    AppTheme("Lavender", Color(0xFF8E7CC3), Color(0xFF3B2A5F), Color(0xFF0B0814), 0.20f, Color(0xFFC4B5FD)),
    AppTheme("Ice", Color(0xFF5BC0EB), Color(0xFF1B4965), Color(0xFF0B132B), 0.25f, Color(0xFF9AE6FF)),
    AppTheme("Lava", Color(0xFFFF512F), Color(0xFF8B1A00), Color.Black, 0.10f, Color(0xFFFF7A3D)),
    AppTheme("Forest", Color(0xFF2D6A4F), Color(0xFF081C15), Color.Black, 0.05f, Color(0xFF74C69D)),
    AppTheme("Peach", Color(0xFFFF9A8B), Color(0xFFB5476B), Color(0xFF2B0B2B), 0.30f, Color(0xFFFFB199)),
    AppTheme("Slate", Color(0xFF475569), Color(0xFF1E293B), Color(0xFF020617), 0.05f, Color(0xFF94A3B8)),
    AppTheme("Candy", Color(0xFFFF6FD8), Color(0xFF7C3AED), Color(0xFF0B0B2B), 0.15f, Color(0xFFFF9CE8)),
    AppTheme("Emerald", Color(0xFF00B09B), Color(0xFF004E3F), Color.Black, 0.10f, Color(0xFF2EE6A6)),
    AppTheme("Amber", Color(0xFFFFB347), Color(0xFF7A3E00), Color.Black, 0.30f, Color(0xFFFFC266)),
    AppTheme("Ruby", Color(0xFF9B111E), Color(0xFF3A0610), Color.Black, 0f, Color(0xFFFF4D5E)),
    AppTheme("Sky", Color(0xFF87CEEB), Color(0xFF2F6DB5), Color(0xFF0A1A3A), 0.35f, Color(0xFF7CC8FF)),
    AppTheme("Mono Dark", Color(0xFF1A1A1A), Color(0xFF0D0D0D), Color.Black, 0f, Color(0xFFFFFFFF))
)

val Themes = mutableListOf<AppTheme>().apply { addAll(BuiltInThemes) }

fun restoreCustomThemes(context: android.content.Context) {
    while (Themes.size > BuiltInThemes.size) Themes.removeAt(Themes.lastIndex)
    val saved = context.getSharedPreferences("mono", android.content.Context.MODE_PRIVATE).getString("customThemes", null) ?: return
    runCatching {
        val array = org.json.JSONArray(saved)
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            Themes.add(AppTheme(item.getString("name"), Color(item.getInt("top")), Color(item.getInt("mid")), Color(item.getInt("bottom")), item.getDouble("scrim").toFloat(), Color(item.getInt("accent"))))
        }
    }
}

private fun persistCustomThemes(context: android.content.Context) {
    val array = org.json.JSONArray()
    Themes.drop(BuiltInThemes.size).forEach { theme ->
        array.put(org.json.JSONObject().put("name", theme.name).put("top", theme.top.toArgb()).put("mid", theme.mid.toArgb()).put("bottom", theme.bottom.toArgb()).put("scrim", theme.scrim.toDouble()).put("accent", theme.accent.toArgb()))
    }
    context.getSharedPreferences("mono", android.content.Context.MODE_PRIVATE).edit().putString("customThemes", array.toString()).apply()
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, info: String, delay: Int, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(20.dp)
    Reveal(delay) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(shape)
                .background(Color(0xE8121219))
                .border(1.dp, Color.White.copy(alpha = .09f), shape)
                .clickable(onClick = onClick).animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                .background(Brush.linearGradient(listOf(accent.copy(alpha = .23f), accent.copy(alpha = .10f)))),
                Alignment.Center) {
                Icon(icon, null, Modifier.size(20.dp), tint = accent)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, letterSpacing = (-.1).sp, color = Color.White)
                Text(info, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .56f), maxLines = 1)
            }
            Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = Color.White.copy(alpha = .44f))
        }
    }
}
@Composable
private fun ThemeOrb(t: AppTheme, selected: Boolean, delay: Int, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val hapticsOn = LocalHaptics.current
    val scale by animateFloatAsState(if (selected) 1.08f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "theme-scale")
    Reveal(delay) {
        Column(Modifier.width(78.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(58.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(CircleShape).background(Brush.linearGradient(listOf(t.top, t.mid, t.bottom)))
                    .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Color.White.copy(alpha = .16f), CircleShape)
                    .clickable { if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
                Alignment.Center
            ) {
                if (selected) Icon(Icons.Default.Check, "Selected theme", tint = Color.White)
            }
            Text(t.name, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelSmall, color = if (selected) Color.White else Color.White.copy(alpha = .62f), maxLines = 1)
        }
    }
}@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    sub: Int, onSub: (Int) -> Unit, themeIdx: Int, onTheme: (Int) -> Unit,
    autoImport: Boolean, onAuto: (Boolean) -> Unit, scanning: Boolean, onRescan: () -> Unit,
    haptics: Boolean, onHaptics: (Boolean) -> Unit, speed: Float, onSpeed: (Float) -> Unit,
    sleepAt: String, onSleep: (Int) -> Unit, onSleepEndOfTrack: () -> Unit,
    profName: String, profEmail: String, onProfile: (String, String) -> Unit, onSignOut: () -> Unit, onWipe: () -> Unit,
    tracks: List<Track>, coverMatch: Boolean, onCoverMatch: (Boolean) -> Unit, onImportFolder: () -> Unit, profVerified: Boolean,
    autoResume: Boolean, onAutoResume: (Boolean) -> Unit, rememberQueue: Boolean, onRememberQueue: (Boolean) -> Unit,
    autoplaySimilar: Boolean, onAutoplaySimilar: (Boolean) -> Unit, keepAwake: Boolean, onKeepAwake: (Boolean) -> Unit,
    wallpaperIdx: Int, onWallpaper: (Int) -> Unit, onImportSamples: () -> Unit, samplesImported: Boolean,
    buttonSounds: Boolean, onButtonSounds: (Boolean) -> Unit, customFontName: String, onImportFont: () -> Unit,
    pauseOnDisconnect: Boolean, onPauseOnDisconnect: (Boolean) -> Unit, sleepFade: Boolean, onSleepFade: (Boolean) -> Unit
) {
    BackHandler(sub != 0) { onSub(0) }
    val titles = listOf("Settings", "Appearance", "", "Library & import", "Playback", "About", "Account", "Listening stats", "Offline art & sound", "Fonts & custom themes")
    AnimatedContent(sub, transitionSpec = {
        if (targetState > initialState)
            (slideInHorizontally(tween(300)) { it } + fadeIn(tween(300))) togetherWith (slideOutHorizontally(tween(300)) { -it / 3 } + fadeOut(tween(300)))
        else
            (slideInHorizontally(tween(300)) { -it / 3 } + fadeIn(tween(300))) togetherWith (slideOutHorizontally(tween(300)) { it } + fadeOut(tween(300)))
    }, label = "settings") { s ->
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 14.dp, start = 2.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                if (s != 0) {
                    EmbossedIconButton({ onSub(0) }, Modifier.size(42.dp)) { Icon(Icons.Default.ArrowBack, "Back to settings") }
                    Spacer(Modifier.width(12.dp))
                }
                if (titles[s].isNotBlank()) {
                    Column(Modifier.weight(1f)) {
                        Text(if (s == 0) "NYXEN  /  CONTROL ROOM" else "SETTINGS",
                            fontSize = 9.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Black, color = LocalAccent.current)
                        Text(titles[s], Modifier.padding(top = 2.dp), style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black, color = Color.White, maxLines = 1)
                    }
                }
            }
            when (s) {
                0 -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    MenuRow(Icons.Default.Person, profName.ifBlank { "Account" }, profEmail.ifBlank { "Manage your profile" }, 0) { onSub(6) }
                    MenuRow(Icons.Default.Palette, "Appearance", "${Themes.size} themes · ${Themes[themeIdx].name} selected", 40) { onSub(1) }
                    MenuRow(Icons.Default.Wallpaper, "Offline art & sound", "Wallpapers and original sample tracks", 65) { onSub(8) }
                    MenuRow(Icons.Default.Equalizer, "Audio Studio", "Equalizer, bass, reverb", 90) { onSub(2) }
                    MenuRow(Icons.Default.Speed, "Playback", "Speed, sleep timer, haptics", 140) { onSub(4) }
                    MenuRow(Icons.Default.LibraryMusic, "Library & import", "Auto-import and rescan", 190) { onSub(3) }
                    MenuRow(Icons.Default.BarChart, "Listening stats", "Top songs, artists and time listened", 215) { onSub(7) }
                    MenuRow(Icons.Default.AutoAwesome, "Fonts & custom themes", "Import a font and save your own palettes", 235) { onSub(9) }
                    MenuRow(Icons.Default.Info, "About", "Nyxen Player · version 1.3", 260) { onSub(5) }
                }
                1 -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    Reveal(0) {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("MATCH COLORS TO ALBUM ART", fontWeight = FontWeight.Bold, letterSpacing = .8.sp, fontSize = 11.sp)
                                    Text("The accent color follows the playing song's artwork", style = MaterialTheme.typography.bodySmall)
                                }
                                Switch(coverMatch, onCoverMatch)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Reveal(30) { Text("COLOR SYSTEM", style = MaterialTheme.typography.labelLarge, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = .62f)) }
                    Spacer(Modifier.height(12.dp))
                    FlowRow(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Arrangement.spacedBy(18.dp), maxItemsInEachRow = 4) {
                        Themes.forEachIndexed { i, t -> ThemeOrb(t, i == themeIdx, i * 45) { onTheme(i) } }
                    }
                }
                2 -> Studio()
                4 -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Reveal(0) {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text("BUTTON SOUNDS", fontWeight = FontWeight.Bold, letterSpacing = .8.sp, fontSize = 11.sp); Text("Play a quiet click on Nyxen buttons", style = MaterialTheme.typography.bodySmall) }
                                Switch(buttonSounds, onButtonSounds)
                            }
                        }
                    }
                    Reveal(0) {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text("PAUSE WHEN HEADPHONES DISCONNECT", fontWeight = FontWeight.Bold, letterSpacing = .8.sp, fontSize = 11.sp); Text("Pause when wired or Bluetooth audio disconnects", style = MaterialTheme.typography.bodySmall) }
                                Switch(pauseOnDisconnect, onPauseOnDisconnect)
                            }
                        }
                    }
                    Reveal(0) {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text("RESUME PLAYBACK ON LAUNCH", fontWeight = FontWeight.Bold, letterSpacing = .8.sp, fontSize = 11.sp); Text("Automatically continue your last song and position", style = MaterialTheme.typography.bodySmall) }
                                Switch(autoResume, onAutoResume)
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text("SAVE PLAYBACK QUEUE", fontWeight = FontWeight.Bold, letterSpacing = .8.sp, fontSize = 11.sp); Text("Restore the same songs and position next time you open Nyxen", style = MaterialTheme.typography.bodySmall) }
                                Switch(rememberQueue, onRememberQueue)
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text("AUTO-PLAY SIMILAR TRACKS", fontWeight = FontWeight.Bold, letterSpacing = .8.sp, fontSize = 11.sp); Text("Add an unqueued artist or genre match as playback reaches the end of the queue", style = MaterialTheme.typography.bodySmall) }
                                Switch(autoplaySimilar, onAutoplaySimilar)
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text("KEEP SCREEN AWAKE", fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, fontSize = 11.sp); Text("Prevent the display from sleeping while music plays", style = MaterialTheme.typography.bodySmall) }
                                Switch(keepAwake, onKeepAwake)
                            }
                        }
                    }
                    Reveal(0) {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Text("PLAYBACK SPEED", fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, fontSize = 11.sp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { v -> FilterChip(speed == v, { onSpeed(v) }, { Text("${v}x") }) }
                            }
                        }
                    }
                    Reveal(70) {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Text("SLEEP TIMER", fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, fontSize = 11.sp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(0, 5, 15, 30, 45, 60, 90).forEach { m -> AssistChip({ onSleep(m) }, { Text(if (m == 0) "Off" else "$m min") }) }
                                AssistChip(onSleepEndOfTrack, { Text("End of track") })
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text("FADE OUT BEFORE STOPPING", fontWeight = FontWeight.Bold, letterSpacing = .8.sp, fontSize = 11.sp); Text("Gradually lower the volume during the final 20 seconds", style = MaterialTheme.typography.bodySmall) }
                                Switch(sleepFade, onSleepFade)
                            }
                            AnimatedVisibility(sleepAt.isNotBlank()) { Text("Music stops at $sleepAt", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    Reveal(140) {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text("HAPTIC FEEDBACK", fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, fontSize = 11.sp); Text("Small vibrations on button presses", style = MaterialTheme.typography.bodySmall) }
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
                                    Text("AUTO-IMPORT MUSIC", fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, fontSize = 11.sp)
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
                    Reveal(110) {
                        EmbossedIconButton(onImportFolder, Modifier.height(44.dp), RoundedCornerShape(22.dp)) { Text("Import a folder", Modifier.padding(horizontal = 18.dp)) }
                    }
                    Reveal(140) { Text("Songs you remove from the library come back on the next scan. Files you imported by hand are kept.", style = MaterialTheme.typography.bodySmall) }
                }

                9 -> CustomizationSettings(customFontName, onImportFont, onTheme)
                8 -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Reveal(0) {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Text("WALLPAPER COLLECTION", fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, fontSize = 11.sp)
                            Text("Eight original Nyxen wallpapers, available fully offline.", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(14.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                val names = listOf("Theme", "Obsidian", "Violet", "Neon", "Aurora", "Ember", "Deep Sea", "Starlight", "Prism")
                                (0..8).forEach { index ->
                                    Column(Modifier.width(78.dp).clickable { onWallpaper(index) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(Modifier.size(72.dp, 112.dp).clip(RoundedCornerShape(13.dp))
                                            .border(if (wallpaperIdx == index) 2.dp else 1.dp, if (wallpaperIdx == index) LocalAccent.current else Color.White.copy(.18f), RoundedCornerShape(13.dp))) {
                                            if (index == 0) {
                                                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF08080B), Color(0xFF4A4A4A), Color.Black))))
                                            } else {
                                                val id = when (index) {
                                                    1 -> R.drawable.nyxen_wallpaper_01
                                                    2 -> R.drawable.nyxen_wallpaper_02
                                                    3 -> R.drawable.nyxen_wallpaper_03
                                                    4 -> R.drawable.nyxen_wallpaper_04
                                                    5 -> R.drawable.nyxen_wallpaper_05
                                                    6 -> R.drawable.nyxen_wallpaper_06
                                                    7 -> R.drawable.nyxen_wallpaper_07
                                                    else -> R.drawable.nyxen_wallpaper_08
                                                }
                                                Image(painterResource(id), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                            }
                                            if (wallpaperIdx == index) Icon(Icons.Default.CheckCircle, "Selected", Modifier.align(Alignment.TopEnd).padding(5.dp).size(18.dp), tint = LocalAccent.current)
                                        }
                                        Text(names[index], Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                    Reveal(90) {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Text("OFFLINE SOUNDSCAPES", fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, fontSize = 11.sp)
                            Text("Three original 48-second stereo instrumentals are bundled with Nyxen. Add them to your library and play without internet.", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(12.dp))
                            listOf("Obsidian Drift · Dark ambient", "Neon Pulse · Electronic ambient", "Aurora Sleep · Atmospheric").forEach { label ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.GraphicEq, null, tint = LocalAccent.current)
                                    Spacer(Modifier.width(10.dp))
                                    Text(label, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            EmbossedIconButton(onImportSamples, Modifier.fillMaxWidth().height(46.dp), RoundedCornerShape(23.dp)) {
                                Text(if (samplesImported) "Add any missing sample tracks" else "Add offline tracks to library", fontWeight = FontWeight.Bold)
                            }
                            Text("Audio and cover art stay on this device.", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                6 -> AccountScreen(profName, profEmail, onProfile, onSignOut, onWipe, profVerified)
                7 -> StatsScreen(tracks)
                5 -> AboutSettings()
            }
        }
    }
}

@Composable
private fun AboutSettings() {
    val accent = LocalAccent.current
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Reveal(0) {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(58.dp).clip(RoundedCornerShape(18.dp))
                            .background(Brush.linearGradient(listOf(accent, lerp(accent, Color.Black, .58f)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("ABOUT NYXEN", fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Black, color = accent)
                        Text("A note from Remnant", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Hi, I’m Remnant. I built NyxenPlayer because I wanted a music app that feels as good as the music sounds.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = .92f)
                )
            }
        }
        AboutStoryCard("WHY I STARTED", "For a long time I used players that were either too plain or too crowded. Some were full of ads, some were locked behind subscriptions, and many were made for streaming when all I wanted was to play the songs already on my phone. So I decided to make the one I was missing.", 60)
        AboutStoryCard("A PLAYER THAT GREW", "It started as a tiny player with a single play button. Then I kept adding the things I wished I had: gradient themes that match my mood, a sound studio to shape the bass, lyrics that follow the song, playlists that fill themselves, and a design that looks great at night.", 110)
        AboutStoryCard("MADE FOR YOUR MUSIC", "NyxenPlayer is made for people who love their own music collection, whether that is a few favorite albums or thousands of songs collected over the years. I hope it makes your everyday listening a little more fun.", 160)
        AboutStoryCard("STILL GROWING", "This is a personal project and it keeps growing. If something is missing, broken or could simply be better, I would really love to hear about it. Thank you for using NyxenPlayer, and enjoy the music.", 210)
        Reveal(260) {
            Text("— Sincerely, Remnant (aka. Sinner)", Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

@Composable
private fun AboutStoryCard(title: String, body: String, delay: Int) {
    Reveal(delay) {
        GlassCard(Modifier.fillMaxWidth()) {
            Text(title, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Black, color = LocalAccent.current)
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .86f))
        }
    }
}

@Composable
private fun CustomizationSettings(customFontName: String, onImportFont: () -> Unit, onTheme: (Int) -> Unit) {
    val context = LocalContext.current
    var themeName by remember { mutableStateOf("") }
    var accentHex by remember { mutableStateOf("#A855F7") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GlassCard(Modifier.fillMaxWidth()) {
            Text("CUSTOM FONT", fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, fontSize = 11.sp)
            Text("Import a .ttf or .otf font. It is copied to private app storage and used across Nyxen.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            Text(customFontName, style = MaterialTheme.typography.labelMedium, color = LocalAccent.current)
            Spacer(Modifier.height(8.dp))
            EmbossedIconButton(onImportFont, Modifier.fillMaxWidth().height(46.dp), RoundedCornerShape(23.dp)) {
                Text(if (customFontName == "No custom font imported") "Import font file" else "Replace imported font", fontWeight = FontWeight.Bold)
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Text("CREATE A THEME", fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, fontSize = 11.sp)
            Text("Save a named accent palette. Your custom themes stay on this device.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(themeName, { themeName = it.take(24); error = null }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Theme name") })
            OutlinedTextField(accentHex, { accentHex = it.take(9).uppercase(); error = null }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Accent color (hex)") })
            error?.let { Text(it, color = Color(0xFFFF7777), style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(8.dp))
            EmbossedIconButton({
                val parsed = runCatching { Color(android.graphics.Color.parseColor(accentHex)) }.getOrNull()
                if (themeName.isBlank()) error = "Give your theme a name."
                else if (parsed == null) error = "Enter a color such as #A855F7."
                else {
                    val accent = parsed
                    Themes.add(AppTheme(themeName.trim(), lerp(accent, Color.Black, .62f), lerp(accent, Color.Black, .78f), Color.Black, .12f, accent))
                    persistCustomThemes(context)
                    onTheme(Themes.lastIndex)
                    themeName = ""
                    error = null
                }
            }, Modifier.fillMaxWidth().height(48.dp), RoundedCornerShape(24.dp)) { Text("Save and apply theme", fontWeight = FontWeight.Bold) }
            if (Themes.size > BuiltInThemes.size) {
                Spacer(Modifier.height(12.dp))
                Text("SAVED THEMES", fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 11.sp)
                Themes.drop(BuiltInThemes.size).forEachIndexed { index, theme ->
                    Row(Modifier.fillMaxWidth().clickable { onTheme(BuiltInThemes.size + index) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(24.dp).clip(CircleShape).background(theme.accent))
                        Spacer(Modifier.width(10.dp))
                        Text(theme.name, Modifier.weight(1f))
                        if (themeName == theme.name) Icon(Icons.Default.CheckCircle, "Selected", tint = LocalAccent.current)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountScreen(name: String, email: String, onSave: (String, String) -> Unit, onSignOut: () -> Unit, onWipe: () -> Unit, verified: Boolean) {
    val a = LocalAccent.current
    val scope = rememberCoroutineScope()
    var n by remember { mutableStateOf(name) }
    var e by remember { mutableStateOf(email) }
    var confirm by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Reveal(0) {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).shadow(12.dp, CircleShape, ambientColor = a, spotColor = a).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(lerp(a, Color.White, .2f), lerp(a, Color.Black, .5f)))), Alignment.Center) {
                        Text((name.firstOrNull() ?: 'N').uppercase(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(name.ifBlank { "Guest" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text((if (verified) "✓ " else "") + email.ifBlank { "No Gmail added" }, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Reveal(70) {
            GlassCard(Modifier.fillMaxWidth()) {
                OutlinedTextField(n, { n = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(e, { e = it }, Modifier.fillMaxWidth(), label = { Text(if (verified) "Gmail (verified with Google)" else "Gmail address") }, singleLine = true, readOnly = verified,
                    isError = err && !isGmail(e), supportingText = { if (err && !isGmail(e)) Text("Enter a valid @gmail.com address") })
                Spacer(Modifier.height(12.dp))
                EmbossedIconButton({ if (n.isNotBlank() && isGmail(e)) { onSave(n.trim(), e.trim()); saved = true; err = false; scope.launch { delay(1500); saved = false } } else err = true }, Modifier.fillMaxWidth().height(48.dp), RoundedCornerShape(24.dp)) {
                    Crossfade(saved, label = "sv") { ok -> Text(if (ok) "Saved" else "Save changes", fontWeight = FontWeight.Bold) }
                }
            }
        }
        Reveal(140) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Your profile is stored on this device only. There is no password and nothing is sent anywhere.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                EmbossedIconButton(onSignOut, Modifier.fillMaxWidth().height(46.dp), RoundedCornerShape(23.dp)) { Text("Sign out") }
                Spacer(Modifier.height(6.dp))
                TextButton({ confirm = true }, Modifier.fillMaxWidth()) { Text("Delete all my data", color = Color(0xFFFF6B6B)) }
            }
        }
    }
    if (confirm) AlertDialog({ confirm = false }, title = { Text("Delete all data?") },
        text = { Text("This removes your library, playlists, favorites, play counts, settings and profile from NyxenPlayer. Your music files are not touched.") },
        confirmButton = { TextButton({ confirm = false; onWipe() }) { Text("Delete", color = Color(0xFFFF6B6B)) } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } })
}
