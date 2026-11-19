package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.Deck
import com.example.domain.model.ExtractedCardDraft
import com.example.domain.model.ExtractionDepth
import com.example.domain.model.ExtractionStyle
import com.example.ui.viewmodel.AiExtractionUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiImportScreen(
    aiState: AiExtractionUiState,
    existingDecks: List<Deck>,
    onFilePicked: (Uri) -> Unit,
    onExtractClicked: (
        cardCount: Int,
        style: ExtractionStyle,
        depth: ExtractionDepth,
        customInstructions: String
    ) -> Unit,
    onToggleDraft: (String) -> Unit,
    onUpdateDraft: (localId: String, front: String, back: String, hint: String) -> Unit,
    onUpdateTargetDeck: (deckId: String?, title: String, description: String) -> Unit,
    onSaveCards: () -> Unit,
    onReset: () -> Unit,
    onOpenSavedDeck: (String) -> Unit
) {
    val context = LocalContext.current

    var cardCount by rememberSaveable { mutableIntStateOf(10) }
    var style by rememberSaveable { mutableStateOf(ExtractionStyle.QA) }
    var depth by rememberSaveable { mutableStateOf(ExtractionDepth.STANDARD) }
    var customInstructions by rememberSaveable { mutableStateOf("") }
    var editingDraft by remember { mutableStateOf<ExtractedCardDraft?>(null) }

    val anyFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onFilePicked(uri)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onFilePicked(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AI File Extractor", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = "Turn any PDF, photo, audio, Word, or notes into flashcards",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (aiState !is AiExtractionUiState.Idle) {
                        IconButton(onClick = onReset) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Start over"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Step 1: Upload File Card
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "1. Upload Any File",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Supports PDFs, textbook or handwritten photos, lecture audio, Word (.docx), slides (.pptx), Markdown, CSV, and text files.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { anyFileLauncher.launch(arrayOf("*/*")) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("pick_any_file_button")
                                ) {
                                    Icon(
                                        Icons.Rounded.UploadFile,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Choose File")
                                }
                                FilledTonalButton(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("pick_photo_ai_button")
                                ) {
                                    Icon(
                                        Icons.Rounded.PhotoCamera,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Pick Photo")
                                }
                            }

                            val selectedInfo = when (aiState) {
                                is AiExtractionUiState.FileSelected -> aiState.fileInfo
                                is AiExtractionUiState.Extracting -> aiState.fileInfo
                                is AiExtractionUiState.Ready -> aiState.fileInfo
                                is AiExtractionUiState.Saving -> aiState.fileInfo
                                is AiExtractionUiState.Error -> aiState.fileInfo
                                else -> null
                            }

                            if (selectedInfo != null) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Description,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = selectedInfo.displayName,
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val sizeLabel = if (selectedInfo.sizeBytes > 0) {
                                                "${(selectedInfo.sizeBytes / 1024).coerceAtLeast(1)} KB"
                                            } else {
                                                "Ready"
                                            }
                                            Text(
                                                text = "${selectedInfo.mimeType} • $sizeLabel",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Step 2: Customize Options
                if (aiState is AiExtractionUiState.FileSelected ||
                    aiState is AiExtractionUiState.Error ||
                    aiState is AiExtractionUiState.Ready
                ) {
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = "2. Extraction Preferences",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                Text("Number of cards", style = MaterialTheme.typography.labelLarge)
                                Spacer(modifier = Modifier.height(6.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(5, 10, 15, 20).forEach { count ->
                                        FilterChip(
                                            selected = cardCount == count,
                                            onClick = { cardCount = count },
                                            label = { Text("$count cards") }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Card style", style = MaterialTheme.typography.labelLarge)
                                Spacer(modifier = Modifier.height(6.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ExtractionStyle.entries.forEach { option ->
                                        FilterChip(
                                            selected = style == option,
                                            onClick = { style = option },
                                            label = { Text(option.label) }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Detail level", style = MaterialTheme.typography.labelLarge)
                                Spacer(modifier = Modifier.height(6.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ExtractionDepth.entries.forEach { level ->
                                        FilterChip(
                                            selected = depth == level,
                                            onClick = { depth = level },
                                            label = { Text(level.label) }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = customInstructions,
                                    onValueChange = { if (it.length <= 250) customInstructions = it },
                                    label = { Text("Focus notes (Optional)") },
                                    placeholder = { Text("e.g., Focus on formulas and Chapter 3 vocabulary") },
                                    maxLines = 2,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(16.dp))
                                val hasFile = when (aiState) {
                                    is AiExtractionUiState.FileSelected -> true
                                    is AiExtractionUiState.Ready -> true
                                    is AiExtractionUiState.Error -> aiState.fileInfo != null
                                    else -> false
                                }
                                Button(
                                    onClick = {
                                        onExtractClicked(cardCount, style, depth, customInstructions)
                                    },
                                    enabled = hasFile,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("extract_cards_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (aiState is AiExtractionUiState.Ready) {
                                            "Regenerate Flashcards"
                                        } else {
                                            "Extract Flashcards with AI"
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Loading States
                if (aiState is AiExtractionUiState.InspectingFile ||
                    aiState is AiExtractionUiState.Extracting ||
                    aiState is AiExtractionUiState.Saving
                ) {
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = when (aiState) {
                                        is AiExtractionUiState.InspectingFile -> "Reading file metadata..."
                                        is AiExtractionUiState.Extracting -> "AI is analyzing your file and crafting flashcards..."
                                        is AiExtractionUiState.Saving -> "Saving flashcards to your deck..."
                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }

                // Error State
                if (aiState is AiExtractionUiState.Error) {
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = aiState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                // Saved Confirmation State
                if (aiState is AiExtractionUiState.Saved) {
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Saved ${aiState.savedCount} flashcards!",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onReset,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                    ) {
                                        Text("Import Another")
                                    }
                                    Button(
                                        onClick = { onOpenSavedDeck(aiState.deckId) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                    ) {
                                        Text("Open Deck")
                                    }
                                }
                            }
                        }
                    }
                }

                // Step 3: Review & Save Extracted Cards
                if (aiState is AiExtractionUiState.Ready) {
                    item {
                        ExtractionTargetDeckCard(
                            state = aiState,
                            existingDecks = existingDecks,
                            onUpdateTargetDeck = onUpdateTargetDeck,
                            onSaveCards = onSaveCards
                        )
                    }

                    items(aiState.drafts, key = { it.localId }) { draft ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Checkbox(
                                    checked = draft.selected,
                                    onCheckedChange = { onToggleDraft(draft.localId) }
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(top = 6.dp)
                                ) {
                                    Text(
                                        text = draft.front,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = draft.back,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (draft.hint.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Hint: ${draft.hint}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                                IconButton(onClick = { editingDraft = draft }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "Edit extracted card"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val draftToEdit = editingDraft
    if (draftToEdit != null) {
        EditDraftDialog(
            draft = draftToEdit,
            onDismiss = { editingDraft = null },
            onSave = { f, b, h ->
                onUpdateDraft(draftToEdit.localId, f, b, h)
                editingDraft = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtractionTargetDeckCard(
    state: AiExtractionUiState.Ready,
    existingDecks: List<Deck>,
    onUpdateTargetDeck: (deckId: String?, title: String, description: String) -> Unit,
    onSaveCards: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedDeck = existingDecks.firstOrNull { it.id == state.selectedDeckId }
    val selectedCount = state.drafts.count { it.selected }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "3. Review & Save ($selectedCount selected)",
                style = MaterialTheme.typography.titleMedium
            )
            if (state.result.summary.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = state.result.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedDeck?.title ?: "Create New Deck",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Destination Deck") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Create New Deck") },
                        onClick = {
                            expanded = false
                            onUpdateTargetDeck(
                                null,
                                state.targetDeckTitle,
                                state.targetDeckDescription
                            )
                        }
                    )
                    existingDecks.forEach { deck ->
                        DropdownMenuItem(
                            text = { Text(deck.title) },
                            onClick = {
                                expanded = false
                                onUpdateTargetDeck(
                                    deck.id,
                                    state.targetDeckTitle,
                                    state.targetDeckDescription
                                )
                            }
                        )
                    }
                }
            }

            if (state.selectedDeckId == null) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.targetDeckTitle,
                    onValueChange = {
                        if (it.length <= 100) {
                            onUpdateTargetDeck(null, it, state.targetDeckDescription)
                        }
                    },
                    label = { Text("New Deck Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onSaveCards,
                enabled = selectedCount > 0,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_extracted_cards_button")
            ) {
                Text("Save $selectedCount Flashcards")
            }
        }
    }
}

@Composable
private fun EditDraftDialog(
    draft: ExtractedCardDraft,
    onDismiss: () -> Unit,
    onSave: (front: String, back: String, hint: String) -> Unit
) {
    var front by remember { mutableStateOf(draft.front) }
    var back by remember { mutableStateOf(draft.back) }
    var hint by remember { mutableStateOf(draft.hint) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Extracted Card") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = front,
                    onValueChange = { if (it.length <= 1000) front = it },
                    label = { Text("Front") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = back,
                    onValueChange = { if (it.length <= 2000) back = it },
                    label = { Text("Back") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = hint,
                    onValueChange = { if (it.length <= 300) hint = it },
                    label = { Text("Hint") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(front.trim(), back.trim(), hint.trim()) },
                enabled = front.isNotBlank() && back.isNotBlank()
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
