package com.example.pdf_import

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.common_components.AdaptiveContentContainer
import com.example.common_components.EditCardCandidateDialog
import com.example.common_components.MemoDeckTopBar
import com.example.image_import.GeneratedCandidateRow
import com.example.models.GeneratedCardCandidate
import com.example.models.PageExtractionStatus
import com.example.theme.LocalSemanticColors

@Composable
fun PdfImportScreen(
    viewModel: PdfImportViewModel,
    onNavigateBack: () -> Unit,
    onImportComplete: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editingCandidate by remember { mutableStateOf<GeneratedCardCandidate?>(null) }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.openPdfUri(uri)
        }
    }

    Scaffold(
        topBar = {
            MemoDeckTopBar(
                title = stringResource(R.string.deck_create_from_pdf),
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
                        Text("Working locally on PDF…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                when (state.step) {
                    PdfImportStep.PICK_PDF -> {
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
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("Offline PDF-to-Flashcards", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "Select a local PDF book or study notes. Text-based PDFs extract embedded text directly, while scanned PDFs automatically use bundled on-device OCR (English & Hindi). Every generated card preserves its source page and excerpt.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Button(
                                onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("pdf_pick_button")
                            ) {
                                Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Select PDF File")
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

                    PdfImportStep.PDF_PREVIEW -> {
                        val info = state.pdfInfo ?: return@AdaptiveContentContainer
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = info.fileName,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Total Pages: ${info.pageCount}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Rendered Page Preview + Navigation
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    val bmp = state.previewBitmap
                                    if (bmp != null) {
                                        val aspect = (bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat())
                                            .coerceIn(0.65f, 1.6f)
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "Preview of page ${state.currentPreviewPage}",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(aspect)
                                        )
                                    } else {
                                        Text(
                                            text = "Preview unavailable for page ${state.currentPreviewPage}",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(24.dp)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        IconButton(
                                            onClick = { viewModel.navigatePreviewPage(state.currentPreviewPage - 1) },
                                            enabled = state.currentPreviewPage > 1
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous page")
                                        }
                                        Text(
                                            text = "Page ${state.currentPreviewPage} of ${info.pageCount}",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        IconButton(
                                            onClick = { viewModel.navigatePreviewPage(state.currentPreviewPage + 1) },
                                            enabled = state.currentPreviewPage < info.pageCount
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next page")
                                        }
                                    }
                                }
                            }

                            // Page Selection Controls
                            Text("Select Pages to Process", style = MaterialTheme.typography.titleSmall)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PageSelectionMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = state.selectionMode == mode,
                                        onClick = { viewModel.selectMode(mode) },
                                        label = { Text(mode.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("pdf_mode_${mode.name.lowercase()}")
                                    )
                                }
                            }

                            when (state.selectionMode) {
                                PageSelectionMode.ALL -> {
                                    Text(
                                        text = "All ${info.pageCount} page(s) will be processed locally.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                PageSelectionMode.RANGE -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = state.rangeStartInput,
                                            onValueChange = { viewModel.updateRangeStart(it) },
                                            label = { Text("From Page") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = state.rangeEndInput,
                                            onValueChange = { viewModel.updateRangeEnd(it) },
                                            label = { Text("To Page") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                PageSelectionMode.SPECIFIC -> {
                                    OutlinedTextField(
                                        value = state.specificPagesInput,
                                        onValueChange = { viewModel.updateSpecificPages(it) },
                                        label = { Text("Pages (e.g. 1, 3, 5-8)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    TextButton(onClick = { viewModel.toggleCurrentPageInSpecificSelection() }) {
                                        Text("Toggle Current Preview Page (${state.currentPreviewPage})")
                                    }
                                }
                            }

                            state.errorMessage?.let { err ->
                                Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) { Text("Change PDF") }
                                Button(
                                    onClick = { viewModel.processSelectedPages() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("pdf_process_button")
                                ) { Text("Process PDF") }
                            }
                        }
                    }

                    PdfImportStep.PROCESSING -> {
                        val progress = if (state.totalSelectedPages <= 0) 0f
                        else state.processedPagesCount.toFloat() / state.totalSelectedPages.toFloat()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Processing Page ${state.currentProcessingPage}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${state.processedPagesCount} of ${state.totalSelectedPages} pages processed offline",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(999.dp))
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            OutlinedButton(
                                onClick = { viewModel.cancelProcessing() },
                                modifier = Modifier.testTag("pdf_cancel_processing_button")
                            ) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        }
                    }

                    PdfImportStep.EXTRACTED_TEXT_PREVIEW -> {
                        val semantic = LocalSemanticColors.current
                        Column(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item {
                                    Text("Extracted Text by Page", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "Review, edit, exclude, or reprocess pages with offline OCR before generating flashcards.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                state.errorMessage?.let { err ->
                                    item {
                                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }

                                items(state.extractedPages, key = { it.pageNumber }) { page ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
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
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("Page ${page.pageNumber}", style = MaterialTheme.typography.titleSmall)
                                                    val statusColor = when (page.status) {
                                                        PageExtractionStatus.EMBEDDED_TEXT -> semantic.success
                                                        PageExtractionStatus.SCANNED_OCR -> MaterialTheme.colorScheme.primary
                                                        PageExtractionStatus.EMPTY -> semantic.warning
                                                        PageExtractionStatus.FAILED -> semantic.danger
                                                    }
                                                    Text(
                                                        text = page.status.label,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = statusColor
                                                    )
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    TextButton(onClick = { viewModel.reprocessPageWithOcr(page.pageNumber) }) {
                                                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("OCR")
                                                    }
                                                    Switch(
                                                        checked = page.included,
                                                        onCheckedChange = { viewModel.togglePageIncluded(page.pageNumber) }
                                                    )
                                                }
                                            }
                                            page.errorMessage?.let { err ->
                                                Text(err, style = MaterialTheme.typography.bodySmall, color = semantic.danger)
                                            }
                                            OutlinedTextField(
                                                value = page.text,
                                                onValueChange = { viewModel.updateExtractedPageText(page.pageNumber, it) },
                                                enabled = page.included,
                                                minLines = 3,
                                                maxLines = 8,
                                                modifier = Modifier.fillMaxWidth()
                                            )
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
                                    onClick = { viewModel.backToPdfPreview() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) { Text("Change Pages") }
                                Button(
                                    onClick = { viewModel.generateCardsFromPages() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("pdf_continue_generate_button")
                                ) { Text("Generate Cards") }
                            }
                        }
                    }

                    PdfImportStep.CARD_PREVIEW -> {
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
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Total: ${state.totalGenerated}", style = MaterialTheme.typography.labelMedium)
                                                Text("Verified: ${state.verifiedCount}", style = MaterialTheme.typography.labelMedium, color = semantic.success)
                                                Text("Review: ${state.needsReviewCount}", style = MaterialTheme.typography.labelMedium, color = semantic.warning)
                                                Text("Rejected: ${state.rejectedCount}", style = MaterialTheme.typography.labelMedium, color = semantic.danger)
                                            }
                                            Text(
                                                text = "Rejected cards cannot be imported unless edited and revalidated. Please review cards against their source excerpts.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { viewModel.selectAllVerified() },
                                                modifier = Modifier.weight(1f)
                                            ) { Text("Select Verified") }
                                            OutlinedButton(
                                                onClick = { viewModel.selectAllValid() },
                                                modifier = Modifier.weight(1f)
                                            ) { Text(stringResource(R.string.action_select_all)) }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { viewModel.deselectAll() },
                                                modifier = Modifier.weight(1f)
                                            ) { Text(stringResource(R.string.action_deselect_all)) }
                                            TextButton(
                                                onClick = { viewModel.backToExtractedText() },
                                                modifier = Modifier.weight(1f)
                                            ) { Text("Edit Page Text") }
                                        }
                                    }
                                }

                                state.errorMessage?.let { err ->
                                    item {
                                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }

                                items(state.candidates, key = { it.id }) { candidate ->
                                    GeneratedCandidateRow(
                                        candidate = candidate,
                                        showSourcePage = true,
                                        onToggleSelect = { viewModel.toggleCandidateSelection(candidate.id) },
                                        onEdit = { editingCandidate = candidate },
                                        onDelete = { viewModel.deleteCandidate(candidate.id) }
                                    )
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
                                    onClick = { viewModel.importSelectedCards { onImportComplete() } },
                                    enabled = state.selectedValidCount > 0,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("pdf_import_selected_button")
                                ) { Text("Import Selected (${state.selectedValidCount})") }
                            }
                        }
                    }
                }
            }
        }
    }

    editingCandidate?.let { candidate ->
        EditCardCandidateDialog(
            initialFront = candidate.front,
            initialBack = candidate.back,
            onSave = { f, b ->
                viewModel.editCandidate(candidate.id, f, b)
                editingCandidate = null
            },
            onDismiss = { editingCandidate = null }
        )
    }
}
