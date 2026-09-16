package com.sumi.app.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.sumi.app.data.SheetsLink
import com.sumi.app.data.SumiRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.time.YearMonth
import java.util.concurrent.TimeUnit

/**
 * Copies the log to the user's Sumi Timesheet, a month at a time.
 *
 * The phone is the source of truth and the sheet is its mirror. Every change
 * queues its month (see SumiRepository); a sync takes each queued month, reads its
 * entries and rewrites that tab whole. Rewriting rather than patching rows means
 * there is no bookkeeping of which row holds which entry to drift out of step,
 * and running the same sync twice gives the same sheet.
 *
 * It runs through WorkManager, which waits for a network, survives the app being
 * closed or the phone restarting, and retries with growing gaps after a failure.
 */
object SheetsSync {

    private const val TAG = "SumiSheets"
    private const val NOW_WORK = "sheets-sync"
    private const val PERIODIC_WORK = "sheets-sync-periodic"

    enum class Outcome { DONE, RETRY, FAILED }

    // ---- scheduling ----

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /**
     * Syncs soon, if there is a sheet and something waiting for it. Cheap to call
     * after any change: with nothing to do it only reads two small tables.
     */
    suspend fun requestIfNeeded(context: Context) {
        val repository = SumiRepository.get(context)
        val link = repository.sheetsLinkNow() ?: return
        if (link.needsReconnect || repository.dirtyMonths().isEmpty()) return
        request(context)
    }

    /**
     * A short delay lets a burst of edits - typing a goal name, logging two things
     * back to back - go out as one sync. APPEND_OR_REPLACE queues this behind a
     * sync already running, so a change made mid-sync is never skipped.
     */
    fun request(context: Context) {
        val work = OneTimeWorkRequestBuilder<SheetsSyncWorker>()
            .setConstraints(online)
            .setInitialDelay(10, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(NOW_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, work)
    }

    /** A twice-daily safety net, for anything a missed request left waiting. */
    fun schedulePeriodic(context: Context) {
        val work = PeriodicWorkRequestBuilder<SheetsSyncWorker>(12, TimeUnit.HOURS)
            .setConstraints(online)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, work)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(NOW_WORK)
            cancelUniqueWork(PERIODIC_WORK)
        }
    }

    // ---- the sync itself ----

    suspend fun run(context: Context): Outcome {
        val repository = SumiRepository.get(context)
        val link = repository.sheetsLinkNow() ?: return Outcome.DONE
        if (link.needsReconnect || repository.dirtyMonths().isEmpty()) return Outcome.DONE

        // A token for the connected account only; this never shows a screen.
        val auth = try {
            GoogleAuth.authorize(context, link.accountEmail)
        } catch (e: ApiException) {
            Log.w(TAG, "Authorization failed (${e.statusCode})", e)
            return when (e.statusCode) {
                CommonStatusCodes.NETWORK_ERROR -> Outcome.RETRY
                // This build isn't registered with Google; asking the user again can't fix that.
                CommonStatusCodes.DEVELOPER_ERROR -> Outcome.FAILED
                else -> {
                    // Typically the account is no longer on this phone. Only the user
                    // can sort that out, so Setup offers Reconnect instead of the queue
                    // silently waiting forever.
                    repository.saveSheetsLink(link.copy(needsReconnect = true))
                    Outcome.DONE
                }
            }
        }
        if (auth.hasResolution() || !GoogleAuth.grantsDrive(auth)) {
            // Access was revoked or has expired: Google wants the user again. Setup
            // offers Reconnect; nothing retries or notifies in the meantime.
            repository.saveSheetsLink(link.copy(needsReconnect = true))
            return Outcome.DONE
        }
        val token = auth.accessToken!!

        return try {
            push(repository, link, GoogleHttp(token))
            Outcome.DONE
        } catch (e: CancellationException) {
            throw e
        } catch (e: GoogleHttp.HttpError) {
            Log.w(TAG, "Sheets refused a sync: ${e.message}")
            when {
                e.code == 401 -> {
                    // The cached token went stale; drop it so the retry gets a new one.
                    runCatching { GoogleAuth.clearToken(context, token) }
                    Outcome.RETRY
                }
                e.code == 429 || e.code >= 500 -> Outcome.RETRY
                else -> Outcome.FAILED
            }
        } catch (e: IOException) {
            Outcome.RETRY
        }
    }

    private suspend fun push(repository: SumiRepository, link: SheetsLink, http: GoogleHttp) {
        val spreadsheetId = liveSheet(repository, link, http)
        val goals = repository.goalsNow()
        val domainNames = repository.domainsNow().associate { it.id to it.name }
        val wordNames = repository.wordNames()
        var tabs = Timesheet.tabs(http, spreadsheetId)

        for (month in repository.dirtyMonths()) {
            // Taken off the queue before reading, so a change that lands while this
            // month is being written queues it again rather than being lost.
            repository.clearDirty(month)
            try {
                val requests = TimesheetLayout.rewriteMonth(
                    month = month,
                    entries = repository.entriesForMonth(month),
                    goals = goals,
                    existing = tabs,
                    domains = domainNames,
                    words = wordNames
                )
                if (requests.length() > 0) {
                    Timesheet.batchUpdate(http, spreadsheetId, requests)
                    if (tabs.none { it.sheetId == TimesheetLayout.sheetIdFor(month) }) {
                        // A tab was added, which shifts the others; re-read before placing the next.
                        tabs = Timesheet.tabs(http, spreadsheetId)
                    }
                }
            } catch (e: Throwable) {
                withContext(NonCancellable) { repository.markDirty(month) }
                throw e
            }
        }

        // Re-read, so a disconnect that happened during the sync is not undone here.
        repository.sheetsLinkNow()
            ?.takeIf { it.spreadsheetId == spreadsheetId }
            ?.let { repository.saveSheetsLink(it.copy(lastSyncedAt = Instant.now())) }
    }

    /**
     * The sheet to write to. If the user deleted or binned it, Sumi looks for
     * another Sumi Timesheet on the account, or makes a new one, and then fills
     * it with every month.
     */
    private suspend fun liveSheet(repository: SumiRepository, link: SheetsLink, http: GoogleHttp): String {
        if (Timesheet.isLive(http, link.spreadsheetId)) return link.spreadsheetId

        val replacement = Timesheet.find(http) ?: Timesheet.create(http, YearMonth.now())
        repository.saveSheetsLink(link.copy(spreadsheetId = replacement))
        repository.markEveryMonthDirty()
        return replacement
    }
}

class SheetsSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (SheetsSync.run(applicationContext)) {
        SheetsSync.Outcome.DONE -> Result.success()
        SheetsSync.Outcome.RETRY -> Result.retry()
        SheetsSync.Outcome.FAILED -> Result.failure()
    }
}
