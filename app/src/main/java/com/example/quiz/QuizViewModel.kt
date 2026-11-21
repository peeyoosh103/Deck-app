package com.example.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.models.DeckSummary
import com.example.models.QuizQuestion
import com.example.models.QuizQuestionCountOption
import com.example.models.QuizResultData
import com.example.models.WrongAnswerRecord
import com.example.repositories.MemoDeckRepository
import com.example.repositories.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class QuizPhase {
    SETUP,
    IN_PROGRESS,
    RESULT,
    REVIEW_WRONG
}

data class QuizUiState(
    val phase: QuizPhase = QuizPhase.SETUP,
    val selectedDeckId: Long? = null,
    val selectedDeckName: String = "",
    val eligibleQuestionCount: Int = 0,
    val selectedCountOption: QuizQuestionCountOption = QuizQuestionCountOption.TEN,
    val questions: List<QuizQuestion> = emptyList(),
    val currentQuestionIndex: Int = 0,
    val selectedOption: String? = null,
    val correctCount: Int = 0,
    val wrongRecords: List<WrongAnswerRecord> = emptyList(),
    val resultData: QuizResultData? = null,
    val errorMessage: String? = null
) {
    val currentQuestion: QuizQuestion?
        get() = questions.getOrNull(currentQuestionIndex)
}

class QuizViewModel(
    private val repository: MemoDeckRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val decks: StateFlow<List<DeckSummary>> = repository.observeDeckSummaries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _uiState = MutableStateFlow(QuizUiState())
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    fun initializeSetup(preselectedDeckId: Long? = null) {
        viewModelScope.launch {
            val defaultOption = settingsRepository.getSettingsOnce().defaultQuizQuestionCount
            val currentDeckId = preselectedDeckId ?: _uiState.value.selectedDeckId
            _uiState.value = QuizUiState(
                phase = QuizPhase.SETUP,
                selectedCountOption = defaultOption
            )
            if (currentDeckId != null) {
                selectDeck(currentDeckId)
            }
        }
    }

    fun selectDeck(deckId: Long) {
        viewModelScope.launch {
            val deck = repository.getDeckById(deckId)
            val cards = repository.getCardsByDeckOnce(deckId)
            val eligible = QuizEngine.countEligibleQuestions(cards)
            _uiState.update {
                it.copy(
                    selectedDeckId = deckId,
                    selectedDeckName = deck?.name ?: "",
                    eligibleQuestionCount = eligible,
                    errorMessage = null
                )
            }
        }
    }

    fun selectCountOption(option: QuizQuestionCountOption) {
        _uiState.update { it.copy(selectedCountOption = option, errorMessage = null) }
    }

    fun startQuiz() {
        val state = _uiState.value
        val deckId = state.selectedDeckId ?: return
        viewModelScope.launch {
            val deck = repository.getDeckById(deckId)
            val cards = repository.getCardsByDeckOnce(deckId)
            val generated = QuizEngine.generateQuiz(cards, state.selectedCountOption)
            if (generated.isEmpty()) {
                _uiState.update {
                    it.copy(
                        eligibleQuestionCount = 0,
                        errorMessage = "At least 4 flashcards with unique answers are required in this deck to start a 4-option quiz."
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    phase = QuizPhase.IN_PROGRESS,
                    selectedDeckName = deck?.name ?: it.selectedDeckName,
                    questions = generated,
                    currentQuestionIndex = 0,
                    selectedOption = null,
                    correctCount = 0,
                    wrongRecords = emptyList(),
                    resultData = null,
                    errorMessage = null
                )
            }
        }
    }

    fun selectAnswer(option: String) {
        val state = _uiState.value
        // Lock answer once selected — never allow changing
        if (state.phase != QuizPhase.IN_PROGRESS || state.selectedOption != null) return
        val current = state.currentQuestion ?: return
        val isCorrect = QuizEngine.normalizeAnswer(option) == QuizEngine.normalizeAnswer(current.correctAnswer)
        val updatedWrong = if (isCorrect) {
            state.wrongRecords
        } else {
            state.wrongRecords + WrongAnswerRecord(
                cardId = current.cardId,
                question = current.question,
                selectedAnswer = option,
                correctAnswer = current.correctAnswer
            )
        }
        _uiState.update {
            it.copy(
                selectedOption = option,
                correctCount = if (isCorrect) it.correctCount + 1 else it.correctCount,
                wrongRecords = updatedWrong
            )
        }
    }

    fun nextQuestionOrFinish() {
        val state = _uiState.value
        if (state.phase != QuizPhase.IN_PROGRESS || state.selectedOption == null) return

        if (state.currentQuestionIndex < state.questions.lastIndex) {
            _uiState.update {
                it.copy(
                    currentQuestionIndex = it.currentQuestionIndex + 1,
                    selectedOption = null
                )
            }
        } else {
            // Complete Quiz: calculate actual results, save quiz history, mark wrong cards as Weak
            val total = state.questions.size
            val correct = state.correctCount
            val wrong = total - correct
            val accuracy = QuizEngine.calculateAccuracyPercentage(correct, total)
            val deckId = state.selectedDeckId ?: return

            val result = QuizResultData(
                deckId = deckId,
                deckName = state.selectedDeckName,
                totalQuestions = total,
                correctAnswers = correct,
                wrongAnswers = wrong,
                accuracyPercentage = accuracy,
                wrongRecords = state.wrongRecords,
                requestedCountOption = state.selectedCountOption
            )

            viewModelScope.launch {
                repository.recordCompletedQuiz(
                    deckId = deckId,
                    totalQuestions = total,
                    correctAnswers = correct,
                    wrongAnswers = wrong,
                    scorePercentage = accuracy,
                    wrongCardIds = state.wrongRecords.map { it.cardId }
                )
                _uiState.update {
                    it.copy(
                        phase = QuizPhase.RESULT,
                        resultData = result
                    )
                }
            }
        }
    }

    fun retryQuiz() {
        // Fresh question order and newly shuffled options with same configuration
        startQuiz()
    }

    fun openWrongAnswerReview() {
        _uiState.update { it.copy(phase = QuizPhase.REVIEW_WRONG) }
    }

    fun backToResultFromReview() {
        _uiState.update { it.copy(phase = QuizPhase.RESULT) }
    }

    fun abortIncompleteQuiz() {
        // Incomplete quizzes are NOT recorded in quiz statistics
        val deckId = _uiState.value.selectedDeckId
        val option = _uiState.value.selectedCountOption
        _uiState.value = QuizUiState(
            phase = QuizPhase.SETUP,
            selectedDeckId = deckId,
            selectedCountOption = option
        )
        if (deckId != null) {
            selectDeck(deckId)
        }
    }

    class Factory(
        private val repository: MemoDeckRepository,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            QuizViewModel(repository, settingsRepository) as T
    }
}
