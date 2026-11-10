package com.mono.player.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mono.player.GlassCard
import com.mono.player.LocalAccent
import com.mono.player.Reveal
import com.mono.player.data.Track

@Composable
private fun BigStat(label: String, value: Long, modifier: Modifier) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(value) { a.animateTo(value.toFloat(), tween(900, easing = FastOutSlowInEasing)) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${a.value.toLong()}", fontSize = 36.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = (-1.5).sp)
        Text(label.uppercase(), fontSize = 9.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = .48f))
    }
}

@Composable
private fun BarRow(title: String, count: Int, max: Int) {
    val accent = LocalAccent.current
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val f by animateFloatAsState(if (go && max > 0) count.toFloat() / max else 0f, tween(800, easing = FastOutSlowInEasing), label = "bar")
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row {
            Text(title, Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$count×", color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
        }
        Box(Modifier.padding(top = 7.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = .10f))) {
            Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(Brush.horizontalGradient(listOf(accent.copy(alpha = .55f), accent, Color.White.copy(alpha = .85f)))))
        }
    }
}

/** Listening stats built from your play counts: totals, top songs and artists, and a short recap. */
@Composable
fun StatsScreen(tracks: List<Track>) {
    val totalPlays = remember(tracks) { tracks.sumOf { it.playCount } }
    val minutes = remember(tracks) { tracks.sumOf { it.durationMs * it.playCount } / 60000L }
    val topSongs = remember(tracks) { tracks.filter { it.playCount > 0 }.sortedByDescending { it.playCount }.take(5) }
    val topArtists = remember(tracks) {
        tracks.filter { it.playCount > 0 }.groupBy { it.artist }.map { it.key to it.value.sumOf { t -> t.playCount } }.sortedByDescending { it.second }.take(5)
    }
    val topGenre = remember(tracks) {
        tracks.filter { it.playCount > 0 && !it.genre.startsWith("Unknown") }.groupBy { it.genre }.maxByOrNull { e -> e.value.sumOf { t -> t.playCount } }?.key
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Reveal(0) {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp)) {
                Text("LISTENING INTELLIGENCE  /  01", fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold, color = LocalAccent.current)
                Text("Your sound,\nyour story.", style = MaterialTheme.typography.headlineLarge, lineHeight = 36.sp, color = Color.White, fontWeight = FontWeight.Black)
            }
        }
        Reveal(0) {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    BigStat("Plays", totalPlays.toLong(), Modifier.weight(1f))
                    BigStat("Minutes", minutes, Modifier.weight(1f))
                    BigStat("Songs", tracks.size.toLong(), Modifier.weight(1f))
                }
            }
        }
        if (totalPlays == 0) {
            Reveal(80) { Text("Play some songs and your stats will appear here.", color = Color.White.copy(alpha = .75f)) }
        } else {
            Reveal(80) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("TOP SONGS", fontWeight = FontWeight.Black, letterSpacing = 1.8.sp, fontSize = 10.sp, color = LocalAccent.current)
                    topSongs.forEachIndexed { i, t -> BarRow("${i + 1}. ${t.title}", t.playCount, topSongs.first().playCount) }
                }
            }
            Reveal(150) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("TOP ARTISTS", fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, fontSize = 11.sp, color = Color.White)
                    topArtists.forEachIndexed { i, a -> BarRow("${i + 1}. ${a.first}", a.second, topArtists.first().second) }
                }
            }
            Reveal(220) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("YOUR RECAP", fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, fontSize = 11.sp, color = Color.White)
                    val hours = minutes / 60
                    Text(
                        "You have listened for about ${if (hours >= 1) "$hours hours" else "$minutes minutes"} across $totalPlays plays. " +
                            (topArtists.firstOrNull()?.let { "Your top artist is ${it.first}. " } ?: "") +
                            (topGenre?.let { "Your favorite genre is $it." } ?: ""),
                        color = Color.White.copy(alpha = .86f), modifier = Modifier.padding(top = 10.dp), lineHeight = 22.sp
                    )
                }
            }
        }
    }
}
