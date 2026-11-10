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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** "Sign in with Google" through Android Credential Manager. Needs GOOGLE_WEB_CLIENT_ID (see gradle.properties). */
object GoogleAuth {
    val configured: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    /** Returns (display name, Gmail address). The address comes from Google, so it is genuinely the user's account. */
    suspend fun signIn(activity: Activity?): Result<Pair<String, String>> {
        if (activity == null) return Result.failure(IllegalStateException("No screen available"))
        if (!configured) return Result.failure(IllegalStateException("not_configured"))
        return try {
            val request = GetCredentialRequest.Builder().addCredentialOption(GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()).build()
            val cred = withTimeout(45_000) { CredentialManager.create(activity).getCredential(activity, request).credential }
            if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val g = GoogleIdTokenCredential.createFrom(cred.data)
                Result.success((g.displayName ?: g.givenName ?: "") to g.id)
            } else Result.failure(IllegalStateException("Unexpected credential type"))
        } catch (e: GetCredentialException) {
            Result.failure(e)
        } catch (e: TimeoutCancellationException) {
            Result.failure(e)
        }
    }

    suspend fun signOut(context: Context) {
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    fun describe(t: Throwable): String {
        val base = when (t) {
            is TimeoutCancellationException -> "Google did not answer within 45 seconds. Check the internet connection, update Google Play services, and make sure a Google account is added on this phone."
            is GetCredentialCancellationException -> "Sign-in was cancelled."
            is NoCredentialException -> "No Google account found on this phone. Add one in your phone's Settings, then try again."
            is GetCredentialException -> "Google sign-in failed."
            else -> t.message ?: "Google sign-in failed."
        }
        val detail = (t as? GetCredentialException)?.let { " [" + it.type.substringAfterLast('.') + (it.message?.let { m -> ": " + m.take(90) } ?: "") + "]" } ?: ""
        return base + detail
    }
}
