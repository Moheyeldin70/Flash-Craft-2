package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.domain.model.Deck
import com.example.domain.model.ExtractedCardDraft
import com.example.domain.model.Flashcard
import com.example.domain.model.MediaType
import com.example.domain.model.StudyLog
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FlashcardRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireMatchingUserId(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        check(uid == expectedUserId) { "Signed-in user changed; refusing cross-user operation." }
        return uid
    }

    fun observeDecks(userId: String): Flow<List<Deck>> = flow {
        val uid = requireMatchingUserId(userId)
        val path = "users/$uid/decks"
        emitAll(
            db.collection("users").document(uid).collection("decks")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Deck::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeCards(userId: String): Flow<List<Flashcard>> = flow {
        val uid = requireMatchingUserId(userId)
        val path = "users/$uid/cards"
        emitAll(
            db.collection("users").document(uid).collection("cards")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Flashcard::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.dueAtMillis }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeStudyLogs(userId: String): Flow<List<StudyLog>> = flow {
        val uid = requireMatchingUserId(userId)
        val path = "users/$uid/studyLogs"
        emitAll(
            db.collection("users").document(uid).collection("studyLogs")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StudyLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.dateKey }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createDeck(
        userId: String,
        title: String,
        description: String,
        colorHex: String,
        iconName: String = "Style"
    ): Result<String> {
        val cleanTitle = title.trim().take(100)
        val cleanDesc = description.trim().take(300)
        var docPath = "users/$userId/decks"
        return try {
            val uid = requireMatchingUserId(userId)
            require(cleanTitle.isNotBlank()) { "Deck title is required." }
            val docRef = db.collection("users").document(uid).collection("decks").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "description" to cleanDesc,
                "colorHex" to sanitizeHexColor(colorHex),
                "iconName" to iconName.take(40).ifBlank { "Style" },
                "cardCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateDeck(
        userId: String,
        deckId: String,
        title: String,
        description: String,
        colorHex: String,
        iconName: String = "Style"
    ): Result<Unit> {
        val cleanTitle = title.trim().take(100)
        val cleanDesc = description.trim().take(300)
        val docPath = "users/$userId/decks/$deckId"
        return try {
            val uid = requireMatchingUserId(userId)
            require(deckId.isNotBlank()) { "Deck ID cannot be blank." }
            require(cleanTitle.isNotBlank()) { "Deck title is required." }
            db.collection("users").document(uid).collection("decks").document(deckId)
                .update(
                    mapOf(
                        "title" to cleanTitle,
                        "description" to cleanDesc,
                        "colorHex" to sanitizeHexColor(colorHex),
                        "iconName" to iconName.take(40).ifBlank { "Style" },
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteDeckWithCards(userId: String, deckId: String): Result<Unit> {
        val docPath = "users/$userId/decks/$deckId"
        return try {
            val uid = requireMatchingUserId(userId)
            require(deckId.isNotBlank()) { "Deck ID cannot be blank." }
            val cardsSnap = db.collection("users").document(uid).collection("cards")
                .whereEqualTo("deckId", deckId)
                .get()
                .await()

            val refsToDelete = cardsSnap.documents.map { it.reference } +
                db.collection("users").document(uid).collection("decks").document(deckId)

            refsToDelete.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addCard(
        userId: String,
        deckId: String,
        front: String,
        back: String,
        hint: String = "",
        tags: List<String> = emptyList(),
        frontMediaUri: String = "",
        frontMediaType: MediaType = MediaType.NONE,
        frontMediaName: String = "",
        backMediaUri: String = "",
        backMediaType: MediaType = MediaType.NONE,
        backMediaName: String = ""
    ): Result<String> {
        val cleanFront = front.trim().take(1000)
        val cleanBack = back.trim().take(2000)
        var docPath = "users/$userId/cards"
        return try {
            val uid = requireMatchingUserId(userId)
            require(deckId.isNotBlank()) { "Deck ID cannot be blank." }
            require(cleanFront.isNotBlank() && cleanBack.isNotBlank()) {
                "Front and back text are required."
            }
            val cardsCol = db.collection("users").document(uid).collection("cards")
            val cardRef = cardsCol.document()
            docPath = cardRef.path
            val deckRef = db.collection("users").document(uid).collection("decks").document(deckId)

            val batch = db.batch()
            batch.set(
                cardRef,
                buildCardCreateMap(
                    uid = uid,
                    deckId = deckId,
                    front = cleanFront,
                    back = cleanBack,
                    hint = hint,
                    tags = tags,
                    frontMediaUri = frontMediaUri,
                    frontMediaType = frontMediaType,
                    frontMediaName = frontMediaName,
                    backMediaUri = backMediaUri,
                    backMediaType = backMediaType,
                    backMediaName = backMediaName
                )
            )
            batch.update(
                deckRef,
                mapOf(
                    "cardCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(cardRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateCardContent(
        userId: String,
        cardId: String,
        front: String,
        back: String,
        hint: String,
        tags: List<String>,
        frontMediaUri: String,
        frontMediaType: MediaType,
        frontMediaName: String,
        backMediaUri: String,
        backMediaType: MediaType,
        backMediaName: String
    ): Result<Unit> {
        val cleanFront = front.trim().take(1000)
        val cleanBack = back.trim().take(2000)
        val docPath = "users/$userId/cards/$cardId"
        return try {
            val uid = requireMatchingUserId(userId)
            require(cardId.isNotBlank()) { "Card ID cannot be blank." }
            require(cleanFront.isNotBlank() && cleanBack.isNotBlank()) {
                "Front and back text are required."
            }
            val cleanTags = tags.mapNotNull { it.trim().take(40).ifBlank { null } }.take(10)
            db.collection("users").document(uid).collection("cards").document(cardId)
                .update(
                    mapOf(
                        "front" to cleanFront,
                        "back" to cleanBack,
                        "hint" to hint.trim().take(300),
                        "tags" to cleanTags,
                        "frontMediaUri" to frontMediaUri.take(500),
                        "frontMediaType" to frontMediaType.name,
                        "frontMediaName" to frontMediaName.take(120),
                        "backMediaUri" to backMediaUri.take(500),
                        "backMediaType" to backMediaType.name,
                        "backMediaName" to backMediaName.take(120),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCard(userId: String, deckId: String, cardId: String): Result<Unit> {
        val docPath = "users/$userId/cards/$cardId"
        return try {
            val uid = requireMatchingUserId(userId)
            require(cardId.isNotBlank() && deckId.isNotBlank()) { "IDs cannot be blank." }
            val cardRef = db.collection("users").document(uid).collection("cards").document(cardId)
            val deckRef = db.collection("users").document(uid).collection("decks").document(deckId)
            val batch = db.batch()
            batch.delete(cardRef)
            batch.update(
                deckRef,
                mapOf(
                    "cardCount" to FieldValue.increment(-1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun recordCardReview(
        userId: String,
        updatedCard: Flashcard,
        remembered: Boolean
    ): Result<Unit> {
        val docPath = "users/$userId/cards/${updatedCard.id}"
        return try {
            val uid = requireMatchingUserId(userId)
            require(updatedCard.id.isNotBlank()) { "Card ID cannot be blank." }
            val cardRef = db.collection("users").document(uid)
                .collection("cards").document(updatedCard.id)

            cardRef.update(
                mapOf(
                    "repetitions" to updatedCard.repetitions.coerceIn(0L, 100000L),
                    "intervalDays" to updatedCard.intervalDays.coerceIn(0.0, 36500.0),
                    "easeFactor" to updatedCard.easeFactor.coerceIn(1.3, 5.0),
                    "stability" to updatedCard.stability.coerceIn(0.0, 36500.0),
                    "difficulty" to updatedCard.difficulty.coerceIn(1.0, 10.0),
                    "leitnerBox" to updatedCard.leitnerBox.coerceIn(1L, 5L),
                    "dueAtMillis" to updatedCard.dueAtMillis.coerceAtLeast(0L),
                    "lastReviewedMillis" to updatedCard.lastReviewedMillis.coerceAtLeast(0L),
                    "lapses" to updatedCard.lapses.coerceIn(0L, 100000L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            incrementTodayStudyLog(uid, remembered)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    private suspend fun incrementTodayStudyLog(uid: String, remembered: Boolean) {
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val logRef = db.collection("users").document(uid)
            .collection("studyLogs").document(dateKey)
        try {
            val existing = logRef.get().await()
            if (existing.exists()) {
                logRef.update(
                    mapOf(
                        "reviewsCount" to FieldValue.increment(1L),
                        "correctCount" to FieldValue.increment(if (remembered) 1L else 0L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                logRef.set(
                    mapOf(
                        "userId" to uid,
                        "dateKey" to dateKey,
                        "reviewsCount" to 1L,
                        "correctCount" to (if (remembered) 1L else 0L),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, logRef.path)
        }
    }

    suspend fun resetDeckProgress(
        userId: String,
        cards: List<Flashcard>
    ): Result<Unit> {
        val path = "users/$userId/cards"
        return try {
            val uid = requireMatchingUserId(userId)
            cards.filter { it.id.isNotBlank() }.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { card ->
                    val ref = db.collection("users").document(uid).collection("cards").document(card.id)
                    batch.update(
                        ref,
                        mapOf(
                            "repetitions" to 0L,
                            "intervalDays" to 0.0,
                            "easeFactor" to 2.5,
                            "stability" to 0.0,
                            "difficulty" to 5.0,
                            "leitnerBox" to 1L,
                            "dueAtMillis" to 0L,
                            "lastReviewedMillis" to 0L,
                            "lapses" to 0L,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveExtractedCards(
        userId: String,
        existingDeckId: String?,
        newDeckTitle: String,
        newDeckDescription: String,
        newDeckColorHex: String,
        cards: List<ExtractedCardDraft>
    ): Result<String> {
        val selected = cards.filter { it.selected && it.front.isNotBlank() && it.back.isNotBlank() }
        var path = "users/$userId/decks"
        return try {
            val uid = requireMatchingUserId(userId)
            require(selected.isNotEmpty()) { "Select at least one valid card to save." }

            val decksCol = db.collection("users").document(uid).collection("decks")
            val cardsCol = db.collection("users").document(uid).collection("cards")

            val targetDeckId: String
            if (existingDeckId.isNullOrBlank()) {
                val deckRef = decksCol.document()
                targetDeckId = deckRef.id
                path = deckRef.path
                val cleanTitle = newDeckTitle.trim().take(100).ifBlank { "AI Extracted Deck" }
                val cleanDesc = newDeckDescription.trim().take(300)

                val firstChunk = selected.take(350)
                val firstBatch = db.batch()
                firstBatch.set(
                    deckRef,
                    mapOf(
                        "userId" to uid,
                        "title" to cleanTitle,
                        "description" to cleanDesc,
                        "colorHex" to sanitizeHexColor(newDeckColorHex),
                        "iconName" to "AutoAwesome",
                        "cardCount" to selected.size.toLong(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                firstChunk.forEach { draft ->
                    val cardRef = cardsCol.document()
                    firstBatch.set(
                        cardRef,
                        buildCardCreateMap(
                            uid = uid,
                            deckId = targetDeckId,
                            front = draft.front.trim().take(1000),
                            back = draft.back.trim().take(2000),
                            hint = draft.hint,
                            tags = draft.tags
                        )
                    )
                }
                firstBatch.commit().await()

                selected.drop(350).chunked(400).forEach { chunk ->
                    val extraBatch = db.batch()
                    chunk.forEach { draft ->
                        extraBatch.set(
                            cardsCol.document(),
                            buildCardCreateMap(
                                uid = uid,
                                deckId = targetDeckId,
                                front = draft.front.trim().take(1000),
                                back = draft.back.trim().take(2000),
                                hint = draft.hint,
                                tags = draft.tags
                            )
                        )
                    }
                    extraBatch.commit().await()
                }
            } else {
                targetDeckId = existingDeckId
                val deckRef = decksCol.document(targetDeckId)
                path = deckRef.path
                selected.chunked(400).forEachIndexed { index, chunk ->
                    val batch = db.batch()
                    if (index == 0) {
                        batch.update(
                            deckRef,
                            mapOf(
                                "cardCount" to FieldValue.increment(selected.size.toLong()),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                    chunk.forEach { draft ->
                        batch.set(
                            cardsCol.document(),
                            buildCardCreateMap(
                                uid = uid,
                                deckId = targetDeckId,
                                front = draft.front.trim().take(1000),
                                back = draft.back.trim().take(2000),
                                hint = draft.hint,
                                tags = draft.tags
                            )
                        )
                    }
                    batch.commit().await()
                }
            }
            Result.success(targetDeckId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun createStarterDeck(userId: String): Result<String> {
        val starterCards = listOf(
            ExtractedCardDraft(
                localId = "s1",
                front = "How does spaced repetition improve long-term memory?",
                back = "By scheduling reviews right before you are likely to forget a concept, strengthening neural recall pathways each time.",
                hint = "Think about the forgetting curve",
                tags = listOf("learning", "memory")
            ),
            ExtractedCardDraft(
                localId = "s2",
                front = "What is the 'Active Recall' principle?",
                back = "Actively retrieving an answer from memory before seeing the solution, which builds much stronger retention than passive rereading.",
                hint = "Testing yourself vs rereading",
                tags = listOf("study-skill")
            ),
            ExtractedCardDraft(
                localId = "s3",
                front = "When should you rate a card 'Again' vs 'Hard'?",
                back = "Tap 'Again' if you couldn't recall the core answer (resets to short review). Tap 'Hard' if you recalled it with significant effort.",
                hint = "Honest grading keeps your schedule accurate",
                tags = listOf("flashcraft")
            ),
            ExtractedCardDraft(
                localId = "s4",
                front = "How can you turn your notes, PDFs, or photos into cards?",
                back = "Open the AI Extract tab, upload any document, image, or audio file, and choose your preferred card style and count.",
                hint = "Check the bottom navigation bar",
                tags = listOf("ai-import")
            )
        )
        return saveExtractedCards(
            userId = userId,
            existingDeckId = null,
            newDeckTitle = "Memory & Study Mastery",
            newDeckDescription = "A quick starter deck showing how spaced repetition and FlashCraft work.",
            newDeckColorHex = "#2442D8",
            cards = starterCards
        )
    }

    private fun buildCardCreateMap(
        uid: String,
        deckId: String,
        front: String,
        back: String,
        hint: String = "",
        tags: List<String> = emptyList(),
        frontMediaUri: String = "",
        frontMediaType: MediaType = MediaType.NONE,
        frontMediaName: String = "",
        backMediaUri: String = "",
        backMediaType: MediaType = MediaType.NONE,
        backMediaName: String = ""
    ): Map<String, Any> {
        val cleanTags = tags.mapNotNull { it.trim().take(40).ifBlank { null } }.take(10)
        return mapOf(
            "userId" to uid,
            "deckId" to deckId,
            "front" to front.trim().take(1000),
            "back" to back.trim().take(2000),
            "hint" to hint.trim().take(300),
            "tags" to cleanTags,
            "frontMediaUri" to frontMediaUri.take(500),
            "frontMediaType" to frontMediaType.name,
            "frontMediaName" to frontMediaName.take(120),
            "backMediaUri" to backMediaUri.take(500),
            "backMediaType" to backMediaType.name,
            "backMediaName" to backMediaName.take(120),
            "repetitions" to 0L,
            "intervalDays" to 0.0,
            "easeFactor" to 2.5,
            "stability" to 0.0,
            "difficulty" to 5.0,
            "leitnerBox" to 1L,
            "dueAtMillis" to 0L,
            "lastReviewedMillis" to 0L,
            "lapses" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    private fun sanitizeHexColor(raw: String): String {
        val trimmed = raw.trim()
        return if (Regex("^#[0-9A-Fa-f]{6,8}$").matches(trimmed)) trimmed else "#2442D8"
    }
}
