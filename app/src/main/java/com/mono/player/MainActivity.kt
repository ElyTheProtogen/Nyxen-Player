package com.mono.player

import android.Manifest
import android.content.ComponentName
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.mono.player.data.*
import com.mono.player.playback.*
import com.mono.player.ui.Studio
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s); enableEdgeToEdge()
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { App() } }
    }
}

fun mi(t: Track) = MediaItem.Builder().setMediaId("${t.id}").setUri(t.uri)
    .setMediaMetadata(MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.album).build()).build()

fun fmt(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }

@Composable
fun Art(t: Track?, mod: Modifier) {
    val bmp = remember(t?.id) { t?.artwork?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull() } }
    Box(mod.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(.12f)), Alignment.Center) {
        if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(.6f))
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val dao = remember { Db.get(ctx).dao() }
    val tracks by dao.tracks().collectAsState(emptyList())
    val playlists by dao.playlists().collectAsState(emptyList())
    var c by remember { mutableStateOf<MediaController?>(null) }
    var mode by remember { mutableStateOf(PlayMode.REPEAT_ALL) }
    var msg by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableIntStateOf(1) }
    var full by remember { mutableStateOf(false) }
    var curId by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableLongStateOf(0L) }
    var sort by remember { mutableStateOf("Title") }
    var asc by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var addFor by remember { mutableStateOf<Track?>(null) }
    var showQueue by remember { mutableStateOf(false) }
    var newPl by remember { mutableStateOf(false) }

    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS) // lock-screen/notification controls
        val f = MediaController.Builder(ctx, SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))).buildAsync()
        f.addListener({
            val m = f.get(); c = m; curId = m.currentMediaItem?.mediaId; playing = m.isPlaying
            m.addListener(object : Player.Listener {
                override fun onMediaItemTransition(item: MediaItem?, reason: Int) { curId = item?.mediaId }
                override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            })
        }, MoreExecutors.directExecutor())
    }
    LaunchedEffect(playing, c) { while (playing) { pos = c?.currentPosition ?: 0; delay(500) }; pos = c?.currentPosition ?: pos }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        scope.launch { val r = Importer.import(ctx, uris); msg = "Added ${r.added}, duplicates ${r.duplicates}, failed ${r.failed}" }
    }
    val cur = tracks.firstOrNull { "${it.id}" == curId }
    fun play(list: List<Track>, i: Int) { c?.run { setMediaItems(list.map(::mi), i, 0); mode.apply(this); prepare(); play() } }

    val sorted = remember(tracks, sort, asc) {
        val cmp: Comparator<Track> = when (sort) {
            "Artist" -> compareBy { it.artist.lowercase() }; "Album" -> compareBy { it.album.lowercase() }
            "Duration" -> compareBy { it.durationMs }; "Added" -> compareBy { it.addedAt }
            "Plays" -> compareBy { it.playCount }; else -> compareBy { it.title.lowercase() }
        }
        tracks.sortedWith(if (asc) cmp else cmp.reversed())
    }

    @Composable fun Row(list: List<Track>, i: Int) {
        val t = list[i]; var menu by remember { mutableStateOf(false) }
        ListItem({ Text(t.title, maxLines = 1) }, Modifier.clickable { play(list, i) },
            supportingContent = { Text("${t.artist} · ${t.album} · Local", maxLines = 1) },
            leadingContent = { Art(t, Modifier.size(48.dp)) },
            trailingContent = {
                IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "More options") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Play next") }, { c?.addMediaItem((c?.currentMediaItemIndex ?: -1) + 1, mi(t)); menu = false })
                    DropdownMenuItem({ Text("Add to queue") }, { c?.addMediaItem(mi(t)); menu = false })
                    DropdownMenuItem({ Text(if (t.favorite) "Unfavorite" else "Favorite") }, { scope.launch { dao.update(t.copy(favorite = !t.favorite)) }; menu = false })
                    DropdownMenuItem({ Text("Add to playlist") }, { addFor = t; menu = false })
                    DropdownMenuItem({ Text("Remove from library") }, { scope.launch { dao.delete(t) }; menu = false })
                }
            }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black, Color(0xFF3A3A3A), Color(0xFFBDBDBD))))) {
        Scaffold(containerColor = Color.Transparent, bottomBar = {
            Column {
                if (cur != null) Surface(Modifier.fillMaxWidth().clickable { full = true }, color = Color.Black.copy(.6f)) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Art(cur, Modifier.size(44.dp)); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text(cur.title, maxLines = 1); Text(cur.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall) }
                        IconButton({ c?.let { if (it.isPlaying) it.pause() else it.play() } }) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause") }
                    }
                }
                NavigationBar(containerColor = Color.Black.copy(.6f)) {
                    listOf("Home" to Icons.Default.Home, "Library" to Icons.Default.LibraryMusic, "Search" to Icons.Default.Search,
                        "Playlists" to Icons.Default.QueueMusic, "Settings" to Icons.Default.Settings).forEachIndexed { i, (n, ic) ->
                        NavigationBarItem(tab == i, { tab = i }, { Icon(ic, n) }, label = { Text(n) })
                    }
                }
            }
        }) { pad ->
            Column(Modifier.padding(pad).padding(horizontal = 16.dp).statusBarsPadding()) {
                when (tab) {
                    0 -> {
                        val sections = listOf(
                            "Recently played" to tracks.filter { it.lastPlayed > 0 }.sortedByDescending { it.lastPlayed }.take(10),
                            "Most played" to tracks.filter { it.playCount > 0 }.sortedByDescending { it.playCount }.take(10),
                            "Favorites" to tracks.filter { it.favorite },
                            "Recently added" to tracks.sortedByDescending { it.addedAt }.take(10))
                        if (tracks.isEmpty()) Text("Import music in the Library tab to get started.", Modifier.padding(top = 32.dp))
                        LazyColumn { sections.forEach { (h, l) -> if (l.isNotEmpty()) {
                            item { Text(h, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
                            items(l.size) { Row(l, it) } } } }
                    }
                    1 -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button({ picker.launch(arrayOf("audio/mpeg", "audio/wav", "audio/x-wav", "audio/ogg", "audio/mp4", "audio/x-m4a")) }) { Text("Import music") }
                            var sm by remember { mutableStateOf(false) }
                            OutlinedButton({ sm = true }) { Text("Sort: $sort") }
                            DropdownMenu(sm, { sm = false }) { listOf("Title", "Artist", "Album", "Duration", "Added", "Plays").forEach { o -> DropdownMenuItem({ Text(o) }, { sort = o; sm = false }) } }
                            IconButton({ asc = !asc }) { Icon(if (asc) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, "Toggle sort direction") }
                        }
                        msg?.let { Text(it, Modifier.padding(vertical = 8.dp)) }
                        if (tracks.isEmpty()) Text("Your library is empty. Import music to begin.", Modifier.padding(top = 32.dp))
                        LazyColumn { items(sorted.size) { Row(sorted, it) } }
                    }
                    2 -> {
                        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true, placeholder = { Text("Search your library") },
                            trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Default.Clear, "Clear search") } })
                        val r = if (query.isBlank()) emptyList() else tracks.filter { t -> listOf(t.title, t.artist, t.album, t.genre).any { it.contains(query, true) } }
                        if (query.isNotBlank() && r.isEmpty()) Text("No results", Modifier.padding(top = 24.dp))
                        LazyColumn { items(r.size) { Row(r, it) } }
                    }
                    3 -> {
                        Button({ newPl = true }, Modifier.padding(top = 8.dp)) { Text("New playlist") }
                        if (playlists.isEmpty()) Text("No playlists yet. Create one, then add songs from a track menu.", Modifier.padding(top = 24.dp))
                        LazyColumn { items(playlists) { p -> ListItem({ Text(p.name) }, Modifier.clickable { scope.launch { val l = dao.playlistTracks(p.id); if (l.isNotEmpty()) play(l, 0) else msg = "Playlist is empty" } },
                            trailingContent = { IconButton({ scope.launch { dao.clearPlaylist(p.id); dao.deletePlaylist(p.id) } }) { Icon(Icons.Default.Delete, "Delete playlist") } },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)) } }
                    }
                    else -> Studio()
                }
            }
        }

        if (full && cur != null) Surface(Modifier.fillMaxSize(), color = Color(0xEE000000)) {
            Column(Modifier.systemBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth()) {
                    IconButton({ full = false }) { Icon(Icons.Default.KeyboardArrowDown, "Close player") }
                    Spacer(Modifier.weight(1f))
                    IconButton({ full = false; tab = 4 }) { Icon(Icons.Default.Equalizer, "Audio Studio") }
                    IconButton({ showQueue = true }) { Icon(Icons.Default.QueueMusic, "Queue") }
                }
                Art(cur, Modifier.fillMaxWidth().aspectRatio(1f).padding(8.dp))
                Text(cur.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp), maxLines = 1)
                Text("${cur.artist} · ${cur.album}", maxLines = 1); Text("Source: Local file", style = MaterialTheme.typography.bodySmall)
                val d = (c?.duration ?: 0L).takeIf { it > 0 } ?: cur.durationMs
                var drag by remember { mutableStateOf<Float?>(null) }
                Slider(drag ?: (pos.toFloat() / d.coerceAtLeast(1)).coerceIn(0f, 1f), { drag = it },
                    onValueChangeFinished = { drag?.let { c?.seekTo((it * d).toLong()); pos = (it * d).toLong() }; drag = null })
                Row(Modifier.fillMaxWidth()) { Text(fmt(pos)); Spacer(Modifier.weight(1f)); Text(fmt(d)) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton({ mode = mode.next(); c?.let { mode.apply(it) } }) {
                        Icon(when (mode) { PlayMode.REPEAT_ALL -> Icons.Default.Repeat; PlayMode.REPEAT_ONE -> Icons.Default.RepeatOne
                            PlayMode.IN_ORDER -> Icons.Default.ArrowRightAlt; PlayMode.SHUFFLE -> Icons.Default.Shuffle }, "Playback mode: ${mode.name}") }
                    IconButton({ c?.seekToPreviousMediaItem() }, Modifier.size(56.dp)) { Icon(Icons.Default.SkipPrevious, "Previous") }
                    FilledIconButton({ c?.let { if (it.isPlaying) it.pause() else it.play() } }, Modifier.size(72.dp)) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause") }
                    IconButton({ c?.seekToNextMediaItem() }, Modifier.size(56.dp)) { Icon(Icons.Default.SkipNext, "Next") }
                    IconButton({ scope.launch { dao.update(cur.copy(favorite = !cur.favorite)) } }) { Icon(if (cur.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite") }
                }
                Text(mode.name.replace('_', ' '), style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (showQueue) AlertDialog({ showQueue = false }, confirmButton = { TextButton({ showQueue = false }) { Text("Close") } }, title = { Text("Queue") }, text = {
        LazyColumn { items((c?.mediaItemCount ?: 0)) { i -> val m = c!!.getMediaItemAt(i)
            ListItem({ Text(m.mediaMetadata.title?.toString() ?: "Track") }, Modifier.clickable { c?.seekTo(i, 0); showQueue = false }) } } })
    addFor?.let { t -> AlertDialog({ addFor = null }, confirmButton = { TextButton({ addFor = null }) { Text("Cancel") } }, title = { Text("Add to playlist") }, text = {
        if (playlists.isEmpty()) Text("Create a playlist first.") else LazyColumn { items(playlists) { p ->
            ListItem({ Text(p.name) }, Modifier.clickable { scope.launch { dao.addToPlaylist(PlaylistTrack(p.id, t.id, dao.count(p.id))) }; addFor = null }) } } }) }
    if (newPl) { var name by remember { mutableStateOf("") }
        AlertDialog({ newPl = false }, title = { Text("New playlist") }, text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { TextButton({ if (name.isNotBlank()) scope.launch { dao.addPlaylist(Playlist(name = name.trim())) }; newPl = false }) { Text("Create") } },
            dismissButton = { TextButton({ newPl = false }) { Text("Cancel") } }) }
}
