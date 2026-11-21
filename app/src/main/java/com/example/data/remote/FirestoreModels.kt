package com.example.data.remote

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class CloudGameSummary(
    @DocumentId val id: String = "",
    val userId: String = "",
    val highestUnlockedLevel: Long = 1L,
    val currentLevel: Long = 1L,
    val hintsRemaining: Long = 3L,
    val livesRemaining: Long = 3L,
    val soundEnabled: Boolean = true,
    val musicEnabled: Boolean = true,
    val livesSystemEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudCompletedLevel(
    @DocumentId val id: String = "",
    val userId: String = "",
    val levelNumber: Long = 1L,
    val stars: Long = 1L,
    val bestMoves: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudDailyChallenge(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateKey: String = "",
    val stars: Long = 1L,
    val moves: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
