package com.sumi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

    if (showSetup) {
        BackHandler { showSetup = false }
        SetupScreen(onBack = { showSetup = false }, modifier = modifier)
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassTabs(
                tabs = tabs,
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showSetup = true }) {
                Icon(Icons.Filled.Settings, contentDescription = "Setup")
            }
        }

        when (selectedTab) {
            0 -> TodayScreen(onOpenSetup = { showSetup = true })
            else -> BalanceScreen()
        }
    }
}
