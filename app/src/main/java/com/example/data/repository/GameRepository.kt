package com.example.data.repository

import android.content.Context
import com.example.data.local.ArrowBrainDao
import com.example.data.local.ArrowBrainDatabase
import com.example.data.local.CompletedLevelEntity
import com.example.data.local.DailyChallengeEntity
import com.example.data.local.GamePreferences
import com.example.data.local.PlayerPreferencesState
import com.example.data.local.SavedBoardEntity
import com.example.data.remote.CloudCompletedLevel
import com.example.data.remote.CloudDailyChallenge
import com.example.data.remote.CloudGameSummary
import com.example.data.remote.CloudSyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class GameRepository(
    private val dao: ArrowBrainDao,
    private val prefs: GamePreferences,
    private val cloudSync: CloudSyncRepository
) {
    constructor(context: Context) : this(
        dao = ArrowBrainDatabase.getInstance(context.applicationContext).arrowBrainDao(),
        prefs = GamePreferences(context.applicationContext),
        cloudSync = CloudSyncRepository(context.applicationContext)
    )

    val preferencesFlow: Flow<PlayerPreferencesState> = prefs.preferencesFlow

    val completedLevelsFlow: Flow<Map<Int, CompletedLevelEntity>> =
        dao.observeCompletedLevels().map { list -> list.associateBy { it.levelNumber } }

    val dailyChallengesFlow: Flow<Map<String, DailyChallengeEntity>> =
        dao.observeDailyChallenges().map { list -> list.associateBy { it.dateKey } }

    fun observeCloudSummary(userId: String): Flow<CloudGameSummary?> =
        cloudSync.observeSummary(userId)

    fun observeCloudCompletedLevels(userId: String): Flow<List<CloudCompletedLevel>> =
        cloudSync.observeCompletedLevels(userId)

    fun observeCloudDailyChallenges(userId: String): Flow<List<CloudDailyChallenge>> =
        cloudSync.observeDailyChallenges(userId)

    suspend fun getSavedBoard(boardKey: String): SavedBoardEntity? =
        dao.getSavedBoard(boardKey)

    suspend fun saveBoardProgress(
        boardKey: String,
        remainingArrowIds: List<Int>,
        movesUsed: Int,
        mistakesUsed: Int
    ) {
        dao.upsertSavedBoard(
            SavedBoardEntity(
                boardKey = boardKey,
                remainingArrowIdsCsv = remainingArrowIds.joinToString(","),
                movesUsed = movesUsed,
                mistakesUsed = mistakesUsed
            )
        )
    }

    suspend fun clearSavedBoard(boardKey: String) {
        dao.deleteSavedBoard(boardKey)
    }

    suspend fun setCurrentLevel(level: Int, syncIfSignedIn: Boolean = true): Result<Unit> {
        prefs.updateCurrentLevel(level)
        if (!syncIfSignedIn) return Result.success(Unit)
        return pushSummaryToCloudIfAuthenticated()
    }

    suspend fun completeLevel(
        levelNumber: Int,
        stars: Int,
        moves: Int,
        isSignedIn: Boolean
    ): Result<Unit> {
        val existing = dao.getCompletedLevel(levelNumber)
        val mergedStars = maxOf(existing?.stars ?: 0, stars.coerceIn(1, 3))
        val mergedMoves = minOf(existing?.bestMoves ?: Int.MAX_VALUE, moves.coerceAtLeast(1))
        dao.upsertCompletedLevel(
            CompletedLevelEntity(
                levelNumber = levelNumber,
                stars = mergedStars,
                bestMoves = mergedMoves
            )
        )
        dao.deleteSavedBoard("level_$levelNumber")
        val nextLevel = levelNumber + 1
        prefs.unlockLevel(nextLevel)
        prefs.updateCurrentLevel(nextLevel)

        if (!isSignedIn) return Result.success(Unit)
        val r1 = cloudSync.upsertCompletedLevel(levelNumber, mergedStars, mergedMoves)
        val r2 = pushSummaryToCloudIfAuthenticated()
        return if (r1.isFailure) r1 else r2
    }

    suspend fun completeDailyChallenge(
        dateKey: String,
        stars: Int,
        moves: Int,
        bonusHints: Int,
        isSignedIn: Boolean
    ): Result<Unit> {
        val existing = dao.getDailyChallenge(dateKey)
        val isFirstCompletion = existing == null
        val mergedStars = maxOf(existing?.stars ?: 0, stars.coerceIn(1, 3))
        val mergedMoves = minOf(existing?.moves ?: Int.MAX_VALUE, moves.coerceAtLeast(1))
        dao.upsertDailyChallenge(
            DailyChallengeEntity(
                dateKey = dateKey,
                stars = mergedStars,
                moves = mergedMoves
            )
        )
        dao.deleteSavedBoard("daily_$dateKey")
        if (isFirstCompletion && bonusHints > 0) {
            prefs.addHints(bonusHints)
        }
        if (!isSignedIn) return Result.success(Unit)
        val r1 = cloudSync.upsertDailyChallenge(dateKey, mergedStars, mergedMoves)
        val r2 = pushSummaryToCloudIfAuthenticated()
        return if (r1.isFailure) r1 else r2
    }

    suspend fun consumeHint(isSignedIn: Boolean): Boolean {
        val current = prefs.preferencesFlow.first().hintsRemaining
        if (current <= 0) return false
        prefs.setHintsRemaining(current - 1)
        if (isSignedIn) pushSummaryToCloudIfAuthenticated()
        return true
    }

    suspend fun addHints(amount: Int, isSignedIn: Boolean) {
        prefs.addHints(amount)
        if (isSignedIn) pushSummaryToCloudIfAuthenticated()
    }

    suspend fun setLivesRemaining(lives: Int, isSignedIn: Boolean) {
        prefs.setLivesRemaining(lives)
        if (isSignedIn) pushSummaryToCloudIfAuthenticated()
    }

    suspend fun setSoundEnabled(enabled: Boolean, isSignedIn: Boolean) {
        prefs.setSoundEnabled(enabled)
        if (isSignedIn) pushSummaryToCloudIfAuthenticated()
    }

    suspend fun setMusicEnabled(enabled: Boolean, isSignedIn: Boolean) {
        prefs.setMusicEnabled(enabled)
        if (isSignedIn) pushSummaryToCloudIfAuthenticated()
    }

    suspend fun setLivesSystemEnabled(enabled: Boolean, isSignedIn: Boolean) {
        prefs.setLivesSystemEnabled(enabled)
        if (isSignedIn) pushSummaryToCloudIfAuthenticated()
    }

    suspend fun mergeCloudSummary(summary: CloudGameSummary) {
        prefs.mergeFromCloud(
            cloudHighestUnlocked = summary.highestUnlockedLevel.toInt(),
            cloudCurrentLevel = summary.currentLevel.toInt(),
            cloudHints = summary.hintsRemaining.toInt(),
            cloudLives = summary.livesRemaining.toInt(),
            cloudSoundEnabled = summary.soundEnabled,
            cloudMusicEnabled = summary.musicEnabled,
            cloudLivesSystemEnabled = summary.livesSystemEnabled
        )
    }

    suspend fun mergeCloudCompletedLevels(cloudLevels: List<CloudCompletedLevel>) {
        if (cloudLevels.isEmpty()) return
        val toUpsert = ArrayList<CompletedLevelEntity>()
        var maxCompleted = 0
        for (c in cloudLevels) {
            val lvl = c.levelNumber.toInt()
            if (lvl < 1) continue
            if (lvl > maxCompleted) maxCompleted = lvl
            val local = dao.getCompletedLevel(lvl)
            val mergedStars = maxOf(local?.stars ?: 0, c.stars.toInt().coerceIn(1, 3))
            val mergedBest = minOf(local?.bestMoves ?: Int.MAX_VALUE, c.bestMoves.toInt().coerceAtLeast(1))
            if (local == null || local.stars != mergedStars || local.bestMoves != mergedBest) {
                toUpsert.add(CompletedLevelEntity(lvl, mergedStars, mergedBest))
            }
        }
        if (toUpsert.isNotEmpty()) {
            dao.upsertCompletedLevels(toUpsert)
        }
        if (maxCompleted >= 1) {
            prefs.unlockLevel(maxCompleted + 1)
        }
    }

    suspend fun mergeCloudDailyChallenges(cloudDaily: List<CloudDailyChallenge>) {
        if (cloudDaily.isEmpty()) return
        val toUpsert = ArrayList<DailyChallengeEntity>()
        for (c in cloudDaily) {
            if (c.dateKey.isBlank()) continue
            val local = dao.getDailyChallenge(c.dateKey)
            val mergedStars = maxOf(local?.stars ?: 0, c.stars.toInt().coerceIn(1, 3))
            val mergedMoves = minOf(local?.moves ?: Int.MAX_VALUE, c.moves.toInt().coerceAtLeast(1))
            if (local == null || local.stars != mergedStars || local.moves != mergedMoves) {
                toUpsert.add(DailyChallengeEntity(c.dateKey, mergedStars, mergedMoves))
            }
        }
        if (toUpsert.isNotEmpty()) {
            dao.upsertDailyChallenges(toUpsert)
        }
    }

    suspend fun pushAllLocalProgressToCloud(): Result<Unit> {
        val rSummary = pushSummaryToCloudIfAuthenticated()
        if (rSummary.isFailure) return rSummary

        val localCompleted = dao.observeCompletedLevels().first()
        for (lvl in localCompleted) {
            val res = cloudSync.upsertCompletedLevel(lvl.levelNumber, lvl.stars, lvl.bestMoves)
            if (res.isFailure) return res
        }
        val localDaily = dao.observeDailyChallenges().first()
        for (d in localDaily) {
            val res = cloudSync.upsertDailyChallenge(d.dateKey, d.stars, d.moves)
            if (res.isFailure) return res
        }
        return Result.success(Unit)
    }

    private suspend fun pushSummaryToCloudIfAuthenticated(): Result<Unit> {
        val state = prefs.preferencesFlow.first()
        return cloudSync.upsertSummary(
            highestUnlockedLevel = state.highestUnlockedLevel,
            currentLevel = state.currentLevel,
            hintsRemaining = state.hintsRemaining,
            livesRemaining = state.livesRemaining,
            soundEnabled = state.soundEnabled,
            musicEnabled = state.musicEnabled,
            livesSystemEnabled = state.livesSystemEnabled
        )
    }

    suspend fun resetLocalProgress() {
        dao.clearCompletedLevels()
        dao.clearDailyChallenges()
        dao.clearSavedBoards()
        prefs.resetAll()
    }
}
