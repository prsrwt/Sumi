package com.sumi.app.ui.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sumi.app.data.Element
import com.sumi.app.data.GOAL_COUNT
import com.sumi.app.data.Goal
import com.sumi.app.data.Settings
import com.sumi.app.data.SumiRepository
import com.sumi.app.widget.WidgetSync
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalTime

class SetupViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = SumiRepository.get(app)

    val goals: StateFlow<List<Goal>> = repository.observeGoals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<Settings> = repository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings.Default)

    /** The text fields own the names while typing; null until the first load. */
    private val _names = MutableStateFlow<List<String>?>(null)
    val names: StateFlow<List<String>?> = _names.asStateFlow()

    init {
        viewModelScope.launch {
            _names.value = repository.observeGoals().first().sortedBy { it.slot }.map { it.name }
        }
        viewModelScope.launch { persistNames() }
    }

    fun onNameChanged(slot: Int, value: String) {
        _names.update { current -> current?.toMutableList()?.also { it[slot] = value } }
    }

    /** Swaps with whichever goal already had this element, keeping the mapping one-to-one. */
    fun assign(slot: Int, element: Element) {
        viewModelScope.launch { repository.assignElement(slot, element) }
    }

    fun setInterval(minutes: Long) = saveSettings { it.copy(askInterval = Duration.ofMinutes(minutes)) }

    fun setQuietStart(time: LocalTime) = saveSettings { it.copy(quietStart = time) }

    fun setQuietEnd(time: LocalTime) = saveSettings { it.copy(quietEnd = time) }

    private fun saveSettings(change: (Settings) -> Settings) {
        viewModelScope.launch {
            repository.saveSettings(change(repository.settingsNow()))
            // The widget's asking state depends on these, so it has to redraw.
            WidgetSync.onEntriesChanged(getApplication())
        }
    }

    /**
     * Mirrors the fields into the database after a pause. Writing all five together
     * keeps saves ordered: one write per pause rather than one racing write per
     * keystroke, which could land out of order and persist a stale name.
     */
    @OptIn(FlowPreview::class)
    private suspend fun persistNames() {
        _names.filterNotNull()
            .drop(1)
            .debounce(350)
            .collectLatest { current ->
                current.take(GOAL_COUNT).forEachIndexed { slot, name -> repository.setGoalName(slot, name) }
            }
    }
}
