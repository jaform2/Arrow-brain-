package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioEngine
import com.example.data.local.CompletedLevelEntity
import com.example.data.local.DailyChallengeEntity
import com.example.data.local.PlayerPreferencesState
import com.example.data.repository.GameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppProgressUiState(
    val prefs: PlayerPreferencesState = PlayerPreferencesState(),
    val completedLevels: Map<Int, CompletedLevelEntity> = emptyMap(),
    val dailyChallenges: Map<String, DailyChallengeEntity> = emptyMap(),
    val todayDateKey: String = currentTodayDateKey(),
    val totalStars: Int = 0,
    val totalCompletedCount: Int = 0,
    val isDailyCompletedToday: Boolean = false
)

fun currentTodayDateKey(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return sdf.format(Date())
}

class MainProgressViewModel(
    private val repository: GameRepository,
    val audioEngine: AudioEngine
) : ViewModel() {

    private val _todayDateKey = MutableStateFlow(currentTodayDateKey())

    val uiState: StateFlow<AppProgressUiState> = combine(
        repository.preferencesFlow,
        repository.completedLevelsFlow,
        repository.dailyChallengesFlow,
        _todayDateKey
    ) { prefs, completed, daily, dateKey ->
        audioEngine.soundEnabled = prefs.soundEnabled
        audioEngine.setMusicEnabled(prefs.musicEnabled)

        val totalStars = completed.values.sumOf { it.stars } + daily.values.sumOf { it.stars }
        AppProgressUiState(
            prefs = prefs,
            completedLevels = completed,
            dailyChallenges = daily,
            todayDateKey = dateKey,
            totalStars = totalStars,
            totalCompletedCount = completed.size,
            isDailyCompletedToday = daily.containsKey(dateKey)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = AppProgressUiState()
    )

    fun refreshTodayKey() {
        _todayDateKey.value = currentTodayDateKey()
    }

    fun toggleSound(isSignedIn: Boolean) {
        audioEngine.playButtonClick()
        val next = !uiState.value.prefs.soundEnabled
        viewModelScope.launch {
            repository.setSoundEnabled(next, isSignedIn)
        }
    }

    fun toggleMusic(isSignedIn: Boolean) {
        audioEngine.playButtonClick()
        val next = !uiState.value.prefs.musicEnabled
        viewModelScope.launch {
            repository.setMusicEnabled(next, isSignedIn)
        }
    }

    fun toggleLivesSystem(isSignedIn: Boolean) {
        audioEngine.playButtonClick()
        val next = !uiState.value.prefs.livesSystemEnabled
        viewModelScope.launch {
            repository.setLivesSystemEnabled(next, isSignedIn)
        }
    }

    fun selectLevelAsCurrent(level: Int, isSignedIn: Boolean) {
        audioEngine.playButtonClick()
        viewModelScope.launch {
            repository.setCurrentLevel(level, isSignedIn)
        }
    }

    fun resetProgress() {
        audioEngine.playButtonClick()
        viewModelScope.launch {
            repository.resetLocalProgress()
        }
    }
}
