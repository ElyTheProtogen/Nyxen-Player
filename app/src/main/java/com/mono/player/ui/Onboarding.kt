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
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF24202D), Color(0xFF101016), Color(0xFF07070B)))), Alignment.Center) {
        AuroraGlow(accent, Modifier.fillMaxSize())
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.ic_launcher_foreground), null,
                Modifier.size(180.dp).graphicsLayer { scaleX = pulse; scaleY = pulse; alpha = 0.7f + 0.3f * glow })
            Text("NYXEN", fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = 7.sp, color = Color.White)
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

private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", RegexOption.IGNORE_CASE)
fun isValidEmail(email: String): Boolean = EMAIL.matches(email.trim())

@Composable
private fun FeatureCard(icon: ImageVector, title: String, text: String) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(shape).background(Color(0xE8121219))
            .border(1.dp, LocalAccent.current.copy(alpha = .24f), shape).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(LocalAccent.current), Alignment.Center) { Icon(icon, null, tint = Color(0xFF101016)) }
        Spacer(Modifier.width(14.dp))
        Column { Text(title, fontWeight = FontWeight.SemiBold, color = Color.White); Text(text, fontSize = 13.sp, color = Color.White.copy(alpha = .7f)) }
    }
}

/** Three-step welcome with email and password account access. */
@Composable
fun WelcomeScreen(
    initialName: String, initialEmail: String,
    onEmailAuth: suspend (String, String, Boolean) -> Result<Pair<String, String>>,
    onDone: (String, String, Boolean) -> Unit
) {
    val accent = LocalAccent.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(if (initialName.isNotBlank()) 2 else 0) { 3 }
    var name by remember { mutableStateOf(initialName) }
    var email by remember { mutableStateOf(initialEmail) }
    var tried by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var createAccount by remember { mutableStateOf(false) }
    val ok = isValidEmail(email)
        val breathe = rememberInfiniteTransition(label = "breathe")
    val pulse by breathe.animateFloat(0.96f, 1.04f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF24202D), Color(0xFF101016), Color(0xFF07070B))))) {
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
                            Text("A player that feels\nlike yours.", fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(18.dp))
                            FeatureCard(Icons.Default.LibraryMusic, "Your library", "Finds and plays .mp3, .m4a, .ogg and .wav files from your phone")
                            FeatureCard(Icons.Default.Equalizer, "Studio sound", "Equalizer, bass boost and reverb with your own presets")
                            FeatureCard(Icons.Default.Palette, "26 themes", "Gradient themes that recolor every button and glow")
                        }
                        else -> {
                            Text("Make it personal.", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center)
                            Text("Sign in or create your Nyxen account", color = Color.White.copy(alpha = .7f), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 22.dp))
                            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email address") }, singleLine = true, leadingIcon = { Icon(Icons.Default.Email, null) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next), isError = tried && !ok, supportingText = { if (tried && !ok) Text("Enter a valid email address") })
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done), visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { createAccount = false }) { Text("Sign in", color = if (!createAccount) accent else Color.White.copy(.65f)) }
                                    Text("·", color = Color.White.copy(.5f))
                                    TextButton(onClick = { createAccount = true }) { Text("Create account", color = if (createAccount) accent else Color.White.copy(.65f)) }
                                }
                                Button(onClick = {
                                    tried = true; authError = null
                                    if (!ok) { authError = "Enter a valid email address."; return@Button }
                                    if (password.length < 6) { authError = "Password must be at least 6 characters."; return@Button }
                                    scope.launch {
                                        busy = true
                                        try {
                                            onEmailAuth(email.trim(), password, createAccount)
                                                .onSuccess { (n, e) -> onDone(n.ifBlank { e.substringBefore("@") }, e, true) }
                                                .onFailure { authError = it.message ?: "Authentication failed." }
                                        } finally { busy = false }
                                    }
                                }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(26.dp), colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                                    Text(if (createAccount) "Create Nyxen account" else "Sign in", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                authError?.let {
                                    Row(
                                        Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(18.dp))
                                            .background(Color(0xFF3A171D).copy(alpha = .72f))
                                            .border(1.dp, Color(0xFFFF6B6B).copy(alpha = .35f), RoundedCornerShape(18.dp))
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Email, null, tint = Color(0xFFFF8585))
                                        Spacer(Modifier.width(10.dp))
                                        Text(it, color = Color(0xFFFFDADA), fontSize = 12.sp, textAlign = TextAlign.Start)
                                    }
                                }
                                Text("Use any valid email address. Your password stays private.", fontSize = 11.sp, color = Color.White.copy(alpha = .6f), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
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
                if (pager.currentPage < 2) EmbossedIconButton(
                    { if (pager.currentPage < 2) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } else { tried = true; if (ok) onDone(name.trim().ifBlank { "Friend" }, email.trim(), false) } },
                    Modifier.height(52.dp).widthIn(min = 140.dp).graphicsLayer { alpha = if (pager.currentPage < 2 || ok) 1f else 0.55f }, RoundedCornerShape(26.dp)
                ) { Text(if (pager.currentPage < 2) "Next" else "Get started", Modifier.padding(horizontal = 24.dp), fontWeight = FontWeight.Bold) }
            }
        }
    }
}

