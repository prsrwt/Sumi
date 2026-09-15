package com.sumi.app.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sumi.app.data.SumiRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OnboardingViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = SumiRepository.get(app)

    /** Null while the settings row is read; the app waits rather than flash the wrong screen. */
    val onboarded: StateFlow<Boolean?> = repository.observeOnboarded()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun finish() {
        viewModelScope.launch { repository.finishOnboarding() }
    }
}
