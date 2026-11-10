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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.mono.player.GoogleAuth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.animation.scaleIn
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF111111), Color(0xFF000000)))), Alignment.Center) {
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

private val GMAIL = Regex("^[A-Za-z0-9._%+-]+@gmail\\.com$", RegexOption.IGNORE_CASE)

/** True only for a well-formed @gmail.com address. */
fun isGmail(email: String): Boolean = GMAIL.matches(email.trim())

@Composable
private fun FeatureCard(icon: ImageVector, title: String, text: String) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(shape).background(Color.White.copy(alpha = .06f))
            .border(1.dp, Color.White.copy(alpha = .14f), shape).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White), Alignment.Center) { Icon(icon, null, tint = Color.Black) }
        Spacer(Modifier.width(14.dp))
        Column { Text(title, fontWeight = FontWeight.SemiBold, color = Color.White); Text(text, fontSize = 13.sp, color = Color.White.copy(alpha = .7f)) }
    }
}

/** Three-step welcome: hello, what the app does, then a profile with a required Gmail address (stored only on this device). */
@Composable
fun WelcomeScreen(
    initialName: String, initialEmail: String, googleEnabled: Boolean,
    onGoogle: suspend () -> Result<Pair<String, String>>, onDone: (String, String, Boolean) -> Unit
) {
    val accent = LocalAccent.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(if (initialName.isNotBlank()) 2 else 0) { 3 }
    var name by remember { mutableStateOf(initialName) }
    var email by remember { mutableStateOf(initialEmail) }
    var tried by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var gError by remember { mutableStateOf<String?>(null) }
    val ok = isGmail(email)
    val breathe = rememberInfiniteTransition(label = "breathe")
    val pulse by breathe.animateFloat(0.96f, 1.04f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0E0E0E), Color(0xFF000000))))) {
        AuroraGlow(accent, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            HorizontalPager(pager, Modifier.weight(1f)) { page ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                ) {
                    when (page) {
                        0 -> {
                            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(230.dp).graphicsLayer { scaleX = pulse; scaleY = pulse })
                            Text("NYXEN", fontSize = 44.sp, fontWeight = FontWeight.Black, letterSpacing = 10.sp, color = Color.White)
                            Text("PLAYER", fontSize = 14.sp, letterSpacing = 12.sp, color = Color.White.copy(alpha = .7f))
                            Spacer(Modifier.height(24.dp))
                            Text("Your music, your way.\nEverything stays on this device.", color = Color.White.copy(alpha = .8f), textAlign = TextAlign.Center)
                        }
                        1 -> {
                            Text("Made for your music", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(18.dp))
                            FeatureCard(Icons.Default.LibraryMusic, "Your library", "Finds and plays .mp3, .m4a, .ogg and .wav files from your phone")
                            FeatureCard(Icons.Default.Equalizer, "Studio sound", "Equalizer, bass boost and reverb with your own presets")
                            FeatureCard(Icons.Default.Palette, "26 themes", "Gradient themes that recolor every button and glow")
                        }
                        else -> {
                            Text("Create your profile", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center)
                            if (googleEnabled) {
                                Text("Sign in with your Google (Gmail) account", color = Color.White.copy(alpha = .7f), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 22.dp))
                                Box(
                                    Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(Color.White).clickable(enabled = !busy) {
                                        scope.launch {
                                            busy = true; gError = null
                                            onGoogle().onSuccess { (n, e) -> if (isGmail(e)) onDone(n.ifBlank { "Friend" }, e, true) else gError = "Please choose a @gmail.com account." }
                                                .onFailure { gError = GoogleAuth.describe(it) }
                                            busy = false
                                        }
                                    },
                                    Alignment.Center
                                ) {
                                    if (busy) CircularProgressIndicator(Modifier.size(24.dp), color = Color.Black, strokeWidth = 2.dp)
                                    else Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Email, null, tint = Color.Black); Spacer(Modifier.width(10.dp))
                                        Text("Continue with Google", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                }
                                gError?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp)) }
                                Text("Google is only used to confirm your Gmail address. Nothing is uploaded.", fontSize = 11.sp, color = Color.White.copy(alpha = .6f), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
                            } else {
                                Text("A Gmail address is required", color = Color.White.copy(alpha = .7f), modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
                                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("What should we call you?") }, singleLine = true)
                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(
                                    email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Gmail address (not verified)") }, singleLine = true,
                                    leadingIcon = { Icon(Icons.Default.Email, null) },
                                    trailingIcon = { AnimatedVisibility(ok, enter = fadeIn() + scaleIn(), exit = fadeOut()) { Icon(Icons.Default.CheckCircle, "Valid Gmail address", tint = Color(0xFF4CD964)) } },
                                    isError = tried && !ok, supportingText = { if (tried && !ok) Text("Enter a valid @gmail.com address") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done)
                                )
                                Text("Google sign-in is not set up in this build, so this address is only checked for format.", fontSize = 11.sp, color = Color.White.copy(alpha = .6f), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { i ->
                        val w by animateDpAsState(if (pager.currentPage == i) 26.dp else 8.dp, spring(Spring.DampingRatioMediumBouncy), label = "dot")
                        Box(Modifier.height(8.dp).width(w).clip(CircleShape).background(if (pager.currentPage == i) Color.White else Color.White.copy(alpha = .3f)))
                    }
                }
                if (pager.currentPage < 2 || !googleEnabled) EmbossedIconButton(
                    { if (pager.currentPage < 2) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } else { tried = true; if (ok) onDone(name.trim().ifBlank { "Friend" }, email.trim(), false) } },
                    Modifier.height(52.dp).widthIn(min = 140.dp).graphicsLayer { alpha = if (pager.currentPage < 2 || ok) 1f else 0.55f }, RoundedCornerShape(26.dp)
                ) { Text(if (pager.currentPage < 2) "Next" else "Get started", Modifier.padding(horizontal = 24.dp), fontWeight = FontWeight.Bold) }
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
