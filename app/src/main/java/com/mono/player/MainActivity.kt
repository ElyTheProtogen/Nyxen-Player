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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import kotlinx.coroutines.flow.flowOf
import com.mono.player.ui.NyxenTypography
import com.mono.player.ui.PageHeader
import com.mono.player.ui.QuickTile
import com.mono.player.ui.NamePlaylistDialog
import com.mono.player.ui.isGmail
import com.mono.player.ui.playsText
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.vector.ImageVector
import com.mono.player.ui.PlaylistCollage
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
            var coverMatch by remember { mutableStateOf(prefs.getBoolean("covermatch", true)) }
            var coverAccent by remember { mutableStateOf<Color?>(null) }
            val accent by animateColorAsState(if (coverMatch) (coverAccent ?: Themes[themeIdx].accent) else Themes[themeIdx].accent, tween(700), label = "accent")
            MaterialTheme(typography = NyxenTypography, colorScheme = darkColorScheme(primary = accent, onPrimary = Color.White, secondaryContainer = accent.copy(alpha = .35f),
                onSecondaryContainer = Color.White, surfaceContainerHigh = Color(0xFF17131F), onBackground = Color.White, onSurface = Color.White, onSurfaceVariant = Color.White)) {
                CompositionLocalProvider(LocalContentColor provides Color.White, LocalAccent provides accent, LocalHaptics provides haptics) {
                    App(themeIdx, { themeIdx = it }, haptics, { haptics = it },
                        coverMatch, { coverMatch = it; prefs.edit().putBoolean("covermatch", it).apply() }, { coverAccent = it })
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

/** Picks a bright, saturated color from cover art (small sample, no extra library). */
fun dominantColor(bytes: ByteArray): Color? {
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val small = android.graphics.Bitmap.createScaledBitmap(bmp, 24, 24, true)
    val hsv = FloatArray(3); var best = 0f; var bestHsv: FloatArray? = null
    for (x in 0 until 24) for (y in 0 until 24) {
        android.graphics.Color.colorToHSV(small.getPixel(x, y), hsv)
        val score = hsv[1] * hsv[2]
        if (hsv[2] > 0.25f && hsv[1] > 0.25f && score > best) { best = score; bestHsv = hsv.copyOf() }
    }
    val h = bestHsv ?: return null
    return Color(android.graphics.Color.HSVToColor(floatArrayOf(h[0], h[1].coerceIn(0.5f, 1f), maxOf(h[2], 0.85f))))
}

fun smartIcon(id: Long): ImageVector = when (id) {
    -1L -> Icons.Default.TrendingUp; -2L -> Icons.Default.History; -3L -> Icons.Default.FiberNew
    -4L -> Icons.Default.NightsStay; -5L -> Icons.Default.Timelapse; else -> Icons.Default.RadioButtonUnchecked
}

fun mi(t: Track) = MediaItem.Builder().setMediaId("${t.id}").setUri(t.uri)
    .setMediaMetadata(MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.album).build()).build()

fun fmt(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }

private object ArtCache {
    var version by mutableIntStateOf(0)
    val lru = object : LruCache<Long, ImageBitmap>(24 * 1024 * 1024) { override fun sizeOf(key: Long, value: ImageBitmap) = value.width * value.height * 4 }
}

/** Artwork is loaded lazily from the database (not with the track list) and cached, so lists stay light. */
@Composable
fun Art(t: Track?, mod: Modifier, corner: Dp = 16.dp) {
    val dao = Db.get(LocalContext.current).dao()
    val bmp by produceState<ImageBitmap?>(t?.let { ArtCache.lru.get(it.id) }, t?.id, ArtCache.version) {
        val id = t?.id
        if (id == null) { value = null; return@produceState }
        val hit = ArtCache.lru.get(id)
        if (hit != null) { value = hit; return@produceState }
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = dao.artwork(id) ?: return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
            }.getOrNull()
        }?.also { ArtCache.lru.put(id, it) }
    }
    Box(mod.clip(RoundedCornerShape(corner)).background(Color.White.copy(.12f)), Alignment.Center) {
        val b = bmp
        if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(.6f))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun App(themeIdx: Int, setTheme: (Int) -> Unit, haptics: Boolean, setHaptics: (Boolean) -> Unit, coverMatch: Boolean, setCoverMatch: (Boolean) -> Unit, onCover: (Color?) -> Unit) {
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
    var autoResume by remember { mutableStateOf(prefs.getBoolean("autoResume", false)) }
    var keepAwake by remember { mutableStateOf(prefs.getBoolean("keepAwake", false)) }
    var sleepEnd by remember { mutableLongStateOf(0L) }
    var restored by remember { mutableStateOf(false) }
    var profName by remember { mutableStateOf(prefs.getString("pname", "") ?: "") }
    var profEmail by remember { mutableStateOf(prefs.getString("pemail", "") ?: "") }
    var profVerified by remember { mutableStateOf(prefs.getBoolean("pverified", false)) }
    var guestMode by remember { mutableStateOf(prefs.getBoolean("guestMode", false)) }
    val signedIn = guestMode || (profName.isNotBlank() && isGmail(profEmail) && (!GoogleAuth.configured || profVerified))
    var booted by remember { mutableStateOf(false) }
    var greeted by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    var libCat by remember { mutableIntStateOf(prefs.getInt("libcat", 0)) }
    var openGroup by remember { mutableStateOf<Pair<String, List<Track>>?>(null) }
    var miniProg by remember { mutableFloatStateOf(0f) }
    var openPlId by remember { mutableStateOf<Long?>(null) }
    var renamePl by remember { mutableStateOf<Playlist?>(null) }
    var delPl by remember { mutableStateOf<Playlist?>(null) }
    var editFor by remember { mutableStateOf<Track?>(null) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var addMany by remember { mutableStateOf<List<Long>?>(null) }
    var sleepMin by remember { mutableIntStateOf(0) }
    var askPerm by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    val audioPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun askNotif() { if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS) }
    fun rescan(manual: Boolean = false) { if (scanning) return; scope.launch { scanning = true; try { val r = DeviceScan.run(ctx); prefs.edit().putLong("lastScan", System.currentTimeMillis()).apply(); if (manual || r.added > 0 || r.failed > 0) msg = "Scan complete: ${r.added} added, ${r.duplicates} already in library, ${r.failed} failed" } catch (e: Exception) { msg = "Scan failed: ${e.localizedMessage?.take(80) ?: "unknown error"}" } finally { scanning = false } } }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) rescan(true) else msg = "Permission denied. You can still add songs with Import music."
        askNotif()
    }
    fun hasAudioPerm() = ContextCompat.checkSelfPermission(ctx, audioPerm) == PackageManager.PERMISSION_GRANTED
    fun requestScan() { if (hasAudioPerm()) rescan(true) else askPerm = true }
    fun wipeAll() { scope.launch {
        c?.run { stop(); clearMediaItems() }
        dao.wipeTracks(); dao.wipePlaylistTracks(); dao.wipePlaylists(); dao.wipeLyrics()
        prefs.edit().clear().apply(); AudioFx.resetAll(ctx)
        setTheme(0); setHaptics(true); autoImport = true; speed = 1f; sort = "Title"; asc = true; mode = PlayMode.REPEAT_ALL
        profName = ""; profEmail = ""; profVerified = false; guestMode = false; sub = 0; restored = false; curId = null; greeted = false; started = false
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
        if (i >= 0) { m.setMediaItems(tracks.map(::mi), i, prefs.getLong("lastPos", 0L)); mode.apply(m); m.prepare(); if (autoResume) m.play(); restored = true; curId = "${tracks[i].id}" }
    }
    LaunchedEffect(full) { if (full) pos = c?.currentPosition ?: 0 }
    LaunchedEffect(playing, c) { var n = 0; while (playing) { val p = c?.currentPosition ?: 0; val dd = c?.duration ?: 0L; miniProg = if (dd > 0) (p.toFloat() / dd).coerceIn(0f, 1f) else 0f; if (full) pos = p; if (++n % 10 == 0) prefs.edit().putLong("lastPos", p).apply(); delay(500) }; pos = c?.currentPosition ?: pos }

    LaunchedEffect(msg) { if (msg != null) { delay(3500); msg = null } }
    LaunchedEffect(sleepEnd) {
        if (sleepEnd > 0) {
            try {
                val fade = 20_000L
                delay((sleepEnd - System.currentTimeMillis() - fade).coerceAtLeast(0))
                val steps = 40
                for (k in 1..steps) { c?.volume = 1f - k / steps.toFloat(); delay(fade / steps) }
                c?.pause(); sleepEnd = 0L; msg = "Sleep timer finished"
            } finally { c?.volume = 1f }
        }
    }
    LaunchedEffect(speed, c) { c?.setPlaybackSpeed(speed) }
    LaunchedEffect(playing, keepAwake) {
        val w = ctx.findActivity()?.window ?: return@LaunchedEffect
        if (playing && keepAwake) w.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    DisposableEffect(Unit) { onDispose { ctx.findActivity()?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }
    LaunchedEffect(pager.currentPage) { prefs.edit().putInt("page", pager.currentPage).apply() }
    LaunchedEffect(Unit) { val t0 = System.currentTimeMillis(); delay(700); while (c == null && System.currentTimeMillis() - t0 < 4000) delay(100); booted = true }
    LaunchedEffect(booted, profName, profEmail, profVerified) {
        if (booted && signedIn) {
            if (!greeted) {
                greeted = true
                val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                msg = if (prefs.getBoolean("firstDone", false)) "${if (h < 12) "Good morning" else if (h < 18) "Good afternoon" else "Good evening"}, $profName" else "Welcome to NyxenPlayer, $profName!"
                prefs.edit().putBoolean("firstDone", true).apply()
            }
            if (!started) { started = true; if (autoImport && !hasAudioPerm()) askPerm = true else { if (autoImport && System.currentTimeMillis() - prefs.getLong("lastScan", 0L) > 5 * 60_000L) rescan(); askNotif() } }
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree: Uri? ->
        if (tree != null) scope.launch {
            msg = "Scanning folder..."
            val r = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.takePersistableUriPermission(tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                val found = ArrayList<Uri>()
                val ok = setOf("mp3", "wav", "ogg", "m4a")
                fun walk(d: androidx.documentfile.provider.DocumentFile?) {
                    d?.listFiles()?.forEach { f -> if (f.isDirectory) walk(f) else if ((f.name ?: "").substringAfterLast('.', "").lowercase() in ok) found.add(f.uri) }
                }
                walk(androidx.documentfile.provider.DocumentFile.fromTreeUri(ctx, tree))
                Importer.import(ctx, found)
            }
            msg = "Folder import: added ${r.added}, duplicates ${r.duplicates}, failed ${r.failed}"
        }
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u: Uri? ->
        val t = editFor
        if (u != null && t != null) scope.launch {
            withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } }.getOrNull()?.let { dao.setArtwork(t.id, Importer.small(it)) }
            }
            ArtCache.lru.remove(t.id); ArtCache.version++; msg = "Cover updated"
        }
    }
    val lyricsPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u: Uri? ->
        val id = curId?.toLongOrNull()
        if (u != null && id != null) scope.launch {
            val text = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(u)?.bufferedReader()?.use { it.readText() } }.getOrNull() }
            if (!text.isNullOrBlank()) { dao.setLyrics(Lyrics(id, text.take(200_000))); msg = "Lyrics added" } else msg = "Couldn't read that file"
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        scope.launch { val r = withContext(Dispatchers.IO) { Importer.import(ctx, uris) }; msg = "Added ${r.added}, duplicates ${r.duplicates}, failed ${r.failed}" }
    }
    val curIdL = curId?.toLongOrNull()
    val cur = remember(tracks, curIdL) { tracks.firstOrNull { it.id == curIdL } }
    LaunchedEffect(curIdL, coverMatch) {
        if (!coverMatch) onCover(null) else {
            val id = curIdL
            onCover(if (id == null) null else withContext(Dispatchers.Default) { runCatching { dao.artwork(id)?.let { dominantColor(it) } }.getOrNull() })
        }
    }
    val lyricsFlow = remember(curIdL) { if (curIdL == null) flowOf<String?>(null) else dao.lyricsFlow(curIdL) }
    val lyrics by lyricsFlow.collectAsState(null)
    fun closeApp() {
        prefs.edit().putLong("lastPos", c?.currentPosition ?: 0L).apply()
        AudioFx.save(ctx)
        c?.pause()
        ctx.findActivity()?.finishAndRemoveTask()
    }
    BackHandler(tab != 0) { scope.launch { pager.animateScrollToPage(0) } }
    BackHandler(tab == 0) { closeApp() }
    fun play(list: List<Track>, i: Int) { if (c == null) { msg = "Player is still starting, try again"; return }; c?.run { setMediaItems(list.map(::mi), i, 0); mode.apply(this); prepare(); play() } }
    fun pauseWithFade() { scope.launch { val player = c ?: return@launch; val original = player.volume; for (step in 1..8) { player.volume = original * (1f - step / 8f); delay(25) }; player.pause(); player.volume = original } }

    val sorted = remember(tracks, sort, asc) {
        val cmp: Comparator<Track> = when (sort) {
            "Artist" -> compareBy { it.artist.lowercase() }; "Album" -> compareBy { it.album.lowercase() }
            "Duration" -> compareBy { it.durationMs }; "Added", "Recently added" -> compareBy { it.addedAt }
            "Plays" -> compareBy { it.playCount }; "Genre" -> compareBy { it.genre.lowercase() }
            "Recently played" -> compareBy { it.lastPlayed }; else -> compareBy { it.title.lowercase() }
        }
        tracks.sortedWith(if (asc) cmp else cmp.reversed())
    }

    @Composable fun TrackRow(list: List<Track>, i: Int) {
        val t = list[i]; var menu by remember { mutableStateOf(false) }
        val isCur = t.id == curIdL
        val shape = RoundedCornerShape(20.dp)
        val isSel = t.id in selected
        val bg by animateColorAsState(if (isSel) accent.copy(alpha = .4f) else if (isCur) accent.copy(alpha = .22f) else Color.Black.copy(alpha = .28f), tween(300), label = "rowbg")
        val src = remember { MutableInteractionSource() }
        val pressed by src.collectIsPressedAsState()
        val sc by animateFloatAsState(if (pressed) 0.97f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "rowpress")
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp).graphicsLayer { scaleX = sc; scaleY = sc }.clip(shape).background(bg)
                .border(1.dp, if (isCur) accent.copy(alpha = .7f) else Color.White.copy(alpha = .08f), shape)
                .combinedClickable(src, null, onClick = { if (selected.isNotEmpty()) selected = if (isSel) selected - t.id else selected + t.id else play(list, i) },
                    onLongClick = { selected = if (isSel) selected - t.id else selected + t.id }).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(54.dp)) {
                Art(t, Modifier.fillMaxSize())
                if (isCur) Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = .45f)), Alignment.Center) { NowPlayingBars(playing, Modifier.size(22.dp)) }
                if (isSel) Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha = .75f)), Alignment.Center) { Icon(Icons.Default.Check, "Selected", tint = Color.White) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${t.artist} · ${t.album}", fontSize = 12.sp, color = Color.White.copy(alpha = .7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(playsText(t.playCount), fontSize = 11.sp, color = accent)
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
                    DropdownMenuItem({ Text("Edit info") }, { editFor = t; menu = false })
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
                AnimatedVisibility(selected.isNotEmpty(), enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(24.dp)).background(Color.Black.copy(alpha = .75f))
                            .border(1.dp, accent.copy(alpha = .7f), RoundedCornerShape(24.dp)).padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${selected.size} selected", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = Color.White)
                        IconButton({ addMany = selected.toList() }) { Icon(Icons.Default.PlaylistAdd, "Add selected to playlist") }
                        IconButton({ val ids = selected; scope.launch { ids.forEach { dao.setFav(it, true) } }; msg = "Added to favorites"; selected = emptySet() }) { Icon(Icons.Default.Favorite, "Favorite selected") }
                        IconButton({ val ids = selected; scope.launch { tracks.filter { it.id in ids }.forEach { dao.delete(it) } }; selected = emptySet() }) { Icon(Icons.Default.Delete, "Remove selected") }
                        IconButton({ selected = emptySet() }) { Icon(Icons.Default.Close, "Clear selection") }
                    }
                }
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
                            item(key = "quick") {
                                Reveal(40) {
                                    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            QuickTile(Icons.Default.Shuffle, "Shuffle all", Modifier.weight(1f)) {
                                                if (tracks.isNotEmpty()) { mode = PlayMode.SHUFFLE; prefs.edit().putInt("mode", mode.ordinal).apply(); play(tracks, kotlin.random.Random.nextInt(tracks.size)) } else msg = "Import some music first"
                                            }
                                            QuickTile(Icons.Default.Favorite, "Favorites", Modifier.weight(1f)) { libCat = 4; openGroup = null; prefs.edit().putInt("libcat", 4).apply(); scope.launch { pager.animateScrollToPage(1) } }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            QuickTile(Icons.Default.TrendingUp, "Most replayed", Modifier.weight(1f)) { openPlId = -1L; scope.launch { pager.animateScrollToPage(3) } }
                                            QuickTile(Icons.Default.History, "Recently played", Modifier.weight(1f)) { openPlId = -2L; scope.launch { pager.animateScrollToPage(3) } }
                                        }
                                    }
                                }
                            }
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
                        PageHeader("Library", remember(tracks) { "${tracks.size} songs · ${tracks.distinctBy { it.album }.size} albums" })
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            EmbossedIconButton({ picker.launch(arrayOf("audio/mpeg", "audio/wav", "audio/x-wav", "audio/ogg", "audio/mp4", "audio/x-m4a")) }, Modifier.height(44.dp), RoundedCornerShape(22.dp)) { Text("Import", Modifier.padding(horizontal = 18.dp)) }
                            var sm by remember { mutableStateOf(false) }
                            Box {
                                EmbossedIconButton({ sm = true }, Modifier.height(44.dp), RoundedCornerShape(22.dp)) { Text("Sort: $sort", Modifier.padding(horizontal = 16.dp)) }
                                DropdownMenu(sm, { sm = false }) { listOf("Title", "Artist", "Album", "Genre", "Duration", "Added", "Recently added", "Plays", "Recently played").forEach { o -> DropdownMenuItem({ Text(o) }, { sort = o; prefs.edit().putString("sort", o).apply(); sm = false }) } }
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
                        BackHandler(openPlId != null && tab == 3) { openPlId = null }
                        val mostList = remember(tracks) { tracks.filter { it.playCount > 0 }.sortedByDescending { it.playCount }.take(50) }
                        val recentList = remember(tracks) { tracks.filter { it.lastPlayed > 0 }.sortedByDescending { it.lastPlayed }.take(50) }
                        val weekList = remember(tracks) { val cut = System.currentTimeMillis() - 7L * 24 * 3600 * 1000; tracks.filter { it.addedAt > cut }.sortedByDescending { it.addedAt } }
                        val lateList = remember(tracks) {
                            tracks.filter { t -> t.lastPlayed > 0 && java.util.Calendar.getInstance().apply { timeInMillis = t.lastPlayed }.get(java.util.Calendar.HOUR_OF_DAY).let { h -> h >= 22 || h < 5 } }
                        }
                        val longList = remember(tracks) { tracks.filter { it.durationMs >= 5 * 60_000L }.sortedByDescending { it.durationMs }.take(100) }
                        val neverList = remember(tracks) { tracks.filter { it.playCount == 0 }.take(100) }
                        val smartMap = remember(tracks, mostList, recentList, weekList, lateList, longList, neverList) {
                            val now = System.currentTimeMillis()
                            val day = java.util.Calendar.getInstance().apply {
                                set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                                set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
                            }.timeInMillis
                            val week = now - 7L * 24 * 60 * 60 * 1000
                            val month = now - 30L * 24 * 60 * 60 * 1000
                            fun byLastPlayed(test: (java.util.Calendar) -> Boolean) = tracks.filter { t ->
                                t.lastPlayed > 0 && test(java.util.Calendar.getInstance().apply { timeInMillis = t.lastPlayed })
                            }.sortedByDescending { it.lastPlayed }
                            fun byAdded(test: (java.util.Calendar) -> Boolean) = tracks.filter { t ->
                                test(java.util.Calendar.getInstance().apply { timeInMillis = t.addedAt })
                            }.sortedByDescending { it.addedAt }
                            linkedMapOf(
                                -1L to ("Most replayed" to mostList),
                                -2L to ("Recently played" to recentList),
                                -3L to ("Added this week" to weekList),
                                -4L to ("Late night plays" to lateList),
                                -5L to ("Long songs" to longList),
                                -6L to ("Never played" to neverList),
                                -7L to ("Added today" to byAdded { it.timeInMillis >= day }),
                                -8L to ("Added this month" to byAdded { it.timeInMillis >= month }),
                                -9L to ("Played today" to tracks.filter { it.lastPlayed >= day }.sortedByDescending { it.lastPlayed }),
                                -10L to ("Played this week" to tracks.filter { it.lastPlayed >= week }.sortedByDescending { it.lastPlayed }),
                                -11L to ("Played this month" to tracks.filter { it.lastPlayed >= month }.sortedByDescending { it.lastPlayed }),
                                -12L to ("Forgotten tracks" to tracks.filter { it.lastPlayed > 0 && it.lastPlayed < month }.sortedBy { it.lastPlayed }),
                                -13L to ("Short tracks" to tracks.filter { it.durationMs in 1L until 180_000L }.sortedBy { it.durationMs }),
                                -14L to ("Medium tracks" to tracks.filter { it.durationMs in 180_000L until 300_000L }.sortedBy { it.durationMs }),
                                -15L to ("Epic tracks" to tracks.filter { it.durationMs >= 10 * 60_000L }.sortedByDescending { it.durationMs }),
                                -16L to ("One-play wonders" to tracks.filter { it.playCount == 1 }.sortedByDescending { it.lastPlayed }),
                                -17L to ("Favorite tracks" to tracks.filter { it.favorite }.sortedByDescending { it.lastPlayed }),
                                -18L to ("Non-favorites" to tracks.filter { !it.favorite }.sortedByDescending { it.playCount }),
                                -19L to ("Unknown artists" to tracks.filter { it.artist.startsWith("Unknown", true) }.sortedBy { it.title }),
                                -20L to ("Unknown albums" to tracks.filter { it.album.startsWith("Unknown", true) }.sortedBy { it.title }),
                                -21L to ("No genre tagged" to tracks.filter { it.genre.isBlank() || it.genre.startsWith("Unknown", true) }.sortedBy { it.title }),
                                -22L to ("Morning sessions" to byLastPlayed { it.get(java.util.Calendar.HOUR_OF_DAY) in 5..11 }),
                                -23L to ("Afternoon sessions" to byLastPlayed { it.get(java.util.Calendar.HOUR_OF_DAY) in 12..16 }),
                                -24L to ("Evening sessions" to byLastPlayed { it.get(java.util.Calendar.HOUR_OF_DAY) in 17..21 }),
                                -25L to ("Night sessions" to byLastPlayed { it.get(java.util.Calendar.HOUR_OF_DAY) in 22..23 || it.get(java.util.Calendar.HOUR_OF_DAY) in 0..4 }),
                                -26L to ("Weekend additions" to byAdded { it.get(java.util.Calendar.DAY_OF_WEEK) in setOf(java.util.Calendar.SATURDAY, java.util.Calendar.SUNDAY) }),
                                -27L to ("Unplayed short tracks" to tracks.filter { it.playCount == 0 && it.durationMs in 1L until 180_000L }),
                                -28L to ("Unplayed long tracks" to tracks.filter { it.playCount == 0 && it.durationMs >= 5 * 60_000L }),
                                -29L to ("Most recent favorites" to tracks.filter { it.favorite }.sortedByDescending { it.addedAt }),
                                -30L to ("Recently discovered" to tracks.sortedByDescending { it.addedAt }.take(30)),
                                -31L to ("Least played" to tracks.filter { it.playCount > 0 }.sortedBy { it.playCount }.take(100))
                            )
                        }
                        AnimatedContent(openPlId, transitionSpec = {
                            if (targetState != null) (slideInHorizontally(tween(300)) { it / 3 } + fadeIn(tween(300))) togetherWith (slideOutHorizontally(tween(300)) { -it / 4 } + fadeOut(tween(200)))
                            else (slideInHorizontally(tween(300)) { -it / 4 } + fadeIn(tween(300))) togetherWith (slideOutHorizontally(tween(300)) { it / 3 } + fadeOut(tween(200)))
                        }, label = "pl") { openId ->
                            val smart = openId != null && openId < 0
                            val pl = if (smart) Playlist(openId ?: -1L, smartMap[openId ?: -1L]?.first ?: "Playlist") else playlists.firstOrNull { it.id == openId }
                            val shape = RoundedCornerShape(22.dp)
                            if (pl == null) Column {
                                @Composable fun SmartRow(id: Long, name: String, list: List<Track>, icon: ImageVector) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(shape)
                                            .background(Brush.linearGradient(listOf(accent.copy(alpha = .35f), Color.Black.copy(alpha = .3f)))).border(1.dp, accent.copy(alpha = .5f), shape)
                                            .clickable { openPlId = id }.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box {
                                            PlaylistCollage(list, 64.dp)
                                            Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).size(22.dp).clip(CircleShape).background(accent), Alignment.Center) { Icon(icon, null, Modifier.size(14.dp), tint = Color.White) }
                                        }
                                        Spacer(Modifier.width(14.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(name, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
                                            Text(if (list.isEmpty()) "Play a few songs to fill this" else "${list.size} songs · Auto playlist", fontSize = 12.sp, color = Color.White.copy(alpha = .7f))
                                        }
                                        IconButton({ if (list.isNotEmpty()) play(list, 0) else msg = "Nothing here yet" }) { Icon(Icons.Default.PlayArrow, "Play $name") }
                                    }
                                }
                                Row(Modifier.padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Playlists", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                                    EmbossedIconButton({ newPl = true }, Modifier.height(44.dp), RoundedCornerShape(22.dp)) { Text("New", Modifier.padding(horizontal = 18.dp)) }
                                }
                                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                                    smartMap.forEach { (id, v) -> item(key = "smart$id") { SmartRow(id, v.first, v.second, smartIcon(id)) } }
                                    if (playlists.isEmpty()) item(key = "none") { EmptyState("No custom playlists yet", "Tap New to create one") }
                                    items(playlists, key = { it.id }) { p ->
                                        val pts by dao.playlistTracksFlow(p.id).collectAsState(emptyList())
                                        Row(
                                            Modifier.animateItem().fillMaxWidth().padding(vertical = 5.dp).clip(shape).background(Color.Black.copy(alpha = .3f))
                                                .border(1.dp, Color.White.copy(alpha = .1f), shape).clickable { openPlId = p.id }.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            PlaylistCollage(pts, 64.dp)
                                            Spacer(Modifier.width(14.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(p.name, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text("${pts.size} songs", fontSize = 12.sp, color = Color.White.copy(alpha = .7f))
                                            }
                                            IconButton({ if (pts.isNotEmpty()) play(pts, 0) else msg = "Playlist is empty" }) { Icon(Icons.Default.PlayArrow, "Play playlist") }
                                            IconButton({ delPl = p }) { Icon(Icons.Default.Delete, "Delete playlist") }
                                        }
                                    }
                                }
                            } else Column {
                                val flowPts by dao.playlistTracksFlow(pl.id).collectAsState(emptyList())
                                val pts = if (smart) (smartMap[pl.id]?.second ?: emptyList()) else flowPts
                                var pmenu by remember { mutableStateOf(false) }
                                Row(Modifier.padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    EmbossedIconButton({ openPlId = null }, Modifier.size(40.dp)) { Icon(Icons.Default.ArrowBack, "Back to playlists") }
                                    Spacer(Modifier.weight(1f))
                                    if (!smart) Box {
                                        EmbossedIconButton({ pmenu = true }, Modifier.size(40.dp)) { Icon(Icons.Default.MoreVert, "Playlist options") }
                                        DropdownMenu(pmenu, { pmenu = false }) {
                                            DropdownMenuItem({ Text("Rename") }, { renamePl = pl; pmenu = false })
                                            DropdownMenuItem({ Text("Delete playlist") }, { delPl = pl; pmenu = false })
                                        }
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.shadow(16.dp, RoundedCornerShape(20.dp), ambientColor = accent, spotColor = accent)) { PlaylistCollage(pts, 104.dp, 20.dp) }
                                    Spacer(Modifier.width(16.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(pl.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text("${pts.size} songs · ${pts.sumOf { it.durationMs } / 60000} min" + if (smart) " · Auto playlist" else "", fontSize = 12.sp, color = Color.White.copy(alpha = .7f))
                                        Spacer(Modifier.height(10.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            EmbossedIconButton({ if (pts.isNotEmpty()) play(pts, 0) else msg = "Nothing here yet" }, Modifier.height(40.dp), RoundedCornerShape(20.dp)) {
                                                Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Play", fontWeight = FontWeight.SemiBold) }
                                            }
                                            EmbossedIconButton({ if (pts.isNotEmpty()) { mode = PlayMode.SHUFFLE; prefs.edit().putInt("mode", mode.ordinal).apply(); play(pts, kotlin.random.Random.nextInt(pts.size)) } else msg = "Nothing here yet" }, Modifier.size(40.dp)) { Icon(Icons.Default.Shuffle, "Shuffle playlist") }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                if (pts.isEmpty()) EmptyState(if (smart) "Nothing here yet" else "This playlist is empty", if (smart) "Play some songs and they will show up here" else "Add songs from any track menu")
                                else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                                    items(pts.size, key = { pts[it].id }) { i ->
                                        val t = pts[i]
                                        Row(
                                            Modifier.animateItem().fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = .28f))
                                                .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(20.dp)).clickable { play(pts, i) }.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Art(t, Modifier.size(48.dp)); Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(t.title, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(t.artist, fontSize = 12.sp, color = Color.White.copy(alpha = .7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            if (smart) Text(playsText(t.playCount), fontSize = 11.sp, color = accent, modifier = Modifier.padding(end = 6.dp))
                                            else {
                                                IconButton({ scope.launch { val ids = pts.map { it.id }.toMutableList(); ids.add(i - 1, ids.removeAt(i)); ids.forEachIndexed { k, id -> dao.setPosition(pl.id, id, k) } } }, Modifier.size(36.dp), enabled = i > 0) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                                                IconButton({ scope.launch { val ids = pts.map { it.id }.toMutableList(); ids.add(i + 1, ids.removeAt(i)); ids.forEachIndexed { k, id -> dao.setPosition(pl.id, id, k) } } }, Modifier.size(36.dp), enabled = i < pts.lastIndex) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                                                IconButton({ scope.launch { val rest = pts.filter { it.id != t.id }.map { it.id }; dao.removeFromPlaylist(pl.id, t.id); rest.forEachIndexed { k, id -> dao.setPosition(pl.id, id, k) } } }, Modifier.size(36.dp)) { Icon(Icons.Default.Close, "Remove from playlist") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> SettingsScreen(sub, { sub = it }, themeIdx, { setTheme(it); prefs.edit().putInt("theme", it).apply() },
                        autoImport, { autoImport = it; prefs.edit().putBoolean("auto", it).apply(); if (it) requestScan() }, scanning, { requestScan() },
                        haptics, { setHaptics(it); prefs.edit().putBoolean("haptics", it).apply() }, speed, { speed = it; prefs.edit().putFloat("speed", it).apply() },
                        if (sleepEnd > 0) android.text.format.DateFormat.getTimeFormat(ctx).format(java.util.Date(sleepEnd)) else "",
                        { min -> sleepMin = min; sleepEnd = if (min > 0) System.currentTimeMillis() + min * 60_000L else 0L },
                        profName, profEmail, { n, e -> if (n.isNotBlank()) profName = n; profEmail = e; prefs.edit().putString("pname", profName).putString("pemail", e).apply() },
                        { profName = ""; profEmail = ""; profVerified = false; guestMode = false; prefs.edit().remove("pname").remove("pemail").remove("pverified").remove("guestMode").apply(); greeted = false; sub = 0; scope.launch { GoogleAuth.signOut(ctx) } }, { wipeAll() },
                        tracks, coverMatch, { setCoverMatch(it) }, { folderPicker.launch(null) }, profVerified,
                        autoResume, { autoResume = it; prefs.edit().putBoolean("autoResume", it).apply() },
                        keepAwake, { keepAwake = it; prefs.edit().putBoolean("keepAwake", it).apply() })
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
                onClose = { full = false }, onPlayPause = { c?.let { if (it.isPlaying) pauseWithFade() else it.play() } },
                onNext = { c?.seekToNextMediaItem() }, onPrev = { c?.seekToPreviousMediaItem() },
                onSeek = { ms -> c?.seekTo(ms); pos = ms },
                onMode = { mode = mode.next(); prefs.edit().putInt("mode", mode.ordinal).apply(); c?.let { mode.apply(it) } },
                onFavorite = { scope.launch { dao.setFav(t.id, !t.favorite) } },
                onQueue = { showQueue = true },
                onStudio = { full = false; sub = 2; scope.launch { pager.animateScrollToPage(4) } },
                onAddToPlaylist = { addFor = t },
                onSpotify = {
                    val query = Uri.encode("${t.title} ${t.artist}")
                    runCatching {
                        ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                            Uri.parse("https://open.spotify.com/search/$query")))
                    }.onFailure { msg = "Couldn't open Spotify on this device" }
                },
                sleepActive = sleepEnd > 0,
                onSleep = {
                    val nxt = if (sleepEnd == 0L) 15 else when (sleepMin) { 15 -> 30; 30 -> 60; else -> 0 }
                    sleepMin = nxt; sleepEnd = if (nxt > 0) System.currentTimeMillis() + nxt * 60_000L else 0L
                    msg = if (nxt > 0) "Sleep timer: $nxt min" else "Sleep timer off"
                },
                lyrics = lyrics, onAddLyrics = { lyricsPicker.launch("*/*") }, onClearLyrics = { scope.launch { dao.clearLyrics(t.id) } }
            )
        }
        Banner(msg, Modifier.align(Alignment.TopCenter))
        AnimatedVisibility(booted && !signedIn, enter = fadeIn(), exit = fadeOut(tween(500))) {
            WelcomeScreen(profName, profEmail, GoogleAuth.configured, { GoogleAuth.signIn(ctx.findActivity()) }) { n, e, v ->
                profName = n; profEmail = e; profVerified = v; guestMode = n == "Guest" && e.isBlank()
                prefs.edit().putString("pname", n).putString("pemail", e).putBoolean("pverified", v).putBoolean("guestMode", guestMode).apply()
            }
        }
        AnimatedVisibility(!booted, exit = fadeOut(tween(600))) { SplashScreen() }
    }

    BackHandler(selected.isNotEmpty()) { selected = emptySet() }
    BackHandler(full) { full = false }
    if (askPerm) AlertDialog({ askPerm = false; askNotif() }, title = { Text("Find your music?") },
        text = { Text("NyxenPlayer needs access to audio files on your device so it can find and add your .mp3, .m4a, .ogg and .wav songs automatically. It only reads music files and never uploads them.") },
        confirmButton = { TextButton({ askPerm = false; permLauncher.launch(audioPerm) }) { Text("Continue") } },
        dismissButton = { TextButton({ askPerm = false; askNotif() }) { Text("Not now") } })
    renamePl?.let { p -> NamePlaylistDialog("Rename playlist", p.name, "Save", { renamePl = null }) { n -> scope.launch { dao.renamePlaylist(p.id, n) }; renamePl = null } }
    delPl?.let { p ->
        AlertDialog({ delPl = null }, title = { Text("Delete playlist?") }, text = { Text("\"${p.name}\" will be deleted. Your songs stay in the library.") },
            confirmButton = { TextButton({ scope.launch { dao.clearPlaylist(p.id); dao.deletePlaylist(p.id) }; if (openPlId == p.id) openPlId = null; delPl = null }) { Text("Delete", color = Color(0xFFFF6B6B)) } },
            dismissButton = { TextButton({ delPl = null }) { Text("Cancel") } })
    }
    addMany?.let { ids ->
        AlertDialog({ addMany = null }, confirmButton = { TextButton({ addMany = null }) { Text("Cancel") } }, title = { Text("Add ${ids.size} songs to playlist") }, text = {
            if (playlists.isEmpty()) Text("Create a playlist first.") else LazyColumn { items(playlists) { p ->
                ListItem({ Text(p.name) }, Modifier.clickable {
                    scope.launch { var pos = dao.count(p.id); ids.forEach { dao.addToPlaylist(PlaylistTrack(p.id, it, pos++)) } }
                    msg = "Added to ${p.name}"; addMany = null; selected = emptySet()
                }) } } })
    }
    editFor?.let { t ->
        var title by remember(t.id) { mutableStateOf(t.title) }
        var artist by remember(t.id) { mutableStateOf(t.artist) }
        var album by remember(t.id) { mutableStateOf(t.album) }
        AlertDialog({ editFor = null }, title = { Text("Edit song info") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(artist, { artist = it }, label = { Text("Artist") }, singleLine = true)
                OutlinedTextField(album, { album = it }, label = { Text("Album") }, singleLine = true)
                TextButton({ coverPicker.launch("image/*") }) { Text("Choose cover from gallery") }
            }
        },
            confirmButton = { TextButton({ if (title.isNotBlank()) scope.launch { dao.editInfo(t.id, title.trim(), artist.trim().ifBlank { "Unknown artist" }, album.trim().ifBlank { "Unknown album" }) }; editFor = null }) { Text("Save") } },
            dismissButton = { TextButton({ editFor = null }) { Text("Cancel") } })
    }
    if (showQueue) AlertDialog(
        onDismissRequest = { showQueue = false },
        title = { Text("Queue · ${c?.mediaItemCount ?: 0} tracks") },
        text = {
            Column(Modifier.heightIn(max = 440.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        scope.launch {
                            val player = c
                            if (player != null) {
                                val ids = (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).mediaId.toLongOrNull() }
                                if (ids.isNotEmpty()) {
                                    val stamp = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                                    val pid = dao.addPlaylist(Playlist(name = "Queue · $stamp"))
                                    ids.forEachIndexed { index, id -> dao.addToPlaylist(PlaylistTrack(pid, id, index)) }
                                    msg = "Queue saved as a playlist"
                                } else msg = "Queue is empty"
                            }
                        }
                    }) { Text("Save as playlist") }
                    TextButton(onClick = { c?.clearMediaItems(); showQueue = false; msg = "Queue cleared" }) { Text("Clear queue") }
                }
                LazyColumn {
                    items((c?.mediaItemCount ?: 0)) { i ->
                        val player = c
                        val item = player?.getMediaItemAt(i)
                        val current = i == player?.currentMediaItemIndex
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).background(if (current) LocalAccent.current.copy(alpha = .18f) else Color.Transparent, RoundedCornerShape(12.dp)).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (current) "▶" else "${i + 1}", modifier = Modifier.width(28.dp), color = if (current) LocalAccent.current else Color.White.copy(alpha = .65f))
                            Text(item?.mediaMetadata?.title?.toString() ?: "Track", Modifier.weight(1f).clickable { player?.seekTo(i, 0); showQueue = false }, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
                            IconButton(onClick = { player?.moveMediaItem(i, i - 1) }, enabled = i > 0) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                            IconButton(onClick = { player?.moveMediaItem(i, i + 1) }, enabled = i < (player?.mediaItemCount ?: 0) - 1) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                            IconButton(onClick = { player?.removeMediaItem(i) }) { Icon(Icons.Default.Close, "Remove from queue") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton({ showQueue = false }) { Text("Done") } }
    )
    addFor?.let { t -> AlertDialog({ addFor = null }, confirmButton = { TextButton({ addFor = null }) { Text("Cancel") } }, title = { Text("Add to playlist") }, text = {
        if (playlists.isEmpty()) Text("Create a playlist first.") else LazyColumn { items(playlists) { p ->
            ListItem({ Text(p.name) }, Modifier.clickable { scope.launch { dao.addToPlaylist(PlaylistTrack(p.id, t.id, dao.count(p.id))) }; addFor = null }) } } }) }
    if (newPl) NamePlaylistDialog("New playlist", "", "Create", { newPl = false }) { n -> scope.launch { dao.addPlaylist(Playlist(name = n)) }; newPl = false }
}
