package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.domain.model.Flashcard
import com.example.domain.model.MediaType
import com.example.domain.model.ReviewRating
import com.example.domain.model.UserSettings
import com.example.domain.srs.SpacedRepetitionEngine
import com.example.ui.components.CardMediaPreview
import com.example.ui.theme.RatingAgainColor
import com.example.ui.theme.RatingEasyColor
import com.example.ui.theme.RatingGoodColor
import com.example.ui.theme.RatingHardColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    title: String,
    cards: List<Flashcard>,
    practiceAll: Boolean,
    settings: UserSettings,
    onSubmitReview: (card: Flashcard, rating: ReviewRating, updateSchedule: Boolean) -> Unit,
    onBack: () -> Unit
) {
    val initialQueue = remember(practiceAll) {
        val now = System.currentTimeMillis()
        if (practiceAll) {
            cards
        } else {
            val due = cards.filter { SpacedRepetitionEngine.isDue(it, now) }
            val newCards = due.filter { SpacedRepetitionEngine.isNew(it) }.take(settings.newCardsPerDay)
            val reviewCards = due.filterNot { SpacedRepetitionEngine.isNew(it) }
            reviewCards + newCards
        }
    }

    val studyQueue = remember {
        mutableStateListOf<Flashcard>().apply { addAll(initialQueue) }
    }
    var currentIndex by remember { mutableIntStateOf(0) }
    var isFlipped by remember { mutableStateOf(false) }
    var hintRevealed by remember(currentIndex) { mutableStateOf(settings.showHintsAutomatically) }
    var completedReviews by remember { mutableIntStateOf(0) }
    var rememberedReviews by remember { mutableIntStateOf(0) }

    val currentCard = studyQueue.getOrNull(currentIndex)
    val progress = if (studyQueue.isEmpty()) 1f else {
        (currentIndex.toFloat() / studyQueue.size.toFloat()).coerceIn(0f, 1f)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (currentCard != null) {
                                "Card ${currentIndex + 1} of ${studyQueue.size} • ${settings.algorithm.displayName}"
                            } else {
                                "Session complete"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Exit study session"
                        )
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
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                if (currentCard == null) {
                    SessionCompleteCard(
                        reviewedCount = completedReviews,
                        rememberedCount = rememberedReviews,
                        wasEmpty = initialQueue.isEmpty(),
                        onDone = onBack,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    val density = LocalDensity.current.density
                    val rotationY by animateFloatAsState(
                        targetValue = if (isFlipped) 180f else 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "card_flip_rotation"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            onClick = { isFlipped = !isFlipped },
                            shape = RoundedCornerShape(26.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 320.dp, max = 520.dp)
                                .graphicsLayer {
                                    this.rotationY = rotationY
                                    cameraDistance = 12f * density
                                }
                                .testTag("study_flashcard")
                        ) {
                            if (rotationY <= 90f) {
                                // FRONT FACE
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(50.dp)
                                    ) {
                                        Text(
                                            text = "FRONT • TAP TO FLIP",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(20.dp))
                                    if (currentCard.resolvedFrontMediaType != MediaType.NONE &&
                                        currentCard.frontMediaUri.isNotBlank()
                                    ) {
                                        CardMediaPreview(
                                            mediaUri = currentCard.frontMediaUri,
                                            mediaType = currentCard.resolvedFrontMediaType,
                                            mediaName = currentCard.frontMediaName,
                                            autoPlayAudio = settings.autoPlayAudio
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                    Text(
                                        text = currentCard.front,
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )

                                    if (currentCard.hint.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(20.dp))
                                        if (hintRevealed) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(
                                                        horizontal = 12.dp,
                                                        vertical = 8.dp
                                                    ),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Lightbulb,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        text = currentCard.hint,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                }
                                            }
                                        } else {
                                            FilledTonalButton(
                                                onClick = { hintRevealed = true },
                                                modifier = Modifier.height(40.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Lightbulb,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text("Show Hint")
                                            }
                                        }
                                    }
                                }
                            } else {
                                // BACK FACE (Counter-rotated 180f so text is readable)
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { this.rotationY = 180f }
                                        .verticalScroll(rememberScrollState())
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = RoundedCornerShape(50.dp)
                                    ) {
                                        Text(
                                            text = "ANSWER",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = currentCard.front,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    if (currentCard.resolvedBackMediaType != MediaType.NONE &&
                                        currentCard.backMediaUri.isNotBlank()
                                    ) {
                                        CardMediaPreview(
                                            mediaUri = currentCard.backMediaUri,
                                            mediaType = currentCard.resolvedBackMediaType,
                                            mediaName = currentCard.backMediaName,
                                            autoPlayAudio = settings.autoPlayAudio
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                    Text(
                                        text = currentCard.back,
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // Bottom Action Area
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        if (!isFlipped) {
                            Button(
                                onClick = { isFlipped = true },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("show_answer_button")
                            ) {
                                Icon(Icons.Rounded.Visibility, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Show Answer", style = MaterialTheme.typography.labelLarge)
                            }
                        } else {
                            val intervalPreviews = remember(currentCard.id, settings) {
                                SpacedRepetitionEngine.previewIntervals(
                                    card = currentCard,
                                    settings = settings,
                                    nowMillis = System.currentTimeMillis()
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RatingButton(
                                    rating = ReviewRating.AGAIN,
                                    color = RatingAgainColor,
                                    intervalLabel = if (settings.showIntervalPreviews) {
                                        intervalPreviews[ReviewRating.AGAIN].orEmpty()
                                    } else "",
                                    onClick = {
                                        onSubmitReview(currentCard, ReviewRating.AGAIN, !practiceAll)
                                        completedReviews++
                                        // Re-queue forgotten cards later in the same session so the user learns them
                                        if (!practiceAll) {
                                            studyQueue.add(
                                                SpacedRepetitionEngine.applyReview(
                                                    currentCard,
                                                    ReviewRating.AGAIN,
                                                    settings,
                                                    System.currentTimeMillis()
                                                )
                                            )
                                        }
                                        isFlipped = false
                                        currentIndex++
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                RatingButton(
                                    rating = ReviewRating.HARD,
                                    color = RatingHardColor,
                                    intervalLabel = if (settings.showIntervalPreviews) {
                                        intervalPreviews[ReviewRating.HARD].orEmpty()
                                    } else "",
                                    onClick = {
                                        onSubmitReview(currentCard, ReviewRating.HARD, !practiceAll)
                                        completedReviews++
                                        rememberedReviews++
                                        isFlipped = false
                                        currentIndex++
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                RatingButton(
                                    rating = ReviewRating.GOOD,
                                    color = RatingGoodColor,
                                    intervalLabel = if (settings.showIntervalPreviews) {
                                        intervalPreviews[ReviewRating.GOOD].orEmpty()
                                    } else "",
                                    onClick = {
                                        onSubmitReview(currentCard, ReviewRating.GOOD, !practiceAll)
                                        completedReviews++
                                        rememberedReviews++
                                        isFlipped = false
                                        currentIndex++
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                RatingButton(
                                    rating = ReviewRating.EASY,
                                    color = RatingEasyColor,
                                    intervalLabel = if (settings.showIntervalPreviews) {
                                        intervalPreviews[ReviewRating.EASY].orEmpty()
                                    } else "",
                                    onClick = {
                                        onSubmitReview(currentCard, ReviewRating.EASY, !practiceAll)
                                        completedReviews++
                                        rememberedReviews++
                                        isFlipped = false
                                        currentIndex++
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingButton(
    rating: ReviewRating,
    color: Color,
    intervalLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = 0.14f),
            contentColor = color
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 6.dp,
            vertical = 10.dp
        ),
        modifier = modifier
            .height(58.dp)
            .testTag("rate_${rating.name.lowercase()}_button")
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = rating.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            AnimatedVisibility(visible = intervalLabel.isNotBlank()) {
                Text(
                    text = intervalLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = color.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
private fun SessionCompleteCard(
    reviewedCount: Int,
    rememberedCount: Int,
    wasEmpty: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accuracy = if (reviewedCount > 0) {
        ((rememberedCount.toFloat() / reviewedCount.toFloat()) * 100f).toInt()
    } else 100

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(50.dp),
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = if (wasEmpty) Icons.Rounded.Flip else Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = if (wasEmpty) "Nothing due right now!" else "Great study session!",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (wasEmpty) {
                        "All cards in this deck are scheduled for future review. You can use Practice All anytime."
                    } else {
                        "You reviewed $reviewedCount cards with $accuracy% recall accuracy. Your spaced repetition schedule is updated."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(22.dp))
                Button(
                    onClick = onDone,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("session_done_button")
                ) {
                    Text("Back to Decks")
                }
            }
        }
    }
}
