package com.mono.player

import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/** Raised, light-from-top-left button. Pressing it sinks it in (debossed), shrinks it with a spring and gives a haptic tick. */
@Composable
fun EmbossedIconButton(onClick: () -> Unit, modifier: Modifier = Modifier.size(48.dp), shape: Shape = CircleShape, content: @Composable () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.91f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "press")
    val lift by animateFloatAsState(if (pressed) 1f else 8f, tween(120), label = "lift")
    val haptic = LocalHapticFeedback.current
    val face = Brush.linearGradient(if (pressed) listOf(Color(0xFF0E0E0E), Color(0xFF383838)) else listOf(Color(0xFF4D4D4D), Color(0xFF141414)))
    val rim = Brush.linearGradient(if (pressed) listOf(Color.Black, Color.White.copy(.35f)) else listOf(Color.White.copy(.6f), Color.Black.copy(.85f)))
    Box(
        modifier.scale(s).shadow(lift.dp, shape).clip(shape).background(face).border(1.5.dp, rim, shape)
            .clickable(src, null) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
        Alignment.Center
    ) { CompositionLocalProvider(LocalContentColor provides Color.White) { content() } }
}

/** Four bars that dance while playing and settle low when paused. */
@Composable
fun NowPlayingBars(playing: Boolean, modifier: Modifier = Modifier.size(24.dp)) {
    val t = rememberInfiniteTransition(label = "bars")
    val hs = listOf(420, 560, 360, 500).mapIndexed { i, ms ->
        t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(ms, easing = LinearEasing), RepeatMode.Reverse, StartOffset(i * 90)), label = "b$i")
    }
    Row(modifier, Arrangement.spacedBy(2.dp), Alignment.Bottom) {
        hs.forEach { h ->
            val f by animateFloatAsState(if (playing) h.value else 0.2f, tween(250), label = "bh")
            Box(Modifier.weight(1f).fillMaxHeight(f).clip(RoundedCornerShape(2.dp)).background(Color.White))
        }
    }
}

/** Fades and slides its content in after [delayMs]; use increasing delays to stagger a screen. */
@Composable
fun Reveal(delayMs: Int = 0, content: @Composable () -> Unit) {
    var on by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(delayMs.toLong()); on = true }
    androidx.compose.animation.AnimatedVisibility(
        on, enter = androidx.compose.animation.fadeIn(tween(350)) + androidx.compose.animation.slideInVertically(tween(350)) { it / 6 }
    ) { content() }
}
