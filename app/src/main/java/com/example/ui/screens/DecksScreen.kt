package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.Deck
import com.example.domain.model.Flashcard
import com.example.domain.model.StudyLog
import com.example.domain.model.UserSettings
import com.example.domain.srs.SpacedRepetitionEngine
import com.example.ui.theme.DeckColorPalette
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecksScreen(
    decksState: UiState<List<Deck>>,
    cardsState: UiState<List<Flashcard>>,
    studyLogsState: UiState<List<StudyLog>>,
    settings: UserSettings,
    onOpenDeck: (String) -> Unit,
    onStudyAllDue: () -> Unit,
    onNavigateToAiImport: () -> Unit,
    onCreateDeck: (title: String, description: String, colorHex: String, iconName: String) -> Unit,
    onCreateStarterDeck: () -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }

    val cards = (cardsState as? UiState.Success)?.data.orEmpty()
    val logs = (studyLogsState as? UiState.Success)?.data.orEmpty()
    val nowMillis = remember(cards.size, logs.size) { System.currentTimeMillis() }

    val todayKey = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val todayReviews = logs.firstOrNull { it.dateKey == todayKey }?.reviewsCount?.toInt() ?: 0
    val totalDueCount = remember(cards, nowMillis) {
        cards.count { SpacedRepetitionEngine.isDue(it, nowMillis) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "FlashCraft",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "${settings.algorithm.displayName} • Goal $todayReviews/${settings.dailyReviewGoal}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = onNavigateToAiImport,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .testTag("top_ai_import_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("AI Import")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("create_deck_fab")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Create new deck"
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            when (decksState) {
                is UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = decksState.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                is UiState.Success -> {
                    val allDecks = decksState.data
                    val filteredDecks = remember(allDecks, searchQuery) {
                        if (searchQuery.isBlank()) allDecks
                        else allDecks.filter {
                            it.title.contains(searchQuery, ignoreCase = true) ||
                                it.description.contains(searchQuery, ignoreCase = true)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 96.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Today's Study Summary Card
                        item {
                            StudySummaryHeroCard(
                                totalDecks = allDecks.size,
                                totalCards = cards.size,
                                totalDue = totalDueCount,
                                todayReviews = todayReviews,
                                dailyGoal = settings.dailyReviewGoal,
                                onStudyAllDue = onStudyAllDue
                            )
                        }

                        if (allDecks.isNotEmpty()) {
                            item {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Search your decks...") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Search,
                                            contentDescription = null
                                        )
                                    },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Close,
                                                    contentDescription = "Clear search"
                                                )
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("deck_search_input")
                                )
                            }
                        }

                        if (allDecks.isEmpty()) {
                            item {
                                EmptyDecksCard(
                                    onCreateDeck = { showCreateDialog = true },
                                    onCreateStarterDeck = onCreateStarterDeck,
                                    onNavigateToAiImport = onNavigateToAiImport
                                )
                            }
                        } else if (filteredDecks.isEmpty()) {
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "No decks match \"$searchQuery\".",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(24.dp)
                                    )
                                }
                            }
                        } else {
                            items(filteredDecks, key = { it.id }) { deck ->
                                val deckCards = remember(cards, deck.id) {
                                    cards.filter { it.deckId == deck.id }
                                }
                                val dueInDeck = remember(deckCards, nowMillis) {
                                    deckCards.count { SpacedRepetitionEngine.isDue(it, nowMillis) }
                                }
                                val masteredInDeck = remember(deckCards) {
                                    deckCards.count { SpacedRepetitionEngine.isMastered(it) }
                                }
                                DeckItemCard(
                                    deck = deck,
                                    actualCardCount = deckCards.size,
                                    dueCount = dueInDeck,
                                    masteredCount = masteredInDeck,
                                    onClick = { onOpenDeck(deck.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateOrEditDeckDialog(
            initialTitle = "",
            initialDescription = "",
            initialColorHex = DeckColorPalette.first(),
            initialIconName = "Style",
            dialogTitle = "Create New Deck",
            confirmLabel = "Create Deck",
            onDismiss = { showCreateDialog = false },
            onConfirm = { title, desc, colorHex, iconName ->
                onCreateDeck(title, desc, colorHex, iconName)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun StudySummaryHeroCard(
    totalDecks: Int,
    totalCards: Int,
    totalDue: Int,
    todayReviews: Int,
    dailyGoal: Int,
    onStudyAllDue: () -> Unit
) {
    val goalProgress = (todayReviews.toFloat() / dailyGoal.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)
                        )
                    )
                )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (totalDue > 0) "$totalDue cards due now" else "All caught up!",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$totalDecks decks • $totalCards total cards",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = "$todayReviews/$dailyGoal today",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { goalProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                AnimatedVisibility(visible = totalDue > 0) {
                    Column {
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onStudyAllDue,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("study_due_all_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Study $totalDue Due Cards",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDecksCard(
    onCreateDeck: () -> Unit,
    onCreateStarterDeck: () -> Unit,
    onNavigateToAiImport: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_decks),
                contentDescription = "Empty flashcards illustration",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Your study studio is ready",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Start by uploading any PDF, photo, audio, or notes for instant AI flashcards, or create a deck manually.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onNavigateToAiImport,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("empty_ai_import_button")
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Extract Flashcards from Any File")
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCreateStarterDeck,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("create_starter_deck_button")
                ) {
                    Text("Starter Deck")
                }
                FilledTonalButton(
                    onClick = onCreateDeck,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("empty_new_deck_button")
                ) {
                    Text("Blank Deck")
                }
            }
        }
    }
}

@Composable
private fun DeckItemCard(
    deck: Deck,
    actualCardCount: Int,
    dueCount: Int,
    masteredCount: Int,
    onClick: () -> Unit
) {
    val accent = parseHexColor(deck.colorHex)
    val count = if (actualCardCount > 0) actualCardCount else deck.cardCount.toInt()
    val masteryRatio = if (count > 0) (masteredCount.toFloat() / count.toFloat()).coerceIn(0f, 1f) else 0f

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("deck_card_${deck.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = accent.copy(alpha = 0.14f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.size(52.dp)
            ) {
                Icon(
                    imageVector = deckIconForName(deck.iconName),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.padding(13.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = deck.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (deck.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = deck.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "$count cards",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$masteredCount mastered",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { masteryRatio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Surface(
                color = if (dueCount > 0) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (dueCount > 0) "$dueCount due" else "Done",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (dueCount > 0) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun CreateOrEditDeckDialog(
    initialTitle: String,
    initialDescription: String,
    initialColorHex: String,
    initialIconName: String,
    dialogTitle: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String, colorHex: String, iconName: String) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var description by remember { mutableStateOf(initialDescription) }
    var selectedColor by remember { mutableStateOf(initialColorHex) }
    var selectedIcon by remember { mutableStateOf(initialIconName) }

    val iconOptions = listOf("Style", "Science", "Book", "Language", "Code", "Calculate", "Psychology", "Lightbulb")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 100) title = it },
                    label = { Text("Deck Title *") },
                    placeholder = { Text("e.g., Spanish Verbs or Anatomy") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("deck_title_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 300) description = it },
                    label = { Text("Description (Optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Color",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DeckColorPalette.forEach { hex ->
                        val color = parseHexColor(hex)
                        val isSelected = selectedColor.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (isSelected) {
                                        Modifier.border(
                                            width = 3.dp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            shape = CircleShape
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }
                Text(
                    text = "Icon",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    iconOptions.forEach { iconKey ->
                        val isSelected = selectedIcon == iconKey
                        Surface(
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { selectedIcon = iconKey }
                        ) {
                            Icon(
                                imageVector = deckIconForName(iconKey),
                                contentDescription = iconKey,
                                tint = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(7.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(title.trim(), description.trim(), selectedColor, selectedIcon)
                    }
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(),
                modifier = Modifier.testTag("confirm_deck_dialog_button")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun parseHexColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        Color(0xFF2442D8)
    }
}

fun deckIconForName(name: String): ImageVector {
    return when (name) {
        "Science" -> Icons.Rounded.Science
        "Book" -> Icons.Rounded.Book
        "Language" -> Icons.Rounded.Language
        "Code" -> Icons.Rounded.Code
        "Calculate" -> Icons.Rounded.Calculate
        "Psychology" -> Icons.Rounded.Psychology
        "Lightbulb" -> Icons.Rounded.Lightbulb
        "AutoAwesome" -> Icons.Rounded.AutoAwesome
        else -> Icons.Rounded.Style
    }
}
