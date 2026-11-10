package com.mono.player.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import kotlinx.coroutines.launch
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mono.player.Art
import com.mono.player.AuroraGlow
import com.mono.player.EmbossedIconButton
import com.mono.player.LocalAccent
import com.mono.player.LocalHaptics
import com.mono.player.NowPlayingBars
import com.mono.player.Reveal
import com.mono.player.data.Track
import com.mono.player.fmt
import com.mono.player.playback.PlayMode

/** Waveform-style seek bar. The bar shapes are decorative (generated from the song id), the progress and seeking are real. */
@Composable
private fun WaveSeek(seed: Long, progress: () -> Float, drag: MutableFloatState, onSeek: (Float) -> Unit, accent: Color, modifier: Modifier) {
    val bars = remember(seed) {
        val r = java.util.Random(seed)
        FloatArray(52) { i -> (0.22f + 0.78f * (0.5f + 0.5f * kotlin.math.sin(i * 0.5f + r.nextFloat() * 3f)) * (0.55f + 0.45f * r.nextFloat())).coerceIn(0.18f, 1f) }
    }
    Canvas(
        modifier.pointerInput(Unit) { detectTapGestures { o -> onSeek((o.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag.floatValue = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { val d = drag.floatValue; drag.floatValue = -1f; if (d >= 0f) onSeek(d) },
                    onDragCancel = { drag.floatValue = -1f }
                ) { change, _ -> change.consume(); drag.floatValue = (change.position.x / size.width).coerceIn(0f, 1f) }
            }
    ) {
        val n = bars.size; val gap = 3.dp.toPx(); val w = (size.width - gap * (n - 1)) / n
        val p = if (drag.floatValue >= 0f) drag.floatValue else progress()
        for (i in 0 until n) {
            val h = bars[i] * size.height
            val played = (i + 0.5f) / n <= p
            drawRoundRect(if (played) Brush.verticalGradient(listOf(Color.White, accent.copy(alpha = .78f))) else Brush.verticalGradient(listOf(Color.White.copy(alpha = .22f), Color.White.copy(alpha = .22f))), Offset(i * (w + gap), (size.height - h) / 2f), Size(w, h), CornerRadius(w / 2))
        }
        val head = 3.dp.toPx()
        drawRoundRect(accent, Offset(p * (size.width - head), 0f), Size(head, size.height), CornerRadius(head / 2))
    }
}

@Composable
private fun PlayOrb(playing: Boolean, ring: Animatable<Float, AnimationVector1D>, accent: Color, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val hapticsOn = LocalHaptics.current
    val on by animateFloatAsState(if (playing) 1f else 0f, tween(220), label = "on")
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.9f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "orb")
    Box(Modifier.size(112.dp), Alignment.Center) {
        Box(Modifier.size(96.dp).graphicsLayer { val s = 1f + 0.28f * ring.value; scaleX = s; scaleY = s; alpha = (1f - ring.value) * 0.45f * on }.border(1.5.dp, accent, CircleShape))
        Box(
            Modifier.size(84.dp).graphicsLayer { scaleX = sc; scaleY = sc }.shadow(12.dp, CircleShape, ambientColor = accent, spotColor = accent)
                .clip(CircleShape).background(Color.White)
                .clickable(src, null) { if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
            Alignment.Center
        ) {
            Crossfade(playing, label = "orbIcon") { p -> Icon(if (p) Icons.Default.Pause else Icons.Default.PlayArrow, if (p) "Pause" else "Play", Modifier.size(44.dp), tint = Color.Black) }
        }
    }
}

@Composable
private fun ActionPill(icon: ImageVector, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .94f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "action-pill-press")
    Row(
        Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
            .clip(shape).background(Color(0xE8121219)).border(1.dp, Color.White.copy(alpha = .09f), shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 17.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { Icon(icon, null, Modifier.size(18.dp), tint = Color.White); Spacer(Modifier.width(8.dp)); Text(label, color = Color.White, fontSize = 13.sp) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(
    track: Track, tracks: List<Track>, playing: Boolean, pos: Long, duration: Long, mode: PlayMode, nextTitle: String?,
    onClose: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit, onSeek: (Long) -> Unit,
    onMode: () -> Unit, onFavorite: () -> Unit, onQueue: () -> Unit, onStudio: () -> Unit, onAddToPlaylist: () -> Unit, onSpotify: () -> Unit,
    sleepActive: Boolean, onSleep: () -> Unit, lyrics: String?, onAddLyrics: () -> Unit, onClearLyrics: () -> Unit
) {
    val accent = LocalAccent.current
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val shownX by animateFloatAsState(if (dragging) dragX else 0f, if (dragging) snap<Float>() else spring<Float>(Spring.DampingRatioMediumBouncy), label = "player-swipe-x")
    val shownY by animateFloatAsState(if (dragging) dragY else 0f, if (dragging) snap<Float>() else spring<Float>(Spring.DampingRatioLowBouncy), label = "player-swipe-y")
    val artScale by animateFloatAsState(if (playing) 1f else .97f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "player-art-scale")
    val progress by animateFloatAsState((pos.toFloat() / duration.coerceAtLeast(1L)).coerceIn(0f, 1f), tween(180, easing = LinearEasing), label = "player-progress")
    val drag = remember { mutableFloatStateOf(-1f) }
    val ring = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val burst = remember { Animatable(0f) }
    val favorite by rememberUpdatedState(track.favorite)
    var showLyrics by remember { mutableStateOf(false) }

    LaunchedEffect(playing) {
        if (playing) while (true) {
            ring.snapTo(0f)
            ring.animateTo(1f, tween(1550, easing = LinearOutSlowInEasing))
        } else ring.snapTo(0f)
    }

    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { translationY = shownY.coerceAtLeast(0f); alpha = 1f - (shownY / 1400f).coerceIn(0f, .5f) }
            .background(Brush.verticalGradient(listOf(Color(0xFF171620), Color(0xFF0B0B11), Color(0xFF050509))))
    ) {
        Crossfade(track.id, animationSpec = tween(220), label = "player-background") { id ->
            Art(tracks.firstOrNull { it.id == id } ?: track,
                Modifier.fillMaxSize().graphicsLayer { scaleX = 1.35f; scaleY = 1.35f; alpha = .36f }.blur(52.dp))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = .68f), Color(0xD20A0910), Color.Black.copy(alpha = .94f))
        )))
        AuroraGlow(accent, Modifier.fillMaxSize(), animate = playing)

        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 10.dp)
                .pointerInput(Unit) {
                    var totalX = 0f
                    var totalY = 0f
                    detectDragGestures(
                        onDragStart = { totalX = 0f; totalY = 0f; dragging = true },
                        onDragEnd = {
                            dragging = false
                            dragX = 0f
                            dragY = 0f
                            if (kotlin.math.abs(totalX) > kotlin.math.abs(totalY)) {
                                if (totalX < -125f) onNext() else if (totalX > 125f) onPrev()
                            } else if (totalY > 190f) onClose()
                        },
                        onDragCancel = { dragging = false; dragX = 0f; dragY = 0f }
                    ) { _, delta ->
                        totalX += delta.x
                        totalY += delta.y
                        val horizontal = kotlin.math.abs(totalX) > kotlin.math.abs(totalY)
                        dragX = if (horizontal) totalX * .48f else 0f
                        dragY = if (!horizontal) totalY.coerceAtLeast(0f) * .48f else 0f
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
                EmbossedIconButton(onClose, Modifier.size(44.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, "Close player", Modifier.size(27.dp))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NowPlayingBars(playing, Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("NOW PLAYING", fontSize = 10.sp, letterSpacing = 2.4.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                    Text("YOUR LIBRARY", fontSize = 9.sp, letterSpacing = 1.8.sp, color = Color.White.copy(alpha = .48f))
                }
                EmbossedIconButton(onStudio, Modifier.size(44.dp)) {
                    Icon(Icons.Default.Equalizer, "Audio Studio", Modifier.size(21.dp))
                }
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val side = minOf(maxWidth * .84f, maxHeight * .94f)
                Box(Modifier.size(side).graphicsLayer {
                    translationX = shownX
                    rotationZ = shownX / 62f
                    alpha = 1f - (kotlin.math.abs(shownX) / 950f).coerceIn(0f, .55f)
                }) {
                    // A visible slice of the record gives the cover a little depth without crowding the screen.
                    Box(
                        Modifier.size(side * .91f).align(Alignment.CenterEnd).offset(x = side * .095f)
                            .graphicsLayer { rotationZ = -13f }
                            .shadow(24.dp, CircleShape, ambientColor = accent.copy(alpha = .7f), spotColor = accent)
                            .clip(CircleShape)
                            .background(Brush.sweepGradient(listOf(Color(0xFF282633), Color(0xFF0B0A10), accent.copy(alpha = .42f), Color(0xFF101016), Color(0xFF282633))))
                            .border(1.dp, Color.White.copy(alpha = .18f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.fillMaxSize(.74f).border(1.dp, Color.White.copy(alpha = .12f), CircleShape))
                        Box(Modifier.size(18.dp).clip(CircleShape).background(accent.copy(alpha = .8f)))
                    }
                    Box(
                        Modifier.size(side * .86f).align(Alignment.CenterStart).offset(x = side * .025f)
                            .graphicsLayer { scaleX = artScale; scaleY = artScale }
                            .pointerInput(Unit) {
                                detectTapGestures(onDoubleTap = {
                                    if (!favorite) {
                                        onFavorite()
                                        scope.launch { burst.snapTo(0f); burst.animateTo(1f, tween(240)) }
                                    }
                                })
                            }
                    ) {
                        Box(Modifier.fillMaxSize().shadow(30.dp, RoundedCornerShape(30.dp), ambientColor = accent, spotColor = accent)
                            .clip(RoundedCornerShape(30.dp))) {
                            Crossfade(track.id, animationSpec = tween(200), label = "player-cover") { id ->
                                Art(tracks.firstOrNull { it.id == id } ?: track, Modifier.fillMaxSize(), 30.dp)
                            }
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = .07f), Color.Transparent, Color.Black.copy(alpha = .18f))
                            )))
                            Box(Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = .22f), RoundedCornerShape(30.dp)))
                        }
                        Icon(Icons.Default.Favorite, null, Modifier.align(Alignment.Center).size(86.dp).graphicsLayer {
                            val amount = burst.value
                            val scale = .45f + amount * .8f
                            scaleX = scale
                            scaleY = scale
                            alpha = kotlin.math.sin(amount * Math.PI).toFloat()
                        }, tint = Color.White)
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(
                    track.id,
                    Modifier.weight(1f),
                    transitionSpec = {
                        (slideInVertically(tween(180)) { it / 3 } + fadeIn(tween(180))) togetherWith
                            (slideOutVertically(tween(180)) { -it / 3 } + fadeOut(tween(150)))
                    },
                    label = "player-track-details"
                ) { id ->
                    val current = tracks.firstOrNull { it.id == id } ?: track
                    Column {
                        Text(current.title, Modifier.basicMarquee(), fontSize = 27.sp, fontWeight = FontWeight.Black,
                            letterSpacing = (-.8).sp, color = Color.White, maxLines = 1, softWrap = false)
                        Text(current.artist, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = .78f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(current.album, fontSize = 9.sp, letterSpacing = 1.5.sp,
                            color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.width(12.dp))
                EmbossedIconButton(onFavorite, Modifier.size(48.dp)) {
                    Crossfade(track.favorite, label = "player-favorite") { isFavorite ->
                        Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            if (isFavorite) "Remove from favorites" else "Add to favorites",
                            Modifier.scale(animateFloatAsState(if (isFavorite) 1.12f else 1f, spring(Spring.DampingRatioHighBouncy), label = "player-favorite-pop").value),
                            tint = if (isFavorite) accent else Color.White)
                    }
                }
            }

            Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                WaveSeek(track.id, { progress }, drag, { fraction -> onSeek((fraction * duration).toLong()) },
                    accent, Modifier.fillMaxWidth().height(42.dp))
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(fmt(if (drag.floatValue >= 0f) (drag.floatValue * duration).toLong() else pos),
                        color = Color.White.copy(alpha = .70f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("-${fmt((duration - (if (drag.floatValue >= 0f) (drag.floatValue * duration).toLong() else pos)).coerceAtLeast(0L))}",
                        color = Color.White.copy(alpha = .70f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                EmbossedIconButton(onMode, Modifier.size(44.dp)) {
                    Crossfade(mode, label = "player-mode") { currentMode ->
                        Icon(when (currentMode) {
                            PlayMode.REPEAT_ALL -> Icons.Default.Repeat
                            PlayMode.REPEAT_ONE -> Icons.Default.RepeatOne
                            PlayMode.IN_ORDER -> Icons.Default.ArrowRightAlt
                            PlayMode.SHUFFLE -> Icons.Default.Shuffle
                        }, "Playback mode: ${currentMode.name}")
                    }
                }
                EmbossedIconButton(onPrev, Modifier.size(52.dp)) {
                    Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(29.dp))
                }
                PlayOrb(playing, ring, accent, onPlayPause)
                EmbossedIconButton(onNext, Modifier.size(52.dp)) {
                    Icon(Icons.Default.SkipNext, "Next", Modifier.size(29.dp))
                }
                EmbossedIconButton(onQueue, Modifier.size(44.dp)) {
                    Icon(Icons.Default.QueueMusic, "Open queue", Modifier.size(21.dp))
                }
            }

            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 2.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionPill(Icons.Default.PlaylistAdd, "Playlist", onAddToPlaylist)
                ActionPill(Icons.Default.OpenInNew, "Spotify", onSpotify)
                ActionPill(Icons.Default.Bedtime, if (sleepActive) "Sleep on" else "Sleep", onSleep)
                ActionPill(Icons.Default.Notes, "Lyrics") { showLyrics = true }
            }

            if (!nextTitle.isNullOrBlank()) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = .055f))
                        .clickable(onClick = onQueue)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.SkipNext, null, Modifier.size(15.dp), tint = accent)
                    Spacer(Modifier.width(7.dp))
                    Text("UP NEXT", fontSize = 9.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Black, color = accent)
                    Spacer(Modifier.width(8.dp))
                    Text(nextTitle, Modifier.weight(1f), color = Color.White.copy(alpha = .82f),
                        fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Default.ChevronRight, null, Modifier.size(17.dp), tint = Color.White.copy(alpha = .55f))
                }
            }
        }

        AnimatedVisibility(showLyrics, enter = fadeIn(tween(200)) + slideInVertically(tween(180)) { it / 5 },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 5 }) {
            LyricsPanel(lyrics, pos, track.title, { showLyrics = false }, onAddLyrics, onClearLyrics)
        }
    }
}
private class LrcLine(val ms: Long, val text: String)

/** Reads [mm:ss.xx] timestamps from an .lrc file. Returns nothing for plain text. */
private fun parseLrc(text: String): List<LrcLine> {
    val re = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    val out = ArrayList<LrcLine>()
    text.lines().forEach { line ->
        val stamps = re.findAll(line).toList()
        if (stamps.isEmpty()) return@forEach
        val body = line.replace(re, "").trim()
        stamps.forEach { m ->
            val min = m.groupValues[1].toLong(); val sec = m.groupValues[2].toLong()
            val frac = m.groupValues[3].padEnd(3, '0').take(3).toLong()
            out.add(LrcLine(min * 60_000 + sec * 1000 + frac, body))
        }
    }
    return out.sortedBy { it.ms }
}

/** Full-screen lyrics. Timed (.lrc) lyrics highlight and scroll with the song; plain text just scrolls. */
@Composable
private fun LyricsPanel(lyrics: String?, pos: Long, title: String, onClose: () -> Unit, onAdd: () -> Unit, onClear: () -> Unit) {
    val lines = remember(lyrics) { if (lyrics.isNullOrBlank()) emptyList() else parseLrc(lyrics) }
    val synced = lines.isNotEmpty()
    val plain = remember(lyrics) { if (lyrics.isNullOrBlank() || synced) emptyList() else lyrics.lines() }
    val current = if (synced) lines.indexOfLast { it.ms <= pos }.coerceAtLeast(0) else -1
    var lyricScale by remember { mutableFloatStateOf(1f) }
    val state = rememberLazyListState()
    LaunchedEffect(current) { if (synced) state.animateScrollToItem((current - 2).coerceAtLeast(0)) }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF17131F), Color(0xF908080D), Color.Black))).pointerInput(Unit) { detectTapGestures { } }.systemBarsPadding()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmbossedIconButton(onClose, Modifier.size(44.dp)) { Icon(Icons.Default.KeyboardArrowDown, "Close lyrics") }
                Text(title, Modifier.weight(1f).padding(horizontal = 14.dp), fontWeight = FontWeight.Black, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!lyrics.isNullOrBlank()) {
                    TextButton(onClear) { Text("Remove") }
                    TextButton({ lyricScale = (lyricScale + 0.15f).coerceAtMost(1.6f) }) { Text("A+") }
                    TextButton({ lyricScale = (lyricScale - 0.15f).coerceAtLeast(0.7f) }) { Text("A−") }
                }
            }
            if (lyrics.isNullOrBlank()) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.Notes, null, Modifier.size(56.dp), tint = Color.White.copy(alpha = .6f))
                    Text("No lyrics yet", Modifier.padding(top = 14.dp), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Pick a .lrc (timed) or .txt file from your phone for this song.", Modifier.padding(top = 6.dp, bottom = 18.dp), color = Color.White.copy(alpha = .7f))
                    EmbossedIconButton(onAdd, Modifier.height(48.dp), RoundedCornerShape(24.dp)) { Text("Add lyrics file", Modifier.padding(horizontal = 24.dp), fontWeight = FontWeight.SemiBold) }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), state, contentPadding = PaddingValues(vertical = 40.dp)) {
                    if (synced) itemsIndexed(lines) { i, l ->
                        val a by animateFloatAsState(if (i == current) 1f else 0.4f, tween(250), label = "ly")
                        Text(l.text.ifBlank { "·" }, Modifier.padding(vertical = 8.dp), fontSize = (if (i == current) 26f else 20f).times(lyricScale).sp,
                            fontWeight = if (i == current) FontWeight.Black else FontWeight.Medium, color = Color.White.copy(alpha = a))
                    } else items(plain.size) { i -> Text(plain[i].ifBlank { " " }, Modifier.padding(vertical = 4.dp), fontSize = (18f * lyricScale).sp, color = Color.White) }
                }
            }
        }
    }
}
