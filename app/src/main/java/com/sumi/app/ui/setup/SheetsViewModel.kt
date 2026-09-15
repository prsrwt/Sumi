package com.sumi.app.ui.setup

import android.app.Activity
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.sumi.app.data.SheetsLink
import com.sumi.app.data.SumiRepository
import com.sumi.app.sync.GoogleAuth
import com.sumi.app.sync.GoogleHttp
import com.sumi.app.sync.SheetsConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

data class SheetsUi(
    /** False until the stored link has been read, so Setup doesn't flash "Connect". */
    val loaded: Boolean = false,
    val link: SheetsLink? = null,
    val working: Boolean = false,
    /** Months changed on the phone and not yet in the sheet. */
    val waiting: Int = 0,
    val message: String? = null
)

class SheetsViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = SumiRepository.get(app)

    private val working = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val ui: StateFlow<SheetsUi> =
        combine(repository.observeSheetsLink(), repository.observeDirtyCount(), working, message) { link, dirty, busy, text ->
            SheetsUi(loaded = true, link = link, working = busy, waiting = dirty, message = text)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SheetsUi())

    /**
     * Google's consent screen, waiting for the UI to launch it. Only an Activity
     * can start it, so the ViewModel hands it over and the screen reports back
     * through [onConsentResult].
     */
    private val _consent = MutableStateFlow<PendingIntent?>(null)
    val consent: StateFlow<PendingIntent?> = _consent.asStateFlow()

    fun connect() {
        if (working.value) return
        working.value = true
        message.value = null
        attempt {
            val result = GoogleAuth.authorize(getApplication())
            if (result.hasResolution()) {
                // Stays "working" until the consent screen answers.
                _consent.value = result.pendingIntent
            } else {
                finish(result)
            }
        }
    }

    fun consentLaunched() {
        _consent.value = null
    }

    fun onConsentResult(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) {
            // Backing out of Google's screen is a choice, not an error.
            working.value = false
            return
        }
        attempt { finish(GoogleAuth.resultFrom(getApplication(), data)) }
    }

    fun disconnect() {
        viewModelScope.launch {
            SheetsConnection.unlink(getApplication(), repository)
            message.value = "Disconnected. Sumi Timesheet stays in your Drive."
        }
    }

    private suspend fun finish(result: AuthorizationResult) {
        if (!GoogleAuth.grantsDrive(result)) {
            message.value = "Sumi needs permission to make its sheet. Connect again and leave that box ticked."
            working.value = false
            return
        }
        SheetsConnection.link(getApplication(), repository, result.accessToken!!)
        working.value = false
    }

    private fun attempt(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Connecting to Google Sheets failed", e)
                message.value = describe(e)
                working.value = false
            }
        }
    }

    /** Plain words for what went wrong, with the code kept for anyone debugging. */
    private fun describe(error: Exception): String? = when (error) {
        is ApiException -> when (error.statusCode) {
            CommonStatusCodes.CANCELED -> null
            CommonStatusCodes.NETWORK_ERROR -> OFFLINE
            CommonStatusCodes.DEVELOPER_ERROR ->
                "Google doesn't recognise this copy of Sumi (code 10). Its signing key " +
                    "needs an Android OAuth client in the Cloud console."
            else -> "Google couldn't connect Sumi (code ${error.statusCode})."
        }
        is GoogleHttp.HttpError -> "Google refused the request (${error.code}): ${error.reason.take(160)}"
        is IOException -> OFFLINE
        else -> "Something went wrong connecting to Google."
    }

    private companion object {
        const val TAG = "SumiSheets"
        const val OFFLINE = "Couldn't reach Google. Check your connection and try again."
    }
}
