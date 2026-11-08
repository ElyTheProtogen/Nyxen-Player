package com.mono.player.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mono.player.Art
import com.mono.player.EmbossedIconButton
import com.mono.player.LocalAccent
import com.mono.player.LocalHaptics
import com.mono.player.NowPlayingBars
import com.mono.player.Reveal
import com.mono.player.data.Track
import kotlinx.coroutines.delay

private fun glassBorder(a: Color) = Brush.linearGradient(listOf(a.copy(alpha = .7f), Color.White.copy(alpha = .08f)))

/** Soft animated aurora background used behind the playlist naming dialog. */
@Composable
fun AuroraGlow(accent: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val drift by transition.animateFloat(
        initialValue = -0.12f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auroraDrift"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auroraPulse"
    )

    Box(
        modifier.drawBehind {
            val radius = size.maxDimension * 0.72f
            val firstCenter = Offset(size.width * (0.24f + drift), size.height * 0.28f)
            val secondCenter = Offset(size.width * (0.82f - drift), size.height * 0.72f)
            val secondary = lerp(accent, Color(0xFF7657C8), 0.45f)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.24f * pulse), Color.Transparent),
                    center = firstCenter,
                    radius = radius
                ),
                radius = radius,
                center = firstCenter
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(secondary.copy(alpha = 0.17f * pulse), Color.Transparent),
                    center = secondCenter,
                    radius = radius * 0.9f
                ),
                radius = radius * 0.9f,
                center = secondCenter
            )
        }
    )
}


/** Floating pill navigation: the selected tab grows and shows its name with a springy animation. */
@Composable
fun FloatingNav(selected: Int, onSelect: (Int) -> Unit) {
    val accent = LocalAccent.current
    val haptic = LocalHapticFeedback.current
    val hapticsOn = LocalHaptics.current
    val items = listOf("Home" to Icons.Default.Home, "Library" to Icons.Default.LibraryMusic, "Search" to Icons.Default.Search,
        "Playlists" to Icons.Default.QueueMusic, "Settings" to Icons.Default.Settings)
    val shape = RoundedCornerShape(32.dp)
    Row(
        Modifier.fillMaxWidth().shadow(14.dp, shape, ambientColor = accent, spotColor = accent).clip(shape)
            .background(Color.Black.copy(alpha = .62f)).border(1.dp, glassBorder(accent), shape).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { i, (name, icon) ->
            val sel = i == selected
            val wt by animateFloatAsState(if (sel) 2.4f else 1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium), label = "navw")
            val on by animateFloatAsState(if (sel) 1f else 0f, tween(250), label = "navon")
            val pop by animateFloatAsState(if (sel) 1.15f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "navpop")
            Row(
                Modifier.weight(wt).height(48.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(lerp(accent, Color.White, .15f), lerp(accent, Color.Black, .35f))), alpha = on)
                    .clickable { if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress); onSelect(i) },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, name, Modifier.size(24.dp).graphicsLayer { scaleX = pop; scaleY = pop }, tint = Color.White)
                AnimatedVisibility(sel, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                    Text(name, Modifier.padding(start = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
                }
            }
        }
    }
}

/** Floating mini-player with a live progress line. Swipe it left or right to skip. */
@Composable
fun MiniPlayer(track: Track?, playing: Boolean, progress: () -> Float, onOpen: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit) {
    val accent = LocalAccent.current
    AnimatedVisibility(
        track != null,
        enter = slideInVertically(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        val t = track ?: return@AnimatedVisibility
        val shape = RoundedCornerShape(24.dp)
        Column {
            Row(
                Modifier.fillMaxWidth().shadow(10.dp, shape, ambientColor = accent, spotColor = accent).clip(shape).background(Color.Black.copy(alpha = .6f))
                    .border(1.dp, glassBorder(accent), shape).clickable(onClick = onOpen)
                    .drawBehind { val h = 3.dp.toPx(); drawRect(accent, Offset(0f, size.height - h), Size(size.width * progress(), h)) }
                    .pointerInput(Unit) {
                        var total = 0f
                        detectHorizontalDragGestures(onDragStart = { total = 0f }, onDragEnd = { if (total < -90f) onNext() else if (total > 90f) onPrev() }) { _, d -> total += d }
                    }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Art(t, Modifier.size(46.dp)); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artist, fontSize = 12.sp, color = Color.White.copy(alpha = .7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                NowPlayingBars(playing, Modifier.size(18.dp)); Spacer(Modifier.width(10.dp))
                EmbossedIconButton(onToggle, Modifier.size(42.dp)) {
                    Crossfade(playing, label = "mp") { p -> Icon(if (p) Icons.Default.Pause else Icons.Default.PlayArrow, if (p) "Pause" else "Play") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun HomeHeader(name: String, songs: Int, onAvatar: () -> Unit) {
    val accent = LocalAccent.current
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greet = if (h < 12) "Good morning" else if (h < 18) "Good afternoon" else "Good evening"
    Reveal(0) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(greet, color = Color.White.copy(alpha = .75f))
                Text(name.ifBlank { "Listener" }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                Text("$songs songs in your library", fontSize = 12.sp, color = Color.White.copy(alpha = .6f))
            }
            Box(Modifier.size(52.dp).shadow(12.dp, CircleShape, ambientColor = accent, spotColor = accent).clip(CircleShape)
                .background(Brush.linearGradient(listOf(lerp(accent, Color.White, .2f), lerp(accent, Color.Black, .5f)))).clickable(onClick = onAvatar), Alignment.Center) {
                Text((name.firstOrNull() ?: 'N').uppercase(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
fun HeroCard(t: Track, current: Boolean, playing: Boolean, onPlay: () -> Unit) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(28.dp)
    Reveal(80) {
        Row(
            Modifier.fillMaxWidth().padding(top = 18.dp).clip(shape)
                .background(Brush.linearGradient(listOf(accent.copy(alpha = .55f), Color.Black.copy(alpha = .45f)))).border(1.dp, accent.copy(alpha = .6f), shape)
                .clickable(onClick = onPlay).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Art(t, Modifier.size(104.dp).shadow(16.dp, RoundedCornerShape(16.dp), ambientColor = accent, spotColor = accent)); Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(if (current && playing) "NOW PLAYING" else "CONTINUE LISTENING", fontSize = 11.sp, letterSpacing = 2.sp, color = Color.White.copy(alpha = .8f))
                Text(t.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(t.artist, color = Color.White.copy(alpha = .75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                EmbossedIconButton(onPlay, Modifier.height(40.dp), RoundedCornerShape(20.dp)) {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Play", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumCard(t: Track, current: Boolean, playing: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.94f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "card")
    Column(Modifier.width(140.dp).graphicsLayer { scaleX = sc; scaleY = sc }.clickable(src, null, onClick = onClick)) {
        Box {
            Art(t, Modifier.size(140.dp).shadow(if (current) 14.dp else 6.dp, RoundedCornerShape(16.dp), ambientColor = accent, spotColor = accent))
            if (current) Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(26.dp).clip(CircleShape).background(accent), Alignment.Center) { NowPlayingBars(playing, Modifier.size(14.dp)) }
        }
        Text(t.title, Modifier.padding(top = 8.dp), fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(t.artist, fontSize = 12.sp, color = Color.White.copy(alpha = .7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(playsText(t.playCount), fontSize = 11.sp, color = accent, maxLines = 1)
    }
}

@Composable
fun Carousel(title: String, list: List<Track>, curId: Long?, playing: Boolean, onPlay: (Int) -> Unit) {
    Column(Modifier.padding(top = 20.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(bottom = 10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(list.size, key = { list[it].id }) { i -> AlbumCard(list[i], list[i].id == curId, playing) { onPlay(i) } }
        }
    }
}

/** Album / artist / genre tile: cover art, name and song count. Springs in with a small stagger. */
@Composable
fun GroupCard(name: String, count: Int, cover: Track?, index: Int, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay((index % 8) * 40L); a.animateTo(1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)) }
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.95f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "grp")
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).graphicsLayer {
            val v = a.value; alpha = v; translationY = (1f - v) * 60f; scaleX = (0.9f + 0.1f * v) * sc; scaleY = (0.9f + 0.1f * v) * sc
        }.shadow(8.dp, shape, ambientColor = accent, spotColor = accent).clip(shape).clickable(src, null, onClick = onClick)
    ) {
        Art(cover, Modifier.fillMaxSize(), 0.dp)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .8f)), startY = 120f)))
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(name, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$count songs", fontSize = 12.sp, color = Color.White.copy(alpha = .75f))
        }
    }
}

@Composable
fun EmptyState(title: String, subtitle: String, action: String? = null, onAction: () -> Unit = {}) {
    val accent = LocalAccent.current
    Reveal(0) {
        Column(Modifier.fillMaxWidth().padding(top = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(110.dp).shadow(24.dp, CircleShape, ambientColor = accent, spotColor = accent).clip(CircleShape)
                .background(Brush.linearGradient(listOf(lerp(accent, Color.White, .2f), lerp(accent, Color.Black, .55f)))), Alignment.Center) {
                Icon(Icons.Default.LibraryMusic, null, Modifier.size(48.dp), tint = Color.White)
            }
            Text(title, Modifier.padding(top = 20.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
            Text(subtitle, Modifier.padding(top = 6.dp), color = Color.White.copy(alpha = .7f))
            if (action != null) {
                Spacer(Modifier.height(18.dp))
                EmbossedIconButton(onAction, Modifier.height(48.dp), RoundedCornerShape(24.dp)) { Text(action, Modifier.padding(horizontal = 24.dp), fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

/** 2x2 cover collage used as playlist artwork. Empty slots show the fallback tile. */
@Composable
fun PlaylistCollage(pts: List<Track>, size: Dp, corner: Dp = 14.dp) {
    Column(Modifier.size(size).clip(RoundedCornerShape(corner))) {
        Row(Modifier.weight(1f)) { Art(pts.getOrNull(0), Modifier.weight(1f).fillMaxHeight(), 0.dp); Art(pts.getOrNull(1), Modifier.weight(1f).fillMaxHeight(), 0.dp) }
        Row(Modifier.weight(1f)) { Art(pts.getOrNull(2), Modifier.weight(1f).fillMaxHeight(), 0.dp); Art(pts.getOrNull(3), Modifier.weight(1f).fillMaxHeight(), 0.dp) }
    }
}

fun playsText(n: Int): String = when { n <= 0 -> "Not played yet"; n == 1 -> "Played 1 time"; else -> "Played $n times" }

/** Create / rename playlist: a full-screen page with a live, gently floating cover preview of the name you type. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NamePlaylistDialog(title: String, initial: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val accent = LocalAccent.current
    var name by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    val ideas = listOf("Road trip", "Workout", "Chill", "Focus", "Party", "Late night", "Throwbacks", "Study", "Sunday mood")
    val float = rememberInfiniteTransition(label = "float")
    val bob by float.animateFloat(-6f, 6f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val cover = RoundedCornerShape(32.dp)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true; delay(350); runCatching { focus.requestFocus() } }
        AnimatedVisibility(shown, enter = fadeIn(tween(250)) + slideInVertically(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)) { it / 6 }) {
            Box(Modifier.fillMaxSize().background(Color(0xFF050505)).systemBarsPadding().imePadding()) {
                AuroraGlow(accent, Modifier.fillMaxSize())
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        EmbossedIconButton(onDismiss, Modifier.size(44.dp)) { Icon(Icons.Default.Close, "Close") }
                        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = Color.White, textAlign = TextAlign.Center)
                        Spacer(Modifier.size(44.dp))
                    }
                    Spacer(Modifier.height(28.dp))
                    Box(
                        Modifier.size(220.dp).graphicsLayer { translationY = bob; rotationZ = -2f }
                            .shadow(28.dp, cover, ambientColor = accent, spotColor = accent).clip(cover)
                            .background(Brush.linearGradient(listOf(lerp(accent, Color.White, .15f), lerp(accent, Color.Black, .65f))))
                            .border(1.dp, Color.White.copy(alpha = .25f), cover),
                        Alignment.BottomStart
                    ) {
                        Icon(Icons.Default.QueueMusic, null, Modifier.align(Alignment.TopEnd).padding(16.dp).size(36.dp), tint = Color.White.copy(alpha = .85f))
                        Text(name.ifBlank { "Your playlist" }, Modifier.padding(18.dp), fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(28.dp))
                    OutlinedTextField(
                        name, { if (it.length <= 30) name = it }, Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
                        placeholder = { Text("Name your playlist") },
                        trailingIcon = { AnimatedVisibility(name.isNotEmpty(), enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) { IconButton({ name = "" }) { Icon(Icons.Default.Close, "Clear name") } } },
                        supportingText = { Text("${name.length}/30", Modifier.fillMaxWidth(), textAlign = TextAlign.End) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onConfirm(name.trim()) })
                    )
                    Text("Ideas", Modifier.align(Alignment.Start).padding(top = 6.dp, bottom = 6.dp), fontSize = 12.sp, color = Color.White.copy(alpha = .6f))
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { ideas.forEach { i -> SuggestionChip({ name = i }, { Text(i) }) } }
                    Spacer(Modifier.height(24.dp))
                    EmbossedIconButton({ if (name.isNotBlank()) onConfirm(name.trim()) },
                        Modifier.fillMaxWidth().height(56.dp).graphicsLayer { alpha = if (name.isBlank()) 0.5f else 1f }, RoundedCornerShape(28.dp)) {
                        Text(confirmLabel, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** Bolder, tighter headings used across the whole app. */
val NyxenTypography = Typography().let { b ->
    b.copy(
        headlineLarge = b.headlineLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
        headlineMedium = b.headlineMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
        headlineSmall = b.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
        titleLarge = b.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = b.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    )
}

/** Large page title with a quiet subtitle. */
@Composable
fun PageHeader(title: String, subtitle: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Reveal(0) {
        Row(modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text(subtitle, fontSize = 13.sp, color = Color.White.copy(alpha = .65f))
            }
            trailing()
        }
    }
}

/** Compact shortcut tile: accent icon block plus a label. Springs down when pressed. */
@Composable
fun QuickTile(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.95f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "qt")
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier.height(56.dp).graphicsLayer { scaleX = sc; scaleY = sc }.clip(shape).background(Color.Black.copy(alpha = .38f))
            .border(1.dp, Color.White.copy(alpha = .12f), shape).clickable(src, null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(56.dp).background(Brush.linearGradient(listOf(lerp(accent, Color.White, .15f), lerp(accent, Color.Black, .5f)))), Alignment.Center) {
            Icon(icon, null, tint = Color.White)
        }
        Text(label, Modifier.padding(horizontal = 12.dp), fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
