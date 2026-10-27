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
    val results = remember(tracks, debounced, filter) {
        if (debounced.isEmpty()) emptyList() else {
            val tokens = debounced.lowercase().split(" ").filter { it.isNotBlank() }
            tracks.filter { t ->
                val hay = when (filter) { 1 -> t.title; 2 -> t.artist; 3 -> t.album; 4 -> t.genre; else -> "${t.title} ${t.artist} ${t.album} ${t.genre}" }.lowercase()
                tokens.all { hay.contains(it) }
            }
        }
    }
    val suggestions = remember(tracks, debounced) {
        if (debounced.isEmpty()) emptyList() else (tracks.map { it.artist } + tracks.map { it.album }).distinct()
            .filter { it.contains(debounced, true) && !it.equals(debounced, true) && !it.startsWith("Unknown") }.take(5)
    }
    LaunchedEffect(debounced, results.size) { if (debounced.length >= 2 && results.isNotEmpty()) { delay(1200); addRecent(debounced) } }

    val glow by animateFloatAsState(if (focused) 1f else 0f, tween(250), label = "glow")
    val shape = RoundedCornerShape(30.dp)
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp).shadow((10 * glow).dp, shape, ambientColor = accent, spotColor = accent).clip(shape)
                .background(Color.Black.copy(alpha = .45f)).border(1.5.dp, accent.copy(alpha = .25f + .75f * glow), shape).padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, null, tint = accent)
            BasicTextField(
                query, { query = it }, Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 14.dp).onFocusChanged { focused = it.isFocused },
                singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 16.sp), cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { addRecent(query.trim()); focus.clearFocus() }),
                decorationBox = { inner -> Box { if (query.isEmpty()) Text("Search songs, artists, albums", color = Color.White.copy(alpha = .5f)); inner() } }
            )
            AnimatedVisibility(query.isNotEmpty(), enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                IconButton({ query = "" }) { Icon(Icons.Default.Close, "Clear search") }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            filters.forEachIndexed { i, f -> FilterChip(filter == i, { filter = i }, { Text(f) }) }
        }
        AnimatedVisibility(focused && suggestions.isNotEmpty()) {
            FlowRow(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        Text("Recent searches", Modifier.weight(1f))
                        TextButton({ recents = emptyList(); prefs.edit().remove("recents").apply() }) { Text("Clear") }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        recents.forEach { r -> AssistChip({ query = r }, { Text(r) }, leadingIcon = { Icon(Icons.Default.History, null, Modifier.size(16.dp)) }) }
                    }
                }
                1 -> Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No results for \"$debounced\"", color = Color.White.copy(alpha = .8f))
                    Text("Try a shorter word or another filter", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .6f))
                }
                else -> Column {
                    Text("${results.size} results", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .7f))
                    LazyColumn { items(results.size, key = { results[it].id }) { Box(Modifier.animateItem()) { row(results, it) } } }
                }
            }
        }
    }
}
