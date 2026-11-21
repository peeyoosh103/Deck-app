package com.example.decks

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.common_components.AdaptiveContentContainer
import com.example.common_components.ConfirmationDialog
import com.example.common_components.DeckProgressIndicator
import com.example.common_components.EmptyStateCard
import com.example.common_components.MemoDeckTopBar
import com.example.common_components.formatPercent
import com.example.models.DeckSummary
import com.example.repositories.MemoDeckRepository
import com.example.theme.LocalSemanticColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DecksViewModel(private val repository: MemoDeckRepository) : ViewModel() {

    val decks: StateFlow<List<DeckSummary>> = repository.observeDeckSummaries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun observeDeck(deckId: Long) = repository.observeDeckSummary(deckId)

    suspend fun createDeck(name: String): Long = repository.createDeck(name)

    fun renameDeck(deckId: Long, newName: String) {
        viewModelScope.launch { repository.renameDeck(deckId, newName) }
    }

    fun deleteDeck(deckId: Long, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteDeck(deckId)
            onDeleted()
        }
    }

    class Factory(private val repository: MemoDeckRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DecksViewModel(repository) as T
    }
}

@Composable
fun DecksListScreen(
    viewModel: DecksViewModel,
    onCreateDeck: () -> Unit,
    onOpenDeck: (Long) -> Unit,
    onViewCards: (Long) -> Unit
) {
    val decks by viewModel.decks.collectAsStateWithLifecycle()
    var deckToRename by remember { mutableStateOf<DeckSummary?>(null) }
    var deckToDelete by remember { mutableStateOf<DeckSummary?>(null) }

    Scaffold(
        topBar = {
            MemoDeckTopBar(title = stringResource(R.string.decks_title))
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateDeck,
                modifier = Modifier.testTag("decks_create_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.home_create_deck))
            }
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
                        title = "No Decks Created",
                        message = "Organize your flashcards into subject decks such as Indian Polity, Geography, History, Biology, or Vocabulary.",
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
                        DeckListItemCard(
                            deck = deck,
                            onOpen = { onOpenDeck(deck.deckId) },
                            onViewCards = { onViewCards(deck.deckId) },
                            onRename = { deckToRename = deck },
                            onDelete = { deckToDelete = deck }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }
    }

    deckToRename?.let { deck ->
        RenameDeckDialog(
            currentName = deck.name,
            onSave = { newName ->
                viewModel.renameDeck(deck.deckId, newName)
                deckToRename = null
            },
            onDismiss = { deckToRename = null }
        )
    }

    deckToDelete?.let { deck ->
        ConfirmationDialog(
            title = stringResource(R.string.deck_delete_title),
            message = "Delete \"${deck.name}\"? ${stringResource(R.string.deck_delete_message)}",
            confirmLabel = stringResource(R.string.action_delete),
            isDestructive = true,
            onConfirm = {
                viewModel.deleteDeck(deck.deckId)
                deckToDelete = null
            },
            onDismiss = { deckToDelete = null }
        )
    }
}

@Composable
private fun DeckListItemCard(
    deck: DeckSummary,
    onOpen: () -> Unit,
    onViewCards: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val semantic = LocalSemanticColors.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("deck_item_card_${deck.deckId}")
            .clickable(onClick = onOpen),
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = deck.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${deck.totalCards} flashcard${if (deck.totalCards == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = formatPercent(deck.progressPercentage),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag("deck_menu_${deck.deckId}")
                    ) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Deck options")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Open Deck") },
                            leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onOpen()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("View Cards") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onViewCards()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit Deck Name") },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Deck", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            DeckProgressIndicator(percentage = deck.progressPercentage)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Remembered: ${deck.rememberedCards}",
                    style = MaterialTheme.typography.labelMedium,
                    color = semantic.success
                )
                Text(
                    text = "Forgotten: ${deck.forgottenCards}",
                    style = MaterialTheme.typography.labelMedium,
                    color = semantic.danger
                )
            }
        }
    }
}

@Composable
fun CreateDeckScreen(
    viewModel: DecksViewModel,
    onDeckCreated: (Long) -> Unit,
    onCancel: () -> Unit
) {
    var deckName by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            MemoDeckTopBar(
                title = stringResource(R.string.create_deck_title),
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
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = deckName,
                    onValueChange = {
                        deckName = it
                        nameError = null
                    },
                    label = { Text(stringResource(R.string.deck_name_label)) },
                    placeholder = { Text(stringResource(R.string.deck_name_hint)) },
                    isError = nameError != null,
                    supportingText = nameError?.let { err -> { Text(err) } },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_deck_name_input")
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
                            .testTag("create_deck_cancel_button")
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Button(
                        onClick = {
                            val trimmed = deckName.trim()
                            if (trimmed.isEmpty()) {
                                nameError = "Deck name cannot be empty."
                            } else {
                                scope.launch {
                                    val newId = viewModel.createDeck(trimmed)
                                    onDeckCreated(newId)
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("create_deck_submit_button")
                    ) {
                        Text(stringResource(R.string.action_create))
                    }
                }
            }
        }
    }
}

@Composable
fun DeckDetailsScreen(
    deckId: Long,
    viewModel: DecksViewModel,
    onNavigateBack: () -> Unit,
    onStudyDeck: (Long) -> Unit,
    onQuizDeck: (Long) -> Unit,
    onAddFlashcard: (Long) -> Unit,
    onCreateFromImage: (Long) -> Unit,
    onImportFromFile: (Long) -> Unit,
    onCreateFromPdf: (Long) -> Unit,
    onViewCards: (Long) -> Unit
) {
    val deckState by remember(deckId) { viewModel.observeDeck(deckId) }
        .collectAsStateWithLifecycle(initialValue = null)
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val semantic = LocalSemanticColors.current

    Scaffold(
        topBar = {
            MemoDeckTopBar(
                title = deckState?.name ?: "Deck Details",
                onNavigateBack = onNavigateBack,
                actions = {
                    if (deckState != null) {
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.testTag("deck_details_menu_button")
                            ) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Deck actions")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit Deck Name") },
                                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        showRenameDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete Deck", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        showDeleteDialog = true
                                    }
                                )
                            }
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
            val deck = deckState
            if (deck == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Deck not found.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Progress & Breakdown Card
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
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Learning Progress",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = formatPercent(deck.progressPercentage),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            DeckProgressIndicator(percentage = deck.progressPercentage)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MetricMiniColumn("Total", deck.totalCards.toString(), MaterialTheme.colorScheme.onSurface)
                                MetricMiniColumn("Remembered", deck.rememberedCards.toString(), semantic.success)
                                MetricMiniColumn("Forgotten", deck.forgottenCards.toString(), semantic.danger)
                                MetricMiniColumn("Unstudied", deck.unstudiedCards.toString(), MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Primary Study & Quiz Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onStudyDeck(deck.deckId) },
                            enabled = deck.totalCards > 0,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("deck_study_button")
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.deck_study_this),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        FilledTonalButton(
                            onClick = { onQuizDeck(deck.deckId) },
                            enabled = deck.totalCards > 0,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("deck_quiz_button")
                        ) {
                            Icon(Icons.Filled.Quiz, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.deck_quiz_this),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Card Creation & Management Actions
                    Text(
                        text = "Create & Import Flashcards",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DeckActionTile(
                            icon = Icons.Filled.Add,
                            title = stringResource(R.string.deck_add_flashcard),
                            subtitle = "Create a manual Question & Answer flashcard",
                            onClick = { onAddFlashcard(deck.deckId) },
                            testTag = "deck_add_flashcard_button"
                        )
                        DeckActionTile(
                            icon = Icons.Filled.PhotoCamera,
                            title = stringResource(R.string.deck_create_from_image),
                            subtitle = "Capture or choose book pages and notes with offline OCR",
                            onClick = { onCreateFromImage(deck.deckId) },
                            testTag = "deck_create_from_image_button"
                        )
                        DeckActionTile(
                            icon = Icons.Filled.UploadFile,
                            title = stringResource(R.string.deck_import_from_file),
                            subtitle = "Bulk import cards from local JSON or CSV files",
                            onClick = { onImportFromFile(deck.deckId) },
                            testTag = "deck_import_file_button"
                        )
                        DeckActionTile(
                            icon = Icons.Filled.PictureAsPdf,
                            title = stringResource(R.string.deck_create_from_pdf),
                            subtitle = "Extract text or scan PDF notes offline into validated cards",
                            onClick = { onCreateFromPdf(deck.deckId) },
                            testTag = "deck_create_from_pdf_button"
                        )
                    }

                    // View Cards Action
                    OutlinedButton(
                        onClick = { onViewCards(deck.deckId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("deck_view_cards_button")
                    ) {
                        Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${stringResource(R.string.deck_view_cards)} (${deck.totalCards})")
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }

    val currentDeck = deckState
    if (showRenameDialog && currentDeck != null) {
        RenameDeckDialog(
            currentName = currentDeck.name,
            onSave = { newName ->
                viewModel.renameDeck(currentDeck.deckId, newName)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false }
        )
    }

    if (showDeleteDialog && currentDeck != null) {
        ConfirmationDialog(
            title = stringResource(R.string.deck_delete_title),
            message = "Delete \"${currentDeck.name}\"? ${stringResource(R.string.deck_delete_message)}",
            confirmLabel = stringResource(R.string.action_delete),
            isDestructive = true,
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteDeck(currentDeck.deckId) {
                    onNavigateBack()
                }
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@Composable
private fun MetricMiniColumn(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DeckActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun RenameDeckDialog(
    currentName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(currentName) { mutableStateOf(currentName) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_deck_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    error = null
                },
                label = { Text(stringResource(R.string.deck_name_label)) },
                isError = error != null,
                supportingText = error?.let { err -> { Text(err) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) {
                        error = "Deck name cannot be empty."
                    } else {
                        onSave(trimmed)
                    }
                }
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
