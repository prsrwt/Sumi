package com.fings.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import com.fings.app.ui.GoalSetupScreen
import com.fings.app.widget.FingsWidget
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            FingsTheme {
                Scaffold { innerPadding ->
                    GoalSetupScreen(modifier = Modifier.padding(innerPadding))
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
        lifecycleScope.launch { FingsWidget().updateAll(this@MainActivity) }
    }
}

@Composable
private fun FingsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF4ADE80),
            surface = Color(0xFF11150F),
            background = Color(0xFF0B0E0A)
        ),
        content = content
    )
}
