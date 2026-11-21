package com.example.image_import

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.common_components.AdaptiveContentContainer
import com.example.common_components.EditCardCandidateDialog
import com.example.common_components.MemoDeckTopBar
import com.example.common_components.QualityStatusBadge
import com.example.models.CardQualityStatus
import com.example.models.GeneratedCardCandidate
import com.example.ocr.ImageBitmapUtils
import com.example.ocr.NormalizedCropRect
import com.example.theme.LocalSemanticColors
import kotlin.math.roundToInt

@Composable
fun ImageImportScreen(
    viewModel: ImageImportViewModel,
    onNavigateBack: () -> Unit,
    onImportComplete: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    var editingCandidate by remember { mutableStateOf<GeneratedCardCandidate?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            permissionMessage = null
            viewModel.loadImageFromUri(context, uri, "Gallery Image")
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            permissionMessage = null
            viewModel.loadImageFromUri(context, uri, "Camera Photo")
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = ImageBitmapUtils.createCameraCaptureUri(context)
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            permissionMessage = "Camera permission is required to take a photo. You can still choose an image from Gallery."
        }
    }

    fun launchCamera() {
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            val uri = ImageBitmapUtils.createCameraCaptureUri(context)
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun launchGallery() {
        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Scaffold(
        topBar = {
            MemoDeckTopBar(
                title = stringResource(R.string.deck_create_from_image),
                onNavigateBack = onNavigateBack
            )
        }
    ) { innerPadding ->
        AdaptiveContentContainer(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.isProcessing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Running offline OCR & validation…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                when (state.step) {
                    ImageImportStep.SELECT_SOURCE -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                                    Text(
                                        text = "Offline Image-to-Flashcards",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Capture book pages, printed study notes, or choose a photo from your gallery. All OCR (English & Hindi) and card extraction runs 100% locally on your device.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Button(
                                onClick = { launchCamera() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("image_take_photo_button")
                            ) {
                                Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Take Photo")
                            }

                            OutlinedButton(
                                onClick = { launchGallery() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("image_choose_gallery_button")
                            ) {
                                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Choose from Gallery")
                            }

                            (permissionMessage ?: state.errorMessage)?.let { err ->
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    ImageImportStep.PREVIEW_IMAGE -> {
                        val bitmap = state.previewBitmap
                        var showCropControls by remember { mutableStateOf(false) }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Adjust Image Before Offline OCR",
                                style = MaterialTheme.typography.titleMedium
                            )

                            if (bitmap != null) {
                                val aspect = (bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat())
                                    .coerceIn(0.6f, 1.8f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(aspect)
                                ) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Selected study image preview",
                                        contentScale = ContentScale.FillBounds,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    // Live Crop Region Overlay
                                    val crop = state.cropRect
                                    val borderColor = MaterialTheme.colorScheme.secondary
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val left = crop.left * size.width
                                        val top = crop.top * size.height
                                        val right = crop.right * size.width
                                        val bottom = crop.bottom * size.height
                                        val dim = Color.Black.copy(alpha = 0.45f)

                                        if (!crop.isFullImage) {
                                            drawRect(dim, Offset(0f, 0f), Size(size.width, top))
                                            drawRect(dim, Offset(0f, bottom), Size(size.width, size.height - bottom))
                                            drawRect(dim, Offset(0f, top), Size(left, bottom - top))
                                            drawRect(dim, Offset(right, top), Size(size.width - right, bottom - top))
                                        }
                                        drawRect(
                                            color = borderColor,
                                            topLeft = Offset(left, top),
                                            size = Size((right - left).coerceAtLeast(1f), (bottom - top).coerceAtLeast(1f)),
                                            style = Stroke(width = 3.dp.toPx())
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.rotateImage(-90) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("image_rotate_left_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.RotateLeft, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Rotate")
                                }
                                OutlinedButton(
                                    onClick = { viewModel.rotateImage(90) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("image_rotate_right_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Rotate")
                                }
                                OutlinedButton(
                                    onClick = { showCropControls = !showCropControls },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("image_crop_toggle_button")
                                ) {
                                    Icon(Icons.Filled.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Crop")
                                }
                            }

                            if (showCropControls) {
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
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Select Region", style = MaterialTheme.typography.titleSmall)
                                            TextButton(onClick = { viewModel.resetCrop() }) {
                                                Text("Reset")
                                            }
                                        }
                                        Text("Horizontal Range", style = MaterialTheme.typography.labelSmall)
                                        RangeSlider(
                                            value = state.cropRect.left..state.cropRect.right,
                                            onValueChange = { range ->
                                                if (range.endInclusive - range.start >= 0.1f) {
                                                    viewModel.updateCropRect(
                                                        state.cropRect.copy(left = range.start, right = range.endInclusive)
                                                    )
                                                }
                                            },
                                            valueRange = 0f..1f
                                        )
                                        Text("Vertical Range", style = MaterialTheme.typography.labelSmall)
                                        RangeSlider(
                                            value = state.cropRect.top..state.cropRect.bottom,
                                            onValueChange = { range ->
                                                if (range.endInclusive - range.start >= 0.1f) {
                                                    viewModel.updateCropRect(
                                                        state.cropRect.copy(top = range.start, bottom = range.endInclusive)
                                                    )
                                                }
                                            },
                                            valueRange = 0f..1f
                                        )
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
                                    onClick = { launchGallery() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Text("Change Image")
                                }
                                Button(
                                    onClick = { viewModel.processImageWithOcr() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("image_process_button")
                                ) {
                                    Text("Process Image")
                                }
                            }
                        }
                    }

                    ImageImportStep.EXTRACTED_TEXT -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Extracted Text Preview", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Confidence: ${(state.ocrConfidence * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            state.errorMessage?.let { err ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                                ) {
                                    Text(
                                        text = err,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = state.extractedText,
                                onValueChange = { viewModel.updateExtractedText(it) },
                                label = { Text("Editable OCR Text") },
                                minLines = 8,
                                maxLines = 18,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("image_extracted_text_input")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.backToImagePreview() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("image_reprocess_button")
                                ) {
                                    Text("Reprocess Image")
                                }
                                Button(
                                    onClick = { viewModel.generateCardsFromExtractedText() },
                                    enabled = state.extractedText.isNotBlank(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("image_generate_cards_button")
                                ) {
                                    Text("Generate Cards")
                                }
                            }
                        }
                    }

                    ImageImportStep.CARD_PREVIEW -> {
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
                                                text = "Local extraction cannot guarantee 100% accuracy. Please review and edit cards before saving.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { viewModel.selectAllValid() },
                                            modifier = Modifier.weight(1f)
                                        ) { Text(stringResource(R.string.action_select_all)) }
                                        OutlinedButton(
                                            onClick = { viewModel.deselectAll() },
                                            modifier = Modifier.weight(1f)
                                        ) { Text(stringResource(R.string.action_deselect_all)) }
                                        TextButton(onClick = { viewModel.backToExtractedText() }) {
                                            Text("Edit Text")
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
                                        showSourcePage = false,
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
                                ) {
                                    Text(stringResource(R.string.action_cancel))
                                }
                                Button(
                                    onClick = { viewModel.saveSelectedCards { onImportComplete() } },
                                    enabled = state.selectedValidCount > 0,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("image_save_selected_button")
                                ) {
                                    Text("Save Selected (${state.selectedValidCount})")
                                }
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

@Composable
fun GeneratedCandidateRow(
    candidate: GeneratedCardCandidate,
    showSourcePage: Boolean,
    onToggleSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isRejected = candidate.qualityStatus == CardQualityStatus.REJECTED
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
                    checked = candidate.selected && !isRejected,
                    onCheckedChange = { onToggleSelect() },
                    enabled = !isRejected
                )
                QualityStatusBadge(status = candidate.qualityStatus)
                Spacer(modifier = Modifier.weight(1f))
                if (showSourcePage && candidate.sourcePageNumber != null) {
                    Text(
                        text = "Page ${candidate.sourcePageNumber}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit generated card")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete generated card", tint = MaterialTheme.colorScheme.error)
                }
            }
            Text(text = "Q: ${candidate.front}", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "A: ${candidate.back}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (showSourcePage && !candidate.sourceExcerpt.isNullOrBlank()) {
                Text(
                    text = "Source Excerpt: ${candidate.sourceExcerpt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (candidate.validationNotes.isNotEmpty()) {
                Text(
                    text = candidate.validationNotes.first(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
