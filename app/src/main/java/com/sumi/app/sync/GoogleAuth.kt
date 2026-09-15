package com.sumi.app.sync

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Asking Google for permission to the user's Drive, through Google Play services.
 *
 * Sumi asks for one scope, drive.file: it can see and change only files it made
 * itself, never the rest of someone's Drive. Nothing here stores a token. Play
 * services caches it and hands over a fresh one (about an hour long) on every
 * call, asking the user only when it has to.
 *
 * No client ID appears in the code: Google recognises the app by its package
 * name and signing certificate, as registered in the Cloud console.
 */
object GoogleAuth {

    const val DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"

    private const val GOOGLE_ACCOUNT_TYPE = "com.google"

    /**
     * Resolves without showing anything when permission was already granted.
     * Otherwise the result carries a pending intent for Google's consent screen,
     * which only an Activity can launch.
     *
     * With [accountEmail] set, the token is for that account and no picker is
     * shown; background sync uses this so it can never switch accounts silently.
     */
    suspend fun authorize(context: Context, accountEmail: String? = null): AuthorizationResult {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE)))
            .apply { accountEmail?.let { setAccount(Account(it, GOOGLE_ACCOUNT_TYPE)) } }
            .build()
        return Identity.getAuthorizationClient(context).authorize(request).await()
    }

    /** The result that comes back from the consent screen. Throws ApiException on failure. */
    fun resultFrom(context: Context, data: Intent?): AuthorizationResult =
        Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)

    /**
     * Google's consent screen lets the user untick individual permissions, so a
     * successful result can still be missing the one Sumi needs.
     */
    fun grantsDrive(result: AuthorizationResult): Boolean =
        result.accessToken != null && DRIVE_FILE in result.grantedScopes

    /** Drops a token Google has rejected, so the next [authorize] fetches a new one. */
    suspend fun clearToken(context: Context, token: String) {
        Identity.getAuthorizationClient(context)
            .clearToken(ClearTokenRequest.builder().setToken(token).build())
            .await()
    }
}

/** Waits for a Play services Task without blocking a thread. */
internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
