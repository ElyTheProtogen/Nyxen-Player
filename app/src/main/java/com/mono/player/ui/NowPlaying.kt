package com.mono.player.ui

import androidx.compose.animation.AnimatedContent
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
            drawRoundRect(if (played) Color.White else Color.White.copy(alpha = .28f), Offset(i * (w + gap), (size.height - h) / 2f), Size(w, h), CornerRadius(w / 2))
        }
        val head = 3.dp.toPx()
        drawRoundRect(accent, Offset(p * (size.width - head), 0f), Size(head, size.height), CornerRadius(head / 2))
    }
}

@Composable
private fun PlayOrb(playing: Boolean, ring: Animatable<Float, AnimationVector1D>, accent: Color, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val hapticsOn = LocalHaptics.current
    val on by animateFloatAsState(if (playing) 1f else 0f, tween(300), label = "on")
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.9f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "orb")
    Box(Modifier.size(100.dp), Alignment.Center) {
        Box(Modifier.size(80.dp).graphicsLayer { val s = 1f + 0.4f * ring.value; scaleX = s; scaleY = s; alpha = (1f - ring.value) * 0.55f * on }.border(2.dp, accent, CircleShape))
        Box(
            Modifier.size(80.dp).graphicsLayer { scaleX = sc; scaleY = sc }.shadow(20.dp, CircleShape, ambientColor = accent, spotColor = accent)
                .clip(CircleShape).background(Color.White)
                .clickable(src, null) { if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
            Alignment.Center
        ) {
            Crossfade(playing, label = "orbIcon") { p -> Icon(if (p) Icons.Default.Pause else Icons.Default.PlayArrow, if (p) "Pause" else "Play", Modifier.size(42.dp), tint = Color.Black) }
        }
    }
}

@Composable
private fun ActionPill(icon: ImageVector, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.clip(shape).background(Color.White.copy(alpha = .10f)).border(1.dp, Color.White.copy(alpha = .18f), shape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { Icon(icon, null, Modifier.size(18.dp), tint = Color.White); Spacer(Modifier.width(8.dp)); Text(label, color = Color.White, fontSize = 13.sp) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(
    track: Track, tracks: List<Track>, playing: Boolean, pos: Long, duration: Long, mode: PlayMode, nextTitle: String?,
    onClose: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit, onSeek: (Long) -> Unit,
    onMode: () -> Unit, onFavorite: () -> Unit, onQueue: () -> Unit, onStudio: () -> Unit, onAddToPlaylist: () -> Unit
) {
    val accent = LocalAccent.current
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val shownX by animateFloatAsState(if (dragging) dragX else 0f, if (dragging) snap<Float>() else spring<Float>(Spring.DampingRatioMediumBouncy), label = "sx")
    val shownY by animateFloatAsState(if (dragging) dragY else 0f, if (dragging) snap<Float>() else spring<Float>(Spring.DampingRatioLowBouncy), label = "sy")
    val artScale by animateFloatAsState(if (playing) 1f else 0.9f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "as")
    val prog by animateFloatAsState((pos.toFloat() / duration.coerceAtLeast(1)).coerceIn(0f, 1f), tween(500, easing = LinearEasing), label = "pg")
    val drag = remember { mutableFloatStateOf(-1f) }
    val ring = remember { Animatable(0f) }
    LaunchedEffect(playing) { if (playing) while (true) { ring.snapTo(0f); ring.animateTo(1f, tween(1500, easing = LinearOutSlowInEasing)) } }

    Box(
        Modifier.fillMaxSize().graphicsLayer { translationY = shownY.coerceAtLeast(0f); alpha = 1f - (shownY / 1400f).coerceIn(0f, .5f) }
            .background(Color(0xFF050505))
    ) {
        Crossfade(track.id, animationSpec = tween(700), label = "bg") { id ->
            Art(tracks.firstOrNull { it.id == id } ?: track, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.4f; scaleY = 1.4f; alpha = 0.5f }.blur(40.dp))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .5f), Color.Black.copy(alpha = .3f), Color.Black.copy(alpha = .9f)))))
        AuroraGlow(accent, Modifier.fillMaxSize(), animate = playing)

        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 10.dp).pointerInput(Unit) {
                var tx = 0f; var ty = 0f
                detectDragGestures(
                    onDragStart = { tx = 0f; ty = 0f; dragging = true },
                    onDragEnd = {
                        dragging = false; dragX = 0f; dragY = 0f
                        if (kotlin.math.abs(tx) > kotlin.math.abs(ty)) { if (tx < -140f) onNext() else if (tx > 140f) onPrev() }
                        else if (ty > 220f) onClose()
                    },
                    onDragCancel = { dragging = false; dragX = 0f; dragY = 0f }
                ) { _, d ->
                    tx += d.x; ty += d.y
                    val horizontal = kotlin.math.abs(tx) > kotlin.math.abs(ty)
                    dragX = if (horizontal) tx * 0.6f else 0f
                    dragY = if (!horizontal) ty.coerceAtLeast(0f) * 0.6f else 0f
                }
            },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                EmbossedIconButton(onClose, Modifier.size(44.dp)) { Icon(Icons.Default.KeyboardArrowDown, "Close player") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NowPlayingBars(playing, Modifier.size(14.dp)); Spacer(Modifier.width(6.dp))
                        Text("NOW PLAYING", fontSize = 11.sp, letterSpacing = 3.sp, color = Color.White)
                    }
                    Text("Local library", fontSize = 11.sp, color = Color.White.copy(alpha = .7f))
                }
                EmbossedIconButton(onStudio, Modifier.size(44.dp)) { Icon(Icons.Default.Equalizer, "Audio Studio") }
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val s = minOf(maxWidth, maxHeight) * 0.9f
                Box(
                    Modifier.size(s).graphicsLayer {
                        translationX = shownX; rotationZ = shownX / 50f
                        alpha = 1f - (kotlin.math.abs(shownX) / 900f).coerceIn(0f, .6f)
                        scaleX = artScale; scaleY = artScale
                    }
                ) {
                    listOf(0.84f to 0.30f, 0.92f to 0.50f).forEach { (sc, al) ->
                        Box(Modifier.fillMaxSize().graphicsLayer { scaleX = sc; scaleY = sc; translationY = size.height * (1f - sc) * 1.1f; alpha = al }
                            .clip(RoundedCornerShape(34.dp)).background(accent))
                    }
                    Crossfade(track.id, animationSpec = tween(500), label = "art") { id ->
                        Art(tracks.firstOrNull { it.id == id } ?: track, Modifier.fillMaxSize().shadow(30.dp, RoundedCornerShape(34.dp), ambientColor = accent, spotColor = accent), 34.dp)
                    }
                }
            }

            Reveal(100) {
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    AnimatedContent(track.id, Modifier.weight(1f), transitionSpec = {
                        (slideInVertically(tween(350)) { it / 2 } + fadeIn(tween(350))) togetherWith (slideOutVertically(tween(250)) { -it / 2 } + fadeOut(tween(250)))
                    }, label = "title") { id ->
                        val t = tracks.firstOrNull { it.id == id } ?: track
                        Column {
                            Text(t.title, Modifier.basicMarquee(), fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1, softWrap = false)
                            Text("${t.artist} · ${t.album}", color = Color.White.copy(alpha = .85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(playsText(t.playCount), fontSize = 12.sp, color = accent)
                        }
                    }
                    EmbossedIconButton(onFavorite, Modifier.size(48.dp)) {
                        Crossfade(track.favorite, label = "fav") { f ->
                            Icon(if (f) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite",
                                Modifier.scale(animateFloatAsState(if (f) 1.25f else 1f, spring(Spring.DampingRatioHighBouncy), label = "pop").value))
                        }
                    }
                }
            }
            Reveal(160) {
                Column(Modifier.padding(top = 10.dp)) {
                    WaveSeek(track.id, { prog }, drag, { f -> onSeek((f * duration).toLong()) }, accent, Modifier.fillMaxWidth().height(54.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text(fmt(if (drag.floatValue >= 0f) (drag.floatValue * duration).toLong() else pos), color = Color.White, fontSize = 12.sp)
                        Spacer(Modifier.weight(1f)); Text(fmt(duration), color = Color.White, fontSize = 12.sp)
                    }
                }
            }
            Reveal(220) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    EmbossedIconButton(onMode, Modifier.size(44.dp)) {
                        Crossfade(mode, label = "mode") { m ->
                            Icon(when (m) { PlayMode.REPEAT_ALL -> Icons.Default.Repeat; PlayMode.REPEAT_ONE -> Icons.Default.RepeatOne
                                PlayMode.IN_ORDER -> Icons.Default.ArrowRightAlt; PlayMode.SHUFFLE -> Icons.Default.Shuffle }, "Playback mode: ${m.name}")
                        }
                    }
                    EmbossedIconButton(onPrev, Modifier.size(56.dp)) { Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(30.dp)) }
                    PlayOrb(playing, ring, accent, onPlayPause)
                    EmbossedIconButton(onNext, Modifier.size(56.dp)) { Icon(Icons.Default.SkipNext, "Next", Modifier.size(30.dp)) }
                    EmbossedIconButton(onQueue, Modifier.size(44.dp)) { Icon(Icons.Default.QueueMusic, "Queue") }
                }
            }
            Reveal(280) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
                        ActionPill(Icons.Default.PlaylistAdd, "Add to playlist", onAddToPlaylist)
                        ActionPill(Icons.Default.Equalizer, "Equalizer", onStudio)
                    }
                    Crossfade(nextTitle, label = "next") { nt ->
                        if (nt != null) Row(
                            Modifier.padding(top = 10.dp, bottom = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .08f))
                                .clickable(onClick = onQueue).padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("UP NEXT", fontSize = 10.sp, letterSpacing = 2.sp, color = accent)
                            Spacer(Modifier.width(10.dp))
                            Text(nt, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
