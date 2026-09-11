package com.beaver.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Two frosted pills in a frosted trough.
 *
 * Compose can blur for real (`Modifier.blur`, API 31+), but blurring here would
 * only smear a flat background and cost a render pass for nothing. What actually
 * reads as frosted glass on a plain page is a translucent fill plus a hairline
 * edge, so that is what this draws. Alpha is taken from `onBackground`, which
 * makes the whole control work unchanged in light and dark.
 */
@Composable
fun GlassTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val ink = MaterialTheme.colorScheme.onBackground

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(ink.copy(alpha = 0.06f))
            .border(1.dp, ink.copy(alpha = 0.12f), RoundedCornerShape(percent = 50))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEachIndexed { index, label ->
            GlassPill(
                label = label,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun GlassPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ink = MaterialTheme.colorScheme.onBackground
    val fill by animateColorAsState(
        targetValue = if (selected) ink.copy(alpha = 0.14f) else ink.copy(alpha = 0f),
        label = "pillFill"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) ink else ink.copy(alpha = 0.55f),
        label = "pillText"
    )
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(fill)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor
        )
    }
}
