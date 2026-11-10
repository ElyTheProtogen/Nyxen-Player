package com.mono.player.ui

import android.content.SharedPreferences
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mono.player.LocalAccent
import com.mono.player.data.Track
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(tracks: List<Track>, prefs: SharedPreferences, row: @Composable (List<Track>, Int) -> Unit) {
    val accent = LocalAccent.current
    val focus = LocalFocusManager.current
    var query by remember { mutableStateOf("") }
    var debounced by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    var recents by remember { mutableStateOf((prefs.getString("recents", "") ?: "").split("\n").filter { it.isNotBlank() }) }
    fun addRecent(q: String) {
        if (q.isBlank()) return
        recents = (listOf(q) + recents.filter { !it.equals(q, true) }).take(8)
        prefs.edit().putString("recents", recents.joinToString("\n")).apply()
    }
    LaunchedEffect(query) { delay(150); debounced = query.trim() }
    val filters = listOf("All", "Songs", "Artists", "Albums", "Genres")
    val idx = remember(tracks) { SearchIndex(tracks) }
    val results = remember(idx, debounced, filter) {
        if (debounced.isEmpty()) emptyList() else {
            val tokens = debounced.lowercase().split(" ").filter { it.isNotBlank() }
            val h = when (filter) { 1 -> idx.title; 2 -> idx.artist; 3 -> idx.album; 4 -> idx.genre; else -> idx.all }
            tracks.filterIndexed { i, _ -> tokens.all { h[i].contains(it) } }
        }
    }
    val suggestions = remember(idx, debounced) {
        if (debounced.isEmpty()) emptyList() else idx.names.filter { it.contains(debounced, true) && !it.equals(debounced, true) }.take(5)
    }
    LaunchedEffect(debounced, results.size) { if (debounced.length >= 2 && results.isNotEmpty()) { delay(1200); addRecent(debounced) } }

    val glow by animateFloatAsState(if (focused) 1f else 0f, tween(250), label = "glow")
    val shape = RoundedCornerShape(24.dp)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("DISCOVER", fontSize = 9.sp, letterSpacing = 2.4.sp, color = accent, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text("Find your next loop", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
            }
            Surface(shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = .08f), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .1f))) {
                Text("${tracks.size} TRACKS", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontSize = 9.sp, letterSpacing = 1.sp, color = Color.White.copy(alpha = .8f))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp).shadow((10 * glow).dp, shape, ambientColor = accent, spotColor = accent).clip(shape)
                .background(Brush.horizontalGradient(listOf(Color(0xE91B1A22), Color(0xE908080C)))).border(1.5.dp, accent.copy(alpha = .22f + .78f * glow), shape).padding(start = 18.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, null, tint = accent)
            BasicTextField(
                query, { query = it }, Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 17.dp).onFocusChanged { focused = it.isFocused },
                singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 16.sp), cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { addRecent(query.trim()); focus.clearFocus() }),
                decorationBox = { inner -> Box { if (query.isEmpty()) Text("Songs, artists, albums, genres…", color = Color.White.copy(alpha = .42f)); inner() } }
            )
            AnimatedVisibility(query.isNotEmpty(), enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                IconButton({ query = "" }) { Icon(Icons.Default.Close, "Clear search") }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            filters.forEachIndexed { i, f -> FilterChip(filter == i, { filter = i }, { Text(f, fontWeight = if (filter == i) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent.copy(alpha = .24f), selectedLabelColor = Color.White, labelColor = Color.White.copy(alpha = .72f))) }
        }
        AnimatedVisibility(focused && suggestions.isNotEmpty()) {
            FlowRow(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { sg -> SuggestionChip({ query = sg }, { Text(sg) }) }
            }
        }
        val state = when { debounced.isEmpty() -> 0; results.isEmpty() -> 1; else -> 2 }
        Crossfade(state, label = "search") { st ->
            when (st) {
                0 -> if (recents.isEmpty()) Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Search, null, Modifier.size(56.dp), tint = accent.copy(alpha = .5f))
                    Text("Find anything in your library", color = Color.White.copy(alpha = .7f), modifier = Modifier.padding(top = 8.dp))
                } else Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("RECENT SEARCHES", Modifier.weight(1f), fontSize = 10.sp, letterSpacing = 1.6.sp, color = Color.White.copy(alpha = .7f), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        TextButton({ recents = emptyList(); prefs.edit().remove("recents").apply() }) { Text("Clear") }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        recents.forEach { r -> AssistChip({ query = r }, { Text(r) }, leadingIcon = { Icon(Icons.Default.History, null, Modifier.size(16.dp)) }) }
                    }
                }
                1 -> Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No matches found", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text("No results for \"$debounced\"", color = Color.White.copy(alpha = .65f), modifier = Modifier.padding(top = 6.dp))
                    Text("Try a shorter word or another filter", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .6f))
                }
                else -> Column {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("MATCHING YOUR VIBE", Modifier.weight(1f), fontSize = 9.sp, letterSpacing = 1.6.sp, color = Color.White.copy(alpha = .58f), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text("${results.size} RESULTS", fontSize = 10.sp, letterSpacing = .8.sp, color = accent, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                    LazyColumn(contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) { items(results.size, key = { results[it].id }) { Box(Modifier.animateItem()) { row(results, it) } } }
                }
            }
        }
    }
}

/** Lower-cased search text built once per library change, so typing never rebuilds strings for every song. */
private class SearchIndex(tracks: List<Track>) {
    val title = Array(tracks.size) { tracks[it].title.lowercase() }
    val artist = Array(tracks.size) { tracks[it].artist.lowercase() }
    val album = Array(tracks.size) { tracks[it].album.lowercase() }
    val genre = Array(tracks.size) { tracks[it].genre.lowercase() }
    val all = Array(tracks.size) { "${title[it]} ${artist[it]} ${album[it]} ${genre[it]}" }
    val names = (tracks.map { it.artist } + tracks.map { it.album }).distinct().filter { !it.startsWith("Unknown") }
}
