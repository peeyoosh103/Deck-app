package com.example.file_import

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.common_components.AdaptiveContentContainer
import com.example.common_components.EditCardCandidateDialog
import com.example.common_components.MemoDeckTopBar
import com.example.models.ImportCardCandidate
import com.example.theme.LocalSemanticColors

@Composable
fun FileImportScreen(
    viewModel: FileImportViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editingCandidate by remember { mutableStateOf<ImportCardCandidate?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.loadFile(context, uri)
        }
    }

    Scaffold(
        topBar = {
            MemoDeckTopBar(
                title = stringResource(R.string.deck_import_from_file),
                onNavigateBack = onNavigateBack
            )
        }
    ) { innerPadding ->
        AdaptiveContentContainer(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("Processing file locally…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                when (state.step) {
                    FileImportStep.PICK_FILE -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("Supported Formats: JSON & CSV", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "Import bulk flashcards offline with full UTF-8, Hindi, English, and multiline support.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "JSON Example:\n{ \"cards\": [ { \"front\": \"Question\", \"back\": \"Answer\" } ] }",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "CSV Example:\nfront,back\n\"Question 1\",\"Answer 1\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    filePickerLauncher.launch(
                                        arrayOf("application/json", "text/csv", "text/comma-separated-values", "text/plain", "*/*")
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("file_import_pick_button")
                            ) {
                                Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Select JSON or CSV File")
                            }

                            state.errorMessage?.let { err ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                                ) {
                                    Text(
                                        text = err,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    FileImportStep.PREVIEW -> {
                        val semantic = LocalSemanticColors.current
                        Column(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = MaterialTheme.shapes.medium,
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(state.fileName, style = MaterialTheme.typography.titleSmall)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Found: ${state.totalFound}", style = MaterialTheme.typography.labelMedium)
                                                Text("Valid: ${state.validCount}", style = MaterialTheme.typography.labelMedium, color = semantic.success)
                                                Text("Invalid: ${state.invalidCount}", style = MaterialTheme.typography.labelMedium, color = semantic.danger)
                                                Text("Duplicates: ${state.duplicateCount}", style = MaterialTheme.typography.labelMedium, color = semantic.warning)
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Skip Duplicate Cards", style = MaterialTheme.typography.bodyMedium)
                                                Switch(
                                                    checked = state.skipDuplicates,
                                                    onCheckedChange = { viewModel.toggleSkipDuplicates(it) },
                                                    modifier = Modifier.testTag("file_import_skip_duplicates_switch")
                                                )
                                            }
                                        }
                                    }
                                }

                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { viewModel.selectAll() },
                                            modifier = Modifier.weight(1f)
                                        ) { Text(stringResource(R.string.action_select_all)) }
                                        OutlinedButton(
                                            onClick = { viewModel.deselectAll() },
                                            modifier = Modifier.weight(1f)
                                        ) { Text(stringResource(R.string.action_deselect_all)) }
                                    }
                                }

                                items(state.candidates, key = { it.id }) { item ->
                                    val canSelect = item.isValid && (!state.skipDuplicates || !item.isDuplicate)
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = MaterialTheme.shapes.medium,
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = item.selected && canSelect,
                                                    onCheckedChange = { viewModel.toggleSelect(item.id) },
                                                    enabled = canSelect
                                                )
                                                val (badgeText, badgeBg, badgeFg) = when {
                                                    !item.isValid -> Triple("Invalid", semantic.dangerContainer, semantic.danger)
                                                    item.isDuplicate -> Triple("Duplicate", semantic.warningContainer, semantic.warning)
                                                    else -> Triple("Valid", semantic.successContainer, semantic.success)
                                                }
                                                Surface(shape = RoundedCornerShape(999.dp), color = badgeBg) {
                                                    Text(
                                                        text = badgeText,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = badgeFg,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.weight(1f))
                                                IconButton(onClick = { editingCandidate = item }) {
                                                    Icon(Icons.Filled.Edit, contentDescription = "Edit imported card")
                                                }
                                                IconButton(onClick = { viewModel.deleteCandidate(item.id) }) {
                                                    Icon(Icons.Filled.Delete, contentDescription = "Delete imported card", tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                            Text(
                                                text = "Front: ${item.front.ifBlank { "(empty)" }}",
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                text = "Back: ${item.back.ifBlank { "(empty)" }}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            (item.validationError ?: item.duplicateReason)?.let { note ->
                                                Text(
                                                    text = note,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (!item.isValid) semantic.danger else semantic.warning
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onNavigateBack,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) { Text(stringResource(R.string.action_cancel)) }
                                Button(
                                    onClick = { viewModel.importSelectedCards() },
                                    enabled = state.selectedImportableCount > 0,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("file_import_confirm_button")
                                ) { Text("Import Selected (${state.selectedImportableCount})") }
                            }
                        }
                    }

                    FileImportStep.COMPLETED -> {
                        val summary = state.completionResult
                        val semantic = LocalSemanticColors.current
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = semantic.success,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Import Complete",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            if (summary != null) {
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
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        ImportSummaryRow("Successfully Imported", summary.importedCount.toString(), semantic.success)
                                        ImportSummaryRow("Skipped Duplicates", summary.skippedDuplicatesCount.toString(), semantic.warning)
                                        ImportSummaryRow("Invalid Cards Skipped", summary.skippedInvalidCount.toString(), semantic.danger)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = onNavigateBack,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("file_import_back_to_deck_button")
                            ) {
                                Text(stringResource(R.string.study_back_to_deck))
                            }
                        }
                    }
                }
            }
        }
    }

    editingCandidate?.let { item ->
        EditCardCandidateDialog(
            initialFront = item.front,
            initialBack = item.back,
            onSave = { f, b ->
                viewModel.editCandidate(item.id, f, b)
                editingCandidate = null
            },
            onDismiss = { editingCandidate = null }
        )
    }
}

@Composable
private fun ImportSummaryRow(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
    }
}
