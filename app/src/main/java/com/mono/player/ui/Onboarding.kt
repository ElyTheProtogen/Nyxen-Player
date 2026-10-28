package com.mono.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mono.player.AuroraGlow
import com.mono.player.EmbossedIconButton
import com.mono.player.LocalAccent
import com.mono.player.R
import com.mono.player.Reveal

/** Loading screen shown while the library and player start up. */
@Composable
fun SplashScreen() {
    val accent = LocalAccent.current
    val t = rememberInfiniteTransition(label = "splash")
    val pulse by t.animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val glow by t.animateFloat(0.4f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "glow")
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0A0614), Color(0xFF05030A)))), Alignment.Center) {
        AuroraGlow(accent, Modifier.fillMaxSize())
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.ic_launcher_foreground), null,
                Modifier.size(180.dp).graphicsLayer { scaleX = pulse; scaleY = pulse; alpha = 0.7f + 0.3f * glow })
            Text("NyxenPlayer", fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp, color = Color.White)
            Spacer(Modifier.height(22.dp))
            LinearProgressIndicator(Modifier.width(140.dp).clip(RoundedCornerShape(50)), color = accent, trackColor = Color.White.copy(alpha = .15f))
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, text: String) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = LocalAccent.current); Spacer(Modifier.width(12.dp)); Text(text, color = Color.White)
    }
}

/** First-run welcome: asks for a display name (and optional email), stored only on this device. */
@Composable
fun WelcomeScreen(onDone: (String, String) -> Unit) {
    val accent = LocalAccent.current
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF120A22), Color(0xFF05030A))))) {
        AuroraGlow(accent, Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Reveal(0) { Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(140.dp)) }
            Reveal(100) { Text("Welcome to NyxenPlayer", fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = Color.White) }
            Reveal(180) { Text("Your music, your way. Everything stays on this device.", color = Color.White.copy(alpha = .75f), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(22.dp))
            Reveal(260) {
                Column {
                    FeatureRow(Icons.Default.LibraryMusic, "Plays your own music files")
                    FeatureRow(Icons.Default.Equalizer, "Equalizer and sound effects")
                    FeatureRow(Icons.Default.Palette, "12 gradient themes")
                }
            }
            Spacer(Modifier.height(20.dp))
            Reveal(340) { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("What should we call you?") }, singleLine = true) }
            Spacer(Modifier.height(8.dp))
            Reveal(400) { OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email (optional)") }, singleLine = true) }
            Spacer(Modifier.height(20.dp))
            Reveal(460) {
                EmbossedIconButton({ onDone(name.trim().ifBlank { "Friend" }, email.trim()) }, Modifier.fillMaxWidth().height(54.dp), RoundedCornerShape(27.dp)) {
                    Text("Get started", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Slide-down message pill used for welcome text, scan results and errors. */
@Composable
fun Banner(text: String?, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    var last by remember { mutableStateOf(text ?: "") }
    LaunchedEffect(text) { if (text != null) last = text }
    AnimatedVisibility(
        text != null, modifier.statusBarsPadding().padding(top = 8.dp),
        enter = slideInVertically(spring(Spring.DampingRatioMediumBouncy)) { -it * 2 } + fadeIn(),
        exit = slideOutVertically { -it * 2 } + fadeOut()
    ) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color.Black.copy(alpha = .8f), border = BorderStroke(1.dp, accent.copy(alpha = .8f)), shadowElevation = 12.dp) {
            Text(text ?: last, Modifier.padding(horizontal = 20.dp, vertical = 12.dp), color = Color.White)
        }
    }
}
