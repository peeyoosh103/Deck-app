package com.example.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.backup.BackupManager
import com.example.backup.BackupPayload
import com.example.backup.BackupValidationResult
import com.example.common_components.AdaptiveContentContainer
import com.example.common_components.ConfirmationDialog
import com.example.common_components.MemoDeckTopBar
import com.example.models.AppSettings
import com.example.models.QuizQuestionCountOption
import com.example.models.ThemeMode
import com.example.repositories.MemoDeckRepository
import com.example.repositories.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SettingsActionState(
    val isBusy: Boolean = false,
    val pendingRestorePayload: BackupPayload? = null,
    val statusMessage: String? = null
)

class SettingsViewModel(
    private val repository: MemoDeckRepository,
    private val settingsRepository: SettingsRepository,
    private val backupManager: BackupManager
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettings()
    )

    private val _actionState = MutableStateFlow(SettingsActionState())
    val actionState: StateFlow<SettingsActionState> = _actionState.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setDefaultShuffle(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDefaultShuffle(enabled) }
    }

    fun setDefaultQuizQuestionCount(option: QuizQuestionCountOption) {
        viewModelScope.launch { settingsRepository.setDefaultQuizQuestionCount(option) }
    }

    fun exportBackupToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _actionState.value = _actionState.value.copy(isBusy = true)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val json = backupManager.exportToJson()
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("Unable to open output location.")
                }
            }
            _actionState.value = _actionState.value.copy(
                isBusy = false,
                statusMessage = if (result.isSuccess) {
                    "Backup exported successfully."
                } else {
                    "Export failed: ${result.exceptionOrNull()?.message ?: "Unable to save file."}"
                }
            )
        }
    }

    fun inspectBackupFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _actionState.value = _actionState.value.copy(isBusy = true)
            val validation = withContext(Dispatchers.IO) {
                runCatching {
                    val content = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                        it.readText()
                    } ?: return@runCatching BackupValidationResult.Invalid("Unable to read the selected file.")
                    backupManager.validateBackupJson(content)
                }.getOrElse { e ->
                    BackupValidationResult.Invalid("Unable to read backup: ${e.message ?: "Unknown error."}")
                }
            }
            when (validation) {
                is BackupValidationResult.Valid -> {
                    _actionState.value = _actionState.value.copy(
                        isBusy = false,
                        pendingRestorePayload = validation.payload
                    )
                }
                is BackupValidationResult.Invalid -> {
                    _actionState.value = _actionState.value.copy(
                        isBusy = false,
                        pendingRestorePayload = null,
                        statusMessage = "Invalid backup: ${validation.errorMessage}"
                    )
                }
            }
        }
    }

    fun confirmRestore() {
        val payload = _actionState.value.pendingRestorePayload ?: return
        viewModelScope.launch {
            _actionState.value = _actionState.value.copy(isBusy = true, pendingRestorePayload = null)
            val result = runCatching { backupManager.restoreFromPayload(payload) }
            _actionState.value = _actionState.value.copy(
                isBusy = false,
                statusMessage = if (result.isSuccess) {
                    "Backup restored: ${payload.decks.size} decks and ${payload.flashcards.size} cards."
                } else {
                    "Restore failed: ${result.exceptionOrNull()?.message ?: "Existing data was not modified."}"
                }
            )
        }
    }

    fun cancelRestore() {
        _actionState.value = _actionState.value.copy(pendingRestorePayload = null)
    }

    fun clearAllData() {
        viewModelScope.launch {
            _actionState.value = _actionState.value.copy(isBusy = true)
            repository.clearAllDatabaseData()
            settingsRepository.resetToDefaults()
            _actionState.value = _actionState.value.copy(
                isBusy = false,
                statusMessage = "All local data and settings have been cleared."
            )
        }
    }

    fun consumeStatusMessage() {
        _actionState.value = _actionState.value.copy(statusMessage = null)
    }

    class Factory(
        private val repository: MemoDeckRepository,
        private val settingsRepository: SettingsRepository,
        private val backupManager: BackupManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(repository, settingsRepository, backupManager) as T
    }
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearAllDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackupToUri(context, uri)
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.inspectBackupFromUri(context, uri)
        }
    }

    LaunchedEffect(actionState.statusMessage) {
        val msg = actionState.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeStatusMessage()
        }
    }

    Scaffold(
        topBar = {
            MemoDeckTopBar(
                title = stringResource(R.string.settings_title),
                onNavigateBack = onNavigateBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // A. Appearance
                SettingsSectionCard(title = stringResource(R.string.settings_appearance)) {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_theme_${mode.name.lowercase()}")
                                .clickable { viewModel.setThemeMode(mode) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = mode.displayLabel,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }

                // B. Study Settings
                SettingsSectionCard(title = stringResource(R.string.settings_study)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Default Shuffle",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Randomize card order by default in Study Mode",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.defaultShuffle,
                            onCheckedChange = { viewModel.setDefaultShuffle(it) },
                            modifier = Modifier.testTag("settings_default_shuffle_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Default Quiz Questions",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(QuizQuestionCountOption.FIVE, QuizQuestionCountOption.TEN).forEach { opt ->
                            FilterChip(
                                selected = settings.defaultQuizQuestionCount == opt,
                                onClick = { viewModel.setDefaultQuizQuestionCount(opt) },
                                label = { Text(opt.label) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_quiz_count_${opt.name.lowercase()}")
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(QuizQuestionCountOption.TWENTY, QuizQuestionCountOption.ALL).forEach { opt ->
                            FilterChip(
                                selected = settings.defaultQuizQuestionCount == opt,
                                onClick = { viewModel.setDefaultQuizQuestionCount(opt) },
                                label = { Text(opt.label) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_quiz_count_${opt.name.lowercase()}")
                            )
                        }
                    }
                }

                // C. Local Backup
                SettingsSectionCard(title = stringResource(R.string.settings_backup)) {
                    Text(
                        text = "Export or restore all decks, flashcards, original card order, learning statuses, weak cards, quiz history, study activity, settings, and source metadata offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                            exportLauncher.launch("memodeck_backup_$stamp.json")
                        },
                        enabled = !actionState.isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("settings_export_backup_button")
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_export_backup))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                        enabled = !actionState.isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("settings_restore_backup_button")
                    ) {
                        Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_restore_backup))
                    }
                }

                // D. Clear All Data
                SettingsSectionCard(title = "Data Management") {
                    Text(
                        text = "Permanently remove all locally stored decks, flashcards, progress, quiz history, study activity, and settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showClearAllDialog = true },
                        enabled = !actionState.isBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("settings_clear_all_button")
                    ) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_clear_data))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    actionState.pendingRestorePayload?.let { payload ->
        ConfirmationDialog(
            title = "Restore Backup?",
            message = "This valid backup contains ${payload.decks.size} deck(s), ${payload.flashcards.size} flashcard(s), ${payload.quizHistory.size} quiz record(s), and settings.\n\nRestoring will replace all current app data.",
            confirmLabel = "Restore",
            isDestructive = true,
            onConfirm = { viewModel.confirmRestore() },
            onDismiss = { viewModel.cancelRestore() }
        )
    }

    if (showClearAllDialog) {
        ConfirmationDialog(
            title = stringResource(R.string.settings_clear_data_title),
            message = stringResource(R.string.settings_clear_data_message),
            confirmLabel = "Clear All Data",
            isDestructive = true,
            onConfirm = {
                showClearAllDialog = false
                viewModel.clearAllData()
            },
            onDismiss = { showClearAllDialog = false }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
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
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}
