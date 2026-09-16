package com.sumi.app.ui.account

import android.app.Application
import androidx.compose.foundation.clickable
import com.sumi.app.data.Goal
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sumi.app.data.Activity
import com.sumi.app.data.Domain
import com.sumi.app.data.Domains
import com.sumi.app.data.Element
import com.sumi.app.data.SumiRepository
import com.sumi.app.ui.SumiFonts
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What one element holds: the parts of life under it, and the words inside those.
 *
 * Everything here is the user's own arrangement, so every action is theirs to
 * take and nothing rearranges itself. The one thing Sumi keeps in step is the
 * name on the pentagon, which is always the first domain of the element.
 */
data class ElementContents(
    val domains: List<Domain> = emptyList(),
    val words: List<Activity> = emptyList()
) {
    fun domainsOf(element: Element): List<Domain> =
        domains.filter { it.element == element }.sortedBy { it.position }

    fun wordsIn(domainId: Long): List<Activity> =
        words.filter { it.domainId == domainId }.sortedBy { it.name.lowercase() }
}

class DomainsViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = SumiRepository.get(app)

    val contents: StateFlow<ElementContents> =
        combine(repository.observeDomains(), repository.observeActivities()) { domains, words ->
            ElementContents(domains, words)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ElementContents())

    fun addDomain(name: String, element: Element) = launch { repository.addDomain(name, element) }

    fun renameDomain(id: Long, name: String) = launch { repository.renameDomain(id, name) }

    fun makeFirst(id: Long) = launch { repository.makeFirstDomain(id) }

    fun removeDomain(id: Long) = launch { repository.removeDomain(id) }

    fun moveWord(id: Long, toDomainId: Long) = launch { repository.moveActivity(id, toDomainId) }

    fun removeWord(id: Long) = launch { repository.removeActivity(id) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElementSheet(
    element: Element,
    onDismiss: () -> Unit,
    viewModel: DomainsViewModel = viewModel()
) {
    val contents by viewModel.contents.collectAsStateWithLifecycle()
    val domains = contents.domainsOf(element)
    var adding by rememberSaveable { mutableStateOf(false) }
    var openWordsOf by rememberSaveable { mutableStateOf<Long?>(null) }
    var renaming by rememberSaveable { mutableStateOf<Long?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.72f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = element.kanji,
                    color = Color(element.color),
                    fontSize = 30.sp,
                    fontFamily = SumiFonts.mincho,
                    modifier = Modifier.padding(end = 14.dp)
                )
                Column {
                    Text("What goes here", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = element.affinity,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "The first one is the name on the pentagon. Anything logged here counts towards " +
                    "this spoke, whichever part of your life it came from.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
            )

            domains.forEachIndexed { index, domain ->
                DomainRow(
                    domain = domain,
                    first = index == 0,
                    words = contents.wordsIn(domain.id).size,
                    onOpenWords = { openWordsOf = domain.id },
                    onMakeFirst = { viewModel.makeFirst(domain.id) },
                    onRename = { renaming = domain.id },
                    onRemove = { viewModel.removeDomain(domain.id) }
                )
                if (index < domains.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                }
            }

            if (domains.isEmpty()) {
                Text(
                    text = "Nothing here yet. Add the parts of your life that belong to this one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }

            TextButton(onClick = { adding = true }, modifier = Modifier.padding(top = 4.dp)) {
                Text("Add a part of life")
            }
        }
    }

    if (adding) {
        AddDomainSheet(
            element = element,
            taken = domains.map { it.name.lowercase() }.toSet(),
            onAdd = {
                viewModel.addDomain(it, element)
                adding = false
            },
            onDismiss = { adding = false }
        )
    }

    renaming?.let { id ->
        val domain = domains.firstOrNull { it.id == id }
        if (domain == null) {
            renaming = null
        } else {
            RenameDialog(
                current = domain.name,
                onDone = {
                    viewModel.renameDomain(id, it)
                    renaming = null
                },
                onDismiss = { renaming = null }
            )
        }
    }

    openWordsOf?.let { id ->
        val domain = contents.domains.firstOrNull { it.id == id }
        if (domain == null) {
            openWordsOf = null
        } else {
            WordsSheet(
                domain = domain,
                words = contents.wordsIn(id),
                others = contents.domains.filter { it.id != id },
                onMove = { wordId, toId -> viewModel.moveWord(wordId, toId) },
                onRemove = { wordId -> viewModel.removeWord(wordId) },
                onDismiss = { openWordsOf = null }
            )
        }
    }
}

@Composable
private fun DomainRow(
    domain: Domain,
    first: Boolean,
    words: Int,
    onOpenWords: () -> Unit,
    onMakeFirst: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpenWords)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(domain.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = when {
                    first && words == 0 -> "the name on the pentagon"
                    first -> "the name on the pentagon · $words ${wordWord(words)}"
                    words == 0 -> "no words yet"
                    else -> "$words ${wordWord(words)}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More for ${domain.name}")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Words inside") },
                    onClick = {
                        menu = false
                        onOpenWords()
                    }
                )
                if (!first) {
                    DropdownMenuItem(
                        text = { Text("Use as the name") },
                        onClick = {
                            menu = false
                            onMakeFirst()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = {
                        menu = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Remove") },
                    onClick = {
                        menu = false
                        onRemove()
                    }
                )
            }
        }
    }
}

/**
 * The words inside one part of life, and the one thing worth warning about:
 * moving a word takes everything logged with it into the other domain.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WordsSheet(
    domain: Domain,
    words: List<Activity>,
    others: List<Domain>,
    onMove: (Long, Long) -> Unit,
    onRemove: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var moving by rememberSaveable { mutableStateOf<Long?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.72f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(domain.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "The words you have used here. They arrive as you log, and stay yours to move.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            if (words.isEmpty()) {
                Text(
                    text = "Nothing yet. Type a word while logging and Sumi will offer to keep it here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            words.forEachIndexed { index, word ->
                var menu by remember(word.id) { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(word.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = if (word.uses == 0) "not logged yet" else "logged ${word.uses} ${timesWord(word.uses)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More for ${word.name}")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (others.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Move somewhere else") },
                                    onClick = {
                                        menu = false
                                        moving = word.id
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Remove") },
                                onClick = {
                                    menu = false
                                    onRemove(word.id)
                                }
                            )
                        }
                    }
                }
                if (index < words.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                }
            }
        }
    }

    moving?.let { wordId ->
        val word = words.firstOrNull { it.id == wordId }
        if (word == null) {
            moving = null
        } else {
            MoveWordDialog(
                word = word.name,
                from = domain,
                others = others,
                onMove = {
                    onMove(wordId, it)
                    moving = null
                },
                onDismiss = { moving = null }
            )
        }
    }
}

/** Says plainly what moving does before it happens, since the log moves too. */
@Composable
private fun MoveWordDialog(
    word: String,
    from: Domain,
    others: List<Domain>,
    onMove: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var chosen by remember { mutableStateOf<Domain?>(null) }
    val target = chosen

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move $word") },
        text = {
            Column {
                Text(
                    text = if (target == null) {
                        "A word lives in one place. Choose where $word should go, and everything " +
                            "logged with it goes too."
                    } else {
                        "This takes $word out of ${from.name} and puts it in ${target.name}, with " +
                            "everything you logged with it. The pentagon does not change: those hours " +
                            "stay on the element you logged them to."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                others.forEach { other ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { chosen = other }
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = other.element.kanji,
                            color = Color(other.element.color),
                            fontFamily = SumiFonts.mincho,
                            fontSize = 17.sp,
                            modifier = Modifier.padding(end = 10.dp)
                        )
                        Text(
                            text = other.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (other.id == target?.id) MaterialTheme.colorScheme.onBackground
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { target?.let { onMove(it.id) } }, enabled = target != null) {
                Text("Move it")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Leave it") } }
    )
}

/** Adding a part of life: what this element usually holds, or a word of your own. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDomainSheet(
    element: Element,
    taken: Set<String>,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var writing by rememberSaveable { mutableStateOf(false) }
    var typed by rememberSaveable { mutableStateOf("") }
    val offered = Domains.common.filter { it.name.lowercase() !in taken }
    val here = offered.filter { it.element == element }
    val elsewhere = offered.filter { it.element != element }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.72f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text("Add to ${element.kanji}", style = MaterialTheme.typography.titleMedium)

            if (writing) {
                Text(
                    text = "Wide enough to hold many things. A part of your life grows over a year; " +
                        "a single activity stays a sliver.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    placeholder = { Text(element.displayName) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
                Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { writing = false }) { Text("Back to the list") }
                    Box(modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { onAdd(typed) }, enabled = typed.isNotBlank()) {
                        Text("Add it")
                    }
                }
            } else {
                Suggestions(here, onAdd)
                if (elsewhere.isNotEmpty()) {
                    Text(
                        text = "From other elements, if this is where it belongs in your life",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)
                    )
                    Suggestions(elsewhere, onAdd)
                }
                TextButton(onClick = { writing = true }, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Write my own")
                }
            }
        }
    }
}

@Composable
private fun Suggestions(ideas: List<com.sumi.app.data.DomainIdea>, onAdd: (String) -> Unit) {
    ideas.forEachIndexed { index, idea ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onAdd(idea.name) }
                .padding(vertical = 9.dp, horizontal = 6.dp)
        ) {
            Column {
                Text(idea.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = idea.holds,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (index < ideas.lastIndex) {
            HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
        }
    }
}

@Composable
private fun RenameDialog(current: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onDone(typed) }, enabled = typed.isNotBlank()) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep it") } }
    )
}

private fun wordWord(count: Int) = if (count == 1) "word" else "words"

private fun timesWord(count: Int) = if (count == 1) "time" else "times"

/**
 * One of the five, as a row: its element, the name on the pentagon, and the parts
 * of life underneath. Tapping it opens what that element holds; tapping the kanji
 * moves the element itself between goals.
 */
@Composable
fun ElementRow(
    goal: Goal,
    goals: List<Goal>,
    onElement: (Element) -> Unit,
    viewModel: DomainsViewModel = viewModel()
) {
    val contents by viewModel.contents.collectAsStateWithLifecycle()
    val here = contents.domainsOf(goal.element)
    var menuOpen by remember { mutableStateOf(false) }
    var sheetOpen by rememberSaveable(goal.element) { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { sheetOpen = true }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .clickable { menuOpen = true },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = goal.element.kanji,
                    color = Color(goal.element.color),
                    fontSize = 26.sp,
                    fontFamily = SumiFonts.mincho
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                Element.entries.forEach { element ->
                    DropdownMenuItem(
                        text = { Text("${element.kanji}  ${element.displayName}", fontFamily = SumiFonts.mincho) },
                        onClick = {
                            menuOpen = false
                            onElement(element)
                        }
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                text = goal.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            // The first domain is already the name above, so the line underneath
            // says what else is here, or what this one holds when it stands alone.
            Text(
                text = when {
                    here.isEmpty() -> "nothing here yet"
                    here.size == 1 -> Domains.holdsFor(here.first().name) ?: "one part of your life"
                    else -> "with " + here.drop(1).joinToString(", ") { it.name }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    HorizontalDivider(
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
        modifier = Modifier.padding(start = 50.dp)
    )

    if (sheetOpen) {
        ElementSheet(element = goal.element, onDismiss = { sheetOpen = false })
    }
}
