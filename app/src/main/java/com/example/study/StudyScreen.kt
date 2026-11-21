package com.example.study

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.common_components.AdaptiveContentContainer
import com.example.common_components.DeckProgressIndicator
import com.example.common_components.EmptyStateCard
import com.example.common_components.LearningStatusChip
import com.example.common_components.MemoDeckTopBar
import com.example.common_components.formatPercent
import com.example.models.LearningStatus
import com.example.theme.LocalSemanticColors

@Composable
fun StudyDeckSelectionScreen(
    viewModel: StudyViewModel,
    onSelectDeck: (Long) -> Unit,
    onCreateDeck: () -> Unit
) {
    val decks by viewModel.decks.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            MemoDeckTopBar(title = stringResource(R.string.study_select_deck))
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
                        message = "Create a deck and add flashcards to begin studying.",
                        actionLabel = stringResource(R.string.home_create_deck),
                        onAction = onCreateDeck
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(decks, key = { it.deckId }) { deck ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("study_deck_option_${deck.deckId}")
                                .clickable(enabled = deck.totalCards > 0) { onSelectDeck(deck.deckId) },
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = deck.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (deck.totalCards == 0) {
                                            "No flashcards in this deck"
                                        } else {
                                            "${deck.totalCards} cards • ${formatPercent(deck.progressPercentage)} remembered"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    DeckProgressIndicator(percentage = deck.progressPercentage)
                                }
                                FilledTonalIconButton(
                                    onClick = { onSelectDeck(deck.deckId) },
                                    enabled = deck.totalCards > 0
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = "Study ${deck.name}")
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
fun StudySessionScreen(
    deckId: Long?,
    startIndex: Int,
    isWeakMode: Boolean,
    viewModel: StudyViewModel,
    onClose: () -> Unit
) {
    LaunchedEffect(deckId, startIndex, isWeakMode) {
        if (isWeakMode) {
            viewModel.startWeakCardsSession()
        } else if (deckId != null) {
            viewModel.startDeckSession(deckId, startIndex)
        }
    }

    val state by viewModel.sessionState.collectAsStateWithLifecycle()
    val semantic = LocalSemanticColors.current

    // Learning statuses are saved immediately on each mark, so back exits cleanly without erasing progress
    BackHandler { onClose() }

    Scaffold { innerPadding ->
        AdaptiveContentContainer(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.activeCards.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyStateCard(
                            icon = Icons.Filled.Folder,
                            title = "No Cards to Study",
                            message = if (isWeakMode) {
                                "There are currently no weak cards to review."
                            } else {
                                "Add at least one flashcard to this deck before starting Study Mode."
                            },
                            actionLabel = "Go Back",
                            onAction = onClose
                        )
                    }
                }
                state.isCompleted -> {
                    StudyCompletionView(
                        title = state.title,
                        totalCards = state.activeCards.size,
                        remembered = state.sessionRememberedCount,
                        forgotten = state.sessionForgottenCount,
                        backButtonLabel = stringResource(
                            if (state.isWeakMode) R.string.study_back_to_weak else R.string.study_back_to_deck
                        ),
                        onRestart = { viewModel.restartSession() },
                        onBack = onClose
                    )
                }
                else -> {
                    val card = state.currentCard ?: return@AdaptiveContentContainer
                    val total = state.activeCards.size
                    val progress = (state.currentIndex + 1).toFloat() / total.toFloat()

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Top Bar: Deck Name, Card Number / Total, Shuffle, Close
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = state.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${state.currentIndex + 1} / $total",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { viewModel.toggleShuffle() },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        contentColor = if (state.isShuffled) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    ),
                                    modifier = Modifier.testTag("study_shuffle_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Shuffle,
                                        contentDescription = if (state.isShuffled) "Shuffle On" else "Shuffle Off"
                                    )
                                }
                                IconButton(
                                    onClick = onClose,
                                    modifier = Modifier.testTag("study_close_button")
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                                }
                            }
                        }

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp))
                        )

                        // Center: Large Flip Flashcard with Swipe Left/Right
                        var accumulatedDrag by remember(state.currentIndex) { mutableFloatStateOf(0f) }
                        val rotation by animateFloatAsState(
                            targetValue = if (state.isFlipped) 180f else 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "flashcard_flip"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .pointerInput(state.currentIndex, total) {
                                    detectHorizontalDragGestures(
                                        onDragStart = { accumulatedDrag = 0f },
                                        onHorizontalDrag = { _, dragAmount ->
                                            accumulatedDrag += dragAmount
                                        },
                                        onDragEnd = {
                                            if (accumulatedDrag < -110f && state.currentIndex < total - 1) {
                                                viewModel.nextCard()
                                            } else if (accumulatedDrag > 110f && state.currentIndex > 0) {
                                                viewModel.previousCard()
                                            }
                                            accumulatedDrag = 0f
                                        }
                                    )
                                }
                        ) {
                            val showingBack = rotation > 90f
                            Card(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        rotationY = rotation
                                        cameraDistance = 12f * density
                                    }
                                    .testTag("study_flashcard_surface")
                                    .clickable { viewModel.toggleFlip() },
                                shape = MaterialTheme.shapes.extraLarge,
                                colors = CardDefaults.cardColors(
                                    containerColor = if (showingBack) {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                ),
                                border = BorderStroke(
                                    width = 1.5.dp,
                                    color = if (showingBack) {
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                    }
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            if (showingBack) rotationY = 180f
                                        }
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = if (showingBack) {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.primaryContainer
                                            }
                                        ) {
                                            Text(
                                                text = if (showingBack) "ANSWER" else "QUESTION",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (showingBack) {
                                                    MaterialTheme.colorScheme.onSecondaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                },
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                            )
                                        }
                                        LearningStatusChip(status = card.learningStatus)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp)
                                            .verticalScroll(rememberScrollState()),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (showingBack) card.back else card.front,
                                            style = if (showingBack) {
                                                MaterialTheme.typography.titleLarge
                                            } else {
                                                MaterialTheme.typography.headlineSmall
                                            },
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Text(
                                        text = stringResource(
                                            if (showingBack) R.string.study_tap_to_flip_front else R.string.study_tap_to_reveal
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Bottom Controls: Remembered / Forgot + Previous / Next
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { viewModel.markCurrentCard(LearningStatus.FORGOTTEN) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = semantic.dangerContainer,
                                    contentColor = semantic.danger
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("study_forgot_button")
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.study_forgot))
                            }
                            Button(
                                onClick = { viewModel.markCurrentCard(LearningStatus.REMEMBERED) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = semantic.successContainer,
                                    contentColor = semantic.success
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("study_remembered_button")
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.study_remembered))
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.previousCard() },
                                enabled = state.currentIndex > 0,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("study_previous_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.action_previous))
                            }
                            OutlinedButton(
                                onClick = { viewModel.nextCard() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("study_next_button")
                            ) {
                                Text(if (state.currentIndex == total - 1) "Finish" else stringResource(R.string.action_next))
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyCompletionView(
    title: String,
    totalCards: Int,
    remembered: Int,
    forgotten: Int,
    backButtonLabel: String,
    onRestart: () -> Unit,
    onBack: () -> Unit
) {
    val semantic = LocalSemanticColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = semantic.successContainer,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = semantic.success,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.study_complete_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "$title • $totalCards card${if (totalCards == 1) "" else "s"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = remembered.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = semantic.success
                    )
                    Text("Remembered", style = MaterialTheme.typography.labelMedium)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = forgotten.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = semantic.danger
                    )
                    Text("Forgotten", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRestart,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("study_restart_button")
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.study_restart))
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("study_back_to_deck_button")
        ) {
            Text(backButtonLabel)
        }
    }
}
