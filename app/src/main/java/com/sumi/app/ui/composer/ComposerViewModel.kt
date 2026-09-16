package com.sumi.app.ui.composer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sumi.app.data.Element
import com.sumi.app.data.Goal
import com.sumi.app.data.Prompts
import com.sumi.app.data.Word
import com.sumi.app.data.nameFor
import com.sumi.app.data.RangeSuggestion
import com.sumi.app.data.SumiRepository
import com.sumi.app.data.SumiRepository.SaveResult
import com.sumi.app.data.TimeRange
import com.sumi.app.widget.WidgetSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

data class ComposerState(
    val loading: Boolean = true,
    /** Non-null when an existing entry is being edited rather than a new one logged. */
    val editingId: Long? = null,
    val start: Instant = Instant.EPOCH,
    val end: Instant = Instant.EPOCH,
    val text: String = "",
    /** The element an edited entry already has; always null for a new entry. */
    val element: Element? = null,
    val goals: List<Goal> = emptyList(),
    /** Your own words, most recently used first. Empty until you have used any. */
    val words: List<Word> = emptyList(),
    /** Whether the line you have typed should be kept as a word of your own. */
    val keeping: Boolean = false,
    val question: String = Prompts.DEFAULT,
    val message: String? = null,
    val saving: Boolean = false,
    val done: Boolean = false
)

class ComposerViewModel(
    app: Application,
    savedState: SavedStateHandle
) : AndroidViewModel(app) {

    private val repository = SumiRepository.get(app)
    private val zone: ZoneId = ZoneId.systemDefault()

    private val _state = MutableStateFlow(ComposerState())
    val state: StateFlow<ComposerState> = _state.asStateFlow()

    init {
        // Intent extras arrive through SavedStateHandle via the activity's default
        // view model arguments, so a widget tap, a gap tap and an entry tap all
        // land here the same way.
        val editingId = savedState.get<Long>(ComposerActivity.EXTRA_ENTRY_ID)?.takeIf { it > 0 }
        val presetStart = savedState.get<Long>(ComposerActivity.EXTRA_START)?.takeIf { it > 0 }
        val presetEnd = savedState.get<Long>(ComposerActivity.EXTRA_END)?.takeIf { it > 0 }

        viewModelScope.launch {
            val words = repository.recentWords()
            val goals = repository.goalsNow()
            val settings = repository.settingsNow()
            val latest = repository.latestEntry()
            val question = latest?.let { Prompts.questionFor(it.end.plus(settings.askInterval)) }
                ?: Prompts.DEFAULT

            val editing = editingId?.let { repository.entry(it) }
            _state.value = when {
                editing != null -> ComposerState(
                    loading = false,
                    editingId = editing.id,
                    start = editing.start,
                    end = editing.end,
                    text = editing.text.orEmpty(),
                    element = editing.element,
                    goals = goals,
                    words = words,
                    question = "Edit this entry"
                )

                presetStart != null && presetEnd != null -> ComposerState(
                    loading = false,
                    start = Instant.ofEpochMilli(presetStart),
                    end = Instant.ofEpochMilli(presetEnd),
                    goals = goals,
                    words = words,
                    question = "What filled this gap?"
                )

                else -> {
                    val range = RangeSuggestion.suggest(latest?.start, latest?.end, Instant.now(), settings.askInterval)
                    ComposerState(
                        loading = false,
                        start = range.start,
                        end = range.endInclusive,
                        goals = goals,
                        words = words,
                        question = question
                    )
                }
            }
        }
    }

    fun onTextChange(text: String) = _state.update {
        // A line that has changed is no longer the line that was going to be kept.
        it.copy(text = text, message = null, keeping = it.keeping && text.isBlank())
    }

    /** The toggle beside a new line: keep this word, or let it be a note and go. */
    fun toggleKeeping() = _state.update { it.copy(keeping = !it.keeping) }

    /**
     * A word tapped is a whole log: its element, its domain, and the word itself as
     * the line, unless something has already been typed.
     */
    fun logWord(word: Word) {
        val s = _state.value
        if (s.saving || s.loading) return
        commit(element = word.element, word = word, line = s.text.ifBlank { word.name })
    }

    /** Both ends at once, from the From | To picker. See [TimeRange] for how days are chosen. */
    fun setRange(from: LocalTime, to: LocalTime) = _state.update { s ->
        val range = TimeRange.resolve(from, to, previousEnd = s.end, zone = zone)
        s.copy(start = range.start, end = range.endInclusive, message = null)
    }

    /** Send: a new entry goes in untagged; an edited entry keeps the element it had. */
    fun send() = commit(_state.value.element)

    /** A kanji tap is a complete log on its own; any typed text rides along. */
    fun commitWith(element: Element) = commit(element)

    fun delete() {
        val id = _state.value.editingId ?: return
        viewModelScope.launch {
            repository.delete(id)
            WidgetSync.onEntriesChanged(getApplication())
            _state.update { it.copy(done = true) }
        }
    }

    private fun commit(element: Element?, word: Word? = null, line: String? = null) {
        val s = _state.value
        if (s.saving || s.loading) return
        _state.update { it.copy(saving = true, message = null) }

        viewModelScope.launch {
            val text = line ?: s.text
            // Either the word that was tapped, the word this line already is, or a
            // new one if the keep toggle is on. Nothing is kept without being asked.
            val tagged = when {
                word != null -> word
                element == null || text.isBlank() -> null
                s.keeping -> repository.keepWord(element, text, s.goals.nameFor(element))
                else -> repository.wordUnder(element, text)
            }

            val result = if (s.editingId == null) {
                repository.log(s.start, s.end, text, element, tagged?.id)
            } else {
                repository.update(s.editingId, s.start, s.end, text, element, tagged?.id)
            }

            when (result) {
                is SaveResult.Saved -> {
                    WidgetSync.onEntriesChanged(getApplication())
                    _state.update { it.copy(saving = false, done = true) }
                }

                is SaveResult.Invalid -> _state.update {
                    it.copy(saving = false, message = result.reason)
                }
            }
        }
    }

}
