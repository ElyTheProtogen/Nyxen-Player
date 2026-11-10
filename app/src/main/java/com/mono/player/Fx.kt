package com.mono.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.translate
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

val LocalAccent = compositionLocalOf { Color(0xFFB14BFF) }
val LocalHaptics = compositionLocalOf { true }
val LocalButtonSounds = compositionLocalOf { false }

/** Raised button tinted with the current theme accent. Sinks in when pressed, springs back, optional haptic tick. */
@Composable
fun EmbossedIconButton(onClick: () -> Unit, modifier: Modifier = Modifier.size(48.dp), shape: Shape = CircleShape, content: @Composable () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.92f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "press")
    val lift by animateFloatAsState(if (pressed) 1f else 10f, tween(120), label = "lift")
    val haptic = LocalHapticFeedback.current
    val hapticsOn = LocalHaptics.current
    val soundsOn = LocalButtonSounds.current
    val view = androidx.compose.ui.platform.LocalView.current
    val a = LocalAccent.current
    val face = Brush.linearGradient(
        if (pressed) listOf(lerp(a, Color.Black, .75f), lerp(a, Color.Black, .45f)) else listOf(lerp(a, Color.White, .18f), lerp(a, Color.Black, .62f))
    )
    val rim = Brush.linearGradient(if (pressed) listOf(Color.Black, Color.White.copy(.3f)) else listOf(Color.White.copy(.65f), Color.Black.copy(.8f)))
    Box(
        modifier.graphicsLayer { scaleX = s; scaleY = s }.shadow(lift.dp, shape, ambientColor = a, spotColor = a)
            .clip(shape).background(face).border(1.5.dp, rim, shape)
            .clickable(src, null) { if (soundsOn) view.playSoundEffect(android.view.SoundEffectConstants.CLICK); if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
        Alignment.Center
    ) { CompositionLocalProvider(LocalContentColor provides Color.White) { content() } }
}

/** Four bars that dance while playing and settle low when paused. Heights are read in the draw phase, so no recomposition per frame. */
@Composable
fun NowPlayingBars(playing: Boolean, modifier: Modifier = Modifier.size(24.dp)) {
    val hs = remember { List(4) { Animatable(0.25f) } }
    LaunchedEffect(playing) {   // animations only run while playing, so a paused app draws nothing extra
        if (playing) coroutineScope {
            hs.forEachIndexed { i, a ->
                launch {
                    val ms = listOf(420, 560, 360, 500)[i]
                    delay(i * 90L)
                    while (true) { a.animateTo(1f, tween(ms, easing = LinearEasing)); a.animateTo(0.25f, tween(ms, easing = LinearEasing)) }
                }
            }
        }
    }
    val amp by animateFloatAsState(if (playing) 1f else 0f, tween(300), label = "amp")
    val tint = lerp(LocalAccent.current, Color.White, .6f)
    Row(modifier, Arrangement.spacedBy(2.dp), Alignment.Bottom) {
        hs.forEach { h ->
            Box(Modifier.weight(1f).fillMaxHeight().graphicsLayer {
                scaleY = 0.2f + (h.value - 0.2f) * amp; transformOrigin = TransformOrigin(0.5f, 1f)
            }.clip(RoundedCornerShape(2.dp)).background(tint))
        }
    }
}

/** Two slow-drifting glows behind the app. Animated values are read only while drawing. */
@Composable
fun AuroraGlow(accent: Color, modifier: Modifier = Modifier, animate: Boolean = true) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(animate) {
        if (animate) while (true) {
            p.animateTo(1f, tween(14000, easing = LinearEasing)); p.animateTo(0f, tween(14000, easing = LinearEasing))
        }
    }
    Box(modifier.drawWithCache {
        val w = size.width; val h = size.height; val r = w * 0.8f
        val b1 = Brush.radialGradient(listOf(accent.copy(alpha = .34f), Color.Transparent), center = Offset.Zero, radius = r)
        val b2 = Brush.radialGradient(listOf(accent.copy(alpha = .22f), Color.Transparent), center = Offset.Zero, radius = r)
        onDrawBehind {
            val v = p.value
            translate(w * (0.1f + 0.7f * v), h * (0.18f + 0.1f * v)) { drawCircle(b1, r, Offset.Zero) }
            translate(w * (0.9f - 0.7f * v), h * (0.85f - 0.12f * v)) { drawCircle(b2, r, Offset.Zero) }
        }
    })
}

/** Dark translucent card with a thin accent-lit border. */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val a = LocalAccent.current
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.clip(shape).background(Color.Black.copy(alpha = .42f))
            .border(1.dp, Brush.linearGradient(listOf(a.copy(alpha = .7f), Color.White.copy(alpha = .08f))), shape).padding(16.dp),
        content = content
    )
}

/** Fades and slides its content in after [delayMs]; use increasing delays to stagger a screen. */
@Composable
fun Reveal(delayMs: Int = 0, content: @Composable () -> Unit) {
    var on by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(delayMs.toLong()); on = true }
    AnimatedVisibility(on, enter = fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 6 }) { content() }
}
