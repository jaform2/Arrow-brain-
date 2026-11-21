package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioEngine
import com.example.data.repository.GameRepository
import com.example.domain.engine.LevelGenerator
import com.example.domain.engine.PuzzleSolver
import com.example.domain.model.ArrowPiece
import com.example.domain.model.GridPos
import com.example.domain.model.PuzzleLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RewardReason {
    HINTS,
    LIVES
}

data class FlyingArrow(
    val piece: ArrowPiece,
    val startedAtMillis: Long = System.currentTimeMillis()
)

data class GameBoardUiState(
    val isLoading: Boolean = true,
    val levelNumber: Int = 1,
    val isDailyChallenge: Boolean = false,
    val dailyDateKey: String? = null,
    val puzzle: PuzzleLevel? = null,
    val remainingArrows: List<ArrowPiece> = emptyList(),
    val flyingArrows: List<FlyingArrow> = emptyList(),
    val totalArrowsInLevel: Int = 0,
    val movesUsed: Int = 0,
    val mistakesUsed: Int = 0,
    val hintedArrowId: Int? = null,
    val blockedArrowId: Int? = null,
    val blockedTargetCell: GridPos? = null,
    val blockedFeedbackNonce: Int = 0,
    val isLevelComplete: Boolean = false,
    val earnedStars: Int = 3,
    val isGameOver: Boolean = false,
    val rewardModalReason: RewardReason? = null
)

class GameBoardViewModel(
    private val repository: GameRepository,
    private val audioEngine: AudioEngine
) : ViewModel() {

    private val _state = MutableStateFlow(GameBoardUiState())
    val state: StateFlow<GameBoardUiState> = _state.asStateFlow()

    private var currentBoardKey: String = "level_1"

    fun loadLevel(levelNumber: Int, isDaily: Boolean, dailyDateKey: String? = null) {
        val dateKey = dailyDateKey ?: currentTodayDateKey()
        currentBoardKey = if (isDaily) "daily_$dateKey" else "level_$levelNumber"

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            val puzzle = if (isDaily) {
                LevelGenerator.generateDailyChallenge(dateKey)
            } else {
                LevelGenerator.generateLevel(levelNumber)
            }

            if (!isDaily) {
                repository.setCurrentLevel(levelNumber, syncIfSignedIn = false)
            }

            val saved = repository.getSavedBoard(currentBoardKey)
            val restoredArrows = if (saved != null && saved.remainingArrowIdsCsv.isNotBlank()) {
                val idSet = saved.remainingArrowIdsCsv.split(",")
                    .mapNotNull { it.toIntOrNull() }
                    .toSet()
                val filtered = puzzle.arrows.filter { it.id in idSet }
                if (filtered.isNotEmpty()) filtered else puzzle.arrows
            } else {
                puzzle.arrows
            }

            val moves = if (restoredArrows.size < puzzle.arrows.size && saved != null) saved.movesUsed else 0
            val mistakes = if (restoredArrows.size < puzzle.arrows.size && saved != null) saved.mistakesUsed else 0

            _state.value = GameBoardUiState(
                isLoading = false,
                levelNumber = if (isDaily) 0 else levelNumber,
                isDailyChallenge = isDaily,
                dailyDateKey = if (isDaily) dateKey else null,
                puzzle = puzzle,
                remainingArrows = restoredArrows,
                flyingArrows = emptyList(),
                totalArrowsInLevel = puzzle.arrows.size,
                movesUsed = moves,
                mistakesUsed = mistakes,
                hintedArrowId = null,
                blockedArrowId = null,
                blockedTargetCell = null,
                isLevelComplete = false,
                isGameOver = false
            )
        }
    }

    fun onArrowTapped(
        arrow: ArrowPiece,
        livesRemaining: Int,
        livesSystemEnabled: Boolean,
        isSignedIn: Boolean
    ) {
        val current = _state.value
        val puzzle = current.puzzle ?: return
        if (current.isLevelComplete || current.isGameOver || current.isLoading) return
        if (current.remainingArrows.none { it.id == arrow.id }) return

        audioEngine.playArrowTap()
        val blocker = PuzzleSolver.findFirstBlocker(
            arrow = arrow,
            remainingArrows = current.remainingArrows,
            obstacles = puzzle.obstacles,
            cols = puzzle.cols,
            rows = puzzle.rows
        )

        if (blocker == null) {
            removeValidArrow(arrow, isSignedIn)
        } else {
            // Blocked move! Deduct 1 life if life system is active
            audioEngine.playBlockedError()
            val newMistakes = current.mistakesUsed + 1
            val newLives = if (livesSystemEnabled) (livesRemaining - 1).coerceAtLeast(0) else livesRemaining
            val outOfLives = livesSystemEnabled && newLives <= 0

            _state.update {
                it.copy(
                    mistakesUsed = newMistakes,
                    blockedArrowId = arrow.id,
                    blockedTargetCell = blocker,
                    blockedFeedbackNonce = it.blockedFeedbackNonce + 1,
                    isGameOver = outOfLives
                )
            }

            viewModelScope.launch {
                if (livesSystemEnabled) {
                    repository.setLivesRemaining(newLives, isSignedIn)
                }
                if (outOfLives) {
                    audioEngine.playGameOver()
                } else {
                    repository.saveBoardProgress(
                        boardKey = currentBoardKey,
                        remainingArrowIds = _state.value.remainingArrows.map { it.id },
                        movesUsed = _state.value.movesUsed,
                        mistakesUsed = newMistakes
                    )
                }
            }

            // Clear red shake highlight shortly after
            val nonceSnapshot = _state.value.blockedFeedbackNonce
            viewModelScope.launch {
                delay(520L)
                _state.update { s ->
                    if (s.blockedFeedbackNonce == nonceSnapshot) {
                        s.copy(blockedArrowId = null, blockedTargetCell = null)
                    } else s
                }
            }
        }
    }

    private fun removeValidArrow(arrow: ArrowPiece, isSignedIn: Boolean) {
        val current = _state.value
        val puzzle = current.puzzle ?: return
        val nextRemaining = current.remainingArrows.filter { it.id != arrow.id }
        val nextMoves = current.movesUsed + 1
        val flying = FlyingArrow(piece = arrow)

        audioEngine.playArrowRemove()

        val completed = nextRemaining.isEmpty()
        val stars = when {
            current.mistakesUsed == 0 -> 3
            current.mistakesUsed == 1 -> 2
            else -> 1
        }

        _state.update {
            it.copy(
                remainingArrows = nextRemaining,
                flyingArrows = it.flyingArrows + flying,
                movesUsed = nextMoves,
                hintedArrowId = if (it.hintedArrowId == arrow.id) null else it.hintedArrowId,
                blockedArrowId = null,
                blockedTargetCell = null,
                isLevelComplete = completed,
                earnedStars = stars
            )
        }

        // Remove flying arrow after animation finishes
        viewModelScope.launch {
            delay(360L)
            _state.update { s ->
                s.copy(flyingArrows = s.flyingArrows.filterNot { it.piece.id == arrow.id && it.startedAtMillis == flying.startedAtMillis })
            }
        }

        viewModelScope.launch {
            if (completed) {
                delay(240L)
                audioEngine.playLevelComplete()
                if (current.isDailyChallenge && current.dailyDateKey != null) {
                    repository.completeDailyChallenge(
                        dateKey = current.dailyDateKey,
                        stars = stars,
                        moves = nextMoves,
                        bonusHints = 2,
                        isSignedIn = isSignedIn
                    )
                } else {
                    repository.completeLevel(
                        levelNumber = puzzle.levelNumber,
                        stars = stars,
                        moves = nextMoves,
                        isSignedIn = isSignedIn
                    )
                }
            } else {
                repository.saveBoardProgress(
                    boardKey = currentBoardKey,
                    remainingArrowIds = nextRemaining.map { it.id },
                    movesUsed = nextMoves,
                    mistakesUsed = current.mistakesUsed
                )
            }
        }
    }

    fun requestHint(hintsRemaining: Int, isSignedIn: Boolean) {
        val current = _state.value
        val puzzle = current.puzzle ?: return
        if (current.isLevelComplete || current.isGameOver || current.remainingArrows.isEmpty()) return

        // If a hint is already highlighted, don't double-charge
        if (current.hintedArrowId != null && current.remainingArrows.any { it.id == current.hintedArrowId }) {
            audioEngine.playHint()
            return
        }

        if (hintsRemaining <= 0) {
            audioEngine.playButtonClick()
            _state.update { it.copy(rewardModalReason = RewardReason.HINTS) }
            return
        }

        val bestArrow = PuzzleSolver.findBestHintArrow(
            remainingArrows = current.remainingArrows,
            obstacles = puzzle.obstacles,
            cols = puzzle.cols,
            rows = puzzle.rows
        ) ?: return

        audioEngine.playHint()
        _state.update { it.copy(hintedArrowId = bestArrow.id) }
        viewModelScope.launch {
            repository.consumeHint(isSignedIn)
        }
    }

    fun revealNextMove(hintsRemaining: Int, isSignedIn: Boolean) {
        val current = _state.value
        val puzzle = current.puzzle ?: return
        if (current.isLevelComplete || current.isGameOver || current.remainingArrows.isEmpty()) return

        if (hintsRemaining <= 0) {
            audioEngine.playButtonClick()
            _state.update { it.copy(rewardModalReason = RewardReason.HINTS) }
            return
        }

        val bestArrow = PuzzleSolver.findBestHintArrow(
            remainingArrows = current.remainingArrows,
            obstacles = puzzle.obstacles,
            cols = puzzle.cols,
            rows = puzzle.rows
        ) ?: return

        viewModelScope.launch {
            val consumed = repository.consumeHint(isSignedIn)
            if (consumed) {
                removeValidArrow(bestArrow, isSignedIn)
            }
        }
    }

    fun restartLevel(isSignedIn: Boolean) {
        audioEngine.playButtonClick()
        val current = _state.value
        viewModelScope.launch {
            repository.clearSavedBoard(currentBoardKey)
            repository.setLivesRemaining(3, isSignedIn)
            loadLevel(
                levelNumber = if (current.isDailyChallenge) 1 else current.levelNumber,
                isDaily = current.isDailyChallenge,
                dailyDateKey = current.dailyDateKey
            )
        }
    }

    fun openRestoreLivesReward() {
        audioEngine.playButtonClick()
        _state.update { it.copy(rewardModalReason = RewardReason.LIVES) }
    }

    fun openEarnHintsReward() {
        audioEngine.playButtonClick()
        _state.update { it.copy(rewardModalReason = RewardReason.HINTS) }
    }

    fun dismissRewardModal() {
        _state.update { it.copy(rewardModalReason = null) }
    }

    fun claimReward(reason: RewardReason, isSignedIn: Boolean) {
        audioEngine.playHint()
        viewModelScope.launch {
            when (reason) {
                RewardReason.HINTS -> {
                    repository.addHints(3, isSignedIn)
                    _state.update { it.copy(rewardModalReason = null) }
                }
                RewardReason.LIVES -> {
                    repository.setLivesRemaining(3, isSignedIn)
                    _state.update { it.copy(isGameOver = false, rewardModalReason = null) }
                }
            }
        }
    }
}
