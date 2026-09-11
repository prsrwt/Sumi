package com.fings.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.fings.app.data.FingsRepository
import java.time.LocalDate

/**
 * Ticks or unticks one goal for today, straight from the home screen.
 *
 * Runs in the widget process off the main thread. The write itself is a
 * transaction in the DAO, so two fast taps cannot lose a tick between them.
 */
class ToggleGoalAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val slot = parameters[SlotKey] ?: return
        FingsRepository.get(context).toggleGoal(LocalDate.now(), slot)

        // Every placed widget shows the same day, so they all need redrawing -
        // not just the one that was tapped.
        FingsWidget().updateAll(context)
    }

    companion object {
        val SlotKey = ActionParameters.Key<Int>("fings.slot")
    }
}
