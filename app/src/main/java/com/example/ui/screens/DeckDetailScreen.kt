package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.Deck
import com.example.domain.model.Flashcard
import com.example.domain.model.MediaType
import com.example.domain.srs.SpacedRepetitionEngine
import com.example.ui.components.MediaAttachmentPickerRow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DeckDetailScreen(
    deck: Deck?,
    cards: List<Flashcard>,
    onBack: () -> Unit,
    onStudyDue: () -> Unit,
    onPracticeAll: () -> Unit,
    onUpdateDeck: (title: String, description: String, colorHex: String, iconName: String) -> Unit,
    onDeleteDeck: () -> Unit,
    onResetProgress: () -> Unit,
    onAddCard: (
        front: String,
        back: String,
        hint: String,
        tagsRaw: String,
        frontMediaUri: String,
        frontMediaType: MediaType,
        frontMediaName: String,
        backMediaUri: String,
        backMediaType: MediaType,
        backMediaName: String
    ) -> Unit,
    onUpdateCard: (
        cardId: String,
        front: String,
        back: String,
        hint: String,
        tagsRaw: String,
        frontMediaUri: String,
        frontMediaType: MediaType,
        frontMediaName: String,
        backMediaUri: String,
        backMediaType: MediaType,
        backMediaName: String
    ) -> Unit,
    onDeleteCard: (cardId: String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showEditDeckDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAddCardDialog by remember { mutableStateOf(false) }
    var editingCard by remember { mutableStateOf<Flashcard?>(null) }

    val nowMillis = remember(cards.size) { System.currentTimeMillis() }
    val dueCount = remember(cards, nowMillis) {
        cards.count { SpacedRepetitionEngine.isDue(it, nowMillis) }
    }
    val masteredCount = remember(cards) {
        cards.count { SpacedRepetitionEngine.isMastered(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = deck?.title ?: "Deck",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                },
                actions = {
                    if (deck != null) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "Deck options"
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit Deck") },
                                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        showEditDeckDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Reset Study Progress") },
                                    leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onResetProgress()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete Deck", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        showDeleteConfirm = true
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (deck != null) {
                FloatingActionButton(
                    onClick = { showAddCardDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_card_fab")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Add flashcard"
                    )
                }
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
            if (deck == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Deck not found.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val accent = parseHexColor(deck.colorHex)
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                if (deck.description.isNotBlank()) {
                                    Text(
                                        text = deck.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    StatPill(
                                        label = "Total",
                                        value = cards.size.toString(),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatPill(
                                        label = "Due Now",
                                        value = dueCount.toString(),
                                        color = accent,
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatPill(
                                        label = "Mastered",
                                        value = masteredCount.toString(),
                                        color = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = onStudyDue,
                                        enabled = dueCount > 0,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("deck_study_due_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (dueCount > 0) "Study ($dueCount)" else "No Due")
                                    }
                                    OutlinedButton(
                                        onClick = onPracticeAll,
                                        enabled = cards.isNotEmpty(),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("deck_practice_all_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Style,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("Practice All")
                                    }
                                }
                            }
                        }
                    }

                    if (cards.isEmpty()) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "No cards in this deck yet",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Tap + to add your first card with text, photos, or audio.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    FilledTonalButton(
                                        onClick = { showAddCardDialog = true },
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Icon(Icons.Rounded.Add, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Add Card")
                                    }
                                }
                            }
                        }
                    } else {
                        items(cards, key = { it.id }) { card ->
                            val isDue = SpacedRepetitionEngine.isDue(card, nowMillis)
                            val statusText = when {
                                SpacedRepetitionEngine.isNew(card) -> "New"
                                isDue -> "Due now"
                                SpacedRepetitionEngine.isMastered(card) ->
                                    "Mastered • ${SpacedRepetitionEngine.formatDuration(card.dueAtMillis - nowMillis)}"
                                else -> "Due in ${SpacedRepetitionEngine.formatDuration(card.dueAtMillis - nowMillis)}"
                            }
                            Card(
                                onClick = { editingCard = card },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("card_list_item_${card.id}")
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            color = if (isDue) {
                                                accent.copy(alpha = 0.14f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = statusText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isDue) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (card.resolvedFrontMediaType == MediaType.IMAGE ||
                                                card.resolvedBackMediaType == MediaType.IMAGE
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Image,
                                                    contentDescription = "Has image media",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier
                                                        .padding(end = 6.dp)
                                                        .size(18.dp)
                                                )
                                            }
                                            if (card.resolvedFrontMediaType == MediaType.AUDIO ||
                                                card.resolvedBackMediaType == MediaType.AUDIO
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.AudioFile,
                                                    contentDescription = "Has audio media",
                                                    tint = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier
                                                        .padding(end = 6.dp)
                                                        .size(18.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { onDeleteCard(card.id) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Delete,
                                                    contentDescription = "Delete card",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = card.front,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        text = card.back,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (card.tags.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            card.tags.forEach { tag ->
                                                AssistChip(
                                                    onClick = { editingCard = card },
                                                    label = {
                                                        Text(
                                                            text = "#$tag",
                                                            style = MaterialTheme.typography.labelSmall
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditDeckDialog && deck != null) {
        CreateOrEditDeckDialog(
            initialTitle = deck.title,
            initialDescription = deck.description,
            initialColorHex = deck.colorHex,
            initialIconName = deck.iconName,
            dialogTitle = "Edit Deck",
            confirmLabel = "Save",
            onDismiss = { showEditDeckDialog = false },
            onConfirm = { title, desc, colorHex, iconName ->
                onUpdateDeck(title, desc, colorHex, iconName)
                showEditDeckDialog = false
            }
        )
    }

    if (showDeleteConfirm && deck != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete \"${deck.title}\"?") },
            text = { Text("This will permanently remove the deck and all ${cards.size} flashcards inside it.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteDeck()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddCardDialog) {
        AddOrEditCardDialog(
            initialCard = null,
            onDismiss = { showAddCardDialog = false },
            onSave = { front, back, hint, tags, fUri, fType, fName, bUri, bType, bName ->
                onAddCard(front, back, hint, tags, fUri, fType, fName, bUri, bType, bName)
                showAddCardDialog = false
            }
        )
    }

    val cardToEdit = editingCard
    if (cardToEdit != null) {
        AddOrEditCardDialog(
            initialCard = cardToEdit,
            onDismiss = { editingCard = null },
            onSave = { front, back, hint, tags, fUri, fType, fName, bUri, bType, bName ->
                onUpdateCard(
                    cardToEdit.id,
                    front,
                    back,
                    hint,
                    tags,
                    fUri,
                    fType,
                    fName,
                    bUri,
                    bType,
                    bName
                )
                editingCard = null
            }
        )
    }
}

@Composable
private fun StatPill(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AddOrEditCardDialog(
    initialCard: Flashcard?,
    onDismiss: () -> Unit,
    onSave: (
        front: String,
        back: String,
        hint: String,
        tagsRaw: String,
        frontMediaUri: String,
        frontMediaType: MediaType,
        frontMediaName: String,
        backMediaUri: String,
        backMediaType: MediaType,
        backMediaName: String
    ) -> Unit
) {
    var front by remember { mutableStateOf(initialCard?.front.orEmpty()) }
    var back by remember { mutableStateOf(initialCard?.back.orEmpty()) }
    var hint by remember { mutableStateOf(initialCard?.hint.orEmpty()) }
    var tagsRaw by remember { mutableStateOf(initialCard?.tags?.joinToString(", ").orEmpty()) }

    var frontMediaUri by remember { mutableStateOf(initialCard?.frontMediaUri.orEmpty()) }
    var frontMediaType by remember {
        mutableStateOf(initialCard?.resolvedFrontMediaType ?: MediaType.NONE)
    }
    var frontMediaName by remember { mutableStateOf(initialCard?.frontMediaName.orEmpty()) }

    var backMediaUri by remember { mutableStateOf(initialCard?.backMediaUri.orEmpty()) }
    var backMediaType by remember {
        mutableStateOf(initialCard?.resolvedBackMediaType ?: MediaType.NONE)
    }
    var backMediaName by remember { mutableStateOf(initialCard?.backMediaName.orEmpty()) }
    var mediaError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialCard == null) "Add Flashcard" else "Edit Flashcard",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = front,
                    onValueChange = { if (it.length <= 1000) front = it },
                    label = { Text("Front (Question / Prompt) *") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_front_input")
                )

                MediaAttachmentPickerRow(
                    sideLabel = "Front",
                    mediaUri = frontMediaUri,
                    mediaType = frontMediaType,
                    mediaName = frontMediaName,
                    onMediaAttached = { uri, type, name ->
                        mediaError = null
                        frontMediaUri = uri
                        frontMediaType = type
                        frontMediaName = name
                    },
                    onMediaRemoved = {
                        frontMediaUri = ""
                        frontMediaType = MediaType.NONE
                        frontMediaName = ""
                    },
                    onError = { mediaError = it }
                )

                OutlinedTextField(
                    value = back,
                    onValueChange = { if (it.length <= 2000) back = it },
                    label = { Text("Back (Answer / Explanation) *") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_back_input")
                )

                MediaAttachmentPickerRow(
                    sideLabel = "Back",
                    mediaUri = backMediaUri,
                    mediaType = backMediaType,
                    mediaName = backMediaName,
                    onMediaAttached = { uri, type, name ->
                        mediaError = null
                        backMediaUri = uri
                        backMediaType = type
                        backMediaName = name
                    },
                    onMediaRemoved = {
                        backMediaUri = ""
                        backMediaType = MediaType.NONE
                        backMediaName = ""
                    },
                    onError = { mediaError = it }
                )

                if (!mediaError.isNullOrBlank()) {
                    Text(
                        text = mediaError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                OutlinedTextField(
                    value = hint,
                    onValueChange = { if (it.length <= 300) hint = it },
                    label = { Text("Hint (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tagsRaw,
                    onValueChange = { tagsRaw = it },
                    label = { Text("Tags (comma-separated, optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (front.isNotBlank() && back.isNotBlank()) {
                        onSave(
                            front.trim(),
                            back.trim(),
                            hint.trim(),
                            tagsRaw,
                            frontMediaUri,
                            frontMediaType,
                            frontMediaName,
                            backMediaUri,
                            backMediaType,
                            backMediaName
                        )
                    }
                },
                enabled = front.isNotBlank() && back.isNotBlank(),
                modifier = Modifier.testTag("save_card_dialog_button")
            ) {
                Text(if (initialCard == null) "Add Card" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
