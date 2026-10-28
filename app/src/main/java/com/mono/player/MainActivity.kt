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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import android.util.LruCache
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.mono.player.ui.SearchScreen
import com.mono.player.ui.Banner
import com.mono.player.ui.NowPlayingScreen
import com.mono.player.ui.SplashScreen
import com.mono.player.ui.WelcomeScreen
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.media3.common.PlaybackException
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.mono.player.ui.SettingsScreen
import com.mono.player.ui.Themes
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.absoluteValue
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
    override fun onStop() { AudioFx.save(this); super.onStop() }

    // Back on Home finishes the activity; here we also stop playback and end the process so nothing keeps running.
    override fun onDestroy() {
        val quitting = isFinishing && !isChangingConfigurations
        super.onDestroy()
        if (quitting) {
            stopService(android.content.Intent(this, PlaybackService::class.java))
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        AudioFx.load(this)
        val prefs = getSharedPreferences("mono", MODE_PRIVATE)
        setContent {
            var themeIdx by remember { mutableIntStateOf(prefs.getInt("theme", 0).coerceIn(0, Themes.lastIndex)) }
            var haptics by remember { mutableStateOf(prefs.getBoolean("haptics", true)) }
            val accent by animateColorAsState(Themes[themeIdx].accent, tween(600), label = "accent")
            MaterialTheme(colorScheme = darkColorScheme(primary = accent, onPrimary = Color.White, secondaryContainer = accent.copy(alpha = .35f),
                onSecondaryContainer = Color.White, surfaceContainerHigh = Color(0xFF17131F), onBackground = Color.White, onSurface = Color.White, onSurfaceVariant = Color.White)) {
                CompositionLocalProvider(LocalContentColor provides Color.White, LocalAccent provides accent, LocalHaptics provides haptics) {
                    App(themeIdx, { themeIdx = it }, haptics, { haptics = it })
                }
            }
        }
    }
}

fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context = this
    while (c is android.content.ContextWrapper) { if (c is android.app.Activity) return c; c = c.baseContext }
    return null
}

fun mi(t: Track) = MediaItem.Builder().setMediaId("${t.id}").setUri(t.uri)
    .setMediaMetadata(MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.album).build()).build()

fun fmt(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }

private object ArtCache { val lru = LruCache<Long, ImageBitmap>(48) }

/** Artwork is loaded lazily from the database (not with the track list) and cached, so lists stay light. */
@Composable
fun Art(t: Track?, mod: Modifier) {
    val dao = Db.get(LocalContext.current).dao()
    val bmp by produceState<ImageBitmap?>(t?.let { ArtCache.lru.get(it.id) }, t?.id) {
        val id = t?.id
        if (id == null) { value = null; return@produceState }
        val hit = ArtCache.lru.get(id)
        if (hit != null) { value = hit; return@produceState }
        value = null
        value = withContext(Dispatchers.Default) {
            runCatching { dao.artwork(id)?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }.getOrNull()
        }?.also { ArtCache.lru.put(id, it) }
    }
    Box(mod.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(.12f)), Alignment.Center) {
        val b = bmp
        if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(.6f))
    }
}

@Composable
fun App(themeIdx: Int, setTheme: (Int) -> Unit, haptics: Boolean, setHaptics: (Boolean) -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("mono", android.content.Context.MODE_PRIVATE) }
    val accent = LocalAccent.current
    val dao = remember { Db.get(ctx).dao() }
    val tracks by dao.tracks().collectAsState(emptyList())
    val playlists by dao.playlists().collectAsState(emptyList())
    var c by remember { mutableStateOf<MediaController?>(null) }
    var mode by remember { mutableStateOf(PlayMode.entries[prefs.getInt("mode", 0).coerceIn(0, 3)]) }
    var msg by remember { mutableStateOf<String?>(null) }
    val pager = rememberPagerState(prefs.getInt("page", 1).coerceIn(0, 4)) { 5 }
    val tab = pager.currentPage
    var full by remember { mutableStateOf(false) }
    var curId by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableLongStateOf(0L) }
    var sort by remember { mutableStateOf(prefs.getString("sort", "Title") ?: "Title") }
    var asc by remember { mutableStateOf(prefs.getBoolean("asc", true)) }
    var query by remember { mutableStateOf("") }
    var addFor by remember { mutableStateOf<Track?>(null) }
    var showQueue by remember { mutableStateOf(false) }
    var newPl by remember { mutableStateOf(false) }

        var autoImport by remember { mutableStateOf(prefs.getBoolean("auto", true)) }
    var sub by remember { mutableIntStateOf(0) }
    var speed by remember { mutableFloatStateOf(prefs.getFloat("speed", 1f)) }
    var sleepEnd by remember { mutableLongStateOf(0L) }
    var restored by remember { mutableStateOf(false) }
    var profName by remember { mutableStateOf(prefs.getString("pname", "") ?: "") }
    var profEmail by remember { mutableStateOf(prefs.getString("pemail", "") ?: "") }
    var booted by remember { mutableStateOf(false) }
    var greeted by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    var askPerm by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    val audioPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun askNotif() { if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS) }
    fun rescan(manual: Boolean = false) { if (scanning) return; scope.launch { scanning = true; val r = DeviceScan.run(ctx); scanning = false; if (r.added > 0) msg = "Added ${r.added} new songs" else if (manual) msg = "No new songs found" } }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) rescan(true) else msg = "Permission denied. You can still add songs with Import music."
        askNotif()
    }
    fun hasAudioPerm() = ContextCompat.checkSelfPermission(ctx, audioPerm) == PackageManager.PERMISSION_GRANTED
    fun requestScan() { if (hasAudioPerm()) rescan(true) else askPerm = true }
    fun wipeAll() { scope.launch {
        c?.run { stop(); clearMediaItems() }
        dao.wipeTracks(); dao.wipePlaylistTracks(); dao.wipePlaylists()
        prefs.edit().clear().apply(); AudioFx.resetAll(ctx)
        setTheme(0); setHaptics(true); autoImport = true; speed = 1f; sort = "Title"; asc = true; mode = PlayMode.REPEAT_ALL
        profName = ""; profEmail = ""; sub = 0; restored = false; curId = null; greeted = false; started = false
        pager.scrollToPage(1)
    } }
    LaunchedEffect(Unit) {
        
        val f = MediaController.Builder(ctx, SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))).buildAsync()
        f.addListener({
            val m = f.get(); c = m; curId = m.currentMediaItem?.mediaId; playing = m.isPlaying
            m.addListener(object : Player.Listener {
                override fun onMediaItemTransition(item: MediaItem?, reason: Int) { curId = item?.mediaId; item?.mediaId?.toLongOrNull()?.let { prefs.edit().putLong("lastId", it).apply() } }
                override fun onPlayerError(error: PlaybackException) { msg = "Couldn't play this file. It may have been moved or deleted." }
                override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying; if (!isPlaying) prefs.edit().putLong("lastPos", m.currentPosition).apply() }
            })
        }, MoreExecutors.directExecutor())
    }
    DisposableEffect(c) { onDispose { c?.release() } }
    LaunchedEffect(c, tracks.size) {
        val m = c ?: return@LaunchedEffect
        if (restored || m.mediaItemCount > 0 || tracks.isEmpty()) return@LaunchedEffect
        val i = tracks.indexOfFirst { it.id == prefs.getLong("lastId", -1L) }
        if (i >= 0) { m.setMediaItems(tracks.map(::mi), i, prefs.getLong("lastPos", 0L)); mode.apply(m); m.prepare(); restored = true; curId = "${tracks[i].id}" }
    }
    LaunchedEffect(playing, c) { var n = 0; while (playing) { pos = c?.currentPosition ?: 0; if (++n % 10 == 0) prefs.edit().putLong("lastPos", pos).apply(); delay(500) }; pos = c?.currentPosition ?: pos }

    LaunchedEffect(msg) { if (msg != null) { delay(3500); msg = null } }
    LaunchedEffect(sleepEnd) { if (sleepEnd > 0) { delay((sleepEnd - System.currentTimeMillis()).coerceAtLeast(0)); c?.pause(); sleepEnd = 0L; msg = "Sleep timer finished" } }
    LaunchedEffect(speed, c) { c?.setPlaybackSpeed(speed) }
    LaunchedEffect(pager.currentPage) { prefs.edit().putInt("page", pager.currentPage).apply() }
    LaunchedEffect(Unit) { val t0 = System.currentTimeMillis(); delay(1300); while (c == null && System.currentTimeMillis() - t0 < 4000) delay(100); booted = true }
    LaunchedEffect(booted, profName) {
        if (booted && profName.isNotBlank()) {
            if (!greeted) {
                greeted = true
                val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                msg = if (prefs.getBoolean("firstDone", false)) "${if (h < 12) "Good morning" else if (h < 18) "Good afternoon" else "Good evening"}, $profName" else "Welcome to NyxenPlayer, $profName!"
                prefs.edit().putBoolean("firstDone", true).apply()
            }
            if (!started) { started = true; if (autoImport && !hasAudioPerm()) askPerm = true else { if (autoImport) rescan(); askNotif() } }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        scope.launch { val r = Importer.import(ctx, uris); msg = "Added ${r.added}, duplicates ${r.duplicates}, failed ${r.failed}" }
    }
    val cur = tracks.firstOrNull { "${it.id}" == curId }
    fun closeApp() {
        prefs.edit().putLong("lastPos", c?.currentPosition ?: 0L).apply()
        AudioFx.save(ctx)
        c?.pause()
        ctx.findActivity()?.finishAndRemoveTask()
    }
    BackHandler(tab != 0) { scope.launch { pager.animateScrollToPage(0) } }
    BackHandler(tab == 0) { closeApp() }
    fun play(list: List<Track>, i: Int) { if (c == null) { msg = "Player is still starting, try again"; return }; c?.run { setMediaItems(list.map(::mi), i, 0); mode.apply(this); prepare(); play() } }

    val sorted = remember(tracks, sort, asc) {
        val cmp: Comparator<Track> = when (sort) {
            "Artist" -> compareBy { it.artist.lowercase() }; "Album" -> compareBy { it.album.lowercase() }
            "Duration" -> compareBy { it.durationMs }; "Added" -> compareBy { it.addedAt }
            "Plays" -> compareBy { it.playCount }; else -> compareBy { it.title.lowercase() }
        }
        tracks.sortedWith(if (asc) cmp else cmp.reversed())
    }

    @Composable fun TrackRow(list: List<Track>, i: Int) {
        val t = list[i]; var menu by remember { mutableStateOf(false) }
        ListItem({ Row(verticalAlignment = Alignment.CenterVertically) { if ("${t.id}" == curId) { NowPlayingBars(playing, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)) }; Text(t.title, maxLines = 1) } }, Modifier.clickable { play(list, i) },
            supportingContent = { Text("${t.artist} · ${t.album} · Local", maxLines = 1) },
            leadingContent = { Art(t, Modifier.size(48.dp)) },
            trailingContent = {
                IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "More options") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Play next") }, { c?.addMediaItem((c?.currentMediaItemIndex ?: -1) + 1, mi(t)); menu = false })
                    DropdownMenuItem({ Text("Add to queue") }, { c?.addMediaItem(mi(t)); menu = false })
                    DropdownMenuItem({ Text(if (t.favorite) "Unfavorite" else "Favorite") }, { scope.launch { dao.setFav(t.id, !t.favorite) }; menu = false })
                    DropdownMenuItem({ Text("Add to playlist") }, { addFor = t; menu = false })
                    DropdownMenuItem({ Text("Remove from library") }, { scope.launch { dao.delete(t) }; menu = false })
                }
            }, colors = ListItemDefaults.colors(containerColor = animateColorAsState(if ("${t.id}" == curId) Color.White.copy(.14f) else Color.Transparent, tween(300), label = "rowbg").value))
    }

    val th = Themes[themeIdx]
    val g0 by animateColorAsState(th.top, tween(700), label = "g0")
    val g1 by animateColorAsState(th.mid, tween(700), label = "g1")
    val g2 by animateColorAsState(th.bottom, tween(700), label = "g2")
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(g0, g1, g2))).background(Color.Black.copy(alpha = th.scrim))) {
        AuroraGlow(accent, Modifier.fillMaxSize())
        Scaffold(containerColor = Color.Transparent, bottomBar = {
            Column(Modifier.animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))) {
                AnimatedVisibility(cur != null, enter = slideInVertically(tween(350)) { it } + fadeIn(tween(350)), exit = slideOutVertically(tween(250)) { it } + fadeOut(tween(250))) {
                val cur = cur ?: return@AnimatedVisibility
                Surface(Modifier.fillMaxWidth().clickable { full = true }, color = Color.Black.copy(.6f)) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Art(cur, Modifier.size(44.dp)); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text(cur.title, maxLines = 1); Text(cur.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall) }
                        NowPlayingBars(playing, Modifier.size(20.dp)); EmbossedIconButton({ c?.let { if (it.isPlaying) it.pause() else it.play() } }, Modifier.size(44.dp)) { Crossfade(playing, label = "pp") { p -> Icon(if (p) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause") } }
                    }
                }
                }
                NavigationBar(containerColor = Color.Black.copy(.6f)) {
                    listOf("Home" to Icons.Default.Home, "Library" to Icons.Default.LibraryMusic, "Search" to Icons.Default.Search,
                        "Playlists" to Icons.Default.QueueMusic, "Settings" to Icons.Default.Settings).forEachIndexed { i, (n, ic) ->
                        NavigationBarItem(tab == i, { scope.launch { pager.animateScrollToPage(i) } }, { Icon(ic, n, Modifier.scale(animateFloatAsState(if (tab == i) 1.2f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "nav").value)) }, label = { Text(n) })
                    }
                }
            }
        }) { pad ->
            HorizontalPager(pager, Modifier.padding(pad), beyondViewportPageCount = 1) { page ->
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).statusBarsPadding().graphicsLayer {
                val o = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                alpha = 1f - o * 0.6f; scaleX = 1f - o * 0.08f; scaleY = scaleX }) {
                when (page) {
                    0 -> {
                        val sections = remember(tracks) { listOf(
                            "Recently played" to tracks.filter { it.lastPlayed > 0 }.sortedByDescending { it.lastPlayed }.take(10),
                            "Most played" to tracks.filter { it.playCount > 0 }.sortedByDescending { it.playCount }.take(10),
                            "Favorites" to tracks.filter { it.favorite },
                            "Recently added" to tracks.sortedByDescending { it.addedAt }.take(10)) }
                        AnimatedVisibility(tracks.isEmpty()) { Text("Import music in the Library tab to get started.", Modifier.padding(top = 32.dp)) }
                        LazyColumn { sections.forEach { (h, l) -> if (l.isNotEmpty()) {
                            item(key = "h-$h") { Text(h, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp).animateItem()) }
                            items(l.size, key = { "$h-${l[it].id}" }) { Box(Modifier.animateItem()) { TrackRow(l, it) } } } } }
                    }
                    1 -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            EmbossedIconButton({ picker.launch(arrayOf("audio/mpeg", "audio/wav", "audio/x-wav", "audio/ogg", "audio/mp4", "audio/x-m4a")) }, Modifier.height(44.dp), RoundedCornerShape(22.dp)) { Text("Import", Modifier.padding(horizontal = 18.dp)) }
                            var sm by remember { mutableStateOf(false) }
                            EmbossedIconButton({ sm = true }, Modifier.height(44.dp), RoundedCornerShape(22.dp)) { Text("Sort: $sort", Modifier.padding(horizontal = 16.dp)) }
                            DropdownMenu(sm, { sm = false }) { listOf("Title", "Artist", "Album", "Duration", "Added", "Plays").forEach { o -> DropdownMenuItem({ Text(o) }, { sort = o; prefs.edit().putString("sort", o).apply(); sm = false }) } }
                            EmbossedIconButton({ if (sorted.isNotEmpty()) { mode = PlayMode.SHUFFLE; prefs.edit().putInt("mode", mode.ordinal).apply(); play(sorted, kotlin.random.Random.nextInt(sorted.size)) } }, Modifier.size(40.dp)) { Icon(Icons.Default.Shuffle, "Shuffle all") }
                            EmbossedIconButton({ asc = !asc; prefs.edit().putBoolean("asc", asc).apply() }, Modifier.size(40.dp)) { val rot by animateFloatAsState(if (asc) 0f else 180f, tween(250), label = "sort"); Icon(Icons.Default.ArrowUpward, "Toggle sort direction", Modifier.rotate(rot)) }
                        }
                        
                        AnimatedVisibility(scanning) { LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp)) }
                        AnimatedVisibility(tracks.isEmpty()) { Text("Your library is empty. Import music to begin.", Modifier.padding(top = 32.dp)) }
                        LazyColumn { items(sorted.size, key = { sorted[it].id }) { Box(Modifier.animateItem()) { TrackRow(sorted, it) } } }
                    }
                    2 -> SearchScreen(tracks, prefs) { l, i -> TrackRow(l, i) }
                    3 -> {
                        Box(Modifier.padding(top = 8.dp)) { EmbossedIconButton({ newPl = true }, Modifier.height(44.dp), RoundedCornerShape(22.dp)) { Text("New playlist", Modifier.padding(horizontal = 18.dp)) } }
                        AnimatedVisibility(playlists.isEmpty()) { Text("No playlists yet. Create one, then add songs from a track menu.", Modifier.padding(top = 24.dp)) }
                        LazyColumn { items(playlists, key = { it.id }) { p -> ListItem({ Text(p.name) }, Modifier.animateItem().clickable { scope.launch { val l = dao.playlistTracks(p.id); if (l.isNotEmpty()) play(l, 0) else msg = "Playlist is empty" } },
                            trailingContent = { IconButton({ scope.launch { dao.clearPlaylist(p.id); dao.deletePlaylist(p.id) } }) { Icon(Icons.Default.Delete, "Delete playlist") } },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)) } }
                    }
                    else -> SettingsScreen(sub, { sub = it }, themeIdx, { setTheme(it); prefs.edit().putInt("theme", it).apply() },
                        autoImport, { autoImport = it; prefs.edit().putBoolean("auto", it).apply(); if (it) requestScan() }, scanning, { requestScan() },
                        haptics, { setHaptics(it); prefs.edit().putBoolean("haptics", it).apply() }, speed, { speed = it; prefs.edit().putFloat("speed", it).apply() },
                        if (sleepEnd > 0) android.text.format.DateFormat.getTimeFormat(ctx).format(java.util.Date(sleepEnd)) else "",
                        { min -> sleepEnd = if (min > 0) System.currentTimeMillis() + min * 60_000L else 0L },
                        profName, profEmail, { n, e -> if (n.isNotBlank()) profName = n; profEmail = e; prefs.edit().putString("pname", profName).putString("pemail", e).apply() },
                        { profName = ""; profEmail = ""; prefs.edit().remove("pname").remove("pemail").apply(); greeted = false; sub = 0 }, { wipeAll() })
                }
            }
            }
        }

        AnimatedVisibility(full && cur != null, enter = slideInVertically(tween(420)) { it } + fadeIn(tween(420)), exit = slideOutVertically(tween(320)) { it } + fadeOut(tween(320))) {
            val t = cur ?: return@AnimatedVisibility
            NowPlayingScreen(
                track = t, tracks = tracks, playing = playing, pos = pos, duration = ((c?.duration ?: 0L).takeIf { it > 0 } ?: t.durationMs),
                mode = mode,
                nextTitle = c?.let { m -> if (m.hasNextMediaItem()) m.getMediaItemAt(m.nextMediaItemIndex).mediaMetadata.title?.toString() else null },
                onClose = { full = false }, onPlayPause = { c?.let { if (it.isPlaying) it.pause() else it.play() } },
                onNext = { c?.seekToNextMediaItem() }, onPrev = { c?.seekToPreviousMediaItem() },
                onSeek = { ms -> c?.seekTo(ms); pos = ms },
                onMode = { mode = mode.next(); prefs.edit().putInt("mode", mode.ordinal).apply(); c?.let { mode.apply(it) } },
                onFavorite = { scope.launch { dao.setFav(t.id, !t.favorite) } },
                onQueue = { showQueue = true },
                onStudio = { full = false; sub = 2; scope.launch { pager.animateScrollToPage(4) } },
                onAddToPlaylist = { addFor = t }
            )
        }
        Banner(msg, Modifier.align(Alignment.TopCenter))
        AnimatedVisibility(booted && profName.isBlank(), enter = fadeIn(), exit = fadeOut(tween(500))) {
            WelcomeScreen { n, e -> profName = n; profEmail = e; prefs.edit().putString("pname", n).putString("pemail", e).apply() }
        }
        AnimatedVisibility(!booted, exit = fadeOut(tween(600))) { SplashScreen() }
    }

    BackHandler(full) { full = false }
    if (askPerm) AlertDialog({ askPerm = false; askNotif() }, title = { Text("Find your music?") },
        text = { Text("NyxenPlayer needs access to audio files on your device so it can find and add your .mp3, .m4a, .ogg and .wav songs automatically. It only reads music files and never uploads them.") },
        confirmButton = { TextButton({ askPerm = false; permLauncher.launch(audioPerm) }) { Text("Continue") } },
        dismissButton = { TextButton({ askPerm = false; askNotif() }) { Text("Not now") } })
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
