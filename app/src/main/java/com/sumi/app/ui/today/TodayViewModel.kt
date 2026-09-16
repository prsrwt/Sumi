package com.sumi.app.ui.today

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sumi.app.data.Goal
import com.sumi.app.data.SumiRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class TodayState(
    val date: LocalDate,
    val isToday: Boolean,
    val rows: List<TimelineRow>,
    val total: Duration,
    val goals: List<Goal>
) {
    val goalsNamed: Boolean get() = goals.any { it.name.isNotBlank() }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = SumiRepository.get(app)
    private val zone: ZoneId = ZoneId.systemDefault()

    private val date = MutableStateFlow(LocalDate.now(zone))

    /**
     * Ticks once a minute so the trailing "unlogged since" row keeps growing
     * while the screen is open, instead of freezing at the moment it was built.
     */
    private val minuteTicks = flow {
        while (true) {
            emit(Instant.now())
            delay(60_000)
        }
    }

    private val entries = date.flatMapLatest { repository.observeDay(it, zone) }

    val state: StateFlow<TodayState> = combine(
        date,
        entries,
        repository.observeGoals(),
        minuteTicks
    ) { day, dayEntries, goals, now ->
        val (from, to) = SumiRepository.dayBounds(day, zone)
        val rows = Timeline.build(dayEntries, from, to, now)
        TodayState(
            date = day,
            isToday = day == LocalDate.now(zone),
            rows = rows,
            total = Timeline.loggedTotal(rows),
            goals = goals
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TodayState(LocalDate.now(zone), true, emptyList(), Duration.ZERO, emptyList())
    )

    fun previousDay() = date.update { it.minusDays(1) }

    /** Opens a day chosen elsewhere, such as from the history sheet. */
    fun showDay(day: LocalDate) = date.update { if (day.isAfter(LocalDate.now(zone))) it else day }

    /** No browsing into the future; there is nothing there to read. */
    fun nextDay() = date.update { if (it.isBefore(LocalDate.now(zone))) it.plusDays(1) else it }
}
