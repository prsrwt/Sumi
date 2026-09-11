package com.beaver.app

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
import com.beaver.app.ui.BalanceScreen
import com.beaver.app.ui.BeaverTheme
import com.beaver.app.ui.GlassTabs
import com.beaver.app.ui.GoalSetupScreen
import com.beaver.app.widget.BeaverWidget
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BeaverTheme {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    BeaverHome(modifier = Modifier.padding(innerPadding))
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
        lifecycleScope.launch { BeaverWidget().updateAll(this@MainActivity) }
    }
}

@Composable
private fun BeaverHome(modifier: Modifier = Modifier) {
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
