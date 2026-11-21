package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "completed_levels")
data class CompletedLevelEntity(
    @PrimaryKey val levelNumber: Int,
    val stars: Int,
    val bestMoves: Int,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "daily_challenges")
data class DailyChallengeEntity(
    @PrimaryKey val dateKey: String,
    val stars: Int,
    val moves: Int,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_boards")
data class SavedBoardEntity(
    @PrimaryKey val boardKey: String,
    val remainingArrowIdsCsv: String,
    val movesUsed: Int,
    val mistakesUsed: Int,
    val updatedAtMillis: Long = System.currentTimeMillis()
)
