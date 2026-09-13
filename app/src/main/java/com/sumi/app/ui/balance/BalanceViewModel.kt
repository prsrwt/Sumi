package com.sumi.app.ui.balance

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sumi.app.data.Goal
import com.sumi.app.data.SumiRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class BalanceState(
    val snapshot: BalanceSnapshot?,
    val goals: List<Goal>
)

@OptIn(ExperimentalCoroutinesApi::class)
class BalanceViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = SumiRepository.get(app)
    private val zone: ZoneId = ZoneId.systemDefault()

    val windowDays = MutableStateFlow(DEFAULT_WINDOW)

    /**
     * One read covering the whole 30-day grid; the 7 or 14 day window is a slice
     * of the same entries, so switching windows never goes back to the database.
     */
    private val entries = run {
        val today = LocalDate.now(zone)
        val from = today.minusDays((GRID_DAYS - 1).toLong()).atStartOfDay(zone).toInstant()
        repository.observeBetween(from, Instant.now().plus(Duration.ofDays(1)))
    }

    val state: StateFlow<BalanceState> = combine(entries, repository.observeGoals(), windowDays) { list, goals, window ->
        BalanceState(
            snapshot = Balance.compute(
                entries = list,
                today = LocalDate.now(zone),
                now = Instant.now(),
                zone = zone,
                windowDays = window,
                gridDays = GRID_DAYS
            ),
            goals = goals
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BalanceState(null, emptyList()))

    fun setWindow(days: Int) {
        windowDays.value = days
    }

    companion object {
        const val DEFAULT_WINDOW = 14
        const val GRID_DAYS = 30
    }
}
