package com.sumi.app.ui.composer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sumi.app.data.Domain
import com.sumi.app.data.Element
import com.sumi.app.data.Goal
import com.sumi.app.data.WordHome
import com.sumi.app.data.nameFor
import com.sumi.app.data.Prompts
import com.sumi.app.data.Word
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

/** An entry that is already logged, and the new word it was logged with. */
data class Saved(val entryId: Long, val word: String)

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
    /** Every part of life, for choosing where a new word belongs. */
    val domains: List<Domain> = emptyList(),
    /**
     * Set once an entry is saved with a word Sumi has not seen: the hour is already
     * recorded, and this is the question of where the word belongs. Answering it or
     * ignoring it both close the composer.
     */
    val askedAfterSaving: Saved? = null,
    /**
     * Where Sumi knows this word goes, when it is one it came with. Offered above
     * the five, because tapping an element can only reach that element's first
     * part of life, and "cooking" belongs with Home and care wherever that sits.
     */
    val suggestion: WordHome? = null,
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
            val domains = repository.domainsNow()
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
                    domains = domains,
                    question = "Edit this entry"
                )

                presetStart != null && presetEnd != null -> ComposerState(
                    loading = false,
                    start = Instant.ofEpochMilli(presetStart),
                    end = Instant.ofEpochMilli(presetEnd),
                    goals = goals,
                    words = words,
                    domains = domains,
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
                        domains = domains,
                        question = question
                    )
                }
            }
        }
    }

    fun onTextChange(text: String) = _state.update { it.copy(text = text, message = null) }

    /**
     * Keeps the word under one of the five and puts it on the entry that was just
     * saved. An element with nothing under it gets its first part of life, named
     * after the element itself, so a word always has somewhere to live. The hour
     * was never waiting on this.
     */
    fun keepUnder(element: Element) {
        val s = _state.value
        val saved = s.askedAfterSaving ?: return
        keeping { repository.keepWord(element, saved.word, s.goals.nameFor(element)) }
    }

    /** Keeps the word where Sumi knows it goes, adding that part of life if it is missing. */
    fun keepAtHome(home: WordHome) {
        keeping { repository.keepWordAtHome(home, _state.value.askedAfterSaving?.word.orEmpty()) }
    }

    private fun keeping(keep: suspend () -> Word?) {
        val saved = _state.value.askedAfterSaving ?: return
        viewModelScope.launch {
            val word = keep()
            if (word != null) repository.tagEntry(saved.entryId, word)
            WidgetSync.onEntriesChanged(getApplication())
            _state.update { it.copy(askedAfterSaving = null, suggestion = null, done = true) }
        }
    }

    /** Ignoring the question is an answer: the line stays a note and nothing is kept. */
    fun keepNothing() =
        _state.update { it.copy(askedAfterSaving = null, suggestion = null, done = true) }

    /**
     * A word tapped is a whole log: its element, its domain, and the word itself as
     * the line, unless something has already been typed.
     */
    fun logWord(word: Word) {
        val s = _state.value
        if (s.saving || s.loading) return
        val typed = s.text.trim()
        // A line already typed is what the entry says, so the word only lends its
        // element: tagging "chai with dad" as "walk" because the chip was nearest
        // would quietly file it in the wrong place.
        val sameThing = typed.isEmpty() || typed.equals(word.name, ignoreCase = true)
        commit(
            element = word.element,
            word = if (sameThing) word else null,
            line = typed.ifEmpty { word.name }
        )
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
                    // A word Sumi has not seen is worth one question, and only once
                    // the hour is safely logged. Sent without an element too:
                    // choosing one answers both questions at once, and an untagged
                    // hour finds its spoke.
                    val line = text.trim()
                    val unknown = s.editingId == null && tagged == null && line.isNotBlank()
                    // The offer is only worth making when it goes somewhere tapping
                    // the element would not: the element's first part of life is
                    // what that row already does.
                    val home = if (!unknown) null else repository.homeFor(line)?.takeIf { found ->
                        s.domains.filter { it.element == found.element }
                            .minByOrNull { it.position }?.name?.equals(found.name, ignoreCase = true) != true
                    }
                    _state.update {
                        if (unknown) {
                            it.copy(
                                saving = false,
                                askedAfterSaving = Saved(result.id, line),
                                suggestion = home
                            )
                        } else {
                            it.copy(saving = false, done = true)
                        }
                    }
                }

                is SaveResult.Invalid -> _state.update {
                    it.copy(saving = false, message = result.reason)
                }
            }
        }
    }

}
