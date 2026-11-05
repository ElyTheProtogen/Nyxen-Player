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
import com.mono.player.ui.Carousel
import com.mono.player.ui.EmptyState
import com.mono.player.ui.FloatingNav
import com.mono.player.ui.GroupCard
import com.mono.player.ui.HeroCard
import com.mono.player.ui.HomeHeader
import com.mono.player.ui.MiniPlayer
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
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

private object ArtCache {
    val lru = object : LruCache<Long, ImageBitmap>(24 * 1024 * 1024) { override fun sizeOf(key: Long, value: ImageBitmap) = value.width * value.height * 4 }
}

/** Artwork is loaded lazily from the database (not with the track list) and cached, so lists stay light. */
@Composable
fun Art(t: Track?, mod: Modifier, corner: Dp = 16.dp) {
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
    Box(mod.clip(RoundedCornerShape(corner)).background(Color.White.copy(.12f)), Alignment.Center) {
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
    var libCat by remember { mutableIntStateOf(prefs.getInt("libcat", 0)) }
    var openGroup by remember { mutableStateOf<Pair<String, List<Track>>?>(null) }
    var miniProg by remember { mutableFloatStateOf(0f) }
    var askPerm by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    val audioPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun askNotif() { if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS) }
    fun rescan(manual: Boolean = false) { if (scanning) return; scope.launch { scanning = true; val r = DeviceScan.run(ctx); scanning = false; prefs.edit().putLong("lastScan", System.currentTimeMillis()).apply(); if (r.added > 0) msg = "Added ${r.added} new songs" else if (manual) msg = "No new songs found" } }
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
    DisposableEffect(c) { val old = c; onDispose { old?.release() } }
    LaunchedEffect(c, tracks.size) {
        val m = c ?: return@LaunchedEffect
        if (restored || m.mediaItemCount > 0 || tracks.isEmpty()) return@LaunchedEffect
        val i = tracks.indexOfFirst { it.id == prefs.getLong("lastId", -1L) }
        if (i >= 0) { m.setMediaItems(tracks.map(::mi), i, prefs.getLong("lastPos", 0L)); mode.apply(m); m.prepare(); restored = true; curId = "${tracks[i].id}" }
    }
    LaunchedEffect(full) { if (full) pos = c?.currentPosition ?: 0 }
    LaunchedEffect(playing, c) { var n = 0; while (playing) { val p = c?.currentPosition ?: 0; val dd = c?.duration ?: 0L; miniProg = if (dd > 0) (p.toFloat() / dd).coerceIn(0f, 1f) else 0f; if (full) pos = p; if (++n % 10 == 0) prefs.edit().putLong("lastPos", p).apply(); delay(500) }; pos = c?.currentPosition ?: pos }

    LaunchedEffect(msg) { if (msg != null) { delay(3500); msg = null } }
    LaunchedEffect(sleepEnd) { if (sleepEnd > 0) { delay((sleepEnd - System.currentTimeMillis()).coerceAtLeast(0)); c?.pause(); sleepEnd = 0L; msg = "Sleep timer finished" } }
    LaunchedEffect(speed, c) { c?.setPlaybackSpeed(speed) }
    LaunchedEffect(pager.currentPage) { prefs.edit().putInt("page", pager.currentPage).apply() }
    LaunchedEffect(Unit) { val t0 = System.currentTimeMillis(); delay(700); while (c == null && System.currentTimeMillis() - t0 < 4000) delay(100); booted = true }
    LaunchedEffect(booted, profName) {
        if (booted && profName.isNotBlank()) {
            if (!greeted) {
                greeted = true
                val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                msg = if (prefs.getBoolean("firstDone", false)) "${if (h < 12) "Good morning" else if (h < 18) "Good afternoon" else "Good evening"}, $profName" else "Welcome to NyxenPlayer, $profName!"
                prefs.edit().putBoolean("firstDone", true).apply()
            }
            if (!started) { started = true; if (autoImport && !hasAudioPerm()) askPerm = true else { if (autoImport && System.currentTimeMillis() - prefs.getLong("lastScan", 0L) > 5 * 60_000L) rescan(); askNotif() } }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        scope.launch { val r = Importer.import(ctx, uris); msg = "Added ${r.added}, duplicates ${r.duplicates}, failed ${r.failed}" }
    }
    val curIdL = curId?.toLongOrNull()
    val cur = remember(tracks, curIdL) { tracks.firstOrNull { it.id == curIdL } }
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
        val isCur = t.id == curIdL
        val shape = RoundedCornerShape(20.dp)
        val bg by animateColorAsState(if (isCur) accent.copy(alpha = .22f) else Color.Black.copy(alpha = .28f), tween(300), label = "rowbg")
        val src = remember { MutableInteractionSource() }
        val pressed by src.collectIsPressedAsState()
        val sc by animateFloatAsState(if (pressed) 0.97f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "rowpress")
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp).graphicsLayer { scaleX = sc; scaleY = sc }.clip(shape).background(bg)
                .border(1.dp, if (isCur) accent.copy(alpha = .7f) else Color.White.copy(alpha = .08f), shape)
                .clickable(src, null) { play(list, i) }.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(54.dp)) {
                Art(t, Modifier.fillMaxSize())
                if (isCur) Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = .45f)), Alignment.Center) { NowPlayingBars(playing, Modifier.size(22.dp)) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${t.artist} · ${t.album}", fontSize = 12.sp, color = Color.White.copy(alpha = .7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (t.favorite) Icon(Icons.Default.Favorite, "Favorite", Modifier.size(16.dp), tint = accent)
            Text(fmt(t.durationMs), Modifier.padding(horizontal = 8.dp), fontSize = 12.sp, color = Color.White.copy(alpha = .6f))
            Box {
                IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "More options") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Play next") }, { c?.addMediaItem((c?.currentMediaItemIndex ?: -1) + 1, mi(t)); menu = false })
                    DropdownMenuItem({ Text("Add to queue") }, { c?.addMediaItem(mi(t)); menu = false })
                    DropdownMenuItem({ Text(if (t.favorite) "Unfavorite" else "Favorite") }, { scope.launch { dao.setFav(t.id, !t.favorite) }; menu = false })
                    DropdownMenuItem({ Text("Add to playlist") }, { addFor = t; menu = false })
                    DropdownMenuItem({ Text("Remove from library") }, { scope.launch { dao.delete(t) }; menu = false })
                }
            }
        }
    }

    val th = Themes[themeIdx]
    val g0 by animateColorAsState(th.top, tween(700), label = "g0")
    val g1 by animateColorAsState(th.mid, tween(700), label = "g1")
    val g2 by animateColorAsState(th.bottom, tween(700), label = "g2")
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(g0, g1, g2))).background(Color.Black.copy(alpha = th.scrim))) {
        AuroraGlow(accent, Modifier.fillMaxSize(), animate = playing)
        Scaffold(containerColor = Color.Transparent, bottomBar = {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 12.dp).padding(bottom = 8.dp).animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))) {
                MiniPlayer(cur, playing, { miniProg }, { full = true }, { c?.let { if (it.isPlaying) it.pause() else it.play() } },
                    { c?.seekToNextMediaItem() }, { c?.seekToPreviousMediaItem() })
                FloatingNav(tab) { i -> scope.launch { pager.animateScrollToPage(i) } }
            }
        }) { pad ->
            HorizontalPager(pager, Modifier.padding(pad), beyondViewportPageCount = 1) { page ->
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).statusBarsPadding().graphicsLayer {
                val off = (pager.currentPage - page) + pager.currentPageOffsetFraction
                val o = off.absoluteValue.coerceIn(0f, 1f)
                alpha = 1f - o * 0.6f; scaleX = 1f - o * 0.08f; scaleY = scaleX
                cameraDistance = 14f * density; rotationY = off * 14f }) {
                when (page) {
                    0 -> {
                        val recent = remember(tracks) { tracks.filter { it.lastPlayed > 0 }.sortedByDescending { it.lastPlayed }.take(12) }
                        val most = remember(tracks) { tracks.filter { it.playCount > 0 }.sortedByDescending { it.playCount }.take(12) }
                        val favs = remember(tracks) { tracks.filter { it.favorite }.take(24) }
                        val added = remember(tracks) { tracks.sortedByDescending { it.addedAt }.take(12) }
                        val hero = cur ?: recent.firstOrNull()
                        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                            item(key = "hdr") { HomeHeader(profName, tracks.size) { sub = 6; scope.launch { pager.animateScrollToPage(4) } } }
                            if (tracks.isEmpty()) item(key = "empty") { EmptyState("Your library is empty", "Add songs to start listening", "Import music") { picker.launch(arrayOf("audio/mpeg", "audio/wav", "audio/x-wav", "audio/ogg", "audio/mp4", "audio/x-m4a")) } }
                            else {
                                hero?.let { h -> item(key = "hero") { HeroCard(h, h.id == curIdL, playing) { val l = recent.ifEmpty { tracks }; play(l, l.indexOfFirst { it.id == h.id }.coerceAtLeast(0)) } } }
                                if (recent.isNotEmpty()) item(key = "c-recent") { Carousel("Recently played", recent, curIdL, playing) { i -> play(recent, i) } }
                                if (most.isNotEmpty()) item(key = "c-most") { Carousel("Most played", most, curIdL, playing) { i -> play(most, i) } }
                                if (favs.isNotEmpty()) item(key = "c-favs") { Carousel("Favorites", favs, curIdL, playing) { i -> play(favs, i) } }
                                item(key = "c-added") { Carousel("Recently added", added, curIdL, playing) { i -> play(added, i) } }
                            }
                        }
                    }
                    1 -> {
                        BackHandler(openGroup != null) { openGroup = null }
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            EmbossedIconButton({ picker.launch(arrayOf("audio/mpeg", "audio/wav", "audio/x-wav", "audio/ogg", "audio/mp4", "audio/x-m4a")) }, Modifier.height(44.dp), RoundedCornerShape(22.dp)) { Text("Import", Modifier.padding(horizontal = 18.dp)) }
                            var sm by remember { mutableStateOf(false) }
                            Box {
                                EmbossedIconButton({ sm = true }, Modifier.height(44.dp), RoundedCornerShape(22.dp)) { Text("Sort: $sort", Modifier.padding(horizontal = 16.dp)) }
                                DropdownMenu(sm, { sm = false }) { listOf("Title", "Artist", "Album", "Duration", "Added", "Plays").forEach { o -> DropdownMenuItem({ Text(o) }, { sort = o; prefs.edit().putString("sort", o).apply(); sm = false }) } }
                            }
                            EmbossedIconButton({ if (sorted.isNotEmpty()) { mode = PlayMode.SHUFFLE; prefs.edit().putInt("mode", mode.ordinal).apply(); play(sorted, kotlin.random.Random.nextInt(sorted.size)) } }, Modifier.size(40.dp)) { Icon(Icons.Default.Shuffle, "Shuffle all") }
                            EmbossedIconButton({ asc = !asc; prefs.edit().putBoolean("asc", asc).apply() }, Modifier.size(40.dp)) { val rot by animateFloatAsState(if (asc) 0f else 180f, tween(250), label = "sort"); Icon(Icons.Default.ArrowUpward, "Toggle sort direction", Modifier.rotate(rot)) }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Tracks", "Albums", "Artists", "Genres", "Favorites").forEachIndexed { i, n ->
                                FilterChip(libCat == i, { libCat = i; openGroup = null; prefs.edit().putInt("libcat", i).apply() }, { Text(n) })
                            }
                        }
                        AnimatedVisibility(scanning) { LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp)) }
                        val groups = remember(tracks, libCat) {
                            if (libCat in 1..3) tracks.groupBy { when (libCat) { 1 -> it.album; 2 -> it.artist; else -> it.genre } }.toList().sortedBy { it.first.lowercase() } else emptyList()
                        }
                        val favList = remember(tracks) { tracks.filter { it.favorite } }
                        if (tracks.isEmpty()) EmptyState("Your library is empty", "Add songs to start listening", "Import music") { picker.launch(arrayOf("audio/mpeg", "audio/wav", "audio/x-wav", "audio/ogg", "audio/mp4", "audio/x-m4a")) }
                        else AnimatedContent(Pair(libCat, openGroup?.first), transitionSpec = {
                            (fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 14 }) togetherWith fadeOut(tween(160))
                        }, label = "lib") { _ ->
                            val og = openGroup
                            if (og != null) Column {
                                Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    EmbossedIconButton({ openGroup = null }, Modifier.size(40.dp)) { Icon(Icons.Default.ArrowBack, "Back") }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) { Text(og.first, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${og.second.size} songs", fontSize = 12.sp, color = Color.White.copy(alpha = .7f)) }
                                    EmbossedIconButton({ play(og.second, 0) }, Modifier.height(40.dp), RoundedCornerShape(20.dp)) { Text("Play", Modifier.padding(horizontal = 18.dp)) }
                                }
                                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) { items(og.second.size, key = { og.second[it].id }) { Box(Modifier.animateItem()) { TrackRow(og.second, it) } } }
                            } else if (libCat == 0) LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) { items(sorted.size, key = { sorted[it].id }) { Box(Modifier.animateItem()) { TrackRow(sorted, it) } } }
                            else if (libCat == 4) {
                                if (favList.isEmpty()) EmptyState("No favorites yet", "Tap the heart on a song to add it here")
                                else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) { items(favList.size, key = { favList[it].id }) { Box(Modifier.animateItem()) { TrackRow(favList, it) } } }
                            } else LazyVerticalGrid(GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                                items(groups.size, key = { groups[it].first }) { idx -> GroupCard(groups[idx].first, groups[idx].second.size, groups[idx].second.firstOrNull(), idx) { openGroup = groups[idx] } }
                            }
                        }
                    }
                    2 -> SearchScreen(tracks, prefs) { l, i -> TrackRow(l, i) }
                    3 -> {
                        Row(Modifier.padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Playlists", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                            EmbossedIconButton({ newPl = true }, Modifier.height(44.dp), RoundedCornerShape(22.dp)) { Text("New", Modifier.padding(horizontal = 18.dp)) }
                        }
                        if (playlists.isEmpty()) EmptyState("No playlists yet", "Create one, then add songs from a track menu")
                        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                            items(playlists, key = { it.id }) { p ->
                                val pts by produceState(emptyList<Track>(), p.id, tracks.size) { value = dao.playlistTracks(p.id) }
                                val shape = RoundedCornerShape(22.dp)
                                Row(
                                    Modifier.animateItem().fillMaxWidth().padding(vertical = 5.dp).clip(shape).background(Color.Black.copy(alpha = .3f))
                                        .border(1.dp, Color.White.copy(alpha = .1f), shape)
                                        .clickable { if (pts.isNotEmpty()) play(pts, 0) else msg = "Playlist is empty" }.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp))) {
                                        Row(Modifier.weight(1f)) { Art(pts.getOrNull(0), Modifier.weight(1f).fillMaxHeight(), 0.dp); Art(pts.getOrNull(1), Modifier.weight(1f).fillMaxHeight(), 0.dp) }
                                        Row(Modifier.weight(1f)) { Art(pts.getOrNull(2), Modifier.weight(1f).fillMaxHeight(), 0.dp); Art(pts.getOrNull(3), Modifier.weight(1f).fillMaxHeight(), 0.dp) }
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(p.name, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${pts.size} songs", fontSize = 12.sp, color = Color.White.copy(alpha = .7f))
                                    }
                                    IconButton({ scope.launch { dao.clearPlaylist(p.id); dao.deletePlaylist(p.id) } }) { Icon(Icons.Default.Delete, "Delete playlist") }
                                }
                            }
                        }
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
