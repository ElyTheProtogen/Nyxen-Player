package com.mono.player

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** Firebase is the shared account registry for Nyxen on Android and Windows. */
object GoogleAuth {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    val configured: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()
    val isSignedIn: Boolean get() = auth.currentUser?.isEmailVerified == true

    suspend fun signIn(activity: Activity?): Result<Pair<String, String>> {
        if (activity == null) return Result.failure(IllegalStateException("No screen available"))
        if (!configured) return Result.failure(IllegalStateException("Google sign-in is not configured."))
        return try {
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build())
                .build()
            val credential = withTimeout(45_000) {
                CredentialManager.create(activity).getCredential(activity, request).credential
            }
            if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                throw IllegalStateException("Unexpected Google credential type.")
            }
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            val methods = auth.fetchSignInMethodsForEmail(google.id).await().signInMethods.orEmpty()
            if (methods.isEmpty()) throw IllegalStateException("This email isn't registered with Nyxen. Please create an account first.")
            val firebaseCredential = GoogleAuthProvider.getCredential(google.idToken, null)
            val user = auth.signInWithCredential(firebaseCredential).await().user
                ?: throw IllegalStateException("Firebase did not return an account.")
            if (!user.isEmailVerified) {
                auth.signOut()
                throw IllegalStateException("Please verify your email address before signing in.")
            }
            Result.success((user.displayName ?: google.displayName ?: user.email ?: "Friend") to (user.email ?: google.id))
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun emailSignIn(email: String, password: String): Result<Pair<String, String>> = try {
        val user = auth.signInWithEmailAndPassword(email.trim(), password).await().user
            ?: throw IllegalStateException("Nyxen could not load this account.")
        if (!user.isEmailVerified) {
            auth.signOut()
            throw IllegalStateException("Please verify your email first. Check your inbox for the verification link.")
        }
        Result.success((user.displayName ?: email.substringBefore("@")) to (user.email ?: email))
    } catch (error: Exception) {
        Result.failure(IllegalStateException(authMessage(error)))
    }

    suspend fun register(email: String, password: String): Result<Pair<String, String>> = try {
        val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user
            ?: throw IllegalStateException("Nyxen could not create this account.")
        user.sendEmailVerification().await()
        auth.signOut()
        Result.failure(IllegalStateException("Account created. We sent a verification link to ${email.trim()}. Verify your email, then sign in."))
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

    suspend fun signOut(context: Context) {
        auth.signOut()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    fun describe(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            error is GetCredentialCancellationException -> "Sign-in was cancelled."
            error is NoCredentialException -> "No Google account found on this phone. Add one in Settings, then try again."
            error is GetCredentialException -> "Google sign-in failed."
            message.isNotBlank() -> message
            else -> "Sign-in failed. Please try again."
        }
    }
}
