package com.example.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.Deck
import com.example.domain.model.Flashcard
import com.example.domain.model.StudyLog
import com.example.domain.model.UserSettings
import com.example.domain.srs.SpacedRepetitionEngine
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    decksState: UiState<List<Deck>>,
    cardsState: UiState<List<Flashcard>>,
    studyLogsState: UiState<List<StudyLog>>,
    settings: UserSettings
) {
    val decks = (decksState as? UiState.Success)?.data.orEmpty()
    val cards = (cardsState as? UiState.Success)?.data.orEmpty()
    val logs = (studyLogsState as? UiState.Success)?.data.orEmpty()

    val nowMillis = remember(cards.size) { System.currentTimeMillis() }
    val dueCount = remember(cards, nowMillis) {
        cards.count { SpacedRepetitionEngine.isDue(it, nowMillis) }
    }
    val masteredCount = remember(cards) {
        cards.count { SpacedRepetitionEngine.isMastered(it) }
    }
    val newCount = remember(cards) {
        cards.count { SpacedRepetitionEngine.isNew(it) }
    }
    val learningCount = (cards.size - masteredCount - newCount).coerceAtLeast(0)

    val todayKey = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val todayLog = logs.firstOrNull { it.dateKey == todayKey }
    val todayReviews = todayLog?.reviewsCount?.toInt() ?: 0
    val totalReviews = logs.sumOf { it.reviewsCount }.toInt()
    val totalCorrect = logs.sumOf { it.correctCount }.toInt()
    val retentionRate = if (totalReviews > 0) {
        ((totalCorrect.toFloat() / totalReviews.toFloat()) * 100f).toInt()
    } else 100

    val last7Days = remember(logs) {
        val keyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val labelFmt = SimpleDateFormat("EEE", Locale.US)
        val logByDate = logs.associateBy { it.dateKey }
        (6 downTo 0).map { daysAgo ->
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
            val key = keyFmt.format(cal.time)
            val label = labelFmt.format(cal.time).take(2)
            val count = logByDate[key]?.reviewsCount?.toInt() ?: 0
            label to count
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Study Progress", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = "Using ${settings.algorithm.displayName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                // Daily Goal Ring Card
                item {
                    val goalFraction = (todayReviews.toFloat() / settings.dailyReviewGoal.coerceAtLeast(1).toFloat())
                        .coerceIn(0f, 1f)
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val trackColor = MaterialTheme.colorScheme.surfaceVariant

                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(92.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val strokeWidth = 10.dp.toPx()
                                    drawArc(
                                        color = trackColor,
                                        startAngle = 0f,
                                        sweepAngle = 360f,
                                        useCenter = false,
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                    )
                                    drawArc(
                                        color = primaryColor,
                                        startAngle = -90f,
                                        sweepAngle = 360f * goalFraction,
                                        useCenter = false,
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                    )
                                }
                                Text(
                                    text = "${(goalFraction * 100).toInt()}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(18.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Today's Review Goal",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$todayReviews of ${settings.dailyReviewGoal} cards reviewed today",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "All-time recall rate: $retentionRate% ($totalReviews reviews)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                }

                // 7-Day Activity Bar Chart Card
                item {
                    val maxReviews = (last7Days.maxOfOrNull { it.second } ?: 0)
                        .coerceAtLeast(settings.dailyReviewGoal.coerceAtMost(10))
                    val barColor = MaterialTheme.colorScheme.primary
                    val emptyBarColor = MaterialTheme.colorScheme.surfaceVariant

                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "Last 7 Days Activity",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                            ) {
                                val count = last7Days.size.coerceAtLeast(1)
                                val slotWidth = size.width / count.toFloat()
                                val barWidth = slotWidth * 0.48f
                                last7Days.forEachIndexed { idx, (_, value) ->
                                    val ratio = (value.toFloat() / maxReviews.toFloat()).coerceIn(0f, 1f)
                                    val barHeight = maxOf(8.dp.toPx(), size.height * ratio)
                                    val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                                    drawRoundRect(
                                        color = if (value > 0) barColor else emptyBarColor,
                                        topLeft = Offset(left, size.height - barHeight),
                                        size = Size(barWidth, barHeight),
                                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                last7Days.forEach { (label, count) ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = count.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Collection Breakdown Card
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "Memory Breakdown (${decks.size} decks)",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MetricTile("Due Now", dueCount.toString(), Modifier.weight(1f))
                                MetricTile("New", newCount.toString(), Modifier.weight(1f))
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MetricTile("Learning", learningCount.toString(), Modifier.weight(1f))
                                MetricTile("Mastered", masteredCount.toString(), Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
