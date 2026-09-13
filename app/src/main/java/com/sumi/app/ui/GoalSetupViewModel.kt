package com.sumi.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sumi.app.data.DayProgress
import com.sumi.app.data.SumiRepository
import com.sumi.app.data.GOAL_COUNT
import com.sumi.app.data.Goal
import com.sumi.app.widget.FaceLayout
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.random.Random

class GoalSetupViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = SumiRepository.get(app)

    private val today: LocalDate = LocalDate.now()
    private val historyRange = FaceLayout.maxRange(today)

    /** null while the initial load is in flight. */
    private val _names = MutableStateFlow<List<String>?>(null)
    val names: StateFlow<List<String>?> = _names.asStateFlow()

    /**
     * Goals as the preview should show them: whatever is in the text fields right
     * now, so the widget face updates while the user is still typing.
     */
    val previewGoals: StateFlow<List<Goal>> = names
        .map { current -> List(GOAL_COUNT) { slot -> Goal(slot, current?.getOrNull(slot) ?: "") } }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            List(GOAL_COUNT) { Goal(it, "") }
        )

    val history: StateFlow<List<DayProgress>> =
        repository.observeRange(historyRange.start, historyRange.endInclusive)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    init {
        viewModelScope.launch {
            _names.value = repository.goalsNow().sortedBy { it.slot }.map { it.name }
        }
        viewModelScope.launch { persistEdits() }
    }

    fun onNameChanged(slot: Int, value: String) {
        _names.update { current ->
            current?.toMutableList()?.also { it[slot] = value }
        }
    }

    /**
     * The text fields are the source of truth while typing; this mirrors them
     * into the database after a pause. Writing all five slots together keeps the
     * saves ordered - one write per pause instead of one racing write per
     * keystroke, which could otherwise land out of order and persist a stale name.
     */
    @OptIn(FlowPreview::class)
    private suspend fun persistEdits() {
        _names.filterNotNull()
            .drop(1) // the initial load is already what is stored
            .debounce(SAVE_DEBOUNCE_MS)
            .collectLatest { current ->
                current.forEachIndexed { slot, name -> repository.setGoalName(slot, name) }
            }
    }

    // ---- debug helpers, surfaced only in debug builds ----

    /**
     * Plausible-looking history so the colour ramp can be judged before real data
     * exists.
     *
     * An earlier version ticked every goal independently at a flat 62%, which put
     * almost every day at 3-5 of 5 and made the grid a solid slab - useless for
     * judging the frosted empty tile or the low end of the ramp. This walks the
     * daily rate around instead, so engagement drifts and there are genuine quiet
     * stretches, which is what real use looks like.
     */
    fun fillSampleHistory() {
        viewModelScope.launch {
            val random = Random(today.toEpochDay())
            var rate = 0.55f
            val generated = buildList {
                var date = historyRange.start
                while (!date.isAfter(today)) {
                    rate = (rate + (random.nextFloat() - 0.5f) * 0.34f).coerceIn(0.04f, 0.95f)

                    val mask = if (random.nextFloat() < QUIET_DAY_CHANCE) {
                        // An off day: nothing, or a single thing.
                        if (random.nextBoolean()) 0 else 1 shl random.nextInt(GOAL_COUNT)
                    } else {
                        var bits = 0
                        for (slot in 0 until GOAL_COUNT) {
                            if (random.nextFloat() < rate) bits = bits or (1 shl slot)
                        }
                        bits
                    }

                    add(DayProgress(date, mask))
                    date = date.plusDays(1)
                }
            }
            repository.overwriteDays(generated)
        }
    }

    /** Writes empty masks across the whole visible range, resetting the heatmap. */
    fun clearHistory() {
        viewModelScope.launch {
            val cleared = buildList {
                var date = historyRange.start
                while (!date.isAfter(historyRange.endInclusive)) {
                    add(DayProgress(date, 0))
                    date = date.plusDays(1)
                }
            }
            repository.overwriteDays(cleared)
        }
    }

    private companion object {
        const val SAVE_DEBOUNCE_MS = 350L
        const val STOP_TIMEOUT_MS = 5_000L
        const val QUIET_DAY_CHANCE = 0.16f
    }
}
