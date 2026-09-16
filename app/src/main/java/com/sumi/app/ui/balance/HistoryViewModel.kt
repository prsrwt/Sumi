package com.sumi.app.ui.balance

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sumi.app.data.Goal
import com.sumi.app.data.SumiRepository
import com.sumi.app.ui.today.Timeline
import com.sumi.app.ui.today.TimelineRow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One day as the history sheet shows it: what was logged, newest first. */
data class HistoryDayDetail(
    val date: LocalDate?,
    val rows: List<TimelineRow.Logged>,
    val total: Duration,
    val goals: List<Goal>
)

/**
 * Every month you have logged anything in, and the one day being read inside the
 * sheet. Both are read only while the sheet is open, so the rest of the app never
 * carries the whole log around.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = SumiRepository.get(app)
    private val zone: ZoneId = ZoneId.systemDefault()

    val months: StateFlow<List<HistoryMonth>> = repository.observeAllEntries()
        .map { History.build(it, LocalDate.now(zone), zone) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val openDate = MutableStateFlow<LocalDate?>(null)

    /** The day the sheet is showing, or null while the calendars are. */
    val openDay: StateFlow<LocalDate?> = openDate.asStateFlow()

    val day: StateFlow<HistoryDayDetail> = openDate
        .flatMapLatest { date ->
            if (date == null) flowOf(EMPTY_DAY)
            else combine(repository.observeDay(date, zone), repository.observeGoals()) { entries, goals ->
                val (from, to) = SumiRepository.dayBounds(date, zone)
                // Built the same way as Today, then only the logged rows: the gaps
                // are there to fill in, which is Today's job, not the history's.
                val rows = Timeline.build(entries, from, to, Instant.now())
                HistoryDayDetail(
                    date = date,
                    rows = rows.filterIsInstance<TimelineRow.Logged>().reversed(),
                    total = Timeline.loggedTotal(rows),
                    goals = goals
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EMPTY_DAY)

    fun open(date: LocalDate) {
        openDate.value = date
    }

    fun backToCalendar() {
        openDate.value = null
    }

    private companion object {
        val EMPTY_DAY = HistoryDayDetail(null, emptyList(), Duration.ZERO, emptyList())
    }
}
