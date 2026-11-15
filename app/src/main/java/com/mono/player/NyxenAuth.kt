package com.mono.player

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/** Firebase is the shared account registry for Nyxen on Android and Windows. */
object NyxenAuth {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    val isSignedIn: Boolean get() = auth.currentUser != null

    suspend fun emailSignIn(email: String, password: String): Result<Pair<String, String>> = try {
        val user = auth.signInWithEmailAndPassword(email.trim(), password).await().user
            ?: throw IllegalStateException("Nyxen could not load this account.")
        Result.success((user.displayName ?: email.substringBefore("@")) to (user.email ?: email))
    } catch (error: Exception) {
        Result.failure(IllegalStateException(authMessage(error)))
    }

    suspend fun register(email: String, password: String): Result<Pair<String, String>> = try {
        val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user
            ?: throw IllegalStateException("Nyxen could not create this account.")
        Result.success((user.displayName ?: email.substringBefore("@")) to (user.email ?: email))
    } catch (error: Exception) {
        Result.failure(IllegalStateException(authMessage(error)))
    }

    private fun authMessage(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("already in use", true) || message.contains("already exists", true) -> "An account with this email already exists. Sign in instead."
            message.contains("password is invalid", true) || message.contains("wrong-password", true) || message.contains("invalid-credential", true) -> "Incorrect email or password."
            message.contains("no user record", true) || message.contains("user-not-found", true) -> "This email isn't registered with Nyxen. Please create an account first."
            message.contains("badly formatted", true) || message.contains("invalid-email", true) -> "Enter a valid email address."
            message.contains("weak password", true) -> "Choose a password with at least 6 characters."
            else -> message.ifBlank { "Authentication failed. Please try again." }
        }
    }

    fun signOut() { auth.signOut() }
}
