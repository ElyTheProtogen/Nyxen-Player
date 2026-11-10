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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
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
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Color(0xF0121218))
            .border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(24.dp))
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { i, (name, icon) ->
            val sel = i == selected
            val weight by animateFloatAsState(if (sel) 1.55f else 1f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium), label = "nav-weight")
            val scale by animateFloatAsState(if (sel) 1.06f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "nav-scale")
            Row(
                Modifier.weight(weight).height(48.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(CircleShape)
                    .background(if (sel) accent else Color.Transparent)
                    .clickable {
                        if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelect(i)
                    },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, name, Modifier.size(20.dp), tint = if (sel) Color(0xFF101016) else Color.White.copy(alpha = .66f))
                AnimatedVisibility(sel, enter = fadeIn(tween(140)) + expandHorizontally(), exit = fadeOut(tween(100)) + shrinkHorizontally()) {
                    Text(name, Modifier.padding(start = 6.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF101016), maxLines = 1)
                }
            }
        }
    }
}@Composable
fun MiniPlayer(track: Track?, playing: Boolean, progress: () -> Float, onOpen: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit) {
    val accent = LocalAccent.current
    AnimatedVisibility(track != null, enter = slideInVertically(spring(Spring.DampingRatioMediumBouncy)) { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        val t = track ?: return@AnimatedVisibility
        val shape = RoundedCornerShape(20.dp)
        Column(Modifier.padding(bottom = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(shape).background(Color(0xF013131A))
                    .border(1.dp, Color.White.copy(alpha = .09f), shape)
                    .clickable(onClick = onOpen)
                    .drawBehind {
                        val h = 2.dp.toPx()
                        drawRect(Color.White.copy(alpha = .10f), Offset(0f, size.height - h), Size(size.width, h))
                        drawRect(accent, Offset(0f, size.height - h), Size(size.width * progress().coerceIn(0f, 1f), h))
                    }
                    .pointerInput(Unit) {
                        var total = 0f
                        detectHorizontalDragGestures(onDragStart = { total = 0f }, onDragEnd = { if (total < -80f) onNext() else if (total > 80f) onPrev() }) { _, d -> total += d }
                    }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Crossfade(t.id, label = "mini-art") { Art(t, Modifier.size(48.dp), 12.dp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artist, fontSize = 12.sp, color = Color.White.copy(alpha = .56f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                NowPlayingBars(playing, Modifier.size(17.dp))
                Spacer(Modifier.width(12.dp))
                EmbossedIconButton(onToggle, Modifier.size(42.dp), CircleShape) {
                    Crossfade(playing, label = "mini-play") { p -> Icon(if (p) Icons.Default.Pause else Icons.Default.PlayArrow, if (p) "Pause" else "Play", tint = Color.White) }
                }
            }
        }
    }
}@Composable
fun HomeHeader(name: String, songs: Int, onAvatar: () -> Unit) {
    val accent = LocalAccent.current
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greet = if (hour < 12) "GOOD MORNING" else if (hour < 18) "GOOD AFTERNOON" else "GOOD EVENING"
    val cardShape = RoundedCornerShape(24.dp)
    Reveal(0) {
        Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("NYXEN  /  YOUR MUSIC", fontSize = 9.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Black, color = accent)
                    Text(greet, Modifier.padding(top = 12.dp), fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = .55f))
                    Text(name.ifBlank { "Listener" }, Modifier.padding(top = 2.dp), fontSize = 32.sp, lineHeight = 36.sp,
                        fontWeight = FontWeight.Black, letterSpacing = (-1.1).sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(19.dp))
                        .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = .68f), Color(0xFF30243F))))
                        .border(1.dp, Color.White.copy(alpha = .22f), RoundedCornerShape(19.dp))
                        .clickable(onClick = onAvatar),
                    Alignment.Center
                ) {
                    Text((name.firstOrNull() ?: 'N').uppercase(), fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFF101016))
                }
            }
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = cardShape, color = Color(0xDD121219), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .09f))) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LibraryMusic, null, Modifier.size(15.dp), tint = accent)
                        Spacer(Modifier.width(7.dp))
                        Text("$songs TRACKS", fontSize = 9.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = .82f))
                    }
                }
                Surface(shape = cardShape, color = Color(0xDD121219), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .09f))) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
                        Spacer(Modifier.width(7.dp))
                        Text("ON DEVICE", fontSize = 9.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = .68f))
                    }
                }
            }
        }
    }
}
@Composable
fun HeroCard(t: Track, current: Boolean, playing: Boolean, onPlay: () -> Unit) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(24.dp)
    Reveal(50) {
        Column(
            Modifier.fillMaxWidth().padding(top = 10.dp).clip(shape)
                .background(Brush.linearGradient(listOf(Color(0xFF211B2B), Color(0xFF15141B), Color(0xFF111116))))
                .border(1.dp, accent.copy(alpha = .32f), shape)
                .clickable(onClick = onPlay).padding(15.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(CircleShape).background(accent.copy(alpha = .15f)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(if (current && playing) "NOW PLAYING" else "PICK UP YOUR SOUND", fontSize = 9.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Black, color = accent)
                }
                Spacer(Modifier.weight(1f))
                NowPlayingBars(playing && current, Modifier.size(17.dp))
            }
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.shadow(16.dp, RoundedCornerShape(18.dp), ambientColor = accent.copy(alpha = .5f), spotColor = accent)) {
                    Art(t, Modifier.size(106.dp), 18.dp)
                }
                Spacer(Modifier.width(15.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, fontSize = 21.sp, lineHeight = 24.sp, fontWeight = FontWeight.Black,
                        color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(t.artist, Modifier.padding(top = 5.dp), fontSize = 13.sp, color = Color.White.copy(alpha = .68f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(13.dp))
                    Row(
                        Modifier.clip(CircleShape).background(accent).padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PlayArrow, null, tint = Color(0xFF101016), modifier = Modifier.size(17.dp))
                        Text("  PLAY NOW", fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Color(0xFF101016))
                    }
                }
            }
        }
    }
}
@Composable
fun AlbumCard(t: Track, current: Boolean, playing: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(20.dp)
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .96f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "album-card")
    Column(
        Modifier.width(148.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape).background(Color(0xD8111118))
            .border(1.dp, if (current) accent.copy(alpha = .58f) else Color.White.copy(alpha = .08f), shape)
            .clickable(src, null, onClick = onClick)
    ) {
        Box {
            Art(t, Modifier.size(148.dp), 19.dp)
            if (current) Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(27.dp).clip(CircleShape).background(accent), Alignment.Center) {
                NowPlayingBars(playing, Modifier.size(14.dp))
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(t.title, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.artist, Modifier.padding(top = 3.dp), fontSize = 12.sp, color = Color.White.copy(alpha = .68f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(playsText(t.playCount), Modifier.padding(top = 5.dp), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = accent, maxLines = 1)
        }
    }
}
@Composable
fun Carousel(title: String, list: List<Track>, curId: Long?, playing: Boolean, onPlay: (Int) -> Unit) {
    Column(Modifier.padding(top = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color.White)
            Text("${list.size} TRACKS", fontSize = 9.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold, color = LocalAccent.current)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(list.size, key = { list[it].id }) { i -> AlbumCard(list[i], list[i].id == curId, playing) { onPlay(i) } }
        }
    }
}

/** Album / artist / genre tile: cover art, name and song count. Springs in with a small stagger. */
@Composable
fun GroupCard(name: String, count: Int, cover: Track?, index: Int, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index % 6) * 24L)
        appear.animateTo(1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow))
    }
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) .96f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "group-press")
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).graphicsLayer {
            val value = appear.value
            alpha = value
            translationY = (1f - value) * 32f
            scaleX = (.96f + .04f * value) * pressScale
            scaleY = (.96f + .04f * value) * pressScale
        }.clip(shape).background(Color(0xD9111118))
            .border(1.dp, Color.White.copy(alpha = .10f), shape)
            .clickable(src, null, onClick = onClick)
    ) {
        Art(cover, Modifier.fillMaxSize(), 0.dp)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = .06f), Color.Black.copy(alpha = .16f), Color.Black.copy(alpha = .88f))
        )))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(11.dp)) {
            Text(name, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$count TRACKS", Modifier.padding(top = 3.dp), fontSize = 9.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}
@Composable
fun EmptyState(title: String, subtitle: String, action: String? = null, onAction: () -> Unit = {}) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(22.dp)
    Reveal(0) {
        Column(
            Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 18.dp)
                .clip(shape).background(Color(0xE8121219))
                .border(1.dp, Color.White.copy(alpha = .09f), shape)
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(60.dp).clip(RoundedCornerShape(18.dp))
                .background(accent.copy(alpha = .14f)), Alignment.Center) {
                Icon(Icons.Default.LibraryMusic, null, Modifier.size(28.dp), tint = accent)
            }
            Text(title, Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center)
            Text(subtitle, Modifier.padding(top = 7.dp), color = Color.White.copy(alpha = .65f),
                textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            if (action != null) {
                Spacer(Modifier.height(18.dp))
                EmbossedIconButton(onAction, Modifier.height(46.dp), RoundedCornerShape(24.dp)) {
                    Text(action, Modifier.padding(horizontal = 22.dp), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
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
fun NamePlaylistDialog(title: String, initial: String, confirmLabel: String, onDismiss: () -> Unit, onAddMusic: ((String) -> Unit)? = null, onConfirm: (String) -> Unit) {
    val accent = LocalAccent.current
    var name by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    val ideas = listOf("Road trip", "Workout", "Chill", "Focus", "Party", "Late night", "Throwbacks", "Study", "Sunday mood")
    val float = rememberInfiniteTransition(label = "float")
    val bob by float.animateFloat(-6f, 6f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val cover = RoundedCornerShape(24.dp)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true; delay(350); runCatching { focus.requestFocus() } }
        AnimatedVisibility(shown, enter = fadeIn(tween(250)) + slideInVertically(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)) { it / 6 }) {
            Box(Modifier.fillMaxSize().background(Color(0xFF09090E)).systemBarsPadding().imePadding()) {
                AuroraGlow(accent, Modifier.fillMaxSize())
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        EmbossedIconButton(onDismiss, Modifier.size(44.dp)) { Icon(Icons.Default.Close, "Close") }
                        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = Color.White, textAlign = TextAlign.Center)
                        Spacer(Modifier.size(44.dp))
                    }
                    Spacer(Modifier.height(28.dp))
                    Box(
                        Modifier.size(220.dp).graphicsLayer { translationY = bob; rotationZ = -1.5f }
                            .shadow(24.dp, cover, ambientColor = accent.copy(alpha = .55f), spotColor = accent)
                            .clip(cover).background(Color(0xFF111118))
                            .border(1.dp, accent.copy(alpha = .38f), cover),
                        Alignment.BottomStart
                    ) {
                        Column(Modifier.fillMaxSize().padding(10.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF09090D))) {
                            Row(Modifier.weight(1f)) {
                                Box(Modifier.weight(1f).fillMaxHeight().background(Brush.linearGradient(listOf(accent.copy(alpha = .42f), Color(0xFF211A2A)))))
                                Box(Modifier.weight(1f).fillMaxHeight().background(Brush.linearGradient(listOf(Color(0xFF201D28), accent.copy(alpha = .18f)))))
                            }
                            Row(Modifier.weight(1f)) {
                                Box(Modifier.weight(1f).fillMaxHeight().background(Brush.linearGradient(listOf(Color(0xFF1B1922), accent.copy(alpha = .25f)))))
                                Box(Modifier.weight(1f).fillMaxHeight().background(Brush.linearGradient(listOf(accent.copy(alpha = .28f), Color(0xFF101015)))))
                            }
                        }
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .74f)))))
                        Icon(Icons.Default.QueueMusic, null, Modifier.align(Alignment.TopEnd).padding(15.dp).size(30.dp), tint = Color.White.copy(alpha = .88f))
                        Text(name.ifBlank { "Your playlist" }, Modifier.align(Alignment.BottomStart).padding(18.dp),
                            fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black, color = Color.White,
                            maxLines = 3, overflow = TextOverflow.Ellipsis)
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
                    onAddMusic?.let { addMusic ->
                        TextButton(onClick = { if (name.isNotBlank()) addMusic(name.trim()) }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Icon(Icons.Default.LibraryMusic, null); Spacer(Modifier.width(8.dp)); Text("Create playlist and add music")
                        }
                    }
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
fun NyxenTypography(customFont: FontFamily? = null): Typography {
    val b = Typography()
    val font = customFont ?: FontFamily.Default
    fun styled(style: androidx.compose.ui.text.TextStyle) = style.copy(fontFamily = font)
    return b.copy(
        displayLarge = styled(b.displayLarge), displayMedium = styled(b.displayMedium), displaySmall = styled(b.displaySmall),
        headlineLarge = styled(b.headlineLarge).copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
        headlineMedium = styled(b.headlineMedium).copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
        headlineSmall = styled(b.headlineSmall).copy(fontWeight = FontWeight.ExtraBold),
        titleLarge = styled(b.titleLarge).copy(fontWeight = FontWeight.Bold),
        titleMedium = styled(b.titleMedium).copy(fontWeight = FontWeight.SemiBold),
        titleSmall = styled(b.titleSmall),
        bodyLarge = styled(b.bodyLarge), bodyMedium = styled(b.bodyMedium), bodySmall = styled(b.bodySmall),
        labelLarge = styled(b.labelLarge), labelMedium = styled(b.labelMedium), labelSmall = styled(b.labelSmall)
    )
}

/** Large page title with a quiet subtitle. */
@Composable
fun PageHeader(title: String, subtitle: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    val accent = LocalAccent.current
    Reveal(0) {
        Row(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.padding(end = 12.dp).width(4.dp).height(42.dp).clip(CircleShape).background(accent))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = Color.White,
                    fontWeight = FontWeight.Black, letterSpacing = (-.7).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, Modifier.padding(top = 2.dp), fontSize = 12.sp, letterSpacing = .15.sp,
                    color = Color.White.copy(alpha = .62f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            trailing()
        }
    }
}
@Composable
fun QuickTile(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .97f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "quick-tile")
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.height(100.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape).background(Color(0xD815151D))
            .border(1.dp, Color.White.copy(alpha = .09f), shape)
            .clickable(source, null, onClick = onClick).padding(13.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(Modifier.size(35.dp).clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = .72f)))), Alignment.Center) {
            Icon(icon, null, tint = Color(0xFF101016), modifier = Modifier.size(19.dp))
        }
        Text(label, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
