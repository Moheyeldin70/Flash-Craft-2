package com.example

import com.example.domain.model.Flashcard
import com.example.domain.model.ReviewRating
import com.example.domain.model.SrsAlgorithm
import com.example.domain.model.UserSettings
import com.example.domain.srs.SpacedRepetitionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpacedRepetitionEngineTest {

    private val baseTime = 1_700_000_000_000L
    private val dayMs = 24L * 60L * 60L * 1000L

    @Test
    fun `sm2 progresses 1d then 6d on consecutive good ratings`() {
        val settings = UserSettings(algorithm = SrsAlgorithm.SM2)
        val newCard = Flashcard(id = "c1", front = "Q", back = "A")

        val afterFirst = SpacedRepetitionEngine.applyReview(
            newCard, ReviewRating.GOOD, settings, baseTime
        )
        assertEquals(1L, afterFirst.repetitions)
        assertEquals(1.0, afterFirst.intervalDays, 0.01)
        assertEquals(baseTime + dayMs, afterFirst.dueAtMillis)

        val afterSecond = SpacedRepetitionEngine.applyReview(
            afterFirst, ReviewRating.GOOD, settings, baseTime + dayMs
        )
        assertEquals(2L, afterSecond.repetitions)
        assertEquals(6.0, afterSecond.intervalDays, 0.01)
    }

    @Test
    fun `sm2 resets repetitions and increments lapses on again`() {
        val settings = UserSettings(algorithm = SrsAlgorithm.SM2)
        val learnedCard = Flashcard(
            id = "c2",
            front = "Q",
            back = "A",
            repetitions = 4L,
            intervalDays = 18.0,
            easeFactor = 2.5
        )
        val afterAgain = SpacedRepetitionEngine.applyReview(
            learnedCard, ReviewRating.AGAIN, settings, baseTime
        )
        assertEquals(0L, afterAgain.repetitions)
        assertEquals(1L, afterAgain.lapses)
        assertTrue(afterAgain.easeFactor < 2.5)
        assertTrue(afterAgain.dueAtMillis > baseTime && afterAgain.dueAtMillis < baseTime + dayMs)
    }

    @Test
    fun `fsrs respects target retention and increases stability on easy`() {
        val strictSettings = UserSettings(algorithm = SrsAlgorithm.FSRS, targetRetention = 0.95f)
        val relaxedSettings = UserSettings(algorithm = SrsAlgorithm.FSRS, targetRetention = 0.85f)
        val card = Flashcard(id = "c3", front = "Q", back = "A")

        val strict = SpacedRepetitionEngine.applyReview(card, ReviewRating.GOOD, strictSettings, baseTime)
        val relaxed = SpacedRepetitionEngine.applyReview(card, ReviewRating.GOOD, relaxedSettings, baseTime)

        assertTrue(relaxed.intervalDays > strict.intervalDays)
        assertTrue(strict.stability > 0.0)
    }

    @Test
    fun `leitner advances boxes and resets to box 1 on again`() {
        val settings = UserSettings(algorithm = SrsAlgorithm.LEITNER)
        val card = Flashcard(id = "c4", front = "Q", back = "A", leitnerBox = 1L)

        val box2 = SpacedRepetitionEngine.applyReview(card, ReviewRating.GOOD, settings, baseTime)
        assertEquals(2L, box2.leitnerBox)
        assertEquals(2.0, box2.intervalDays, 0.01)

        val box4 = SpacedRepetitionEngine.applyReview(box2, ReviewRating.EASY, settings, baseTime)
        assertEquals(4L, box4.leitnerBox)
        assertEquals(12.0, box4.intervalDays, 0.01)

        val reset = SpacedRepetitionEngine.applyReview(box4, ReviewRating.AGAIN, settings, baseTime)
        assertEquals(1L, reset.leitnerBox)
        assertEquals(1L, reset.lapses)
    }
}
