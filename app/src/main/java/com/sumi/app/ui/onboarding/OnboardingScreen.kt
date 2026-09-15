package com.sumi.app.ui.onboarding

import android.appwidget.AppWidgetManager
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sumi.app.data.GOAL_COUNT
import com.sumi.app.data.Prompts
import com.sumi.app.data.Untagged
import com.sumi.app.ui.GlassTabs
import com.sumi.app.ui.SumiFonts
import com.sumi.app.ui.setup.AddWidgetButton
import com.sumi.app.ui.setup.GoalRow
import com.sumi.app.ui.setup.SetupViewModel
import com.sumi.app.ui.setup.SheetsSection
import com.sumi.app.ui.setup.TimeButton
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * The first-launch introduction: seven quiet pages from what Sumi is for to a
 * ready home screen.
 *
 * Every choice on these pages is the real setting, saved the moment it changes,
 * through the same ViewModel and controls as Setup. So skipping part-way keeps
 * whatever was already chosen, and nothing here can drift from what Setup does.
 */
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    /** Back on the first page. Null on a first launch, where Back simply leaves the app. */
    onClose: (() -> Unit)? = null,
    setup: SetupViewModel = viewModel()
) {
    val pager = rememberPagerState { PAGE_COUNT }
    val scope = rememberCoroutineScope()
    val page = pager.currentPage
    val isLast = page == PAGE_COUNT - 1

    // A slow, eased glide for Next and Back. The default is a quick spring, which
    // reads as the page being yanked across rather than turned.
    fun goTo(target: Int) = scope.launch {
        pager.animateScrollToPage(target, animationSpec = tween(PAGE_TURN_MILLIS, easing = FastOutSlowInEasing))
    }

    BackHandler(enabled = page > 0) { goTo(page - 1) }
    BackHandler(enabled = page == 0 && onClose != null) { onClose?.invoke() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(visible = !isLast, enter = fadeIn(tween(FADE_MILLIS)), exit = fadeOut(tween(FADE_MILLIS))) {
                TextButton(onClick = onFinish) { Text("Skip") }
            }
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Top,
            // The neighbouring pages are built ahead of time, so a turn never has
            // to lay out five text fields in its first frames and drop them.
            beyondViewportPageCount = 1
        ) { index ->
            Box(
                modifier = Modifier.graphicsLayer {
                    // While a page turns, its content trails the page a little and
                    // fades, so pages seem to settle into place rather than slide
                    // past as flat cards.
                    val offset = pager.getOffsetDistanceInPages(index).coerceIn(-1f, 1f)
                    translationX = offset * size.width * 0.35f
                    alpha = 1f - abs(offset) * 0.85f
                }
            ) {
                when (index) {
                    0 -> WelcomePage()
                    1 -> HowItWorksPage(setup)
                    2 -> FivePage(setup)
                    3 -> RhythmPage(setup)
                    4 -> WidgetPage()
                    5 -> SheetsPage()
                    else -> ReadyPage()
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(88.dp)) {
                // Named in full: inside this Row, the Row-only overload would be picked.
                androidx.compose.animation.AnimatedVisibility(visible = page > 0, enter = fadeIn(tween(FADE_MILLIS)), exit = fadeOut(tween(FADE_MILLIS))) {
                    TextButton(onClick = { goTo(page - 1) }) { Text("Back") }
                }
            }
            Dots(current = page, modifier = Modifier.weight(1f))
            Box(modifier = Modifier.width(88.dp), contentAlignment = Alignment.CenterEnd) {
                Button(
                    onClick = { if (isLast) onFinish() else goTo(page + 1) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onBackground,
                        contentColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    val label = when {
                        page == 0 -> "Begin"
                        isLast -> "Start"
                        else -> "Next"
                    }
                    AnimatedContent(
                        targetState = label,
                        transitionSpec = { fadeIn(tween(FADE_MILLIS)) togetherWith fadeOut(tween(FADE_MILLIS)) },
                        label = "next-label"
                    ) { Text(it) }
                }
            }
        }
    }
}

// ---- pages ----

@Composable
private fun WelcomePage() {
    // The brush touches paper: the mark settles in from slightly larger and faint.
    val arrival = remember { Animatable(0f) }
    LaunchedEffect(Unit) { arrival.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "墨",
            fontFamily = SumiFonts.mincho,
            fontSize = 112.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .alpha(arrival.value)
                .scale(1.08f - 0.08f * arrival.value)
        )
        Spacer(Modifier.height(8.dp))
        Text("Sumi", fontFamily = SumiFonts.mincho, fontSize = 40.sp, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "A quiet record of where your time goes.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun HowItWorksPage(setup: SetupViewModel) {
    val settings by setup.settings.collectAsStateWithLifecycle()
    Page(title = "How it works") {
        WidgetPreview(asking = true)
        Point("問", "Every ${settings.askInterval.toMinutes()} minutes, the widget on your home screen turns into a question.")
        Point("書", "Tap it and write a line about how the time went, or tap one of the five things that matter most to you. You will choose them next.")
        Point("衡", "The pentagon shows where your time goes: where to push, and where you may be pushing too hard.")
        Point("静", "No notifications, no streaks, no scores. A missed hour is just a missed hour.")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FivePage(setup: SetupViewModel) {
    val goals by setup.goals.collectAsStateWithLifecycle()
    val names by setup.names.collectAsStateWithLifecycle()

    Page(title = "Your five", scrollable = WindowInsets.isImeVisible) {
        Body("The five things you most want your time to go to. Each gets one of the classical Japanese elements.")
        val current = names
        if (current != null && goals.size == GOAL_COUNT) {
            goals.sortedBy { it.slot }.forEach { goal ->
                GoalRow(
                    goal = goal,
                    goals = goals,
                    name = current.getOrElse(goal.slot) { "" },
                    onName = { setup.onNameChanged(goal.slot, it) },
                    onElement = { setup.assign(goal.slot, it) }
                )
            }
        }
        Body("Anything else is ${Untagged.KANJI}, untagged. You can change these later in Setup.")
    }
}

@Composable
private fun RhythmPage(setup: SetupViewModel) {
    val settings by setup.settings.collectAsStateWithLifecycle()
    val intervals = listOf(30L, 45L, 60L, 90L)

    Page(title = "Rhythm") {
        Body("How long after your last entry the widget asks again.")
        GlassTabs(
            tabs = intervals.map { "$it min" },
            selectedIndex = intervals.indexOf(settings.askInterval.toMinutes()).coerceAtLeast(0),
            onSelect = { setup.setInterval(intervals[it]) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Body("Quiet hours. The widget shows only the time while you sleep.")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("From", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            TimeButton(settings.quietStart, "Quiet hours begin", setup::setQuietStart)
            Text("to", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TimeButton(settings.quietEnd, "Quiet hours end", setup::setQuietEnd)
        }
    }
}

@Composable
private fun WidgetPage() {
    val context = LocalContext.current
    val canPin = remember {
        context.getSystemService(AppWidgetManager::class.java)?.isRequestPinAppWidgetSupported == true
    }
    Page(title = "Put Sumi on your home screen") {
        WidgetPreview(asking = false)
        Body("The widget is where Sumi lives. It shows the time, and turns into a question when it is time to write something down.")
        if (canPin) {
            AddWidgetButton()
        } else {
            Body("Long-press an empty spot on your home screen, choose Widgets, and find Sumi.")
        }
    }
}

@Composable
private fun SheetsPage() {
    Page(title = "Keep a copy in Google Sheets") {
        Body(
            "Optional. Sumi makes one spreadsheet in your own Google Drive and keeps it up to date, " +
                "with a tab for each month. You can also connect later in Setup."
        )
        SheetsSection(inOnboarding = true)
    }
}

@Composable
private fun ReadyPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("始", fontFamily = SumiFonts.mincho, fontSize = 96.sp, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(16.dp))
        Text("You are set.", fontFamily = SumiFonts.mincho, fontSize = 30.sp, lineHeight = 40.sp, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "When the widget asks, tap it and write what you are doing. Everything else can wait.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ---- pieces ----

/**
 * One page, sized to fit a phone screen without scrolling: a page that scrolls
 * hides part of what it is introducing. [scrollable] is only for the moment the
 * keyboard covers the page, so the field being typed in can stay in view.
 */
@Composable
private fun Page(title: String, scrollable: Boolean = false, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState(), enabled = scrollable)
            .padding(horizontal = 24.dp)
            .padding(top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Mincho's own line spacing is tight enough for a wrapped title to overlap itself.
        Text(
            text = title,
            fontFamily = SumiFonts.mincho,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        content()
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Point(glyph: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = glyph,
            fontFamily = SumiFonts.mincho,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.width(40.dp)
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}

/**
 * A drawing of the widget, flat glass like the real one. Words are in Mincho, as
 * the widget draws them; the clock is in the serif its live clock uses. [asking]
 * shows its question face.
 */
@Composable
private fun WidgetPreview(asking: Boolean, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onBackground
    val context = LocalContext.current
    val time = remember {
        LocalTime.now().format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"))
    }
    val date = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE · d MMMM")) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(ink.copy(alpha = 0.05f))
            .border(1.dp, ink.copy(alpha = 0.12f), RoundedCornerShape(28.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            if (asking) {
                Text(
                    text = Prompts.DEFAULT,
                    fontFamily = SumiFonts.mincho,
                    fontSize = 21.sp,
                    lineHeight = 28.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    color = ink
                )
                Text(time, fontFamily = FontFamily.Serif, fontSize = 13.sp, color = ink.copy(alpha = 0.7f))
            } else {
                Text(time, fontFamily = FontFamily.Serif, fontSize = 40.sp, color = ink)
                Text(date, fontFamily = SumiFonts.mincho, fontSize = 13.sp, color = ink.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun Dots(current: Int, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onBackground
    Row(modifier = modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        repeat(PAGE_COUNT) { index ->
            val active = index == current
            val color by animateColorAsState(
                targetValue = if (active) ink else ink.copy(alpha = 0.2f),
                animationSpec = tween(PAGE_TURN_MILLIS),
                label = "dot-$index"
            )
            // The current dot stretches into a short stroke, and the stroke glides
            // from dot to dot with the page.
            val width by animateDpAsState(
                targetValue = if (active) 18.dp else 6.dp,
                animationSpec = tween(PAGE_TURN_MILLIS, easing = FastOutSlowInEasing),
                label = "dot-width-$index"
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .height(6.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

private const val PAGE_COUNT = 7
private const val PAGE_TURN_MILLIS = 560
private const val FADE_MILLIS = 260
