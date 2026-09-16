package com.sumi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sumi.app.ui.GlassTabs
import com.sumi.app.ui.SumiTheme
import com.sumi.app.ui.account.AccountMark
import com.sumi.app.ui.account.YouScreen
import com.sumi.app.ui.balance.BalanceScreen
import com.sumi.app.ui.onboarding.OnboardingScreen
import com.sumi.app.ui.onboarding.OnboardingViewModel
import com.sumi.app.ui.setup.SetupScreen
import com.sumi.app.ui.today.TodayScreen
import com.sumi.app.ui.today.TodayViewModel
import java.time.LocalDate
import com.sumi.app.widget.WidgetSync
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SumiTheme {
                Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
                    SumiHome(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    /**
     * Anything changed in the app should be on the widget by the time the user is
     * back on the home screen, including a wallpaper change made while Sumi was
     * open, which Android never tells widgets about.
     */
    override fun onStop() {
        super.onStop()
        lifecycleScope.launch { WidgetSync.onEntriesChanged(this@MainActivity) }
    }
}

private enum class Screen { WAITING, INTRODUCTION, SETUP, YOU, HOME }

@Composable
private fun SumiHome(
    modifier: Modifier = Modifier,
    onboarding: OnboardingViewModel = viewModel(),
    // Shared with the Today tab, so a day picked in the history opens there.
    today: TodayViewModel = viewModel()
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showSetup by rememberSaveable { mutableStateOf(false) }
    var showYou by rememberSaveable { mutableStateOf(false) }
    var replayingIntroduction by rememberSaveable { mutableStateOf(false) }
    val onboarded by onboarding.onboarded.collectAsStateWithLifecycle()
    val tabs = remember { listOf("Today", "Balance") }

    val screen = when {
        onboarded == null -> Screen.WAITING
        onboarded == false || replayingIntroduction -> Screen.INTRODUCTION
        showSetup -> Screen.SETUP
        showYou -> Screen.YOU
        else -> Screen.HOME
    }

    val todayState by today.state.collectAsStateWithLifecycle()

    // Back always walks home: to Today, on today's date. Only from there, already
    // on today, does it leave the app.
    BackHandler(enabled = screen == Screen.SETUP || screen == Screen.YOU) {
        showSetup = false
        showYou = false
        selectedTab = 0
        today.showToday()
    }
    BackHandler(enabled = screen == Screen.HOME && selectedTab != 0) { selectedTab = 0 }
    BackHandler(enabled = screen == Screen.HOME && selectedTab == 0 && !todayState.isToday) { today.showToday() }

    // Each screen fades over the last rather than replacing it in a single frame.
    AnimatedContent(
        targetState = screen,
        transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(180)) },
        modifier = modifier,
        label = "screen"
    ) { current ->
        when (current) {
            // One database read at launch; a blank page beats a flash of the wrong screen.
            Screen.WAITING -> Box(Modifier.fillMaxSize())

            Screen.INTRODUCTION -> OnboardingScreen(
                onFinish = {
                    onboarding.finish()
                    replayingIntroduction = false
                    showSetup = false
                    showYou = false
                    selectedTab = 0
                },
                onClose = if (replayingIntroduction) ({ replayingIntroduction = false }) else null
            )

            Screen.SETUP -> SetupScreen(
                onBack = { showSetup = false },
                onShowIntroduction = { replayingIntroduction = true }
            )

            Screen.YOU -> YouScreen(onBack = { showYou = false })

            Screen.HOME -> 
HomeTabs(
                selectedTab = selectedTab,
                tabs = tabs,
                onSelectTab = { selectedTab = it },
                onOpenSetup = { showSetup = true },
                onOpenYou = { showYou = true },
                onOpenDay = { day ->
                    today.showDay(day)
                    selectedTab = 0
                }
            )
        }
    }
}

@Composable
private fun HomeTabs(
    selectedTab: Int,
    tabs: List<String>,
    onSelectTab: (Int) -> Unit,
    onOpenSetup: () -> Unit,
    onOpenYou: () -> Unit,
    onOpenDay: (LocalDate) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // You on the left, Sumi's own settings on the right, and the tabs centred
        // between two marks of the same size rather than pushed off by one.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AccountMark(onClick = onOpenYou)
            GlassTabs(
                tabs = tabs,
                selectedIndex = selectedTab,
                onSelect = onSelectTab,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onOpenSetup, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Settings, contentDescription = "Setup")
            }
        }

        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "tab"
        ) { tab ->
            when (tab) {
                0 -> TodayScreen(onOpenSetup = onOpenSetup)
                else -> BalanceScreen(onOpenDay = onOpenDay)
            }
        }
    }
}
