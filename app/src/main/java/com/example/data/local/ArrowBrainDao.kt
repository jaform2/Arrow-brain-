package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ArrowBrainDao {
    @Query("SELECT * FROM completed_levels ORDER BY levelNumber ASC")
    fun observeCompletedLevels(): Flow<List<CompletedLevelEntity>>

    @Query("SELECT * FROM completed_levels WHERE levelNumber = :levelNumber LIMIT 1")
    suspend fun getCompletedLevel(levelNumber: Int): CompletedLevelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCompletedLevel(entity: CompletedLevelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCompletedLevels(entities: List<CompletedLevelEntity>)

    @Query("SELECT * FROM daily_challenges ORDER BY dateKey DESC")
    fun observeDailyChallenges(): Flow<List<DailyChallengeEntity>>

    @Query("SELECT * FROM daily_challenges WHERE dateKey = :dateKey LIMIT 1")
    suspend fun getDailyChallenge(dateKey: String): DailyChallengeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyChallenge(entity: DailyChallengeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyChallenges(entities: List<DailyChallengeEntity>)

    @Query("SELECT * FROM saved_boards WHERE boardKey = :boardKey LIMIT 1")
    suspend fun getSavedBoard(boardKey: String): SavedBoardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedBoard(entity: SavedBoardEntity)

    @Query("DELETE FROM saved_boards WHERE boardKey = :boardKey")
    suspend fun deleteSavedBoard(boardKey: String)

    @Query("DELETE FROM completed_levels")
    suspend fun clearCompletedLevels()

    @Query("DELETE FROM daily_challenges")
    suspend fun clearDailyChallenges()

    @Query("DELETE FROM saved_boards")
    suspend fun clearSavedBoards()
}
