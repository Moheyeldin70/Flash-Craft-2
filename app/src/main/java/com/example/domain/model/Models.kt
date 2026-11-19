package com.example.domain.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class MediaType {
    NONE,
    IMAGE,
    AUDIO;

    companion object {
        fun fromRaw(raw: String?): MediaType =
            entries.firstOrNull { it.name == raw } ?: NONE
    }
}

enum class SrsAlgorithm(
    val displayName: String,
    val subtitle: String,
    val explanation: String
) {
    SM2(
        displayName = "SM-2 Classic",
        subtitle = "Proven interval & ease multiplier",
        explanation = "Starts at 1 day, then 6 days, then multiplies by each card's personal Ease Factor. Best all-around default."
    ),
    FSRS(
        displayName = "FSRS Adaptive",
        subtitle = "Modern memory-stability scheduling",
        explanation = "Models memory stability and difficulty to hit your chosen retention target with fewer unnecessary reviews."
    ),
    LEITNER(
        displayName = "Leitner 5-Box",
        subtitle = "Simple 5-step box progression",
        explanation = "Cards advance through 5 boxes (1d, 2d, 5d, 12d, 30d). Easy to understand and great for beginners."
    );

    companion object {
        fun fromRaw(raw: String?): SrsAlgorithm =
            entries.firstOrNull { it.name == raw } ?: SM2
    }
}

enum class ReviewRating(val label: String) {
    AGAIN("Again"),
    HARD("Hard"),
    GOOD("Good"),
    EASY("Easy")
}

enum class ThemeMode(val label: String) {
    SYSTEM("Match System"),
    LIGHT("Light Paper"),
    DARK("Dark Studio");

    companion object {
        fun fromRaw(raw: String?): ThemeMode =
            entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}

data class UserSettings(
    val algorithm: SrsAlgorithm = SrsAlgorithm.SM2,
    val targetRetention: Float = 0.90f,
    val newCardsPerDay: Int = 20,
    val dailyReviewGoal: Int = 30,
    val showIntervalPreviews: Boolean = true,
    val autoPlayAudio: Boolean = false,
    val showHintsAutomatically: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)

@Keep
@IgnoreExtraProperties
data class Deck(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val colorHex: String = "#2442D8",
    val iconName: String = "Style",
    val cardCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Flashcard(
    @DocumentId val id: String = "",
    val userId: String = "",
    val deckId: String = "",
    val front: String = "",
    val back: String = "",
    val hint: String = "",
    val tags: List<String> = emptyList(),
    val frontMediaUri: String = "",
    val frontMediaType: String = MediaType.NONE.name,
    val frontMediaName: String = "",
    val backMediaUri: String = "",
    val backMediaType: String = MediaType.NONE.name,
    val backMediaName: String = "",
    val repetitions: Long = 0L,
    val intervalDays: Double = 0.0,
    val easeFactor: Double = 2.5,
    val stability: Double = 0.0,
    val difficulty: Double = 5.0,
    val leitnerBox: Long = 1L,
    val dueAtMillis: Long = 0L,
    val lastReviewedMillis: Long = 0L,
    val lapses: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val resolvedFrontMediaType: MediaType get() = MediaType.fromRaw(frontMediaType)
    val resolvedBackMediaType: MediaType get() = MediaType.fromRaw(backMediaType)
}

@Keep
@IgnoreExtraProperties
data class StudyLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateKey: String = "",
    val reviewsCount: Long = 0L,
    val correctCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class ExtractionStyle(val label: String, val promptHint: String) {
    QA("Q&A", "Clear conceptual questions on the front and concise accurate answers on the back."),
    DEFINITIONS("Definitions", "Key terms or concepts on the front and clear definitions with brief context on the back."),
    CLOZE("Fill-in-Blank", "A sentence with the key fact blanked out using [___] on the front, and the missing answer plus brief explanation on the back."),
    KEY_FACTS("Exam Facts", "High-yield prompts testing cause, effect, formulas, or dates on the front and structured answers on the back.")
}

enum class ExtractionDepth(val label: String) {
    BEGINNER("Beginner-Friendly"),
    STANDARD("Balanced"),
    EXAM_PREP("Detailed Exam Prep")
}

data class ExtractedCardDraft(
    val localId: String,
    val front: String,
    val back: String,
    val hint: String = "",
    val tags: List<String> = emptyList(),
    val selected: Boolean = true
)

data class ExtractionResult(
    val suggestedDeckTitle: String,
    val suggestedDeckDescription: String,
    val summary: String,
    val cards: List<ExtractedCardDraft>
)
