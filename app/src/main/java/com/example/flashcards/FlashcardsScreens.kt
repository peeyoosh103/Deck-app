package com.example.flashcards

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.common_components.AdaptiveContentContainer
import com.example.common_components.ConfirmationDialog
import com.example.common_components.EmptyStateCard
import com.example.common_components.LearningStatusChip
import com.example.common_components.MemoDeckTopBar
import com.example.database.FlashcardEntity
import com.example.models.SourceType
import com.example.repositories.MemoDeckRepository
import kotlinx.coroutines.launch

class FlashcardsViewModel(private val repository: MemoDeckRepository) : ViewModel() {

    fun observeDeckSummary(deckId: Long) = repository.observeDeckSummary(deckId)
    fun observeCards(deckId: Long) = repository.observeCardsByDeck(deckId)
    fun observeCard(cardId: Long) = repository.observeCardById(cardId)
    fun observeWeakStatus(cardId: Long) = repository.observeWeakStatus(cardId)

    suspend fun getCardOnce(cardId: Long): FlashcardEntity? = repository.getCardByIdOnce(cardId)

    suspend fun createCard(deckId: Long, front: String, back: String): Long =
        repository.addFlashcard(deckId = deckId, front = front, back = back, sourceType = SourceType.MANUAL)

    suspend fun updateCard(cardId: Long, front: String, back: String) {
        repository.updateFlashcardContent(cardId = cardId, front = front, back = back)
    }

    fun deleteCard(cardId: Long, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteFlashcard(cardId)
            onDeleted()
        }
    }

    fun moveCard(deckId: Long, currentCards: List<FlashcardEntity>, fromIndex: Int, toIndex: Int) {
        if (fromIndex !in currentCards.indices || toIndex !in currentCards.indices) return
        val mutable = currentCards.map { it.id }.toMutableList()
        val moved = mutable.removeAt(fromIndex)
        mutable.add(toIndex, moved)
        viewModelScope.launch {
            repository.reorderFlashcards(deckId, mutable)
        }
    }

    fun removeWeakStatus(cardId: Long) {
        viewModelScope.launch { repository.removeWeakStatus(cardId) }
    }

    class Factory(private val repository: MemoDeckRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = FlashcardsViewModel(repository) as T
    }
}

@Composable
fun CardListScreen(
    deckId: Long,
    viewModel: FlashcardsViewModel,
    onNavigateBack: () -> Unit,
    onAddCard: () -> Unit,
    onOpenCard: (Long) -> Unit,
    onEditCard: (Long) -> Unit,
    onStudyFromIndex: (Int) -> Unit
) {
    val deck by remember(deckId) { viewModel.observeDeckSummary(deckId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val cards by remember(deckId) { viewModel.observeCards(deckId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var reorderMode by remember { mutableStateOf(false) }
    var cardToDelete by remember { mutableStateOf<FlashcardEntity?>(null) }

    Scaffold(
        topBar = {
            MemoDeckTopBar(
                title = deck?.name?.let { "$it — Cards" } ?: "Cards",
                onNavigateBack = onNavigateBack,
                actions = {
                    if (cards.size >= 2) {
                        TextButton(
                            onClick = { reorderMode = !reorderMode },
                            modifier = Modifier.testTag("card_list_reorder_toggle")
                        ) {
                            Icon(Icons.Filled.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (reorderMode) "Done" else "Reorder")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddCard,
                modifier = Modifier.testTag("card_list_add_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.deck_add_flashcard))
            }
        }
    ) { innerPadding ->
        AdaptiveContentContainer(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (cards.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateCard(
                        icon = Icons.Filled.Style,
                        title = "No Flashcards Yet",
                        message = "Add flashcards manually or import them from Images, JSON/CSV files, or PDF notes.",
                        actionLabel = stringResource(R.string.deck_add_flashcard),
                        onAction = onAddCard
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(cards, key = { _, card -> card.id }) { index, card ->
                        CardListRow(
                            index = index,
                            total = cards.size,
                            card = card,
                            reorderMode = reorderMode,
                            onOpen = { onOpenCard(card.id) },
                            onEdit = { onEditCard(card.id) },
                            onDelete = { cardToDelete = card },
                            onStudyFromHere = { onStudyFromIndex(index) },
                            onMoveUp = { viewModel.moveCard(deckId, cards, index, index - 1) },
                            onMoveDown = { viewModel.moveCard(deckId, cards, index, index + 1) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }
    }

    cardToDelete?.let { card ->
        ConfirmationDialog(
            title = stringResource(R.string.card_delete_title),
            message = stringResource(R.string.card_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            isDestructive = true,
            onConfirm = {
                viewModel.deleteCard(card.id)
                cardToDelete = null
            },
            onDismiss = { cardToDelete = null }
        )
    }
}

@Composable
private fun CardListRow(
    index: Int,
    total: Int,
    card: FlashcardEntity,
    reorderMode: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onStudyFromHere: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("flashcard_list_item_${card.id}")
            .clickable(onClick = onOpen),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                LearningStatusChip(status = card.learningStatus)
            }
            Text(
                text = card.front,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = card.back,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (reorderMode) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onMoveUp,
                            enabled = index > 0,
                            modifier = Modifier.testTag("card_move_up_${card.id}")
                        ) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = "Move card up")
                        }
                        IconButton(
                            onClick = onMoveDown,
                            enabled = index < total - 1,
                            modifier = Modifier.testTag("card_move_down_${card.id}")
                        ) {
                            Icon(Icons.Filled.ArrowDownward, contentDescription = "Move card down")
                        }
                    }
                } else {
                    TextButton(
                        onClick = onStudyFromHere,
                        modifier = Modifier.testTag("card_study_from_${card.id}")
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Study from here")
                    }
                }
                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("card_edit_${card.id}")
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit card")
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("card_delete_${card.id}")
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete card",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CardEditorScreen(
    deckId: Long,
    cardId: Long?, // null = Create, non-null = Edit
    viewModel: FlashcardsViewModel,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    onCreateFromImage: () -> Unit,
    onImportFromFile: () -> Unit,
    onCreateFromPdf: () -> Unit
) {
    val isEditing = cardId != null
    var front by remember { mutableStateOf("") }
    var back by remember { mutableStateOf("") }
    var frontError by remember { mutableStateOf<String?>(null) }
    var backError by remember { mutableStateOf<String?>(null) }
    var loaded by remember(cardId) { mutableStateOf(!isEditing) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(cardId) {
        if (cardId != null) {
            val existing = viewModel.getCardOnce(cardId)
            if (existing != null) {
                front = existing.front
                back = existing.back
            }
            loaded = true
        }
    }

    Scaffold(
        topBar = {
            MemoDeckTopBar(
                title = stringResource(if (isEditing) R.string.card_edit_title else R.string.card_create_title),
                onNavigateBack = onCancel
            )
        }
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = front,
                    onValueChange = {
                        front = it
                        frontError = null
                    },
                    label = { Text(stringResource(R.string.card_front_label)) },
                    placeholder = { Text("Enter question, keyword, term, or statement") },
                    isError = frontError != null,
                    supportingText = frontError?.let { err -> { Text(err) } },
                    minLines = 3,
                    maxLines = 8,
                    enabled = loaded,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_front_input")
                )

                OutlinedTextField(
                    value = back,
                    onValueChange = {
                        back = it
                        backError = null
                    },
                    label = { Text(stringResource(R.string.card_back_label)) },
                    placeholder = { Text("Enter answer, explanation, or definition") },
                    isError = backError != null,
                    supportingText = backError?.let { err -> { Text(err) } },
                    minLines = 4,
                    maxLines = 10,
                    enabled = loaded,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_back_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("card_cancel_button")
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Button(
                        onClick = {
                            val cleanFront = front.trim()
                            val cleanBack = back.trim()
                            if (cleanFront.isEmpty()) frontError = "Front text cannot be empty."
                            if (cleanBack.isEmpty()) backError = "Back text cannot be empty."
                            if (cleanFront.isNotEmpty() && cleanBack.isNotEmpty()) {
                                scope.launch {
                                    if (cardId == null) {
                                        viewModel.createCard(deckId, cleanFront, cleanBack)
                                    } else {
                                        viewModel.updateCard(cardId, cleanFront, cleanBack)
                                    }
                                    onSaved()
                                }
                            }
                        },
                        enabled = loaded,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("card_save_button")
                    ) {
                        Text(stringResource(R.string.card_save))
                    }
                }

                // Entry points inside Add Flashcard required by Modules 11, 12, and 13
                if (!isEditing) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "Other Creation Methods",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onCreateFromImage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("add_card_from_image_button")
                    ) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.deck_create_from_image))
                    }
                    OutlinedButton(
                        onClick = onImportFromFile,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("add_card_import_file_button")
                    ) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.deck_import_from_file))
                    }
                    OutlinedButton(
                        onClick = onCreateFromPdf,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("add_card_from_pdf_button")
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.deck_create_from_pdf))
                    }
                }
            }
        }
    }
}

@Composable
fun CardViewerScreen(
    cardId: Long,
    viewModel: FlashcardsViewModel,
    onNavigateBack: () -> Unit,
    onEditCard: (deckId: Long, cardId: Long) -> Unit
) {
    val cardState by remember(cardId) { viewModel.observeCard(cardId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val weakState by remember(cardId) { viewModel.observeWeakStatus(cardId) }
        .collectAsStateWithLifecycle(initialValue = null)
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            MemoDeckTopBar(
                title = stringResource(R.string.card_viewer_title),
                onNavigateBack = onNavigateBack,
                actions = {
                    val card = cardState
                    if (card != null) {
                        IconButton(
                            onClick = { onEditCard(card.deckId, card.id) },
                            modifier = Modifier.testTag("card_viewer_edit_button")
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit card")
                        }
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.testTag("card_viewer_delete_button")
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete card",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        AdaptiveContentContainer(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val card = cardState
            if (card == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Flashcard not found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LearningStatusChip(status = card.learningStatus)
                        val weak = weakState
                        if (weak != null && weak.isWeak) {
                            OutlinedButton(
                                onClick = { viewModel.removeWeakStatus(card.id) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("card_viewer_remove_weak_button")
                            ) {
                                Text("Weak: ${weak.reason.displayLabel} (Remove)", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

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
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "FRONT",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = card.front,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

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
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "BACK",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = card.back,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Internal source traceability metadata (shown separately, never mixed into Front/Back)
                    if (card.sourceType != null && card.sourceType != SourceType.MANUAL) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Source Metadata",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Origin: ${card.sourceType.displayLabel}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                card.sourceName?.let {
                                    Text(text = "File: $it", style = MaterialTheme.typography.bodySmall)
                                }
                                card.sourcePageNumber?.let {
                                    Text(text = "Source Page: $it", style = MaterialTheme.typography.bodySmall)
                                }
                                card.sourceExcerpt?.takeIf { it.isNotBlank() }?.let { excerpt ->
                                    Text(
                                        text = "Excerpt: $excerpt",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog && cardState != null) {
        ConfirmationDialog(
            title = stringResource(R.string.card_delete_title),
            message = stringResource(R.string.card_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            isDestructive = true,
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteCard(cardId) { onNavigateBack() }
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}
