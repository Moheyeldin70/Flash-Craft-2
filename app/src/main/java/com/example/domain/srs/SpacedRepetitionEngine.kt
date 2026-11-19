package com.example.domain.srs

import com.example.domain.model.Flashcard
import com.example.domain.model.ReviewRating
import com.example.domain.model.SrsAlgorithm
import com.example.domain.model.UserSettings
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

object SpacedRepetitionEngine {
    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val MINUTE_MS = 60L * 1000L
    private val LEITNER_INTERVALS_DAYS = listOf(1.0, 2.0, 5.0, 12.0, 30.0)

    fun isDue(card: Flashcard, nowMillis: Long): Boolean =
        card.dueAtMillis <= nowMillis

    fun isNew(card: Flashcard): Boolean =
        card.lastReviewedMillis == 0L && card.repetitions == 0L

    fun isMastered(card: Flashcard): Boolean =
        card.intervalDays >= 14.0 && card.repetitions >= 2L

    fun applyReview(
        card: Flashcard,
        rating: ReviewRating,
        settings: UserSettings,
        nowMillis: Long
    ): Flashcard {
        val scheduled = computeSchedule(card, rating, settings, nowMillis)
        return card.copy(
            repetitions = scheduled.repetitions,
            intervalDays = scheduled.intervalDays,
            easeFactor = scheduled.easeFactor,
            stability = scheduled.stability,
            difficulty = scheduled.difficulty,
            leitnerBox = scheduled.leitnerBox,
            dueAtMillis = scheduled.dueAtMillis,
            lastReviewedMillis = nowMillis,
            lapses = scheduled.lapses
        )
    }

    fun previewIntervals(
        card: Flashcard,
        settings: UserSettings,
        nowMillis: Long
    ): Map<ReviewRating, String> =
        ReviewRating.entries.associateWith { rating ->
            val result = computeSchedule(card, rating, settings, nowMillis)
            formatDuration(result.dueAtMillis - nowMillis)
        }

    fun formatDuration(deltaMillis: Long): String {
        val safeDelta = max(0L, deltaMillis)
        val minutes = safeDelta / MINUTE_MS
        val hours = safeDelta / (60L * MINUTE_MS)
        val days = safeDelta.toDouble() / DAY_MS.toDouble()
        return when {
            minutes < 1 -> "<1m"
            minutes < 60 -> "${minutes}m"
            hours < 24 -> "${hours}h"
            days < 30.0 -> "${days.roundToLong().coerceAtLeast(1L)}d"
            days < 365.0 -> "${(days / 30.0).roundToLong().coerceAtLeast(1L)}mo"
            else -> String.format("%.1fy", days / 365.0)
        }
    }

    private data class ScheduleOutcome(
        val repetitions: Long,
        val intervalDays: Double,
        val easeFactor: Double,
        val stability: Double,
        val difficulty: Double,
        val leitnerBox: Long,
        val dueAtMillis: Long,
        val lapses: Long
    )

    private fun computeSchedule(
        card: Flashcard,
        rating: ReviewRating,
        settings: UserSettings,
        nowMillis: Long
    ): ScheduleOutcome {
        return when (settings.algorithm) {
            SrsAlgorithm.SM2 -> scheduleSm2(card, rating, nowMillis)
            SrsAlgorithm.FSRS -> scheduleFsrs(card, rating, settings.targetRetention, nowMillis)
            SrsAlgorithm.LEITNER -> scheduleLeitner(card, rating, nowMillis)
        }
    }

    private fun scheduleSm2(
        card: Flashcard,
        rating: ReviewRating,
        nowMillis: Long
    ): ScheduleOutcome {
        val currentEf = card.easeFactor.coerceIn(1.3, 5.0)
        val currentInterval = max(0.0, card.intervalDays)
        val currentReps = max(0L, card.repetitions)

        return when (rating) {
            ReviewRating.AGAIN -> {
                val newEf = max(1.3, currentEf - 0.20)
                ScheduleOutcome(
                    repetitions = 0L,
                    intervalDays = 0.0,
                    easeFactor = newEf,
                    stability = max(0.5, card.stability * 0.5),
                    difficulty = min(10.0, card.difficulty + 0.8),
                    leitnerBox = 1L,
                    dueAtMillis = nowMillis + 10L * MINUTE_MS,
                    lapses = card.lapses + 1L
                )
            }
            ReviewRating.HARD -> {
                val newEf = max(1.3, currentEf - 0.14)
                val nextDays = when {
                    currentReps == 0L -> 0.5
                    currentInterval < 1.0 -> 1.0
                    else -> max(1.0, currentInterval * 1.2)
                }.coerceAtMost(36500.0)
                ScheduleOutcome(
                    repetitions = max(1L, currentReps),
                    intervalDays = nextDays,
                    easeFactor = newEf,
                    stability = max(1.0, nextDays),
                    difficulty = min(10.0, card.difficulty + 0.3),
                    leitnerBox = card.leitnerBox.coerceIn(1L, 5L),
                    dueAtMillis = nowMillis + (nextDays * DAY_MS).roundToLong(),
                    lapses = card.lapses
                )
            }
            ReviewRating.GOOD -> {
                val nextReps = currentReps + 1L
                val nextDays = when (currentReps) {
                    0L -> 1.0
                    1L -> 6.0
                    else -> max(1.0, currentInterval * currentEf)
                }.coerceAtMost(36500.0)
                ScheduleOutcome(
                    repetitions = nextReps,
                    intervalDays = nextDays,
                    easeFactor = currentEf,
                    stability = max(1.0, nextDays),
                    difficulty = card.difficulty.coerceIn(1.0, 10.0),
                    leitnerBox = min(5L, card.leitnerBox + 1L),
                    dueAtMillis = nowMillis + (nextDays * DAY_MS).roundToLong(),
                    lapses = card.lapses
                )
            }
            ReviewRating.EASY -> {
                val newEf = min(5.0, currentEf + 0.15)
                val nextReps = currentReps + 1L
                val nextDays = when (currentReps) {
                    0L -> 4.0
                    1L -> 8.0
                    else -> max(2.0, currentInterval * newEf * 1.3)
                }.coerceAtMost(36500.0)
                ScheduleOutcome(
                    repetitions = nextReps,
                    intervalDays = nextDays,
                    easeFactor = newEf,
                    stability = max(2.0, nextDays),
                    difficulty = max(1.0, card.difficulty - 0.4),
                    leitnerBox = min(5L, card.leitnerBox + 1L),
                    dueAtMillis = nowMillis + (nextDays * DAY_MS).roundToLong(),
                    lapses = card.lapses
                )
            }
        }
    }

    private fun scheduleFsrs(
        card: Flashcard,
        rating: ReviewRating,
        targetRetention: Float,
        nowMillis: Long
    ): ScheduleOutcome {
        val retention = targetRetention.toDouble().coerceIn(0.75, 0.97)
        val elapsedDays = if (card.lastReviewedMillis > 0L) {
            max(0.0, (nowMillis - card.lastReviewedMillis).toDouble() / DAY_MS.toDouble())
        } else {
            0.0
        }

        val currentDifficulty = card.difficulty.coerceIn(1.0, 10.0)
        val currentStability = max(0.0, card.stability)

        if (currentStability <= 0.0 || card.repetitions == 0L) {
            val initStability = when (rating) {
                ReviewRating.AGAIN -> 0.4
                ReviewRating.HARD -> 1.2
                ReviewRating.GOOD -> 3.0
                ReviewRating.EASY -> 7.0
            }
            val initDifficulty = when (rating) {
                ReviewRating.AGAIN -> 7.0
                ReviewRating.HARD -> 6.0
                ReviewRating.GOOD -> 5.0
                ReviewRating.EASY -> 3.5
            }
            val nextDays = if (rating == ReviewRating.AGAIN) 0.0 else fsrsInterval(initStability, retention)
            val dueMillis = if (rating == ReviewRating.AGAIN) {
                nowMillis + 10L * MINUTE_MS
            } else {
                nowMillis + (max(1.0, nextDays) * DAY_MS).roundToLong()
            }
            return ScheduleOutcome(
                repetitions = if (rating == ReviewRating.AGAIN) 0L else 1L,
                intervalDays = if (rating == ReviewRating.AGAIN) 0.0 else max(1.0, nextDays),
                easeFactor = card.easeFactor.coerceIn(1.3, 5.0),
                stability = initStability,
                difficulty = initDifficulty,
                leitnerBox = if (rating == ReviewRating.AGAIN) 1L else 2L,
                dueAtMillis = dueMillis,
                lapses = if (rating == ReviewRating.AGAIN) card.lapses + 1L else card.lapses
            )
        }

        // Retrievability R = (1 + elapsed / (9 * S))^-1
        val retrievability = (1.0 + elapsedDays / (9.0 * currentStability)).pow(-1.0)
        val difficultyDelta = when (rating) {
            ReviewRating.AGAIN -> 1.2
            ReviewRating.HARD -> 0.4
            ReviewRating.GOOD -> -0.1
            ReviewRating.EASY -> -0.6
        }
        val nextDifficulty = (currentDifficulty + difficultyDelta)
            .let { 0.85 * it + 0.15 * 5.0 } // mean reversion toward 5.0
            .coerceIn(1.0, 10.0)

        if (rating == ReviewRating.AGAIN) {
            val resetStability = max(0.5, currentStability * 0.35)
            return ScheduleOutcome(
                repetitions = 0L,
                intervalDays = 0.0,
                easeFactor = max(1.3, card.easeFactor - 0.15),
                stability = resetStability,
                difficulty = nextDifficulty,
                leitnerBox = 1L,
                dueAtMillis = nowMillis + 10L * MINUTE_MS,
                lapses = card.lapses + 1L
            )
        }

        val ratingMultiplier = when (rating) {
            ReviewRating.HARD -> 0.8
            ReviewRating.GOOD -> 1.0
            ReviewRating.EASY -> 1.35
            ReviewRating.AGAIN -> 0.35
        }
        val gain = 1.0 + exp(0.85) *
            (11.0 - nextDifficulty) *
            currentStability.pow(-0.15) *
            (exp((1.0 - retrievability) * 1.4) - 1.0) *
            ratingMultiplier
        val nextStability = (currentStability * max(1.05, gain)).coerceAtMost(36500.0)
        val nextDays = max(1.0, fsrsInterval(nextStability, retention)).coerceAtMost(36500.0)

        return ScheduleOutcome(
            repetitions = card.repetitions + 1L,
            intervalDays = nextDays,
            easeFactor = card.easeFactor.coerceIn(1.3, 5.0),
            stability = nextStability,
            difficulty = nextDifficulty,
            leitnerBox = min(5L, card.leitnerBox + 1L),
            dueAtMillis = nowMillis + (nextDays * DAY_MS).roundToLong(),
            lapses = card.lapses
        )
    }

    private fun fsrsInterval(stability: Double, retention: Double): Double {
        // Solving R = (1 + t / (9 * S))^-1 for t -> t = 9 * S * (1/R - 1)
        return max(0.5, 9.0 * stability * (1.0 / retention - 1.0))
    }

    private fun scheduleLeitner(
        card: Flashcard,
        rating: ReviewRating,
        nowMillis: Long
    ): ScheduleOutcome {
        val currentBox = card.leitnerBox.toInt().coerceIn(1, 5)
        return when (rating) {
            ReviewRating.AGAIN -> ScheduleOutcome(
                repetitions = 0L,
                intervalDays = 0.0,
                easeFactor = card.easeFactor.coerceIn(1.3, 5.0),
                stability = 0.5,
                difficulty = min(10.0, card.difficulty + 0.5),
                leitnerBox = 1L,
                dueAtMillis = nowMillis + 10L * MINUTE_MS,
                lapses = card.lapses + 1L
            )
            ReviewRating.HARD -> {
                val nextBox = currentBox
                val days = LEITNER_INTERVALS_DAYS[nextBox - 1]
                ScheduleOutcome(
                    repetitions = max(1L, card.repetitions),
                    intervalDays = days,
                    easeFactor = card.easeFactor.coerceIn(1.3, 5.0),
                    stability = days,
                    difficulty = card.difficulty.coerceIn(1.0, 10.0),
                    leitnerBox = nextBox.toLong(),
                    dueAtMillis = nowMillis + (days * DAY_MS).roundToLong(),
                    lapses = card.lapses
                )
            }
            ReviewRating.GOOD -> {
                val nextBox = min(5, currentBox + 1)
                val days = LEITNER_INTERVALS_DAYS[nextBox - 1]
                ScheduleOutcome(
                    repetitions = card.repetitions + 1L,
                    intervalDays = days,
                    easeFactor = card.easeFactor.coerceIn(1.3, 5.0),
                    stability = days,
                    difficulty = card.difficulty.coerceIn(1.0, 10.0),
                    leitnerBox = nextBox.toLong(),
                    dueAtMillis = nowMillis + (days * DAY_MS).roundToLong(),
                    lapses = card.lapses
                )
            }
            ReviewRating.EASY -> {
                val nextBox = min(5, currentBox + 2)
                val days = LEITNER_INTERVALS_DAYS[nextBox - 1]
                ScheduleOutcome(
                    repetitions = card.repetitions + 1L,
                    intervalDays = days,
                    easeFactor = min(5.0, card.easeFactor + 0.1),
                    stability = days,
                    difficulty = max(1.0, card.difficulty - 0.3),
                    leitnerBox = nextBox.toLong(),
                    dueAtMillis = nowMillis + (days * DAY_MS).roundToLong(),
                    lapses = card.lapses
                )
            }
        }
    }
}
