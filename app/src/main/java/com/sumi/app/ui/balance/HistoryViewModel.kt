package com.sumi.app.ui.balance

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sumi.app.data.SumiRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

/**
 * Every month you have logged anything in. Read only while the history sheet is
 * open, so the rest of the app never carries the whole log around.
 */
class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val zone: ZoneId = ZoneId.systemDefault()

    val months: StateFlow<List<HistoryMonth>> = SumiRepository.get(app).observeAllEntries()
        .map { History.build(it, LocalDate.now(zone), zone) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
