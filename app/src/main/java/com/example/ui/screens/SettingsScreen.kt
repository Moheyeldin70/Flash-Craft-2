package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.SrsAlgorithm
import com.example.domain.model.ThemeMode
import com.example.domain.model.UserSettings
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    userEmail: String?,
    userDisplayName: String?,
    onSelectAlgorithm: (SrsAlgorithm) -> Unit,
    onChangeRetention: (Float) -> Unit,
    onChangeNewCardsPerDay: (Int) -> Unit,
    onChangeDailyGoal: (Int) -> Unit,
    onToggleIntervalPreviews: (Boolean) -> Unit,
    onToggleAutoPlayAudio: (Boolean) -> Unit,
    onToggleShowHints: (Boolean) -> Unit,
    onSelectTheme: (ThemeMode) -> Unit,
    onResetDefaults: () -> Unit,
    onSignOut: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Settings", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = "Simple controls for your study schedule & display",
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
                // 1. Spaced Repetition Algorithm
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Spaced Repetition Algorithm",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Choose how FlashCraft decides when you see each card next.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            SrsAlgorithm.entries.forEach { algo ->
                                val selected = settings.algorithm == algo
                                Surface(
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectAlgorithm(algo) }
                                        .testTag("algorithm_option_${algo.name.lowercase()}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        RadioButton(
                                            selected = selected,
                                            onClick = { onSelectAlgorithm(algo) }
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = algo.displayName,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = algo.subtitle,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = algo.explanation,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            AnimatedVisibility(visible = settings.algorithm == SrsAlgorithm.FSRS) {
                                Column(modifier = Modifier.padding(top = 6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Target Memory Retention",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        Text(
                                            text = "${(settings.targetRetention * 100).roundToInt()}%",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Slider(
                                        value = settings.targetRetention,
                                        onValueChange = onChangeRetention,
                                        valueRange = 0.80f..0.96f,
                                        steps = 15
                                    )
                                    Text(
                                        text = "Higher retention schedules more frequent reviews; 90% is ideal for most learners.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Daily Study Pace
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Daily Study Pace", style = MaterialTheme.typography.titleMedium)

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Daily Review Goal", style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        text = "${settings.dailyReviewGoal} cards",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = settings.dailyReviewGoal.toFloat(),
                                    onValueChange = { onChangeDailyGoal(it.roundToInt()) },
                                    valueRange = 5f..150f
                                )
                            }

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("New Cards per Day", style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        text = "${settings.newCardsPerDay} new",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = settings.newCardsPerDay.toFloat(),
                                    onValueChange = { onChangeNewCardsPerDay(it.roundToInt()) },
                                    valueRange = 5f..80f
                                )
                            }
                        }
                    }
                }

                // 3. Study Experience & Appearance
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Study Experience", style = MaterialTheme.typography.titleMedium)

                            SettingSwitchRow(
                                title = "Show next interval on buttons",
                                subtitle = "Display 1d, 6d, etc. below Again / Hard / Good / Easy",
                                checked = settings.showIntervalPreviews,
                                onCheckedChange = onToggleIntervalPreviews
                            )
                            SettingSwitchRow(
                                title = "Auto-play card audio",
                                subtitle = "Automatically play attached audio clips when viewing a card",
                                checked = settings.autoPlayAudio,
                                onCheckedChange = onToggleAutoPlayAudio
                            )
                            SettingSwitchRow(
                                title = "Show hints automatically",
                                subtitle = "Display card hints on the front without tapping Show Hint",
                                checked = settings.showHintsAutomatically,
                                onCheckedChange = onToggleShowHints
                            )

                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Appearance", style = MaterialTheme.typography.labelLarge)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ThemeMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = settings.themeMode == mode,
                                        onClick = { onSelectTheme(mode) },
                                        label = { Text(mode.label) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Account & Reset
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Account", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = listOfNotNull(userDisplayName, userEmail)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" • ")
                                    .ifBlank { "Signed in with Google" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                TextButton(
                                    onClick = onResetDefaults,
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Icon(Icons.Rounded.RestartAlt, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Reset Settings")
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                OutlinedButton(
                                    onClick = onSignOut,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("sign_out_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Sign Out")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
