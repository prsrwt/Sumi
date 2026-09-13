package com.sumi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import com.sumi.app.ui.BalanceScreen
import com.sumi.app.ui.SumiTheme
import com.sumi.app.ui.GlassTabs
import com.sumi.app.ui.GoalSetupScreen
import com.sumi.app.widget.SumiWidget
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SumiTheme {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    SumiHome(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    /**
     * Renamed goals and edited history should be on the widget by the time the
     * user gets back to the home screen. Also what pulls a freshly installed
     * widget off Glance's loading placeholder.
     */
    override fun onStop() {
        super.onStop()
        lifecycleScope.launch { SumiWidget().updateAll(this@MainActivity) }
    }
}

@Composable
private fun SumiHome(modifier: Modifier = Modifier) {
    // Survives rotation so the user does not get bounced back to Goals.
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = remember { listOf("Goals", "Balance") }

    Column(modifier = modifier.fillMaxSize()) {
        GlassTabs(
            tabs = tabs,
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        )

        when (selectedTab) {
            0 -> GoalSetupScreen(modifier = Modifier.fillMaxSize())
            else -> BalanceScreen(modifier = Modifier.fillMaxSize())
        }
    }
}
