package com.example.weakcards

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.common_components.EmptyStateCard
import com.example.common_components.MemoDeckTopBar
import com.example.models.WeakCardItem
import com.example.repositories.MemoDeckRepository
import com.example.theme.LocalSemanticColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WeakCardsViewModel(private val repository: MemoDeckRepository) : ViewModel() {

    val weakCards: StateFlow<List<WeakCardItem>> = repository.observeWeakCards()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun removeWeakStatus(cardId: Long) {
        viewModelScope.launch {
            repository.removeWeakStatus(cardId)
        }
    }

    class Factory(private val repository: MemoDeckRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = WeakCardsViewModel(repository) as T
    }
}

@Composable
fun WeakCardsScreen(
    viewModel: WeakCardsViewModel,
    onNavigateBack: () -> Unit,
    onStudyWeakCards: () -> Unit,
    onOpenCard: (Long) -> Unit
) {
    val weakCards by viewModel.weakCards.collectAsStateWithLifecycle()
    val semantic = LocalSemanticColors.current

    Scaffold(
        topBar = {
            MemoDeckTopBar(
                title = stringResource(R.string.weak_cards_title),
                onNavigateBack = onNavigateBack
            )
        }
    ) { innerPadding ->
        AdaptiveContentContainer(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (weakCards.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateCard(
                        icon = Icons.Filled.CheckCircle,
                        title = "No Weak Cards",
                        message = "Cards marked Forgotten in Study Mode or answered incorrectly in a completed Quiz appear here automatically for targeted revision."
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Button(
                            onClick = onStudyWeakCards,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("weak_cards_study_button")
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${stringResource(R.string.weak_cards_study)} (${weakCards.size})")
                        }
                    }

                    items(weakCards, key = { it.cardId }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("weak_card_item_${item.cardId}")
                                .clickable { onOpenCard(item.cardId) },
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
                                        text = item.deckName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = semantic.warningContainer
                                    ) {
                                        Text(
                                            text = item.reason.displayLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = semantic.warning,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = item.front,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.back,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.removeWeakStatus(item.cardId) },
                                        modifier = Modifier.testTag("weak_remove_button_${item.cardId}")
                                    ) {
                                        Text(stringResource(R.string.weak_cards_remove))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
