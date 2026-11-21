package com.example.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.database.FlashcardEntity
import com.example.models.DeckSummary
import com.example.models.LearningStatus
import com.example.repositories.MemoDeckRepository
import com.example.repositories.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudySessionUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val isWeakMode: Boolean = false,
    val deckId: Long? = null,
    val originalOrderCards: List<FlashcardEntity> = emptyList(),
    val activeCards: List<FlashcardEntity> = emptyList(),
    val currentIndex: Int = 0,
    val isFlipped: Boolean = false,
    val isShuffled: Boolean = false,
    val isCompleted: Boolean = false,
    val sessionRememberedCount: Int = 0,
    val sessionForgottenCount: Int = 0
) {
    val currentCard: FlashcardEntity?
        get() = activeCards.getOrNull(currentIndex)
}

class StudyViewModel(
    private val repository: MemoDeckRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val decks: StateFlow<List<DeckSummary>> = repository.observeDeckSummaries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _sessionState = MutableStateFlow(StudySessionUiState())
    val sessionState: StateFlow<StudySessionUiState> = _sessionState.asStateFlow()

    fun startDeckSession(deckId: Long, startIndex: Int = 0) {
        viewModelScope.launch {
            _sessionState.value = StudySessionUiState(isLoading = true, deckId = deckId, isWeakMode = false)
            val deck = repository.getDeckById(deckId)
            val cards = repository.getCardsByDeckOnce(deckId)
            val defaultShuffle = settingsRepository.getSettingsOnce().defaultShuffle

            val useShuffle = defaultShuffle && startIndex == 0 && cards.size > 1
            val active = if (useShuffle) cards.shuffled() else cards
            val clampedStart = startIndex.coerceIn(0, (active.size - 1).coerceAtLeast(0))

            _sessionState.value = StudySessionUiState(
                isLoading = false,
                title = deck?.name ?: "Study",
                isWeakMode = false,
                deckId = deckId,
                originalOrderCards = cards,
                activeCards = active,
                currentIndex = clampedStart,
                isFlipped = false,
                isShuffled = useShuffle,
                isCompleted = false
            )
        }
    }

    fun startWeakCardsSession() {
        viewModelScope.launch {
            _sessionState.value = StudySessionUiState(isLoading = true, isWeakMode = true)
            val weakItems = repository.observeWeakCards().first()
            val cards = weakItems.mapNotNull { repository.getCardByIdOnce(it.cardId) }
            val defaultShuffle = settingsRepository.getSettingsOnce().defaultShuffle

            val useShuffle = defaultShuffle && cards.size > 1
            val active = if (useShuffle) cards.shuffled() else cards

            _sessionState.value = StudySessionUiState(
                isLoading = false,
                title = "Weak Cards",
                isWeakMode = true,
                deckId = null,
                originalOrderCards = cards,
                activeCards = active,
                currentIndex = 0,
                isFlipped = false,
                isShuffled = useShuffle,
                isCompleted = false
            )
        }
    }

    fun toggleFlip() {
        _sessionState.update { it.copy(isFlipped = !it.isFlipped) }
    }

    fun nextCard() {
        _sessionState.update { state ->
            if (state.activeCards.isEmpty()) return@update state
            if (state.currentIndex < state.activeCards.lastIndex) {
                state.copy(currentIndex = state.currentIndex + 1, isFlipped = false)
            } else {
                state.copy(isCompleted = true, isFlipped = false)
            }
        }
    }

    fun previousCard() {
        _sessionState.update { state ->
            if (state.currentIndex > 0) {
                state.copy(currentIndex = state.currentIndex - 1, isFlipped = false)
            } else {
                state
            }
        }
    }

    fun toggleShuffle() {
        _sessionState.update { state ->
            if (state.originalOrderCards.size <= 1) return@update state
            val currentCardId = state.currentCard?.id
            val newShuffled = !state.isShuffled
            val newOrder = if (newShuffled) {
                state.originalOrderCards.shuffled()
            } else {
                state.originalOrderCards
            }
            val newIndex = if (currentCardId != null) {
                newOrder.indexOfFirst { it.id == currentCardId }.coerceAtLeast(0)
            } else {
                0
            }
            state.copy(
                activeCards = newOrder,
                currentIndex = newIndex,
                isShuffled = newShuffled,
                isFlipped = false
            )
        }
    }

    fun markCurrentCard(status: LearningStatus) {
        val state = _sessionState.value
        val card = state.currentCard ?: return
        viewModelScope.launch {
            repository.markCardStudyStatus(card.id, card.deckId, status)
            _sessionState.update { current ->
                val updatedOriginal = current.originalOrderCards.map {
                    if (it.id == card.id) it.copy(learningStatus = status) else it
                }
                val updatedActive = current.activeCards.map {
                    if (it.id == card.id) it.copy(learningStatus = status) else it
                }
                val remDelta = if (status == LearningStatus.REMEMBERED) 1 else 0
                val forgDelta = if (status == LearningStatus.FORGOTTEN) 1 else 0
                val isLast = current.currentIndex >= updatedActive.lastIndex
                current.copy(
                    originalOrderCards = updatedOriginal,
                    activeCards = updatedActive,
                    sessionRememberedCount = current.sessionRememberedCount + remDelta,
                    sessionForgottenCount = current.sessionForgottenCount + forgDelta,
                    currentIndex = if (isLast) current.currentIndex else current.currentIndex + 1,
                    isFlipped = false,
                    isCompleted = isLast
                )
            }
        }
    }

    fun restartSession() {
        _sessionState.update { state ->
            val order = if (state.isShuffled && state.originalOrderCards.size > 1) {
                state.originalOrderCards.shuffled()
            } else {
                state.originalOrderCards
            }
            state.copy(
                activeCards = order,
                currentIndex = 0,
                isFlipped = false,
                isCompleted = false,
                sessionRememberedCount = 0,
                sessionForgottenCount = 0
            )
        }
    }

    class Factory(
        private val repository: MemoDeckRepository,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            StudyViewModel(repository, settingsRepository) as T
    }
}
