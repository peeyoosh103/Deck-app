package com.example.statistics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.common_components.AdaptiveContentContainer
import com.example.common_components.DeckProgressIndicator
import com.example.common_components.MemoDeckTopBar
import com.example.common_components.StatSummaryTile
import com.example.common_components.formatPercent
import com.example.models.DailyStudyPoint
import com.example.models.DeckSummary
import com.example.models.QuizHistoryPoint
import com.example.models.QuizStatsSummary
import com.example.repositories.MemoDeckRepository
import com.example.theme.LocalSemanticColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.math.max
import kotlin.math.roundToInt

data class StatisticsUiState(
    val decks: List<DeckSummary> = emptyList(),
    val quizStats: QuizStatsSummary = QuizStatsSummary(),
    val dailyStudyPoints: List<DailyStudyPoint> = emptyList(),
    val quizHistoryPoints: List<QuizHistoryPoint> = emptyList()
)

class StatisticsViewModel(repository: MemoDeckRepository) : ViewModel() {
    val uiState: StateFlow<StatisticsUiState> = combine(
        repository.observeDeckSummaries(),
        repository.observeQuizHistory(),
        repository.observeDailyStudyTotals()
    ) { decks, quizHistory, dailyRows ->
        StatisticsUiState(
            decks = decks,
            quizStats = StatisticsCalculator.calculateQuizStats(quizHistory),
            dailyStudyPoints = StatisticsCalculator.buildRecentDailyStudyPoints(dailyRows, days = 7),
            quizHistoryPoints = StatisticsCalculator.buildQuizHistoryPoints(quizHistory)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StatisticsUiState()
    )

    class Factory(private val repository: MemoDeckRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = StatisticsViewModel(repository) as T
    }
}

@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val semantic = LocalSemanticColors.current

    Scaffold(
        topBar = {
            MemoDeckTopBar(
                title = stringResource(R.string.statistics_title),
                onNavigateBack = onNavigateBack
            )
        }
    ) { innerPadding ->
        AdaptiveContentContainer(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // B. Quiz Statistics Summary
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(R.string.stats_quiz_performance),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StatSummaryTile(
                                label = "Completed Quizzes",
                                value = state.quizStats.totalCompletedQuizzes.toString(),
                                icon = Icons.AutoMirrored.Filled.FactCheck,
                                accentColor = MaterialTheme.colorScheme.primary,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            StatSummaryTile(
                                label = "Highest Score",
                                value = if (state.quizStats.totalCompletedQuizzes == 0) "—" else formatPercent(state.quizStats.highestScore),
                                icon = Icons.Filled.EmojiEvents,
                                accentColor = semantic.success,
                                containerColor = semantic.successContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StatSummaryTile(
                                label = "Average Score",
                                value = if (state.quizStats.totalCompletedQuizzes == 0) "—" else formatPercent(state.quizStats.averageScore),
                                icon = Icons.Filled.QueryStats,
                                accentColor = MaterialTheme.colorScheme.secondary,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            StatSummaryTile(
                                label = "Last Quiz Score",
                                value = state.quizStats.lastQuizScore?.let { formatPercent(it) } ?: "—",
                                icon = Icons.Filled.Timeline,
                                accentColor = MaterialTheme.colorScheme.tertiary,
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Graph 1: Daily Study Activity Graph
                item {
                    DailyStudyActivityGraphCard(points = state.dailyStudyPoints)
                }

                // Graph 2: Quiz Score History Graph
                item {
                    QuizScoreHistoryGraphCard(points = state.quizHistoryPoints)
                }

                // A. Deck Progress
                item {
                    Text(
                        text = stringResource(R.string.stats_deck_progress),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (state.decks.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = "No decks created yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(18.dp)
                            )
                        }
                    }
                } else {
                    items(state.decks, key = { it.deckId }) { deck ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = deck.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = formatPercent(deck.progressPercentage),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                DeckProgressIndicator(percentage = deck.progressPercentage)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Total: ${deck.totalCards}", style = MaterialTheme.typography.labelSmall)
                                    Text("Remembered: ${deck.rememberedCards}", style = MaterialTheme.typography.labelSmall, color = semantic.success)
                                    Text("Forgotten: ${deck.forgottenCards}", style = MaterialTheme.typography.labelSmall, color = semantic.danger)
                                    Text("Unstudied: ${deck.unstudiedCards}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun DailyStudyActivityGraphCard(points: List<DailyStudyPoint>) {
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val maxVal = max(1, points.maxOfOrNull { it.cardsStudied } ?: 0)
    val totalRecent = points.sumOf { it.cardsStudied }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.stats_daily_study_graph),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "$totalRecent studied (7d)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (totalRecent == 0) {
                Text(
                    text = "No study activity recorded in the last 7 days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                if (points.isEmpty()) return@Canvas
                val slotWidth = size.width / points.size
                val barWidth = (slotWidth * 0.48f).coerceAtMost(38.dp.toPx())
                val chartHeight = size.height - 8.dp.toPx()

                // Horizontal reference lines
                for (step in 0..2) {
                    val y = chartHeight * (step / 2f)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                points.forEachIndexed { idx, point ->
                    val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                    // Track
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(left, 0f),
                        size = Size(barWidth, chartHeight),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                    if (point.cardsStudied > 0) {
                        val heightRatio = (point.cardsStudied.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
                        val barH = max(8.dp.toPx(), chartHeight * heightRatio)
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(left, chartHeight - barH),
                            size = Size(barWidth, barH),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                points.forEach { point ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = point.cardsStudied.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = point.displayLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizScoreHistoryGraphCard(points: List<QuizHistoryPoint>) {
    val lineColor = MaterialTheme.colorScheme.secondary
    val dotFill = MaterialTheme.colorScheme.surface
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.stats_quiz_history_graph),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "0% – 100%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (points.isEmpty()) {
                Text(
                    text = "Complete a quiz to track your score history over time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                ) {
                    val padH = 14.dp.toPx()
                    val padV = 12.dp.toPx()
                    val usableW = (size.width - padH * 2).coerceAtLeast(1f)
                    val usableH = (size.height - padV * 2).coerceAtLeast(1f)

                    // Grid lines at 0%, 50%, 100%
                    listOf(0f, 0.5f, 1f).forEach { frac ->
                        val y = padV + usableH * (1f - frac)
                        drawLine(
                            color = gridColor,
                            start = Offset(padH, y),
                            end = Offset(size.width - padH, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    val coords = points.mapIndexed { idx, pt ->
                        val x = if (points.size == 1) {
                            size.width / 2f
                        } else {
                            padH + usableW * (idx.toFloat() / (points.size - 1).toFloat())
                        }
                        val y = padV + usableH * (1f - (pt.scorePercentage / 100f).coerceIn(0f, 1f))
                        Offset(x, y)
                    }

                    if (coords.size >= 2) {
                        val path = Path().apply {
                            moveTo(coords.first().x, coords.first().y)
                            for (i in 1 until coords.size) {
                                lineTo(coords[i].x, coords[i].y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = lineColor,
                            style = Stroke(
                                width = 3.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    coords.forEach { center ->
                        drawCircle(color = lineColor, radius = 6.dp.toPx(), center = center)
                        drawCircle(color = dotFill, radius = 3.dp.toPx(), center = center)
                    }
                }

                // Recent quiz labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val displayed = if (points.size <= 5) points else listOf(points.first(), points[points.size / 2], points.last())
                    displayed.forEach { pt ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${pt.scorePercentage.roundToInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = pt.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
