package com.example.quiz

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.common_components.AdaptiveContentContainer
import com.example.common_components.ConfirmationDialog
import com.example.common_components.EmptyStateCard
import com.example.common_components.MemoDeckTopBar
import com.example.common_components.formatPercent
import com.example.models.QuizQuestionCountOption
import com.example.models.QuizResultData
import com.example.models.WrongAnswerRecord
import com.example.theme.LocalSemanticColors

@Composable
fun QuizFlowScreen(
    preselectedDeckId: Long?,
    viewModel: QuizViewModel,
    onCreateDeck: () -> Unit,
    onBackToDeck: (Long) -> Unit,
    onExitToParent: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val decks by viewModel.decks.collectAsStateWithLifecycle()
    var showExitDialog by remember { mutableStateOf(false) }

    LaunchedEffect(preselectedDeckId) {
        if (state.phase == QuizPhase.SETUP || preselectedDeckId != null) {
            viewModel.initializeSetup(preselectedDeckId)
        }
    }

    when (state.phase) {
        QuizPhase.SETUP -> {
            QuizSetupView(
                decks = decks,
                state = state,
                showBack = onExitToParent != null,
                onBack = { onExitToParent?.invoke() },
                onSelectDeck = { viewModel.selectDeck(it) },
                onSelectCount = { viewModel.selectCountOption(it) },
                onStartQuiz = { viewModel.startQuiz() },
                onCreateDeck = onCreateDeck
            )
        }

        QuizPhase.IN_PROGRESS -> {
            BackHandler { showExitDialog = true }
            QuizSessionView(
                state = state,
                onSelectOption = { viewModel.selectAnswer(it) },
                onNext = { viewModel.nextQuestionOrFinish() },
                onRequestExit = { showExitDialog = true }
            )
        }

        QuizPhase.RESULT -> {
            val result = state.resultData
            if (result != null) {
                QuizResultView(
                    result = result,
                    onRetry = { viewModel.retryQuiz() },
                    onReviewWrong = { viewModel.openWrongAnswerReview() },
                    onBackToDeck = {
                        viewModel.abortIncompleteQuiz()
                        onBackToDeck(result.deckId)
                    }
                )
            }
        }

        QuizPhase.REVIEW_WRONG -> {
            BackHandler { viewModel.backToResultFromReview() }
            WrongAnswerReviewView(
                wrongRecords = state.resultData?.wrongRecords.orEmpty(),
                onBack = { viewModel.backToResultFromReview() }
            )
        }
    }

    if (showExitDialog) {
        ConfirmationDialog(
            title = stringResource(R.string.quiz_exit_title),
            message = stringResource(R.string.quiz_exit_message),
            confirmLabel = "Exit Quiz",
            isDestructive = true,
            onConfirm = {
                showExitDialog = false
                viewModel.abortIncompleteQuiz()
                onExitToParent?.invoke()
            },
            onDismiss = { showExitDialog = false }
        )
    }
}

@Composable
private fun QuizSetupView(
    decks: List<com.example.models.DeckSummary>,
    state: QuizUiState,
    showBack: Boolean,
    onBack: () -> Unit,
    onSelectDeck: (Long) -> Unit,
    onSelectCount: (QuizQuestionCountOption) -> Unit,
    onStartQuiz: () -> Unit,
    onCreateDeck: () -> Unit
) {
    Scaffold(
        topBar = {
            MemoDeckTopBar(
                title = stringResource(R.string.quiz_setup_title),
                onNavigateBack = if (showBack) onBack else null
            )
        }
    ) { innerPadding ->
        AdaptiveContentContainer(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (decks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateCard(
                        icon = Icons.Filled.Folder,
                        title = "No Decks Available",
                        message = "Create a deck with at least 4 flashcards having unique answers to generate a Multiple Choice Quiz.",
                        actionLabel = stringResource(R.string.home_create_deck),
                        onAction = onCreateDeck
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Text(
                            text = stringResource(R.string.quiz_select_deck),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    items(decks, key = { it.deckId }) { deck ->
                        val isSelected = state.selectedDeckId == deck.deckId
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quiz_deck_option_${deck.deckId}")
                                .clickable { onSelectDeck(deck.deckId) },
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onSelectDeck(deck.deckId) }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = deck.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${deck.totalCards} card${if (deck.totalCards == 1) "" else "s"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (state.selectedDeckId != null) {
                        item {
                            val semantic = LocalSemanticColors.current
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(
                                    containerColor = if (state.eligibleQuestionCount > 0) {
                                        semantic.successContainer.copy(alpha = 0.6f)
                                    } else {
                                        semantic.warningContainer.copy(alpha = 0.7f)
                                    }
                                )
                            ) {
                                Text(
                                    text = if (state.eligibleQuestionCount > 0) {
                                        "${state.eligibleQuestionCount} eligible multiple-choice question${if (state.eligibleQuestionCount == 1) "" else "s"} available in \"${state.selectedDeckName}\"."
                                    } else {
                                        "This deck needs at least 4 flashcards with unique answers to generate 4-option quiz questions."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    }

                    item {
                        Text(
                            text = stringResource(R.string.quiz_question_count),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(QuizQuestionCountOption.FIVE, QuizQuestionCountOption.TEN).forEach { opt ->
                                    FilterChip(
                                        selected = state.selectedCountOption == opt,
                                        onClick = { onSelectCount(opt) },
                                        label = { Text(opt.label) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("quiz_count_${opt.name.lowercase()}")
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(QuizQuestionCountOption.TWENTY, QuizQuestionCountOption.ALL).forEach { opt ->
                                    FilterChip(
                                        selected = state.selectedCountOption == opt,
                                        onClick = { onSelectCount(opt) },
                                        label = { Text(opt.label) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("quiz_count_${opt.name.lowercase()}")
                                    )
                                }
                            }
                        }
                    }

                    state.errorMessage?.let { err ->
                        item {
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    item {
                        Button(
                            onClick = onStartQuiz,
                            enabled = state.selectedDeckId != null && state.eligibleQuestionCount > 0,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("quiz_start_button")
                        ) {
                            Icon(Icons.Filled.Quiz, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.quiz_start))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizSessionView(
    state: QuizUiState,
    onSelectOption: (String) -> Unit,
    onNext: () -> Unit,
    onRequestExit: () -> Unit
) {
    val question = state.currentQuestion ?: return
    val total = state.questions.size
    val progress = (state.currentQuestionIndex + 1).toFloat() / total.toFloat()
    val answered = state.selectedOption != null
    val semantic = LocalSemanticColors.current

    Scaffold { innerPadding ->
        AdaptiveContentContainer(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.selectedDeckName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Question ${state.currentQuestionIndex + 1} / $total",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onRequestExit,
                        modifier = Modifier.testTag("quiz_exit_button")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Exit quiz")
                    }
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                )

                // Scrollable Question + 4 Options
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "QUESTION",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = question.question,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    question.options.forEachIndexed { idx, option ->
                        val isSelected = state.selectedOption == option
                        val isCorrectOption = QuizEngine.normalizeAnswer(option) ==
                            QuizEngine.normalizeAnswer(question.correctAnswer)

                        val containerColor = when {
                            !answered -> MaterialTheme.colorScheme.surface
                            isCorrectOption -> semantic.successContainer
                            isSelected -> semantic.dangerContainer
                            else -> MaterialTheme.colorScheme.surface
                        }
                        val borderColor = when {
                            !answered -> MaterialTheme.colorScheme.outlineVariant
                            isCorrectOption -> semantic.success
                            isSelected -> semantic.danger
                            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quiz_option_$idx")
                                .clickable(enabled = !answered) { onSelectOption(option) },
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = containerColor),
                            border = BorderStroke(
                                width = if (answered && (isCorrectOption || isSelected)) 2.dp else 1.dp,
                                color = borderColor
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = ('A' + idx).toString(),
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                if (answered && isCorrectOption) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = "Correct option",
                                        tint = semantic.success
                                    )
                                } else if (answered && isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.ErrorOutline,
                                        contentDescription = "Incorrect selection",
                                        tint = semantic.danger
                                    )
                                }
                            }
                        }
                    }

                    // Immediate Answer Feedback Banner
                    val chosen = state.selectedOption
                    if (chosen != null) {
                        val userWasCorrect = QuizEngine.normalizeAnswer(chosen) ==
                            QuizEngine.normalizeAnswer(question.correctAnswer)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quiz_feedback_banner"),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = if (userWasCorrect) semantic.successContainer else semantic.dangerContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (userWasCorrect) "Correct" else "Incorrect",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (userWasCorrect) semantic.success else semantic.danger
                                )
                                if (!userWasCorrect) {
                                    Text(
                                        text = "Correct Answer: ${question.correctAnswer}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = onNext,
                    enabled = answered,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("quiz_next_button")
                ) {
                    Text(
                        if (state.currentQuestionIndex == total - 1) "Finish Quiz"
                        else stringResource(R.string.action_next)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuizResultView(
    result: QuizResultData,
    onRetry: () -> Unit,
    onReviewWrong: () -> Unit,
    onBackToDeck: () -> Unit
) {
    val semantic = LocalSemanticColors.current

    Scaffold(
        topBar = { MemoDeckTopBar(title = stringResource(R.string.quiz_result_title)) }
    ) { innerPadding ->
        AdaptiveContentContainer(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = result.deckName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = formatPercent(result.accuracyPercentage),
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Accuracy",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Score: ${result.correctAnswers} / ${result.totalQuestions}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ResultStatItem("Total", result.totalQuestions.toString(), MaterialTheme.colorScheme.onSurface)
                            ResultStatItem("Correct", result.correctAnswers.toString(), semantic.success)
                            ResultStatItem("Wrong", result.wrongAnswers.toString(), semantic.danger)
                        }
                    }
                }

                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("quiz_retry_button")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.quiz_retry))
                }

                if (result.wrongRecords.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onReviewWrong,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("quiz_review_wrong_button")
                    ) {
                        Text("${stringResource(R.string.quiz_review_wrong)} (${result.wrongRecords.size})")
                    }
                }

                OutlinedButton(
                    onClick = onBackToDeck,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("quiz_back_to_deck_button")
                ) {
                    Text(stringResource(R.string.study_back_to_deck))
                }
            }
        }
    }
}

@Composable
private fun ResultStatItem(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WrongAnswerReviewView(
    wrongRecords: List<WrongAnswerRecord>,
    onBack: () -> Unit
) {
    val semantic = LocalSemanticColors.current
    Scaffold(
        topBar = {
            MemoDeckTopBar(
                title = stringResource(R.string.quiz_review_wrong),
                onNavigateBack = onBack
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(wrongRecords) { record ->
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
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = record.question,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = semantic.dangerContainer
                            ) {
                                Text(
                                    text = "Your Answer: ${record.selectedAnswer}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = semantic.danger,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = semantic.successContainer
                            ) {
                                Text(
                                    text = "Correct Answer: ${record.correctAnswer}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = semantic.success,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
