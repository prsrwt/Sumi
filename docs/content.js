/*
 * Inside Sumi: the study guide's content.
 *
 * Each chapter is a short slideshow. A slide has a title, a body (HTML), and
 * optionally a code excerpt from the real source, a diagram, a "why it is built
 * this way" note, and a "try it" exercise. Code excerpts are trimmed copies of
 * the files named above them; open those files for the full picture.
 */
window.SUMI_GUIDE = [
  // ---------------------------------------------------------------------------
  {
    id: "idea",
    kanji: "念",
    element: "gold",
    title: "The idea",
    blurb: "What Sumi is for, and the research rules every screen follows.",
    slides: [
      {
        title: "A quiet record of where your time goes",
        body: `
          <p>Sumi is an Android home-screen widget and a small app. The widget shows the time. Every so often it turns into a gentle question, like <em>What has this hour held?</em> You tap it, write a line or tap one of your five goals, and the widget goes back to being a clock.</p>
          <p>Over days, those answers become a timesheet (the Today tab) and a picture of balance (the Balance tab). Optionally, everything is copied to a Google Sheet in your own Drive.</p>
          <p>That is the whole product. Everything in this guide is about doing those few things well.</p>`,
        why: `A widget is the one place you look many times a day without opening anything. Asking there costs a glance, not a trip into an app.`
      },
      {
        title: "Borrowed from psychology research",
        body: `
          <p>Two research methods shaped Sumi:</p>
          <ul>
            <li><strong>Experience sampling (ESM).</strong> People are prompted at intervals through the day to note what they are doing right now. Recording in the moment beats trying to remember a whole day later.</li>
            <li><strong>Day Reconstruction Method (DRM).</strong> People rebuild a day as a sequence of episodes, each with a start and an end. This is why every Sumi entry is a stretch of time, <em>from</em> and <em>to</em>, not a single tap.</li>
          </ul>
          <p>Sumi uses ESM to ask and DRM to structure the answer.</p>`
      },
      {
        title: "The rules nothing in the app breaks",
        body: `
          <p>Habit apps often motivate with pressure. Research on "lapsing" shows pressure backfires: one missed day, a broken streak, and people quit. So Sumi follows fixed rules:</p>
          <ul>
            <li><strong>No streaks, badges or points.</strong> Nothing counts consecutive days.</li>
            <li><strong>No red anywhere.</strong> Fire is orange. Low time is shown as quiet, never as a warning.</li>
            <li><strong>Exactly five goals.</strong> A closed set you can hold in your head.</li>
            <li><strong>Ratios, not counts.</strong> Balance compares shares of time.</li>
            <li><strong>Rolling windows.</strong> The last 7 or 14 days, never "this week", so Monday is not a reset.</li>
            <li><strong>Descriptive words.</strong> "Water has been quiet lately", never "You failed Water".</li>
          </ul>`,
        why: `A calendar week empties every Monday, which reads as starting over. A rolling window just slides forward a day.`
      },
      {
        title: "Five goals, five elements",
        body: `
          <p>Your five goals are paired one to one with the <strong>Godai</strong>, the five classical Japanese elements. Each has a kanji and a colour:</p>
          <div class="element-row">
            <span style="--c: var(--earth)"><b>地</b>Earth</span>
            <span style="--c: var(--water)"><b>水</b>Water</span>
            <span style="--c: var(--fire)"><b>火</b>Fire</span>
            <span style="--c: var(--wind)"><b>風</b>Wind</span>
            <span style="--c: var(--void)"><b>空</b>Void</span>
          </div>
          <p>Name Fire "Workout" and it is called Workout everywhere, with 火 beside it. Time with no element is <strong>無</strong> (mu, "without"), shown in grey.</p>`,
        code: {
          file: "app/src/main/java/com/sumi/app/data/Element.kt",
          text: `enum class Element(
    val kanji: String,
    val color: Int,
    val displayName: String,
    val affinity: String
) {
    EARTH("地", Color.rgb(217, 164, 65), "Earth", "stability, body, grounding"),
    WATER("水", Color.rgb(79, 163, 217), "Water", "flow, recovery, connection"),
    FIRE("火", Color.rgb(255, 138, 76), "Fire", "drive, intensity, output"),
    WIND("風", Color.rgb(143, 209, 160), "Wind", "change, learning, ideas"),
    VOID("空", Color.rgb(160, 140, 196), "Void", "spirit, reflection, rest");
}`
        }
      },
      {
        title: "Try it",
        body: `
          <p>Before reading any code, use Sumi for a morning. Notice three things:</p>
          <ol>
            <li>When exactly does the widget ask? Is it a fixed clock time, or does it depend on your last answer?</li>
            <li>What happens when you tap a kanji without typing anything?</li>
            <li>Does anything on any screen tell you that you did badly?</li>
          </ol>
          <p>You will find the answers to 1 and 2 in the chapters on Rhythm and the Composer. The answer to 3 should be no.</p>`
      }
    ]
  },

  // ---------------------------------------------------------------------------
  {
    id: "map",
    kanji: "図",
    element: "water",
    title: "Map of the app",
    blurb: "The pieces, the layers, and the journey of a single log.",
    slides: [
      {
        title: "The pieces you can see",
        body: `
          <ul>
            <li><strong>Widget.</strong> The clock that asks. Lives on the home screen.</li>
            <li><strong>Composer.</strong> The sheet that rises when you tap the widget: from and to, a line of text, the five kanji.</li>
            <li><strong>Today.</strong> The day as a timesheet, newest first, with unlogged gaps you can fill.</li>
            <li><strong>Balance.</strong> A pentagon of the last 7 or 14 days and a 30-day grid.</li>
            <li><strong>Setup.</strong> Your five, the rhythm, quiet hours, Google Sheets, resets.</li>
            <li><strong>Introduction.</strong> Seven pages on first launch.</li>
            <li><strong>Sync.</strong> Invisible: copies the log to Google Sheets in the background.</li>
          </ul>`
      },
      {
        title: "Layers, from screen to disk",
        body: `
          <p>Sumi follows the architecture Android recommends. Each layer only talks to the one below it.</p>
          <ul>
            <li><strong>UI</strong> (Jetpack Compose): draws state and reports taps. No database code.</li>
            <li><strong>ViewModel</strong>: holds screen state, survives screen rotation, decides what a tap means.</li>
            <li><strong>Repository</strong>: the one place that turns stored rows into app types like <code>Entry</code> and back.</li>
            <li><strong>DAO</strong> (Room): SQL queries, written as annotated Kotlin functions.</li>
            <li><strong>SQLite</strong>: the database file <code>sumi.db</code> on the phone.</li>
          </ul>`,
        diagram: `
          <div class="stack">
            <div>Compose screens<small>TodayScreen, BalanceScreen, SetupScreen</small></div>
            <div>ViewModels<small>TodayViewModel, ComposerViewModel</small></div>
            <div>SumiRepository<small>rows in, Entry and Goal out</small></div>
            <div>SumiDao<small>@Query functions</small></div>
            <div>sumi.db<small>SQLite, on the phone</small></div>
          </div>`,
        why: `When the widget, the composer and the sync all read the same repository, they can never disagree about what a goal is called or how long an entry lasted.`
      },
      {
        title: "Where the files live",
        body: `<p>Everything is under <code>app/src/main/java/com/sumi/app/</code>, grouped by what it is for:</p>`,
        code: {
          file: "app/src/main/java/com/sumi/app/",
          lang: "text",
          text: `MainActivity.kt        the app's window: tabs, Setup, introduction
data/                  Element, entities, DAO, database, repository,
                       and the pure time logic (Intervals, TimeRange...)
ui/composer/           the log sheet opened from the widget
ui/today/              Timeline and the Today tab
ui/balance/            Balance math and the pentagon
ui/setup/              Setup and the Google Sheets section
ui/onboarding/         the first-launch introduction
widget/                the widget, glass, Mincho text, alarms
sync/                  Google sign-in, HTTPS, the sheet, WorkManager`
        }
      },
      {
        title: "The journey of one log",
        body: `<p>Follow a single tap on 火 from the home screen to every place it shows up:</p>`,
        diagram: `
          <ol class="flow">
            <li><b>Widget tapped</b><span>opens ComposerActivity</span></li>
            <li><b>火 tapped</b><span>ComposerViewModel.commitWith(FIRE)</span></li>
            <li><b>Saved</b><span>SumiRepository.log inside one transaction, which also queues the month for Sheets</span></li>
            <li><b>WidgetSync</b><span>redraws the widget, re-arms the next alarm, requests a Sheets sync</span></li>
            <li><b>Screens update</b><span>Today and Balance observe the database as a Flow and redraw themselves</span></li>
            <li><b>About 10 seconds later</b><span>WorkManager rewrites this month's tab in your sheet</span></li>
          </ol>`,
        why: `Nothing tells Today to refresh. It watches the database, so any change from anywhere appears on its own.`
      },
      {
        title: "Try it",
        body: `
          <ol>
            <li>Open <code>widget/WidgetSync.kt</code>. It is ten lines. List the three things that happen after every change.</li>
            <li>Search the project for <code>WidgetSync.onEntriesChanged</code>. How many places call it, and why does each need to?</li>
          </ol>`
      }
    ]
  },

  // ---------------------------------------------------------------------------
  {
    id: "kotlin",
    kanji: "言",
    element: "wind",
    title: "Kotlin and Android basics",
    blurb: "Just enough of the language and platform to read the rest.",
    slides: [
      {
        title: "Kotlin in five features",
        body: `
          <ul>
            <li><code>val</code> never changes after it is set; <code>var</code> can. Sumi uses <code>val</code> almost everywhere.</li>
            <li><strong>data class</strong>: a class that is just data, with equality and <code>copy()</code> for free.</li>
            <li><strong>Null safety</strong>: a type ending in <code>?</code> may be null. <code>?.</code> skips a call on null, <code>?:</code> gives a fallback.</li>
            <li><strong>sealed interface</strong>: a fixed set of cases, so a <code>when</code> can be checked for completeness.</li>
            <li><strong>Extension functions</strong>: add a function to an existing type without changing it.</li>
          </ul>`,
        code: {
          file: "app/src/main/java/com/sumi/app/data/Entities.kt",
          text: `fun List<Goal>.nameFor(element: Element?): String =
    if (element == null) Untagged.NAME
    else firstOrNull { it.element == element }?.displayName ?: element.displayName`
        },
        why: `nameFor reads like plain English at every call site: goals.nameFor(FIRE). One function names time everywhere, which is how a rename reaches every screen.`
      },
      {
        title: "Coroutines: waiting without freezing",
        body: `
          <p>Reading the database or calling Google takes time. If the main thread (the one drawing the screen) waited, the app would freeze.</p>
          <p>A <strong>coroutine</strong> is a lightweight task that can pause without blocking a thread. A <code>suspend</code> function is one that may pause. <code>viewModelScope.launch</code> starts a coroutine that is cancelled automatically when the screen goes away.</p>`,
        code: {
          file: "app/src/main/java/com/sumi/app/ui/composer/ComposerViewModel.kt",
          text: `viewModelScope.launch {
    val result = if (s.editingId == null) {
        repository.log(s.start, s.end, s.text, element)
    } else {
        repository.update(s.editingId, s.start, s.end, s.text, element)
    }
    when (result) {
        is SaveResult.Saved -> {
            WidgetSync.onEntriesChanged(getApplication())
            _state.update { it.copy(saving = false, done = true) }
        }
        is SaveResult.Invalid -> _state.update {
            it.copy(saving = false, message = result.reason)
        }
    }
}`
        }
      },
      {
        title: "Flow: data that keeps arriving",
        body: `
          <p>A <strong>Flow</strong> is a stream of values over time. Room can return a query as a Flow, so every time the table changes, a fresh result arrives.</p>
          <p>A <strong>StateFlow</strong> always holds a current value, which is what a screen needs to draw. <code>stateIn</code> turns a Flow into one, and <code>collectAsStateWithLifecycle</code> lets Compose redraw when it changes, pausing while the app is in the background.</p>`,
        code: {
          file: "app/src/main/java/com/sumi/app/ui/setup/SetupViewModel.kt",
          text: `val goals: StateFlow<List<Goal>> = repository.observeGoals()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())`
        },
        why: `WhileSubscribed(5_000) keeps the query alive for five seconds after the screen stops watching, so a quick rotation does not re-run it.`
      },
      {
        title: "Activities, the manifest and intents",
        body: `
          <p>An <strong>Activity</strong> is a window the system can open. Sumi has two: <code>MainActivity</code> (the app) and <code>ComposerActivity</code> (the sheet opened from the widget).</p>
          <p>The <strong>manifest</strong> (<code>AndroidManifest.xml</code>) declares them to Android, along with the widget receiver, the alarm receiver and the two permissions Sumi uses: starting after a reboot, and the internet.</p>
          <p>An <strong>Intent</strong> is a request to open something, optionally with extras attached, like the start and end of a gap you tapped on Today.</p>`,
        code: {
          file: "app/src/main/java/com/sumi/app/ui/composer/ComposerActivity.kt",
          text: `fun backfill(context: Context, from: Instant, to: Instant): Intent =
    newEntry(context)
        .putExtra(EXTRA_START, from.toEpochMilli())
        .putExtra(EXTRA_END, to.toEpochMilli())`
        }
      },
      {
        title: "Jetpack Compose: UI as functions",
        body: `
          <p>In Compose, a screen is a Kotlin function marked <code>@Composable</code> that describes the UI for the current state. When state changes, Compose calls the function again and updates only what changed. This is called <strong>recomposition</strong>.</p>
          <ul>
            <li><code>remember</code> keeps a value across recompositions.</li>
            <li><code>rememberSaveable</code> also survives rotation and process death, which is how the selected tab is kept.</li>
            <li>State lives in ViewModels; composables stay simple and just draw.</li>
          </ul>`,
        code: {
          file: "app/src/main/java/com/sumi/app/MainActivity.kt",
          text: `var selectedTab by rememberSaveable { mutableIntStateOf(0) }
var showSetup by rememberSaveable { mutableStateOf(false) }`
        }
      },
      {
        title: "Try it",
        body: `
          <ol>
            <li>In <code>Entities.kt</code>, find <code>Settings.isQuiet</code>. Explain its three branches in your own words, especially what equal start and end mean.</li>
            <li>In <code>MainActivity.kt</code>, the selected tab uses <code>rememberSaveable</code>. Rotate the phone on the Balance tab. What would happen with plain <code>remember</code>?</li>
          </ol>`
      }
    ]
  },

  // ---------------------------------------------------------------------------
  {
    id: "data",
    kanji: "蔵",
    element: "earth",
    title: "Data and Room",
    blurb: "Tables, queries, transactions and database migrations.",
    slides: [
      {
        title: "Tables are Kotlin classes",
        body: `
          <p><strong>Room</strong> is Android's database library. You write a data class with <code>@Entity</code>, and Room creates a table for it.</p>
          <p>Times are stored as <strong>UTC milliseconds</strong> plus the <strong>time zone</strong> the entry was logged in. A timesheet then stays correct across daylight saving changes and travel.</p>`,
        code: {
          file: "app/src/main/java/com/sumi/app/data/Entities.kt",
          text: `@Entity(tableName = "entries", indices = [Index("startMillis"), Index("endMillis")])
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startMillis: Long,
    val endMillis: Long,
    val zoneId: String,
    val text: String?,
    val element: String?,
    val updatedAt: Long,
    val syncedAt: Long?,
    val deletedAt: Long? = null
)`
        },
        why: `The indices on start and end make "entries that overlap this day" fast, which is the query Today and Balance run constantly.`
      },
      {
        title: "Queries are annotated functions",
        body: `
          <p>A <strong>DAO</strong> (data access object) holds the SQL. Room checks each query against the schema while compiling, so a typo in a column name fails the build instead of crashing on a phone.</p>
          <p>Returning <code>Flow</code> makes the query live.</p>`,
        code: {
          file: "app/src/main/java/com/sumi/app/data/SumiDao.kt",
          text: `/** Every entry that overlaps the half-open window [from, to). */
@Query("SELECT * FROM entries WHERE deletedAt IS NULL AND startMillis < :to AND endMillis > :from ORDER BY startMillis")
abstract fun observeOverlapping(from: Long, to: Long): Flow<List<EntryEntity>>`
        }
      },
      {
        title: "Transactions: all or nothing",
        body: `
          <p>A <strong>transaction</strong> groups several writes so they either all happen or none do.</p>
          <p>Each element may belong to only one goal, enforced by a <strong>unique index</strong>. Swapping two goals' elements would briefly give both the same element, which the index rejects. So the other goal is parked on a placeholder first, inside one transaction, and nothing ever sees the placeholder.</p>`,
        code: {
          file: "app/src/main/java/com/sumi/app/data/SumiDao.kt",
          text: `@Transaction
open suspend fun assignElement(slot: Int, element: String) {
    val current = getGoals().firstOrNull { it.slot == slot } ?: return
    if (current.element == element) return

    val holder = slotHolding(element)
    if (holder == null) {
        setGoalElement(slot, element)
        return
    }
    setGoalElement(holder, SWAP_PLACEHOLDER)
    setGoalElement(slot, element)
    setGoalElement(holder, current.element)
}`
        }
      },
      {
        title: "Migrations: upgrading without losing data",
        body: `
          <p>When the tables change, phones that already have the app need their database upgraded. A <strong>migration</strong> is the SQL that does it.</p>
          <ul>
            <li><strong>Version 1:</strong> goals, entries, settings.</li>
            <li><strong>Version 2:</strong> added <code>sync_state</code> and <code>dirty_months</code> for Google Sheets.</li>
            <li><strong>Version 3:</strong> added <code>onboardedAt</code> to settings for the introduction.</li>
          </ul>
          <p>Room writes each version's exact layout to <code>app/schemas/</code>, so a migration can be checked against what is really on people's phones.</p>`,
        code: {
          file: "app/src/main/java/com/sumi/app/data/SumiDatabase.kt",
          text: `private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE \`settings\` ADD COLUMN \`onboardedAt\` INTEGER")
    }
}`
        },
        why: `If a migration's SQL differs even slightly from what Room expects, the app crashes on launch after the update. That is why each one was tested by installing the old version, adding data, then installing the new one.`
      },
      {
        title: "The repository translates",
        body: `
          <p><code>SumiRepository</code> is the only class that knows rows store milliseconds and enum names as strings. Everything above it works with <code>Instant</code>, <code>Duration</code> and <code>Element</code>.</p>
          <p>It also enforces rules: an entry needs text or an element, must end after it starts, and cannot be longer than a day. Overlapping entries are allowed on purpose; totals count shared time once.</p>`,
        code: {
          file: "app/src/main/java/com/sumi/app/data/SumiRepository.kt",
          text: `private fun EntryEntity.toEntry() = Entry(
    id = id,
    start = Instant.ofEpochMilli(startMillis),
    end = Instant.ofEpochMilli(endMillis),
    zone = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault()),
    text = text,
    element = Element.fromStored(element)
)`
        }
      },
      {
        title: "Try it",
        body: `
          <ol>
            <li>Open <code>app/schemas/com.sumi.app.data.SumiDatabase/3.json</code> and find the <code>settings</code> table. Compare its <code>createSql</code> with <code>MIGRATION_2_3</code>.</li>
            <li>Why does <code>saveSettings</code> use an <code>UPDATE</code> of three columns instead of replacing the whole row? Hint: what would happen to <code>onboardedAt</code>?</li>
          </ol>`
      }
    ]
  },

  // ---------------------------------------------------------------------------
  {
    id: "time",
    kanji: "刻",
    element: "void",
    title: "Time logic",
    blurb: "Overlaps, midnight, gaps and the suggested range, as pure functions.",
    slides: [
      {
        title: "Pure functions for the hard parts",
        body: `
          <p>The trickiest code in Sumi is about time: overlapping entries, entries that cross midnight, suggesting a range. All of it lives in small objects with no Android code at all: <code>Intervals</code>, <code>TimeRange</code>, <code>RangeSuggestion</code>, <code>Timeline</code>, <code>Balance</code>.</p>
          <p>A <strong>pure function</strong> takes inputs and returns an output, touching nothing else. That makes it easy to test with exact times, including awkward ones like 23:44 to 00:29.</p>`,
        why: `Passing "now" and the time zone in as parameters, instead of reading the clock inside, is what lets a test say "pretend it is 11:20 on 13 September".`
      },
      {
        title: "Overlaps count once",
        body: `
          <p>An hour of work with five minutes of water inside it is an hour logged, not an hour and five minutes. <code>Intervals.covered</code> sorts stretches by start and merges any that overlap or touch.</p>`,
        diagram: `
          <svg class="intervals" viewBox="0 0 420 150" role="img" aria-label="Three overlapping stretches merge into one 90 minute run">
            <text x="0" y="16" class="lbl">09:00</text><text x="190" y="16" class="lbl">10:00</text><text x="365" y="16" class="lbl">10:30</text>
            <rect x="0" y="28" width="280" height="14" rx="7" fill="var(--fire)"/>
            <rect x="70" y="50" width="24" height="14" rx="7" fill="var(--water)"/>
            <rect x="233" y="72" width="187" height="14" rx="7" fill="var(--wind)"/>
            <rect x="0" y="112" width="420" height="14" rx="7" fill="var(--ink)"/>
            <text x="0" y="146" class="lbl">covered: 90 minutes, not 105</text>
          </svg>`,
        code: {
          file: "app/src/main/java/com/sumi/app/data/Intervals.kt",
          text: `for ((start, end) in intervals.filter { it.second.isAfter(it.first) }.sortedBy { it.first }) {
    val currentEnd = runEnd
    if (currentEnd == null || start.isAfter(currentEnd)) {
        // A clean break: bank the run so far and start a new one.
        if (runStart != null && currentEnd != null) total += Duration.between(runStart, currentEnd)
        runStart = start
        runEnd = end
    } else if (end.isAfter(currentEnd)) {
        // Overlapping or touching: extend the run rather than adding to it.
        runEnd = end
    }
}`
        }
      },
      {
        title: "Guessing from and to",
        body: `
          <p>When the composer opens, it suggests a range so most logs need no editing:</p>
          <ul>
            <li><strong>Your last entry ends now or later</strong> (you just logged): offer that same stretch again, so you can add a second element to it.</li>
            <li><strong>It ended within 3 hours</strong>: continue from where it ended until now.</li>
            <li><strong>Longer ago</strong> (for example overnight): one interval back from now.</li>
          </ul>`,
        code: {
          file: "app/src/main/java/com/sumi/app/data/RangeSuggestion.kt",
          text: `fun suggest(latestStart: Instant?, latestEnd: Instant?, now: Instant, interval: Duration): ClosedRange<Instant> {
    val end = now.truncatedTo(ChronoUnit.MINUTES)
    if (latestStart != null && latestEnd != null && !latestEnd.isBefore(end)) return latestStart..latestEnd
    if (latestEnd != null && Duration.between(latestEnd, end) <= MAX_CONTINUATION) return latestEnd..end
    return end.minus(interval)..end
}`
        }
      },
      {
        title: "A clock time has no date",
        body: `
          <p>The range picker gives two clock times, like 23:30 and 00:15. Which days are they on?</p>
          <p><code>TimeRange.resolve</code> puts the end on whichever day keeps it closest to where it was, then puts the start on the same day, or the day before if it would otherwise be at or after the end. So 23:30 to 00:15 reads as crossing midnight, and pulling an end back from 00:15 to 23:50 lands on the evening before instead of a day later.</p>`,
        code: {
          file: "app/src/main/java/com/sumi/app/data/TimeRange.kt",
          text: `val end = listOf(-1L, 0L, 1L)
    .map { anchor.plusDays(it).atTime(to).atZone(zone).toInstant() }
    .minBy { Duration.between(it, previousEnd).abs() }

val endDay = end.atZone(zone).toLocalDate()
var start = endDay.atTime(from).atZone(zone).toInstant()
if (!start.isBefore(end)) start = endDay.minusDays(1).atTime(from).atZone(zone).toInstant()`
        }
      },
      {
        title: "Gaps on the timesheet",
        body: `
          <p><code>Timeline.build</code> turns a day's entries into rows. Between two entries, a gap of at least <strong>5 minutes</strong> becomes an "unlogged" row you can tap to fill. Shorter gaps are just the day.</p>
          <p>There is no gap before the first entry (that was probably sleep), and a trailing gap up to now only appears on today. An entry crossing midnight is clipped to the day and says where it really began: "from 11:44 PM yesterday".</p>`,
        code: {
          file: "app/src/main/java/com/sumi/app/ui/today/Timeline.kt",
          text: `for (entry in entries.sortedBy { it.start }) {
    val shownStart = maxOf(entry.start, dayStart)
    val shownEnd = minOf(entry.end, dayEnd)
    if (!shownEnd.isAfter(shownStart)) continue

    cursor?.let { if (Duration.between(it, shownStart) >= MIN_GAP) rows += TimelineRow.Unlogged(it, shownStart) }
    rows += TimelineRow.Logged(entry, shownStart, shownEnd)
    cursor = if (cursor == null) shownEnd else maxOf(cursor, shownEnd)
}`
        }
      },
      {
        title: "Try it",
        body: `
          <ol>
            <li>Change <code>MIN_GAP</code> in <code>Timeline.kt</code> from 5 to 15 minutes. Run <code>./gradlew testDebugUnitTest</code>. Which test fails, and is the test or the change wrong?</li>
            <li>Write a new test in <code>SettingsAndRangeTest.kt</code>: a last entry that ended exactly 3 hours ago. Which branch of <code>suggest</code> should it take?</li>
          </ol>`
      }
    ]
  },

  // ---------------------------------------------------------------------------
  {
    id: "widget",
    kanji: "窓",
    element: "water",
    title: "The widget",
    blurb: "RemoteViews, instant redraws, thin glass, and words drawn in Mincho.",
    slides: [
      {
        title: "A widget is drawn by someone else",
        body: `
          <p>Your launcher draws widgets, not the app. The app sends a description of views called <strong>RemoteViews</strong>, which only allows a short list of view types and settings. No blur, no gradients, no custom fonts.</p>
          <p>Sumi builds its RemoteViews directly and hands them to <code>AppWidgetManager.updateAppWidget</code>, which reaches the launcher at once.</p>`,
        why: `Sumi first used Jetpack Glance, a library for writing widgets in a Compose style. Glance runs each update as a queued background session and worked out the widget's face once per session, so logging while a session was alive redrew the old face: the widget kept asking after you had answered. Drawing directly removed both the delay and the stale face, and made the app 600 KB smaller.`
      },
      {
        title: "Two layers: glass and text",
        body: `
          <p><strong>The glass</strong> is a bitmap (an image) drawn with Android's Canvas: one flat fill, a faint grain, one hairline edge. No shadows or bevels; an earlier version with them read as a raised object instead of a quiet surface.</p>
          <p><strong>The text</strong> sits on top as real views, because the clock has to be a <code>TextClock</code>. The system ticks a TextClock every minute at no cost, while an app redraws its widget only now and then.</p>`,
        code: {
          file: "app/src/main/java/com/sumi/app/widget/SumiWidget.kt",
          text: `return RemoteViews(context.packageName, R.layout.widget_root).apply {
    setImageViewBitmap(R.id.widget_glass, GlassRenderer.render(widthPx, heightPx, density, style))
    removeAllViews(R.id.widget_text)
    addView(R.id.widget_text, textLayer(context, face, style, today, sizeDp.width, sizeDp.height))
    setOnClickPendingIntent(R.id.widget_root, composerIntent(context))
}`
        }
      },
      {
        title: "Glass that follows the wallpaper",
        body: `
          <p>Apps cannot see the wallpaper's pixels, but they can ask for its colours. On Android 12 and later, those come with the system's own verdict on whether dark text reads on it, the same hint the launcher uses for icon labels.</p>
          <ul>
            <li><strong>Light wallpaper:</strong> pale glass at 22%, near-black ink.</li>
            <li><strong>Dark wallpaper:</strong> dark glass at 32%, paper-white ink.</li>
          </ul>
          <p>Android does not tell widgets when the wallpaper changes, so a new wallpaper shows on the next redraw.</p>`,
        code: {
          file: "app/src/main/java/com/sumi/app/widget/WallpaperTone.kt",
          text: `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    return colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
}
return ColorUtils.calculateLuminance(colors.primaryColor.toArgb()) > LIGHT_WALLPAPER_LUMINANCE`
        }
      },
      {
        title: "Mincho on a widget, the long way round",
        body: `
          <p>RemoteViews cannot set a typeface, so a TextView on a widget always uses a system font, and some phone makers swap even that for their own.</p>
          <p>Words that only change on a redraw, the question and the date, are drawn into small images using the app's Mincho, with a touch of extra stroke. The live clock stays a system TextClock. To turn the date over, the widget redraws just after midnight.</p>`,
        code: {
          file: "app/src/main/java/com/sumi/app/widget/InkText.kt",
          text: `val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = sizePx
    this.color = color
    typeface = typefaceFor(context, text, this)
    style = Paint.Style.FILL_AND_STROKE
    strokeWidth = sizePx * EXTRA_WEIGHT
}`
        }
      },
      {
        title: "Two faces: resting and asking",
        body: `
          <p><code>WidgetFace.compute</code> decides which face to show. It asks once your ask interval has passed since your last entry ended, never during quiet hours, and straight away if nothing has ever been logged.</p>
          <p>Below about 150 dp tall the widget uses side-by-side layouts; above it, stacked ones, with text sizes scaling to the space.</p>`,
        code: {
          file: "app/src/main/java/com/sumi/app/widget/WidgetFace.kt",
          text: `fun compute(latest: Entry?, settings: Settings, now: Instant, zone: ZoneId): WidgetFace {
    if (settings.isQuiet(now.atZone(zone).toLocalTime())) return Resting
    if (latest == null) return Asking(Prompts.DEFAULT)

    val due = dueAt(latest, settings)
    return if (now.isBefore(due)) Resting else Asking(Prompts.questionFor(due))
}`
        }
      },
      {
        title: "Try it",
        body: `
          <ol>
            <li>In <code>GlassRenderer.kt</code>, change the light glass fill alpha from 56 to 120. Run <code>./gradlew installDebug</code> and compare on a light wallpaper.</li>
            <li>Resize the widget from one row to two. Which layout files are used at each size?</li>
            <li>In <code>Prompts.kt</code>, how is the question chosen, and why does it not change on every redraw?</li>
          </ol>`
      }
    ]
  },

  // ---------------------------------------------------------------------------
  {
    id: "rhythm",
    kanji: "律",
    element: "wind",
    title: "Rhythm and alarms",
    blurb: "How the widget knows when to change, with no timer running.",
    slides: [
      {
        title: "No clock ticking in the background",
        body: `
          <p>A widget only changes when something redraws it. Sumi does not poll every minute. It works out the next moment the face ought to differ, and sets one alarm for then.</p>
          <p>The candidates are: the ask coming due, quiet hours starting, quiet hours ending, and midnight. The earliest one wins.</p>`,
        code: {
          file: "app/src/main/java/com/sumi/app/widget/Rhythm.kt",
          text: `val candidates = buildList {
    // The date on the widget is drawn, not ticked, so a new day needs a redraw.
    add(nextOccurrence(LocalTime.MIDNIGHT, now, zone))
    if (quietNow) {
        // Whatever is due has to wait; the only change coming is quiet ending.
        add(nextOccurrence(settings.quietEnd, now, zone))
    } else {
        if (hasQuietHours) add(nextOccurrence(settings.quietStart, now, zone))
        if (latestEnd != null) {
            val due = latestEnd.plus(settings.askInterval)
            if (due.isAfter(now)) add(due)
        }
    }
}
return candidates.min()`
        }
      },
      {
        title: "Exact when allowed, a window when not",
        body: `
          <p><strong>AlarmManager</strong> wakes an app at a set time. Since Android 12, exact alarms need permission. Sumi uses an exact alarm when it may, and otherwise a 10-minute window, which Android respects even while the phone dozes.</p>
          <p>Alarms fire 2 seconds after the boundary, so the redraw reliably sees the new state instead of racing it.</p>`,
        code: {
          file: "app/src/main/java/com/sumi/app/widget/Rhythm.kt",
          text: `val canBeExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
if (canBeExact) {
    alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
} else {
    alarms.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, WINDOW_MILLIS, operation)
}`
        },
        why: `A late question while the phone sleeps costs nothing: nobody is looking at the home screen then.`
      },
      {
        title: "When the alarm fires",
        body: `
          <p>A <strong>BroadcastReceiver</strong> is code Android runs when something happens. <code>RhythmReceiver</code> handles the alarm, and also reboots (which clear alarms), clock changes and time zone changes (which move every boundary).</p>
          <p><code>goAsync()</code> gives the receiver a little time to finish work on a background thread instead of being cut off immediately.</p>`,
        code: {
          file: "app/src/main/java/com/sumi/app/widget/Rhythm.kt",
          text: `override fun onReceive(context: Context, intent: Intent) {
    val pending = goAsync()
    val appContext = context.applicationContext
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            SumiWidget().updateAll(appContext)
            Rhythm.scheduleNext(appContext)
        } finally {
            pending.finish()
        }
    }
}`
        }
      },
      {
        title: "Self-healing",
        body: `
          <p>The next alarm is re-armed on every save, on every widget redraw, after a reboot, and after a clock change. If one is ever lost, the next redraw for any reason puts it back.</p>`,
        why: `Background work on Android is unreliable by design: phones kill apps to save battery. Re-arming often is cheaper than trying to guarantee one alarm survives.`
      },
      {
        title: "Try it",
        body: `
          <ol>
            <li>Remove the midnight line from <code>nextChange</code> and run the tests. Read the names of the tests that fail: they describe exactly what you broke.</li>
            <li>With quiet hours 23:00 to 07:00 and an ask due at 23:30, what does <code>nextChange</code> return at 22:50? Check against <code>RhythmTest.kt</code>.</li>
          </ol>`
      }
    ]
  },

  // ---------------------------------------------------------------------------
  {
    id: "composer",
    kanji: "筆",
    element: "fire",
    title: "The composer",
    blurb: "A translucent window, one state object, and ink that blooms.",
    slides: [
      {
        title: "A window over the home screen",
        body: `
          <p>Tapping the widget opens <code>ComposerActivity</code>, a translucent activity: the home screen stays visible behind a sheet that rises from the bottom.</p>
          <ul>
            <li><code>taskAffinity=""</code> and <code>excludeFromRecents</code> keep it out of the app's task, so logging never pulls the rest of Sumi forward.</li>
            <li><code>windowSoftInputMode=adjustResize</code> lifts the sheet above the keyboard.</li>
          </ul>`
      },
      {
        title: "One object holds the whole screen",
        body: `
          <p>Everything the composer shows is one data class in a StateFlow: the range, the text, the goals, the question, whether it is saving, and whether it is done. The screen draws from it; taps call ViewModel functions that <code>copy()</code> it with changes.</p>`,
        code: {
          file: "app/src/main/java/com/sumi/app/ui/composer/ComposerViewModel.kt",
          text: `data class ComposerState(
    val loading: Boolean = true,
    val editingId: Long? = null,
    val start: Instant = Instant.EPOCH,
    val end: Instant = Instant.EPOCH,
    val text: String = "",
    val element: Element? = null,
    val goals: List<Goal> = emptyList(),
    val question: String = Prompts.DEFAULT,
    val message: String? = null,
    val saving: Boolean = false,
    val done: Boolean = false
)`
        },
        why: `With a single state object there is no way for, say, the saving spinner and the error message to disagree. Every change produces a whole new consistent state.`
      },
      {
        title: "Three ways to log",
        body: `
          <ul>
            <li><strong>Tap a kanji:</strong> logs immediately with that element. One tap, no typing.</li>
            <li><strong>Type and press send:</strong> logs the text untagged (無).</li>
            <li><strong>Type, then tap a kanji:</strong> both the note and the element.</li>
          </ul>
          <p>The range reads "from" and "to". Tapping either opens one dialog with a From and To switch; setting From offers "Next: end time", so the end is never forgotten.</p>`
      },
      {
        title: "Motion with a purpose",
        body: `
          <ul>
            <li>The sheet rises in 260 ms. On closing, the keyboard goes down first and the sheet sinks with it over 300 ms, so nothing vanishes in a single frame.</li>
            <li>A tapped kanji blooms: its ink spreads outward and fades over 380 ms, with a confirming vibration.</li>
            <li>After saving, the sheet holds 240 ms so you see the bloom before it closes.</li>
            <li>If the phone's animations are turned off, the hold is skipped too.</li>
          </ul>
          <p>A <code>closing</code> flag makes the activity finish only once the exit animation has really run, never before the sheet was even shown.</p>`
      },
      {
        title: "Try it",
        body: `
          <ol>
            <li>In developer options, set animation scale to off. Log from the widget. What is different, and where in <code>ComposerScreen.kt</code> is that decided?</li>
            <li>Why is the text field <code>singleLine</code>? Try removing it and pressing Enter.</li>
          </ol>`
      }
    ]
  },

  // ---------------------------------------------------------------------------
  {
    id: "views",
    kanji: "衡",
    element: "earth",
    title: "Today and Balance",
    blurb: "A timesheet, a pentagon drawn on Canvas, and a dated 30-day grid.",
    slides: [
      {
        title: "Today is a list of rows",
        body: `
          <p>The Today tab observes the entries touching the chosen day and passes them through <code>Timeline.build</code>. Each row shows start and end, the kanji (or 無), the note or goal name, and the length.</p>
          <p>Unlogged gaps open the composer with that exact stretch filled in. Rows animate into place with <code>Modifier.animateItem()</code>.</p>`
      },
      {
        title: "Balance counts time per element",
        body: `
          <p><code>Balance.compute</code> clips each entry to a rolling window (the last 7 or 14 days) and totals time per element with overlaps merged.</p>
          <p>Logging the same element twice for one stretch counts once. Two different elements sharing a stretch each get all of it, because both really happened.</p>`,
        code: {
          file: "app/src/main/java/com/sumi/app/ui/balance/Balance.kt",
          text: `val slices = entries.mapNotNull { entry ->
    val start = maxOf(entry.start, windowStart)
    val end = minOf(entry.end, now)
    if (end.isAfter(start)) Triple(entry.element, start, end) else null
}
val perElement = Element.entries.associateWith { element ->
    Intervals.covered(slices.filter { it.first == element }.map { it.second to it.third })
}`
        }
      },
      {
        title: "The pentagon",
        body: `
          <p>Five spokes, one per element. Each spoke's length is that element's time relative to the most-used one. It is drawn on a Compose <code>Canvas</code>, with the kanji, your goal's name and the time at each tip.</p>
          <p>It grows from the centre over 700 ms when Balance opens, and each spoke glides to its new length over 520 ms when you switch windows. Before anything is logged it still shows, empty, so the screen explains itself.</p>`,
        why: `Relative length is a ratio, not a score. A short spoke is simply small; nothing is marked as behind.`
      },
      {
        title: "\"Quiet lately\"",
        body: `
          <p>Below the pentagon, one element may be named: "Water has been quiet lately". It only appears when there is history older than 3 days, some element has had time recently, and one element has had none in that time. If several qualify, the one with the least time is named.</p>`,
        why: `Without the 3-day rule, a brand-new user would be told four of their five goals are quiet on day one, which is technically true and completely unhelpful.`
      },
      {
        title: "Where you may be pushing too hard",
        body: `
          <p>The quiet note shows where to push. Its calm opposite shows where you may be pushing too hard, with one of two lines:</p>
          <ul>
            <li><strong>"Deep work has averaged more than 55 hours a week lately."</strong> The WHO and ILO found that 55 or more working hours a week raises the risk of stroke by about 35% and of dying from heart disease by about 17%.</li>
            <li><strong>"Deep work has taken more than half your time lately."</strong> Balance research measures balance as time shared across the parts of life that matter; more than half in one of five outweighs all the others together.</li>
          </ul>
          <p>Neither appears in the first days of use, and shares are only spoken of once at least 10 hours are logged.</p>`,
        code: {
          file: "app/src/main/java/com/sumi/app/ui/balance/Balance.kt",
          text: `val minutesPerWeek = time.toMinutes() * 7.0 / windowDays
if (minutesPerWeek > LONG_WEEK.toMinutes()) return Heavy(element, longWeeks = true)

val tagged = perElement.values.fold(Duration.ZERO, Duration::plus)
if (tagged >= MIN_TAGGED_FOR_SHARE && time.toMinutes() > tagged.toMinutes() * MAJORITY_SHARE) {
    return Heavy(element, longWeeks = false)
}`
        },
        why: `Both notes describe what happened and never judge it. A long week can be exactly what a deadline needed; Sumi only makes it visible.`
      },
      {
        title: "Looking back through every day",
        body: `
          <p>The 30-day grid is a window on a longer record. Tapping it opens a sheet of month calendars, newest first, that scrolls back to the first day you ever logged. Tapping a day closes the sheet and opens that day on Today.</p>
          <p>Whole months here, rather than a rolling window: this is for finding a particular day again, and people look for days by date. The weeks start on whichever day this phone's language starts them.</p>`,
        code: {
          file: "app/src/main/java/com/sumi/app/ui/balance/History.kt",
          text: `private fun dayOf(date: LocalDate, entries: List<Entry>, zone: ZoneId): HistoryDay {
    val (from, to) = SumiRepository.dayBounds(date, zone)
    val onDay = entries.filter { it.start.isBefore(to) && it.end.isAfter(from) }
    val clipped = onDay.map { maxOf(it.start, from) to minOf(it.end, to) }
    return HistoryDay(
        date = date,
        elementsTouched = onDay.mapNotNull { it.element }.distinct().size,
        logged = Intervals.covered(clipped)
    )
}`
        },
        why: `The whole log is only read while the sheet is open, so the rest of the app never carries every entry you have ever made around with it.`
      },
      {
        title: "The 30-day grid",
        body: `
          <p>Thirty dots, ten across, oldest first and today last with a ring. Each fills darker with how many of your five got any time that day. Each dot carries its day number, and the 1st of a month shows the month's name.</p>
          <p>It is rolling, like the pentagon, so it never empties at the start of a month.</p>`
      },
      {
        title: "Try it",
        body: `
          <ol>
            <li>In <code>BalanceTest.kt</code>, read "nothing is called quiet in the first days of use". Then find the line in <code>Balance.kt</code> it protects.</li>
            <li>Add a sixth spoke for untagged time. Why might that make the pentagon less useful, not more?</li>
          </ol>`
      }
    ]
  },

  // ---------------------------------------------------------------------------
  {
    id: "sheets",
    kanji: "紙",
    element: "fire",
    title: "Google Sheets sync",
    blurb: "Permission, plain HTTPS, month tabs and background work.",
    slides: [
      {
        title: "Your data, your Drive",
        body: `
          <p>Sync is optional. When connected, Sumi creates one spreadsheet, <em>Sumi Timesheet</em>, in your own Google Drive, with a tab per month. There is no Sumi server: the phone talks to Google directly.</p>
          <p>Sumi asks for a single permission, <code>drive.file</code>, which only lets an app see files it created itself. It cannot read anything else in your Drive.</p>`,
        why: `drive.file is a "non-sensitive" permission, which also means Google does not require a security review of the app before it can be used by the public.`
      },
      {
        title: "Asking permission: OAuth",
        body: `
          <p><strong>OAuth</strong> is the standard way an app gets permission to your Google data without ever seeing your password. Google shows its own consent screen and gives the app an <strong>access token</strong>, a pass that lasts about an hour.</p>
          <p>Sumi uses Google Play services' <code>AuthorizationClient</code>. When permission was already granted, it returns a fresh token silently; otherwise it returns a screen for the user. No client ID appears in the code: Google recognises the app by its package name and signing certificate.</p>`,
        code: {
          file: "app/src/main/java/com/sumi/app/sync/GoogleAuth.kt",
          text: `suspend fun authorize(context: Context, accountEmail: String? = null): AuthorizationResult {
    val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_FILE)))
        .apply { accountEmail?.let { setAccount(Account(it, GOOGLE_ACCOUNT_TYPE)) } }
        .build()
    return Identity.getAuthorizationClient(context).authorize(request).await()
}`
        }
      },
      {
        title: "Plain HTTPS instead of big libraries",
        body: `
          <p>Google offers Java client libraries for Sheets and Drive, but they add megabytes and need extra shrinker rules. Sumi makes only a handful of JSON calls, so it uses Android's built-in <code>HttpURLConnection</code> and <code>org.json</code> in a 60-line class, <code>GoogleHttp</code>.</p>
          <p>The whole release app stays under 3 MB.</p>`
      },
      {
        title: "Finding the sheet again",
        body: `
          <p>When Sumi creates the spreadsheet, it tags it with a hidden marker (<code>appProperties</code>: sumi = timesheet). After a reinstall, connecting searches Drive for that marker and reuses the sheet instead of making a second one.</p>
          <p>Each month tab's id is the month itself: September 2026 is tab <code>202609</code>. So a tab you rename is still found, and months stay newest first.</p>`,
        code: {
          file: "app/src/main/java/com/sumi/app/sync/TimesheetLayout.kt",
          text: `val FIND_QUERY = "appProperties has { key='$MARKER_KEY' and value='$MARKER_VALUE' } and trashed = false"

fun sheetIdFor(month: YearMonth): Int = month.year * 100 + month.monthValue`
        }
      },
      {
        title: "Rewrite a month, never patch a row",
        body: `
          <p>Every change marks its month "dirty" in the same database transaction as the change itself. A sync takes each dirty month, reads its entries, clears the tab's columns A to H and writes them again, all in one batch that Sheets applies completely or not at all.</p>
          <p>Patching single rows would mean tracking which row holds which entry, and rows shift when something is deleted. Rewriting has nothing to drift out of step, and running it twice gives the same sheet.</p>`,
        code: {
          file: "app/src/main/java/com/sumi/app/sync/SheetsSync.kt",
          text: `for (month in repository.dirtyMonths()) {
    // Taken off the queue before reading, so a change that lands while this
    // month is being written queues it again rather than being lost.
    repository.clearDirty(month)
    try {
        val requests = TimesheetLayout.rewriteMonth(month, repository.entriesForMonth(month), goals, tabs)
        if (requests.length() > 0) Timesheet.batchUpdate(http, spreadsheetId, requests)
    } catch (e: Throwable) {
        withContext(NonCancellable) { repository.markDirty(month) }
        throw e
    }
}`
        }
      },
      {
        title: "Real dates, safe notes",
        body: `
          <p>Dates and times are written as Sheets numbers with a display format, so they sort and add up. Sheets counts days from 30 December 1899, so 1 January 1970 is day 25569.</p>
          <p>Notes are written as plain strings, never as typed input. A note like <code>=SUM(A1)</code> stays text instead of becoming a formula.</p>`,
        code: {
          file: "app/src/main/java/com/sumi/app/sync/TimesheetLayout.kt",
          text: `.put(numberCell(start.toLocalDate().toEpochDay() + SHEETS_EPOCH_OFFSET, "DATE", "yyyy-mm-dd"))
.put(numberCell(start.toLocalTime().toSecondOfDay() / SECONDS_PER_DAY, "TIME", "hh:mm"))
.put(numberCell(end.toLocalTime().toSecondOfDay() / SECONDS_PER_DAY, "TIME", "hh:mm"))
.put(numberCell(ChronoUnit.MINUTES.between(entry.start, entry.end).toDouble()))
.put(textCell(elementLabel(entry.element)))
.put(textCell(entry.element?.let { goals.nameFor(it) }))
.put(textCell(entry.text))`
        }
      },
      {
        title: "Background work that survives",
        body: `
          <p><strong>WorkManager</strong> runs work that must happen eventually, even if the app is closed or the phone restarts. Sumi queues a sync 10 seconds after a change, only when online, retrying with growing gaps.</p>
          <ul>
            <li><strong>Expired token (401):</strong> drop it and retry with a new one.</li>
            <li><strong>Busy or down (429, 5xx), offline:</strong> retry later.</li>
            <li><strong>Access revoked or account removed:</strong> stop, and show Reconnect in Setup. No notifications.</li>
            <li><strong>Sheet deleted or binned:</strong> find or create another and fill every month.</li>
          </ul>`,
        why: `APPEND_OR_REPLACE queues a new sync behind one already running, so an entry saved mid-sync is never skipped.`
      },
      {
        title: "Try it",
        body: `
          <ol>
            <li>Connect Sheets, then log in airplane mode. What does Setup say, and which database table explains it?</li>
            <li>Delete the sheet in Drive and log something. Watch what Sumi does in the next minute.</li>
            <li>Read <code>SheetRowsTest.kt</code> and find the test that proves a formula-like note stays text.</li>
          </ol>`
      }
    ]
  },

  // ---------------------------------------------------------------------------
  {
    id: "intro",
    kanji: "迎",
    element: "void",
    title: "The introduction",
    blurb: "Seven pages on first launch, built from Setup's own parts.",
    slides: [
      {
        title: "Seven pages, every one skippable",
        body: `
          <ol>
            <li>墨 Sumi</li>
            <li>How it works</li>
            <li>Your five</li>
            <li>Rhythm</li>
            <li>Add the widget</li>
            <li>Google Sheets (optional)</li>
            <li>始 You are set</li>
          </ol>
          <p>Every choice on these pages is the real setting, saved as you make it, so skipping part-way keeps what you chose.</p>`
      },
      {
        title: "Remembered in the database",
        body: `
          <p>Whether the introduction was finished is a timestamp, <code>onboardedAt</code>, in the settings row (database version 3). While it is being read, the app shows a blank page for a moment rather than flashing the wrong screen.</p>
          <p>"Erase everything" clears it, so Sumi greets you again as if newly installed. Setup can replay it at any time.</p>`,
        code: {
          file: "app/src/main/java/com/sumi/app/MainActivity.kt",
          text: `val screen = when {
    onboarded == null -> Screen.WAITING
    onboarded == false || replayingIntroduction -> Screen.INTRODUCTION
    showSetup -> Screen.SETUP
    else -> Screen.HOME
}`
        }
      },
      {
        title: "Reuse, not copies",
        body: `
          <p>The goal rows, the rhythm tabs, the quiet hours buttons, the add-widget button and the Sheets section on these pages are the same composables and the same <code>SetupViewModel</code> that Setup uses.</p>`,
        why: `Two copies of the goal editor would eventually behave differently. One copy cannot.`
      },
      {
        title: "Pages that turn smoothly",
        body: `
          <ul>
            <li>Next and Back glide for 560 ms with easing, instead of a quick spring.</li>
            <li>Neighbouring pages are built ahead of time, so a turn never has to lay out five text fields in its first frames.</li>
            <li>Each page's content trails the page slightly and fades, so pages settle into place.</li>
            <li>The current dot stretches into a short stroke that glides with the page.</li>
          </ul>`,
        code: {
          file: "app/src/main/java/com/sumi/app/ui/onboarding/OnboardingScreen.kt",
          text: `Box(
    modifier = Modifier.graphicsLayer {
        val offset = pager.getOffsetDistanceInPages(index).coerceIn(-1f, 1f)
        translationX = offset * size.width * 0.35f
        alpha = 1f - abs(offset) * 0.85f
    }
)`
        }
      },
      {
        title: "Try it",
        body: `
          <ol>
            <li>In Setup, tap "Show the introduction again". Name a goal on page 3, then Skip. Is the name kept? Why?</li>
            <li>Set <code>beyondViewportPageCount</code> to 0 and turn pages quickly on a slow phone or emulator. Can you feel the difference?</li>
          </ol>`
      }
    ]
  },

  // ---------------------------------------------------------------------------
  {
    id: "build",
    kanji: "工",
    element: "gold",
    title: "Build and release",
    blurb: "Gradle, signing keys, R8 shrinking and the font subset.",
    slides: [
      {
        title: "Gradle builds the app",
        body: `
          <p><strong>Gradle</strong> is the build tool. <code>app/build.gradle.kts</code> describes the app; <code>gradle/libs.versions.toml</code>, the <strong>version catalog</strong>, lists every library and its version in one place.</p>
          <p>Useful commands, run from the project folder:</p>`,
        code: {
          file: "Terminal",
          lang: "text",
          text: `./gradlew testDebugUnitTest   run all unit tests
./gradlew installDebug        build and install a debug build on a connected phone
./gradlew assembleRelease     build the signed, shrunk release APK
./gradlew bundleRelease       build the AAB that Google Play asks for`
        }
      },
      {
        title: "App ID and namespace",
        body: `
          <p>The <strong>applicationId</strong>, <code>io.github.prsrwt.sumi</code>, is the app's permanent identity on phones and on Google Play. Once published it can never change.</p>
          <p>The <strong>namespace</strong>, <code>com.sumi.app</code>, is only where the code and its generated resource class live. The two do not have to match, which is why the app ID could change without moving a single source file.</p>`
      },
      {
        title: "Signing keys",
        body: `
          <p>Every Android app is signed with a private key. Phones only accept an update signed with the same key, so the key proves an update really comes from you.</p>
          <ul>
            <li>The release key lives in a <strong>keystore</strong> file outside the project, read by Gradle from <code>~/.sumi-signing/keystore.properties</code>. It is never committed.</li>
            <li>With Google Play App Signing, Google holds the key that signs what users install, and your key becomes an <strong>upload key</strong>. A lost upload key can be reset through Play support; a lost key without Play App Signing cannot be recovered.</li>
          </ul>`,
        why: `The keystore and its passwords are kept out of the repository and out of synced folders, so a public repo or a shared drive can never leak them.`
      },
      {
        title: "R8 shrinks the release",
        body: `
          <p><strong>R8</strong> removes unused code and resources and shortens names in release builds. Sumi's release APK is about 2.7 MB.</p>
          <p>Shortened names break anything looked up by name at runtime. Sumi stores elements by their enum name, like <code>FIRE</code>, so the enum is kept exactly as written:</p>`,
        code: {
          file: "app/proguard-rules.pro",
          lang: "text",
          text: `-keep enum com.sumi.app.data.Element { *; }`
        },
        why: `R8 problems never show in debug builds, which are not shrunk. That is why every release build was installed on a real phone before being called done.`
      },
      {
        title: "A font cut to size",
        body: `
          <p>Shippori Mincho covers thousands of characters and weighs about 8.7 MB per weight. Sumi only needs Latin letters and a few kanji, so the bundled fonts are <strong>subset</strong> with Python's fontTools to about 115 KB each.</p>
          <p>Adding a new kanji to the app means adding it to the subset too, or it draws as an empty box. The font's SIL Open Font License ships inside the app and is readable from Setup.</p>`
      },
      {
        title: "Try it",
        body: `
          <ol>
            <li>Run <code>./gradlew assembleRelease</code> and find the APK's size in <code>app/build/outputs/apk/release/</code>.</li>
            <li>Remove the keep rule from <code>proguard-rules.pro</code>, build release and install it over a copy with entries. What happens to the elements, and why?</li>
          </ol>`
      }
    ]
  },

  // ---------------------------------------------------------------------------
  {
    id: "tests",
    kanji: "試",
    element: "wind",
    title: "Tests",
    blurb: "What is tested, how, and what still needs a real phone.",
    slides: [
      {
        title: "73 tests, all on your computer",
        body: `
          <p>Sumi's tests are JVM unit tests: they run on your computer in seconds, without a phone. They cover the logic most likely to be subtly wrong:</p>
          <ul>
            <li><strong>RhythmTest</strong>: when the widget next changes, including quiet hours, midnight and daylight saving.</li>
            <li><strong>WidgetFaceTest</strong>: resting or asking.</li>
            <li><strong>SettingsAndRangeTest</strong>: quiet hours, suggested ranges, overlaps, midnight ranges.</li>
            <li><strong>TimelineTest</strong> and <strong>BalanceTest</strong>: gaps, clipping, totals, the quiet rule, the grid.</li>
            <li><strong>TimesheetLayoutTest</strong> and <strong>SheetRowsTest</strong>: tabs, rows, dates and formula-safe notes.</li>
          </ul>`
      },
      {
        title: "Test names are sentences",
        body: `
          <p>Kotlin allows function names in backticks, so each test reads as the rule it protects. When one fails, its name tells you what broke before you read any code.</p>`,
        code: {
          file: "app/src/test/java/com/sumi/app/ui/balance/BalanceTest.kt",
          text: `@Test
fun \`nothing is called quiet in the first days of use\`() {
    // Only today's history exists, so the other four are "untouched" only vacuously.
    val snapshot = compute(listOf(entry("2026-09-13T09:00", "2026-09-13T10:00", Element.FIRE)))
    assertNull(snapshot.quietElement)
}`
        }
      },
      {
        title: "What a phone is still needed for",
        body: `
          <p>Some things cannot be proven off a phone, so each change was also checked on an emulator or a real device:</p>
          <ul>
            <li>Database upgrades from the previous version with real data in it.</li>
            <li>How the widget actually looks on light and dark wallpapers.</li>
            <li>The release build after R8 shrinking.</li>
            <li>Google sign-in and a real sync, which need a real Google account.</li>
          </ul>`
      },
      {
        title: "Try it",
        body: `
          <ol>
            <li>Run <code>./gradlew testDebugUnitTest</code> and open <code>app/build/reports/tests/testDebugUnitTest/index.html</code> in a browser.</li>
            <li>Break something on purpose, for example make <code>Intervals.covered</code> add lengths without merging. Count how many tests fail and read their names.</li>
          </ol>`
      }
    ]
  },

  // ---------------------------------------------------------------------------
  {
    id: "glossary",
    kanji: "辞",
    element: "gold",
    title: "Glossary",
    blurb: "Every term in this guide, in plain words.",
    slides: [
      {
        title: "Android",
        body: `
          <dl class="terms">
            <dt>Activity</dt><dd>A window Android can open. Sumi has MainActivity and ComposerActivity.</dd>
            <dt>Manifest</dt><dd>AndroidManifest.xml: tells Android what the app contains and what it may do.</dd>
            <dt>Intent</dt><dd>A request to open or notify something, optionally carrying extra values.</dd>
            <dt>BroadcastReceiver</dt><dd>Code Android runs when an event happens, like an alarm or a reboot.</dd>
            <dt>AlarmManager</dt><dd>The system service that wakes an app at a set time.</dd>
            <dt>WorkManager</dt><dd>A library for background work that must eventually run, even after restarts.</dd>
            <dt>dp</dt><dd>Density-independent pixels: a size that looks the same on screens of different sharpness.</dd>
          </dl>`
      },
      {
        title: "Kotlin and Compose",
        body: `
          <dl class="terms">
            <dt>Coroutine</dt><dd>A lightweight task that can pause without blocking a thread.</dd>
            <dt>suspend</dt><dd>Marks a function that may pause, such as a database read.</dd>
            <dt>Flow / StateFlow</dt><dd>A stream of values over time / one that always holds a current value.</dd>
            <dt>ViewModel</dt><dd>Holds a screen's state and logic, and survives rotation.</dd>
            <dt>Composable</dt><dd>A function that describes UI for the current state.</dd>
            <dt>Recomposition</dt><dd>Compose calling a composable again because its state changed.</dd>
            <dt>Pure function</dt><dd>Returns a result from its inputs only, changing nothing else.</dd>
          </dl>`
      },
      {
        title: "Data and widget",
        body: `
          <dl class="terms">
            <dt>Room</dt><dd>Android's SQLite database library.</dd>
            <dt>Entity / DAO</dt><dd>A class that is a table / an interface that holds the queries.</dd>
            <dt>Transaction</dt><dd>Several writes that all happen or none do.</dd>
            <dt>Migration</dt><dd>SQL that upgrades an existing database to a new layout.</dd>
            <dt>Soft delete</dt><dd>Marking a row deleted with a timestamp instead of removing it.</dd>
            <dt>RemoteViews</dt><dd>The limited view description a launcher draws a widget from.</dd>
            <dt>AppWidgetManager</dt><dd>The system service an app sends its widget's RemoteViews to.</dd>
            <dt>TextClock</dt><dd>A widget view the system updates every minute.</dd>
          </dl>`
      },
      {
        title: "Google and release",
        body: `
          <dl class="terms">
            <dt>OAuth</dt><dd>The standard way to grant an app access to your account without sharing your password.</dd>
            <dt>Access token</dt><dd>A short-lived pass proving permission, about an hour long.</dd>
            <dt>Scope</dt><dd>One specific permission an app asks for, such as drive.file.</dd>
            <dt>Application ID</dt><dd>The app's permanent identity on phones and Google Play.</dd>
            <dt>Keystore</dt><dd>The file holding the private key that signs the app.</dd>
            <dt>R8</dt><dd>The tool that shrinks and optimises release builds.</dd>
            <dt>AAB</dt><dd>Android App Bundle, the upload format Google Play requires.</dd>
            <dt>ESM</dt><dd>Experience sampling: recording what you are doing when prompted, in the moment.</dd>
          </dl>`
      }
    ]
  }
];
