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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.sumi.app.ui.GlassTabs
import com.sumi.app.ui.SumiTheme
import com.sumi.app.ui.balance.BalanceScreen
import com.sumi.app.ui.setup.SetupScreen
import com.sumi.app.ui.today.TodayScreen
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
     * back on the home screen. Also what pulls a freshly installed widget off
     * Glance's loading placeholder.
     */
    override fun onStop() {
        super.onStop()
        lifecycleScope.launch { WidgetSync.onEntriesChanged(this@MainActivity) }
    }
}

@Composable
private fun SumiHome(modifier: Modifier = Modifier) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showSetup by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val tabs = remember { listOf("Today", "Balance") }

    BackHandler(enabled = showSetup) { showSetup = false }

    // Setup fades over the home screen rather than replacing it in a single frame.
    AnimatedContent(
        targetState = showSetup,
        transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(180)) },
        modifier = modifier,
        label = "setup"
    ) { inSetup ->
        if (inSetup) {
            SetupScreen(onBack = { showSetup = false })
        } else {
            HomeTabs(
                selectedTab = selectedTab,
                tabs = tabs,
                onSelectTab = { selectedTab = it },
                onOpenSetup = { showSetup = true }
            )
        }
    }
}

@Composable
private fun HomeTabs(
    selectedTab: Int,
    tabs: List<String>,
    onSelectTab: (Int) -> Unit,
    onOpenSetup: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassTabs(
                tabs = tabs,
                selectedIndex = selectedTab,
                onSelect = onSelectTab,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onOpenSetup) {
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
                else -> BalanceScreen()
            }
        }
    }
}
