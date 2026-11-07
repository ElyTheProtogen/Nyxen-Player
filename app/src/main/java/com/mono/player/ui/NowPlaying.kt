package com.mono.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mono.player.Art
import com.mono.player.AuroraGlow
import com.mono.player.EmbossedIconButton
import com.mono.player.LocalAccent
import com.mono.player.NowPlayingBars
import com.mono.player.Reveal
import com.mono.player.data.Track
import com.mono.player.fmt
import com.mono.player.playback.PlayMode

@Composable
private fun VinylDisc(track: Track, tracks: List<Track>, size: Dp, angle: Animatable<Float, AnimationVector1D>, glow: () -> Float, accent: Color) {
    Box(Modifier.size(size), Alignment.Center) {
        Box(Modifier.fillMaxSize().graphicsLayer { val g = glow(); scaleX = 1f + 0.06f * g; scaleY = 1f + 0.06f * g; alpha = 0.35f + 0.4f * g }
            .background(Brush.radialGradient(listOf(accent.copy(alpha = .7f), Color.Transparent)), CircleShape))
        Box(Modifier.fillMaxSize(0.94f).graphicsLayer { rotationZ = angle.value }
            .shadow(24.dp, CircleShape, ambientColor = accent, spotColor = accent).clip(CircleShape).background(Color(0xFF0B0B10)), Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = this.size.minDimension / 2
                for (i in 0 until 14) drawCircle(Color.White.copy(alpha = if (i % 3 == 0) .09f else .04f), radius = r * (0.40f + i * 0.04f), style = Stroke(1.dp.toPx()))
            }
            Crossfade(track.id, animationSpec = tween(500), label = "vinylArt") { id ->
                Art(tracks.firstOrNull { it.id == id } ?: track, Modifier.fillMaxSize(0.62f).clip(CircleShape))
            }
            Box(Modifier.size(14.dp).clip(CircleShape).background(Color(0xFF05030A)).border(2.dp, Color.White.copy(alpha = .25f), CircleShape))
        }
        Canvas(Modifier.fillMaxSize(0.94f)) {
            drawCircle(Brush.sweepGradient(listOf(Color.Transparent, Color.White.copy(alpha = .10f), Color.Transparent, Color.White.copy(alpha = .08f), Color.Transparent)))
        }
    }
}

@Composable
private fun SquareArt(track: Track, tracks: List<Track>, size: Dp, accent: Color) {
    Crossfade(track.id, animationSpec = tween(500), label = "squareArt") { id ->
        Art(tracks.firstOrNull { it.id == id } ?: track,
            Modifier.size(size).shadow(30.dp, RoundedCornerShape(16.dp), ambientColor = accent, spotColor = accent))
    }
}

/** Decorative animated bars (not a real spectrum). Values are read while drawing, so nothing recomposes per frame. */
@Composable
private fun PulseBars(playing: Boolean, accent: Color, modifier: Modifier) {
    val phase = remember { Animatable(0f) }
    LaunchedEffect(playing) { if (playing) while (true) { phase.snapTo(0f); phase.animateTo((2 * Math.PI).toFloat(), tween(1800, easing = LinearEasing)) } }
    val amp by animateFloatAsState(if (playing) 1f else 0f, tween(500), label = "pa")
    Box(modifier.drawWithCache {
        val brush = Brush.verticalGradient(listOf(lerp(accent, Color.White, .5f), accent), startY = 0f, endY = size.height)
        val n = 36; val gap = 4.dp.toPx(); val w = (size.width - gap * (n - 1)) / n
        val corner = CornerRadius(w / 2)
        onDrawBehind {
            val ph = phase.value; val a = amp
            for (i in 0 until n) {
                val wave = 0.5f + 0.5f * kotlin.math.sin(ph * (1 + i % 3) + i * 0.55f)
                val h = (0.16f + a * (0.12f + 0.55f * wave)) * size.height
                drawRoundRect(brush, Offset(i * (w + gap), size.height - h), Size(w, h), corner)
            }
        }
    })
}

@Composable
private fun PlayButton(playing: Boolean, accent: Color, onClick: () -> Unit) {
    val ring = remember { Animatable(0f) }
    LaunchedEffect(playing) { if (playing) while (true) { ring.snapTo(0f); ring.animateTo(1f, tween(1500, easing = LinearOutSlowInEasing)) } }
    val on by animateFloatAsState(if (playing) 1f else 0f, tween(300), label = "on")
    Box(Modifier.size(96.dp), Alignment.Center) {
        Box(Modifier.size(76.dp).graphicsLayer { val s = 1f + 0.35f * ring.value; scaleX = s; scaleY = s; alpha = (1f - ring.value) * 0.5f * on }.border(2.dp, accent, CircleShape))
        EmbossedIconButton(onClick, Modifier.size(76.dp)) {
            Crossfade(playing, label = "pp") { p -> Icon(if (p) Icons.Default.Pause else Icons.Default.PlayArrow, if (p) "Pause" else "Play", Modifier.size(40.dp)) }
        }
    }
}

@Composable
private fun ActionPill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(Modifier.clip(shape).background(Color.White.copy(alpha = .10f)).border(1.dp, Color.White.copy(alpha = .18f), shape)
        .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = Color.White); Spacer(Modifier.width(8.dp)); Text(label, color = Color.White, fontSize = 13.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(
    track: Track, tracks: List<Track>, playing: Boolean, pos: Long, duration: Long, mode: PlayMode, nextTitle: String?,
    onClose: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit, onSeek: (Long) -> Unit,
    onMode: () -> Unit, onFavorite: () -> Unit, onQueue: () -> Unit, onStudio: () -> Unit, onAddToPlaylist: () -> Unit
) {
    val accent = LocalAccent.current
    var vinyl by remember { mutableStateOf(true) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val swipeShown by animateFloatAsState(if (dragging) dragX else 0f, if (dragging) snap<Float>() else spring<Float>(Spring.DampingRatioMediumBouncy), label = "swipe")
    val angle = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        if (playing) { while (true) angle.animateTo(angle.value + 360f, tween(14000, easing = LinearEasing)) }
        else angle.animateTo(angle.value + 25f, tween(900, easing = FastOutSlowInEasing))
    }
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        if (playing) while (true) { pulse.animateTo(1f, tween(1600, easing = FastOutSlowInEasing)); pulse.animateTo(0f, tween(1600, easing = FastOutSlowInEasing)) }
    }
    val amp by animateFloatAsState(if (playing) 1f else 0f, tween(400), label = "amp")
    val artScale by animateFloatAsState(if (playing) 1f else 0.9f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "artScale")

    Box(Modifier.fillMaxSize().background(Color(0xFF05030A))) {
        Crossfade(track.id, animationSpec = tween(700), label = "bg") { id ->
            Art(tracks.firstOrNull { it.id == id } ?: track, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.4f; scaleY = 1.4f; alpha = 0.55f }.blur(40.dp))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .55f), Color.Black.copy(alpha = .35f), Color.Black.copy(alpha = .88f)))))
        AuroraGlow(accent, Modifier.fillMaxSize(), animate = playing)

        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 22.dp, vertical = 10.dp).pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f; dragging = true },
                    onDragEnd = { dragging = false; dragX = 0f; if (total < -140f) onNext() else if (total > 140f) onPrev() },
                    onDragCancel = { dragging = false; dragX = 0f }
                ) { _, d -> total += d; dragX = total * 0.6f }
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
                val s = minOf(maxWidth, maxHeight) * 0.92f
                Box(
                    Modifier.graphicsLayer {
                        translationX = swipeShown; rotationZ = swipeShown / 40f
                        alpha = 1f - (kotlin.math.abs(swipeShown) / 900f).coerceIn(0f, .6f)
                        scaleX = artScale; scaleY = artScale
                    }.pointerInput(Unit) { detectTapGestures(onTap = { vinyl = !vinyl }) }
                ) {
                    AnimatedContent(vinyl, transitionSpec = {
                        (fadeIn(tween(350)) + scaleIn(tween(350), initialScale = .8f)) togetherWith (fadeOut(tween(250)) + scaleOut(tween(250), targetScale = .8f))
                    }, label = "artMode") { isVinyl ->
                        if (isVinyl) VinylDisc(track, tracks, s, angle, { amp * pulse.value }, accent) else SquareArt(track, tracks, s, accent)
                    }
                }
            }

            Reveal(120) {
                AnimatedContent(track.id, transitionSpec = {
                    (slideInVertically(tween(350)) { it / 2 } + fadeIn(tween(350))) togetherWith (slideOutVertically(tween(250)) { -it / 2 } + fadeOut(tween(250)))
                }, label = "title") { id ->
                    val t = tracks.firstOrNull { it.id == id } ?: track
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(t.title, Modifier.basicMarquee(), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false)
                        Text("${t.artist} · ${t.album}", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(playsText(t.playCount), fontSize = 12.sp, color = accent)
                    }
                }
            }
            Reveal(170) { PulseBars(playing, accent, Modifier.fillMaxWidth().height(34.dp).padding(vertical = 4.dp)) }
            Reveal(220) {
                Column {
                    var drag by remember { mutableStateOf<Float?>(null) }
                    val prog by animateFloatAsState((pos.toFloat() / duration.coerceAtLeast(1)).coerceIn(0f, 1f), tween(500, easing = LinearEasing), label = "seek")
                    Slider(
                        drag ?: prog, { drag = it }, onValueChangeFinished = { drag?.let { onSeek((it * duration).toLong()) }; drag = null },
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = accent, inactiveTrackColor = Color.White.copy(alpha = .2f)),
                        thumb = { Box(Modifier.size(20.dp).shadow(10.dp, CircleShape, ambientColor = accent, spotColor = accent).background(Color.White, CircleShape)) }
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(fmt(drag?.let { (it * duration).toLong() } ?: pos), color = Color.White)
                        Spacer(Modifier.weight(1f)); Text(fmt(duration), color = Color.White)
                    }
                }
            }
            Reveal(270) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    EmbossedIconButton(onMode, Modifier.size(44.dp)) {
                        Crossfade(mode, label = "mode") { m ->
                            Icon(when (m) { PlayMode.REPEAT_ALL -> Icons.Default.Repeat; PlayMode.REPEAT_ONE -> Icons.Default.RepeatOne
                                PlayMode.IN_ORDER -> Icons.Default.ArrowRightAlt; PlayMode.SHUFFLE -> Icons.Default.Shuffle }, "Playback mode: ${m.name}")
                        }
                    }
                    EmbossedIconButton(onPrev, Modifier.size(52.dp)) { Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(28.dp)) }
                    PlayButton(playing, accent, onPlayPause)
                    EmbossedIconButton(onNext, Modifier.size(52.dp)) { Icon(Icons.Default.SkipNext, "Next", Modifier.size(28.dp)) }
                    EmbossedIconButton(onFavorite, Modifier.size(44.dp)) {
                        Crossfade(track.favorite, label = "fav") { f ->
                            Icon(if (f) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite",
                                Modifier.scale(animateFloatAsState(if (f) 1.25f else 1f, spring(Spring.DampingRatioHighBouncy), label = "pop").value))
                        }
                    }
                }
            }
            Reveal(320) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    ActionPill(Icons.Default.QueueMusic, "Queue", onQueue)
                    ActionPill(Icons.Default.PlaylistAdd, "Playlist", onAddToPlaylist)
                    Crossfade(nextTitle, label = "next") { nt ->
                        if (nt != null) Text("Up next: $nt", Modifier.widthIn(max = 130.dp), color = Color.White.copy(alpha = .8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
