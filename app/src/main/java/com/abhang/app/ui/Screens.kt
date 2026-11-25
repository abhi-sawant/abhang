package com.abhang.app.ui

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abhang.app.R
import com.abhang.app.data.Abhang
import com.abhang.app.data.AbhangRepository
import com.abhang.app.data.ReadingStore
import com.abhang.app.data.SearchOutcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

private const val BASE_FONT = 22f
private const val MIN_FONT = 14f
private const val MAX_FONT = 44f

/** Follow the system font size (a user preference) but never past this, so layouts survive. */
private const val MAX_SYSTEM_SCALE = 1.4f
/** Upper bound on system scale times the in-app size step. */
private const val MAX_TOTAL_SCALE = 2.4f
/** Wide windows and landscape: reading text stays at a comfortable measure. */
private val MAX_CONTENT_WIDTH = 640.dp

/** Outlined star (material-icons-core only ships the filled shape, and its Outlined.Star is identical). */
private val StarOutline: ImageVector = ImageVector.Builder(
    name = "StarOutline", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
).addPath(
    pathData = addPathNodes("M22,9.24l-7.19,-0.62L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21 12,17.27 18.18,21l-1.63,-7.03L22,9.24zM12,15.4l-3.76,2.27 1,-4.28 -3.32,-2.88 4.38,-0.38L12,6.1l1.71,4.04 4.38,0.38 -3.32,2.88 1,4.28L12,15.4z"),
    fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd,
).build()

/** Microphone (not in material-icons-core). */
private val MicIcon: ImageVector = ImageVector.Builder(
    name = "Mic", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
).addPath(
    pathData = addPathNodes("M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3zM17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11H5c0,3.41 2.72,6.23 6,6.72V21h2v-3.28c3.28,-0.48 6,-3.3 6,-6.72h-1.7z"),
    fill = SolidColor(Color.Black),
).build()

// ---------------------------------------------------------------------------------------------
// Shared frame
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BarScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    // Font size is a single persisted app-wide setting, shown in every header: readers need it within reach.
    val prefs = LocalContext.current.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
    var fontSize by remember { mutableFloatStateOf(prefs.getFloat("font_size", BASE_FONT)) }
    fun setFont(v: Float) { fontSize = v.coerceIn(MIN_FONT, MAX_FONT); prefs.edit().putFloat("font_size", fontSize).apply() }
    val density = LocalDensity.current
    val scale = (density.fontScale.coerceAtMost(MAX_SYSTEM_SCALE) * fontSize / BASE_FONT).coerceAtMost(MAX_TOTAL_SCALE)
    val scaled = Density(density.density, scale)
    val smaller = stringResource(R.string.font_smaller)
    val larger = stringResource(R.string.font_larger)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { setFont(fontSize - 2f) }, enabled = fontSize > MIN_FONT,
                        modifier = Modifier.semantics { contentDescription = smaller }) {
                        Text("अ−", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(onClick = { setFont(fontSize + 2f) }, enabled = fontSize < MAX_FONT,
                        modifier = Modifier.semantics { contentDescription = larger }) {
                        Text("अ+", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    }
                    actions()
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BarColors.container(),
                    titleContentColor = BarColors.content(),
                    navigationIconContentColor = BarColors.content(),
                    actionIconContentColor = BarColors.content(),
                ),
            )
        },
    ) { padding -> CompositionLocalProvider(LocalDensity provides scaled) { content(Modifier.padding(padding)) } }
}

@Composable
private fun AppField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Search,
    onVoice: (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        modifier = modifier,
        placeholder = { Text(placeholder) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = when {
            value.isNotEmpty() -> {
                { IconButton(onClick = { onValueChange("") }) { Icon(Icons.Filled.Close, stringResource(R.string.clear)) } }
            }
            onVoice != null -> {
                { IconButton(onClick = onVoice) { Icon(MicIcon, stringResource(R.string.voice_search), tint = cs.primary) } }
            }
            else -> null
        },
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }, onDone = { keyboard?.hide() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = cs.surfaceVariant, unfocusedContainerColor = cs.surfaceVariant,
            focusedBorderColor = cs.primary, unfocusedBorderColor = cs.outline,
            cursorColor = cs.primary,
            focusedLeadingIconColor = cs.primary, unfocusedLeadingIconColor = cs.primary,
            focusedPlaceholderColor = cs.onSurfaceVariant, unfocusedPlaceholderColor = cs.onSurfaceVariant,
        ),
    )
}

/** One abhang in a list: title, author. */
@Composable
private fun AbhangRow(a: Abhang, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, end = 28.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(a.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(a.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

// ---------------------------------------------------------------------------------------------
// Home
// ---------------------------------------------------------------------------------------------

@Composable
private fun HomeRow(
    onClick: () -> Unit,
    title: String,
    desc: String,
    leading: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) { leading() }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(desc, style = MaterialTheme.typography.bodyMedium)
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun HomeScreen(
    repo: AbhangRepository,
    store: ReadingStore,
    onCategory: (Int) -> Unit,
    onSearch: () -> Unit,
    onAbout: () -> Unit,
    onFavorites: () -> Unit,
    onAbhang: (Int) -> Unit,
) {
    val last = repo.abhangOrNull(store.lastRead)
    BarScaffold(
        stringResource(R.string.app_name),
        actions = {
            IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, stringResource(R.string.search)) }
        },
    ) { mod ->
        LazyColumn(mod.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary) }
            if (last != null) item(key = "continue") {
                Card(
                    onClick = { onAbhang(last.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.continue_reading), style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(last.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(last.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            if (store.favorites.isNotEmpty()) item(key = "favorites") {
                HomeRow(
                    onClick = onFavorites,
                    title = stringResource(R.string.favorites),
                    desc = stringResource(R.string.favorites_desc),
                    leading = { Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.primary) },
                    trailing = { CountLabel(store.favorites.size) },
                )
            }
            items(repo.categories, key = { it.id }) { c ->
                HomeRow(
                    onClick = { onCategory(c.id) },
                    title = c.name,
                    desc = c.description,
                    leading = { Text("${c.id}".toDevanagariDigits(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                    trailing = { CountLabel(repo.count(c.id)) },
                )
            }
            // Sources and the text-quality notice live in the flow of the list, like one more category.
            item(key = "about") {
                HomeRow(
                    onClick = onAbout,
                    title = stringResource(R.string.about),
                    desc = stringResource(R.string.about_row_desc),
                    leading = { Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary) },
                )
            }
        }
    }
}

@Composable
private fun CountLabel(n: Int) {
    val unit = stringResource(R.string.count_unit)
    val spoken = stringResource(R.string.count_format, n.toString().toDevanagariDigits())
    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = spoken }, horizontalAlignment = Alignment.End) {
        Text(n.toString().toDevanagariDigits(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}

// ---------------------------------------------------------------------------------------------
// Category list
// ---------------------------------------------------------------------------------------------

@Composable
fun CategoryScreen(repo: AbhangRepository, categoryId: Int, onBack: () -> Unit, onAbhang: (Int) -> Unit) {
    val category = repo.category(categoryId)
    val total = repo.count(categoryId)
    var query by rememberSaveable { mutableStateOf("") }
    var items by remember { mutableStateOf(repo.abhangsIn(categoryId)) }
    val listState = rememberLazyListState()
    LaunchedEffect(query) {
        items = repo.filterCategory(categoryId, query)
        if (query.isNotBlank()) listState.scrollToItem(0)
    }
    val focus = LocalFocusManager.current
    BarScaffold(category.name, onBack) { mod ->
        Column(mod.fillMaxSize()) {
            if (total > 15) {
                AppField(query, { query = it }, stringResource(R.string.filter_hint),
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), ImeAction.Done)
            }
            if (total == 0) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.empty_category), style = MaterialTheme.typography.bodyLarge)
                }
            } else if (items.isEmpty()) {
                Text(stringResource(R.string.no_results), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
            } else {
                FastScrollList(listState, Modifier.fillMaxSize()) {
                    items(items, key = { it.id }) { a -> AbhangRow(a) { focus.clearFocus(); onAbhang(a.id) } }
                }
            }
        }
    }
}

/** A lazy list with a draggable thumb on the right edge, for the 4,000-item category. */
@Composable
private fun FastScrollList(state: LazyListState, modifier: Modifier, content: LazyListScope.() -> Unit) {
    val total by remember { derivedStateOf { state.layoutInfo.totalItemsCount } }
    Box(modifier) {
        LazyColumn(Modifier.fillMaxSize(), state = state, content = content)
        if (total > 60) Scroller(state, total, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

@Composable
private fun Scroller(state: LazyListState, total: Int, modifier: Modifier) {
    var heightPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val thumbPx = with(LocalDensity.current) { 56.dp.toPx() }
    val scope = rememberCoroutineScope()
    val fraction by remember {
        derivedStateOf {
            val visible = state.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
            val span = (total - visible).coerceAtLeast(1)
            (state.firstVisibleItemIndex.toFloat() / span).coerceIn(0f, 1f)
        }
    }
    val alpha by animateFloatAsState(if (state.isScrollInProgress || dragging) 1f else 0.4f, label = "thumb")
    Box(
        modifier.width(24.dp).onSizeChanged { heightPx = it.height.toFloat() }
            .pointerInput(total) {
                detectVerticalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    val f = ((change.position.y - thumbPx / 2) / (heightPx - thumbPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    scope.launch { state.scrollToItem((f * (total - 1)).toInt()) }
                }
            },
    ) {
        Box(
            Modifier.align(Alignment.TopEnd)
                .offset { IntOffset(0, (fraction * (heightPx - thumbPx)).toInt()) }
                .padding(end = 4.dp).width(6.dp).height(56.dp)
                .background(Saffron.copy(alpha = alpha), RoundedCornerShape(3.dp)),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Reading view
// ---------------------------------------------------------------------------------------------

private data class VerseBlock(val lines: List<String>, val refrain: Boolean)

private val verseEnd = Regex("॥\\s*[०-९0-9]+\\s*॥\\s*$")
private val refrainEnd = Regex("॥\\s*ध्रु\\.?\\s*॥\\s*$")
private val markerSpan = Regex("॥[^॥]*॥")
private val caesura = Regex("\\s+।\\s+")
private val trailingMark = Regex("\\s+(।|॥[^॥]*॥|॥)$")
private const val NBSP = " "

/** Groups the stored lines into verses: a verse ends at its ॥n॥ number, a refrain at ॥ध्रु.॥. Display only. */
private fun parseVerse(body: String): List<VerseBlock> {
    val blocks = mutableListOf<VerseBlock>()
    var cur = mutableListOf<String>()
    for (raw in body.lines()) {
        val line = raw.trim()
        if (line.isEmpty()) continue
        cur.add(line)
        when {
            refrainEnd.containsMatchIn(line) -> { blocks.add(VerseBlock(cur, true)); cur = mutableListOf() }
            verseEnd.containsMatchIn(line) -> { blocks.add(VerseBlock(cur, false)); cur = mutableListOf() }
        }
    }
    if (cur.isNotEmpty()) blocks.add(VerseBlock(cur, false))
    return blocks
}

/** Breaks a stored line at the । caesura and glues each ।/॥n॥ mark to the word before it so it never strands. */
private fun displayLines(line: String): List<String> {
    val parts = line.split(caesura).filter { it.isNotBlank() }
    val out = if (parts.size >= 2) parts.mapIndexed { i, p -> if (i < parts.lastIndex) p.trim() + NBSP + "।" else p.trim() } else listOf(line.trim())
    // A word joiner inside ॥…॥ stops the marker (e.g. ॥ध्रु.॥) from splitting across lines at large sizes.
    return out.map { it.replace(trailingMark, NBSP + "$1").replace(markerSpan) { m -> "॥⁠" + m.value.drop(1).dropLast(1) + "⁠॥" } }
}

private fun styledLine(text: String, markerColor: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    markerSpan.findAll(text).forEach { addStyle(SpanStyle(color = markerColor), it.range.first, it.range.last + 1) }
}

@Composable
private fun VerseBody(body: String) {
    val blocks = remember(body) { parseVerse(body) }
    val marker = MaterialTheme.colorScheme.primary
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            blocks.forEach { b ->
                val lines: @Composable () -> Unit = {
                    Column {
                        b.lines.forEach { line ->
                            displayLines(line).forEach {
                                Text(styledLine(it, marker), fontSize = BASE_FONT.sp, lineHeight = (BASE_FONT * 1.6f).sp)
                            }
                        }
                    }
                }
                if (b.refrain) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(12.dp),
                    ) { Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) { lines() } }
                } else lines()
            }
        }
    }
}

@Composable
private fun Provenance(source: String, onAbout: () -> Unit) {
    val auto = source.contains("स्वयंचलित")
    val check = source.contains("पाठ तपासणे आवश्यक")
    val verified = source.contains("(तपासले)")
    if (verified) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.verified), Modifier.padding(start = 4.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
    if (!auto && !check) return
    val title = stringResource(if (auto) R.string.prov_auto_title else R.string.prov_check_title)
    val hint = stringResource(if (auto) R.string.prov_auto_hint else R.string.prov_check_hint)
    Surface(
        onClick = onAbout,
        modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Info, null, Modifier.padding(top = 2.dp).size(20.dp))
            Column(Modifier.padding(start = 10.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(hint, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun DetailScreen(repo: AbhangRepository, store: ReadingStore, id: Int, onBack: () -> Unit, onAbout: () -> Unit) {
    val a = repo.abhang(id)
    val context = LocalContext.current
    val view = LocalView.current
    // Singers read with the phone in hand for a long time: the screen stays on while an abhang is open.
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(id) { store.markRead(id) }
    val isFav = id in store.favorites
    val footer = if (a.num > 0) "\n" + stringResource(R.string.share_footer, a.num.toString().toDevanagariDigits()) else ""
    BarScaffold(
        "", onBack,
        actions = {
            IconButton(onClick = { store.toggleFavorite(id) }) {
                Icon(
                    if (isFav) Icons.Filled.Star else StarOutline,
                    stringResource(if (isFav) R.string.favorite_remove else R.string.favorite_add),
                )
            }
            IconButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "${a.title}\n\n${a.body}\n\n— ${a.author}$footer")
                }
                context.startActivity(Intent.createChooser(send, null))
            }) { Icon(Icons.Filled.Share, stringResource(R.string.share)) }
        },
    ) { mod ->
        Box(mod.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxWidth().padding(16.dp)) {
                Text(repo.category(a.categoryId).name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(a.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(a.author, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
                val clean = a.source.replace(Regex("\\s*\\([^)]*\\)\\s*$"), "")
                if (clean.isNotBlank()) Text(clean, style = MaterialTheme.typography.bodySmall)
                Provenance(a.source, onAbout)
                Spacer(Modifier.height(20.dp))
                VerseBody(a.body)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Favourites
// ---------------------------------------------------------------------------------------------

@Composable
fun FavoritesScreen(repo: AbhangRepository, store: ReadingStore, onBack: () -> Unit, onAbhang: (Int) -> Unit) {
    val items = store.favorites.mapNotNull { repo.abhangOrNull(it) }
    BarScaffold(stringResource(R.string.favorites), onBack) { mod ->
        if (items.isEmpty()) {
            Box(mod.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.favorites_empty), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(mod.fillMaxSize()) { items(items, key = { it.id }) { a -> AbhangRow(a) { onAbhang(a.id) } } }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Search
// ---------------------------------------------------------------------------------------------

@Composable
fun SearchScreen(repo: AbhangRepository, onBack: () -> Unit, onAbhang: (Int) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var outcome by remember { mutableStateOf(SearchOutcome(emptyList(), 0)) }
    var done by remember { mutableStateOf(false) } // true once `outcome` belongs to the current query
    LaunchedEffect(query) {
        done = false
        if (query.isBlank()) { outcome = SearchOutcome(emptyList(), 0); return@LaunchedEffect }
        delay(150) // debounce while typing
        outcome = repo.searchWithTotal(query)
        done = true
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val cs = MaterialTheme.colorScheme
    val results = outcome.results
    // Voice search via the system recognizer; null (mic hidden) when the device has none.
    val onVoice = rememberVoiceSearch { spoken -> query = spoken; keyboard?.hide() }
    BarScaffold(stringResource(R.string.search), onBack) { mod ->
        Column(mod.fillMaxSize()) {
            AppField(query, { query = it }, stringResource(R.string.search_hint), Modifier.fillMaxWidth().padding(12.dp).focusRequester(focus), onVoice = onVoice)
            when {
                query.isBlank() -> SearchExamples { query = it }
                !done -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 12.dp), color = cs.primary, trackColor = cs.primaryContainer)
                    Text(stringResource(R.string.searching), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                }
                results.isEmpty() -> Text(stringResource(R.string.no_results), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                else -> {
                    val hlBg = cs.primaryContainer
                    val hlFg = cs.onPrimaryContainer
                    fun highlight(text: String): AnnotatedString = buildAnnotatedString {
                        append(text)
                        repo.highlights(text, query).forEach {
                            addStyle(SpanStyle(background = hlBg, color = hlFg, fontWeight = FontWeight.Bold), it.first, it.last + 1)
                        }
                    }
                    // Show what the Latin spelling was understood as, from the best result's first matching word.
                    val echo = remember(results, query) {
                        if (isLatinQuery(query)) {
                            val top = results.first()
                            firstPhrase(top.abhang.title, repo.highlights(top.abhang.title, query))
                                ?: firstPhrase(top.snippet, repo.highlights(top.snippet, query))
                        } else null
                    }
                    if (echo != null) Text(stringResource(R.string.search_echo, query.trim(), echo), Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodyMedium, color = cs.primary, fontWeight = FontWeight.SemiBold)
                    if (outcome.total > results.size) Text(
                        stringResource(R.string.search_capped, outcome.total.toString().toDevanagariDigits(), results.size.toString().toDevanagariDigits()),
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    )
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(results, key = { it.abhang.id }) { r ->
                            Column(
                                Modifier.fillMaxWidth()
                                    .clickable(role = Role.Button) { keyboard?.hide(); focusManager.clearFocus(); onAbhang(r.abhang.id) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Text(highlight(r.abhang.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(highlight(r.snippet), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${r.categoryName} • ${r.abhang.author}", style = MaterialTheme.typography.bodySmall, color = cs.secondary)
                            }
                            HorizontalDivider(color = cs.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The first highlighted word together with any highlighted words right after it (only spaces between),
 * so a joined query like "sundarte" echoes as "सुंदर तें" rather than just "सुंदर".
 */
private fun firstPhrase(text: String, ranges: List<IntRange>): String? {
    val first = ranges.firstOrNull() ?: return null
    var end = first.last
    for (r in ranges.drop(1)) {
        if (text.substring(end + 1, r.first).isBlank()) end = r.last else break
    }
    return text.substring(first.first, end + 1)
}

private fun isLatinQuery(q: String) = q.isNotBlank() && !com.abhang.app.data.PhoneticKey.hasDevanagari(q)

@Composable
private fun SearchExamples(onPick: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.search_examples), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("vitthal", "tukaram", "pandhari", "हरिपाठ").forEach { AssistChip(onClick = { onPick(it) }, label = { Text(it) }) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// About
// ---------------------------------------------------------------------------------------------

@Composable
private fun AboutSection(title: String, text: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SourceRow(name: String, site: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text(site, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty() }
    BarScaffold(stringResource(R.string.about), onBack) { mod ->
        Box(mod.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                AboutSection(stringResource(R.string.about_offline_title), stringResource(R.string.about_offline_text))
                Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Info, null, Modifier.padding(top = 2.dp))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(stringResource(R.string.about_important_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.about_important_text), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                Column {
                    Text(stringResource(R.string.about_sources_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    SourceRow(stringResource(R.string.about_src_gatha), stringResource(R.string.about_src_gatha_site))
                    SourceRow(stringResource(R.string.about_src_haripath), stringResource(R.string.about_src_haripath_site))
                    SourceRow(stringResource(R.string.about_src_vitthal_aarti), stringResource(R.string.about_src_vitthal_aarti_site))
                    SourceRow(stringResource(R.string.about_src_ganpati_aarti), stringResource(R.string.about_src_ganpati_aarti_site))
                }
                AboutSection(stringResource(R.string.about_font_title), stringResource(R.string.about_font_text))
                if (version.isNotBlank()) Text(stringResource(R.string.about_version, version.toDevanagariDigits()),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun String.toDevanagariDigits() = map { if (it in '0'..'9') '०' + (it - '0') else it }.joinToString("")
