package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiExtractionRepository
import com.example.data.ai.SelectedFileInfo
import com.example.data.repository.FlashcardRepository
import com.example.data.repository.SettingsRepository
import com.example.domain.model.Deck
import com.example.domain.model.ExtractedCardDraft
import com.example.domain.model.ExtractionDepth
import com.example.domain.model.ExtractionResult
import com.example.domain.model.ExtractionStyle
import com.example.domain.model.Flashcard
import com.example.domain.model.MediaType
import com.example.domain.model.ReviewRating
import com.example.domain.model.SrsAlgorithm
import com.example.domain.model.StudyLog
import com.example.domain.model.ThemeMode
import com.example.domain.model.UserSettings
import com.example.domain.srs.SpacedRepetitionEngine
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface AiExtractionUiState {
    data object Idle : AiExtractionUiState
    data object InspectingFile : AiExtractionUiState
    data class FileSelected(val fileInfo: SelectedFileInfo) : AiExtractionUiState
    data class Extracting(val fileInfo: SelectedFileInfo) : AiExtractionUiState
    data class Ready(
        val fileInfo: SelectedFileInfo,
        val result: ExtractionResult,
        val selectedDeckId: String?,
        val targetDeckTitle: String,
        val targetDeckDescription: String,
        val drafts: List<ExtractedCardDraft>
    ) : AiExtractionUiState
    data class Saving(val fileInfo: SelectedFileInfo) : AiExtractionUiState
    data class Saved(val deckId: String, val savedCount: Int) : AiExtractionUiState
    data class Error(val fileInfo: SelectedFileInfo?, val message: String) : AiExtractionUiState
}

class FlashCraftViewModel(
    private val repository: FlashcardRepository,
    private val settingsRepository: SettingsRepository,
    private val aiRepository: AiExtractionRepository,
    private val currentUserId: String
) : ViewModel() {

    val decksState: StateFlow<UiState<List<Deck>>> = repository.observeDecks(currentUserId)
        .map<List<Deck>, UiState<List<Deck>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            emit(UiState.Error(root.message ?: "Failed to load decks."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = UiState.Loading
        )

    val cardsState: StateFlow<UiState<List<Flashcard>>> = repository.observeCards(currentUserId)
        .map<List<Flashcard>, UiState<List<Flashcard>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            emit(UiState.Error(root.message ?: "Failed to load flashcards."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = UiState.Loading
        )

    val studyLogsState: StateFlow<UiState<List<StudyLog>>> = repository.observeStudyLogs(currentUserId)
        .map<List<StudyLog>, UiState<List<StudyLog>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            emit(UiState.Error(root.message ?: "Failed to load study progress."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = UiState.Loading
        )

    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = UserSettings()
        )

    private val _aiState = MutableStateFlow<AiExtractionUiState>(AiExtractionUiState.Idle)
    val aiState: StateFlow<AiExtractionUiState> = _aiState.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    // --- DECK OPERATIONS ---

    fun createDeck(title: String, description: String, colorHex: String, iconName: String) {
        viewModelScope.launch {
            repository.createDeck(currentUserId, title, description, colorHex, iconName)
                .onSuccess { _snackbarEvents.tryEmit("Deck created") }
                .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to create deck") }
        }
    }

    fun updateDeck(
        deckId: String,
        title: String,
        description: String,
        colorHex: String,
        iconName: String
    ) {
        viewModelScope.launch {
            repository.updateDeck(currentUserId, deckId, title, description, colorHex, iconName)
                .onSuccess { _snackbarEvents.tryEmit("Deck updated") }
                .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to update deck") }
        }
    }

    fun deleteDeck(deckId: String) {
        viewModelScope.launch {
            repository.deleteDeckWithCards(currentUserId, deckId)
                .onSuccess { _snackbarEvents.tryEmit("Deck deleted") }
                .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to delete deck") }
        }
    }

    fun createStarterDeck() {
        viewModelScope.launch {
            repository.createStarterDeck(currentUserId)
                .onSuccess { _snackbarEvents.tryEmit("Starter deck added") }
                .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Could not create starter deck") }
        }
    }

    // --- CARD OPERATIONS ---

    fun addCard(
        deckId: String,
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
    ) {
        val tags = parseTags(tagsRaw)
        viewModelScope.launch {
            repository.addCard(
                userId = currentUserId,
                deckId = deckId,
                front = front,
                back = back,
                hint = hint,
                tags = tags,
                frontMediaUri = frontMediaUri,
                frontMediaType = frontMediaType,
                frontMediaName = frontMediaName,
                backMediaUri = backMediaUri,
                backMediaType = backMediaType,
                backMediaName = backMediaName
            ).onSuccess {
                _snackbarEvents.tryEmit("Card added")
            }.onFailure {
                _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to add card")
            }
        }
    }

    fun updateCard(
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
    ) {
        val tags = parseTags(tagsRaw)
        viewModelScope.launch {
            repository.updateCardContent(
                userId = currentUserId,
                cardId = cardId,
                front = front,
                back = back,
                hint = hint,
                tags = tags,
                frontMediaUri = frontMediaUri,
                frontMediaType = frontMediaType,
                frontMediaName = frontMediaName,
                backMediaUri = backMediaUri,
                backMediaType = backMediaType,
                backMediaName = backMediaName
            ).onSuccess {
                _snackbarEvents.tryEmit("Card updated")
            }.onFailure {
                _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to update card")
            }
        }
    }

    fun deleteCard(deckId: String, cardId: String) {
        viewModelScope.launch {
            repository.deleteCard(currentUserId, deckId, cardId)
                .onSuccess { _snackbarEvents.tryEmit("Card deleted") }
                .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to delete card") }
        }
    }

    fun resetDeckProgress(cards: List<Flashcard>) {
        viewModelScope.launch {
            repository.resetDeckProgress(currentUserId, cards)
                .onSuccess { _snackbarEvents.tryEmit("Study progress reset") }
                .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to reset progress") }
        }
    }

    fun submitReview(card: Flashcard, rating: ReviewRating, updateSchedule: Boolean) {
        val nowMillis = System.currentTimeMillis()
        val nextCard = if (updateSchedule) {
            SpacedRepetitionEngine.applyReview(card, rating, settings.value, nowMillis)
        } else {
            card.copy(lastReviewedMillis = nowMillis)
        }
        val remembered = rating != ReviewRating.AGAIN
        viewModelScope.launch {
            repository.recordCardReview(currentUserId, nextCard, remembered)
                .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Could not save review") }
        }
    }

    // --- AI FILE EXTRACTION ---

    fun selectFileForAi(context: Context, uri: Uri) {
        viewModelScope.launch {
            _aiState.value = AiExtractionUiState.InspectingFile
            try {
                val info = aiRepository.inspectFile(context, uri)
                _aiState.value = AiExtractionUiState.FileSelected(info)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiState.value = AiExtractionUiState.Error(
                    fileInfo = null,
                    message = e.localizedMessage ?: "Could not inspect selected file."
                )
            }
        }
    }

    fun extractFlashcards(
        context: Context,
        cardCount: Int,
        style: ExtractionStyle,
        depth: ExtractionDepth,
        customInstructions: String
    ) {
        val fileInfo = when (val current = _aiState.value) {
            is AiExtractionUiState.FileSelected -> current.fileInfo
            is AiExtractionUiState.Ready -> current.fileInfo
            is AiExtractionUiState.Error -> current.fileInfo
            else -> null
        } ?: return

        viewModelScope.launch {
            _aiState.value = AiExtractionUiState.Extracting(fileInfo)
            aiRepository.extractFlashcardsFromFile(
                context = context,
                fileInfo = fileInfo,
                cardCount = cardCount,
                style = style,
                depth = depth,
                customInstructions = customInstructions
            ).onSuccess { result ->
                _aiState.value = AiExtractionUiState.Ready(
                    fileInfo = fileInfo,
                    result = result,
                    selectedDeckId = null,
                    targetDeckTitle = result.suggestedDeckTitle,
                    targetDeckDescription = result.suggestedDeckDescription,
                    drafts = result.cards
                )
            }.onFailure { err ->
                _aiState.value = AiExtractionUiState.Error(
                    fileInfo = fileInfo,
                    message = err.localizedMessage ?: "AI extraction failed. Please try again."
                )
            }
        }
    }

    fun toggleDraftSelection(localId: String) {
        val current = _aiState.value as? AiExtractionUiState.Ready ?: return
        _aiState.value = current.copy(
            drafts = current.drafts.map {
                if (it.localId == localId) it.copy(selected = !it.selected) else it
            }
        )
    }

    fun updateDraft(localId: String, front: String, back: String, hint: String) {
        val current = _aiState.value as? AiExtractionUiState.Ready ?: return
        _aiState.value = current.copy(
            drafts = current.drafts.map {
                if (it.localId == localId) it.copy(front = front, back = back, hint = hint) else it
            }
        )
    }

    fun updateTargetDeckForExtraction(deckId: String?, title: String, description: String) {
        val current = _aiState.value as? AiExtractionUiState.Ready ?: return
        _aiState.value = current.copy(
            selectedDeckId = deckId,
            targetDeckTitle = title,
            targetDeckDescription = description
        )
    }

    fun saveExtractedDrafts() {
        val current = _aiState.value as? AiExtractionUiState.Ready ?: return
        val selectedCount = current.drafts.count { it.selected && it.front.isNotBlank() && it.back.isNotBlank() }
        if (selectedCount == 0) {
            _snackbarEvents.tryEmit("Select at least one valid card to save.")
            return
        }
        viewModelScope.launch {
            _aiState.value = AiExtractionUiState.Saving(current.fileInfo)
            repository.saveExtractedCards(
                userId = currentUserId,
                existingDeckId = current.selectedDeckId,
                newDeckTitle = current.targetDeckTitle,
                newDeckDescription = current.targetDeckDescription,
                newDeckColorHex = "#2442D8",
                cards = current.drafts
            ).onSuccess { deckId ->
                _aiState.value = AiExtractionUiState.Saved(deckId, selectedCount)
                _snackbarEvents.tryEmit("Saved $selectedCount flashcards!")
            }.onFailure { err ->
                _aiState.value = AiExtractionUiState.Error(
                    fileInfo = current.fileInfo,
                    message = err.localizedMessage ?: "Failed to save extracted cards."
                )
            }
        }
    }

    fun resetAiExtraction() {
        _aiState.value = AiExtractionUiState.Idle
    }

    // --- SETTINGS ---

    fun updateAlgorithm(algorithm: SrsAlgorithm) {
        viewModelScope.launch { settingsRepository.updateAlgorithm(algorithm) }
    }

    fun updateTargetRetention(retention: Float) {
        viewModelScope.launch { settingsRepository.updateTargetRetention(retention) }
    }

    fun updateNewCardsPerDay(count: Int) {
        viewModelScope.launch { settingsRepository.updateNewCardsPerDay(count) }
    }

    fun updateDailyReviewGoal(goal: Int) {
        viewModelScope.launch { settingsRepository.updateDailyReviewGoal(goal) }
    }

    fun updateShowIntervalPreviews(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateShowIntervalPreviews(enabled) }
    }

    fun updateAutoPlayAudio(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateAutoPlayAudio(enabled) }
    }

    fun updateShowHintsAutomatically(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateShowHintsAutomatically(enabled) }
    }

    fun updateThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.updateThemeMode(mode) }
    }

    fun resetSettingsDefaults() {
        viewModelScope.launch {
            settingsRepository.resetDefaults()
            _snackbarEvents.tryEmit("Settings restored to defaults")
        }
    }

    private fun parseTags(raw: String): List<String> =
        raw.split(',')
            .mapNotNull { it.trim().take(40).ifBlank { null } }
            .distinct()
            .take(10)
}
