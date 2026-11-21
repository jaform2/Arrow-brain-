package com.example.data.remote

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CloudSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeSummary(userId: String): Flow<CloudGameSummary?> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || uid != userId) {
            emit(null)
            return@flow
        }
        val docRef = db.collection("users").document(uid)
            .collection("gameData").document("summary")
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            CloudGameSummary::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeCompletedLevels(userId: String): Flow<List<CloudCompletedLevel>> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || uid != userId) {
            emit(emptyList())
            return@flow
        }
        val colRef = db.collection("users").document(uid).collection("completedLevels")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudCompletedLevel::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeDailyChallenges(userId: String): Flow<List<CloudDailyChallenge>> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || uid != userId) {
            emit(emptyList())
            return@flow
        }
        val colRef = db.collection("users").document(uid).collection("dailyChallenges")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudDailyChallenge::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertSummary(
        highestUnlockedLevel: Int,
        currentLevel: Int,
        hintsRemaining: Int,
        livesRemaining: Int,
        soundEnabled: Boolean,
        musicEnabled: Boolean,
        livesSystemEnabled: Boolean
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/gameData/summary"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
                .collection("gameData").document("summary")
            docPath = docRef.path

            val safeHighest = highestUnlockedLevel.coerceIn(1, 100000)
            val safeCurrent = currentLevel.coerceIn(1, 100000)
            val safeHints = hintsRemaining.coerceIn(0, 9999)
            val safeLives = livesRemaining.coerceIn(0, 3)

            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                docRef.update(
                    mapOf(
                        "highestUnlockedLevel" to safeHighest,
                        "currentLevel" to safeCurrent,
                        "hintsRemaining" to safeHints,
                        "livesRemaining" to safeLives,
                        "soundEnabled" to soundEnabled,
                        "musicEnabled" to musicEnabled,
                        "livesSystemEnabled" to livesSystemEnabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "highestUnlockedLevel" to safeHighest,
                        "currentLevel" to safeCurrent,
                        "hintsRemaining" to safeHints,
                        "livesRemaining" to safeLives,
                        "soundEnabled" to soundEnabled,
                        "musicEnabled" to musicEnabled,
                        "livesSystemEnabled" to livesSystemEnabled,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun upsertCompletedLevel(
        levelNumber: Int,
        stars: Int,
        bestMoves: Int
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/completedLevels/$levelNumber"
        return try {
            val uid = requireUserId()
            val docId = levelNumber.coerceIn(1, 100000).toString()
            val docRef = db.collection("users").document(uid)
                .collection("completedLevels").document(docId)
            docPath = docRef.path

            val safeStars = stars.coerceIn(1, 3)
            val safeMoves = bestMoves.coerceIn(1, 10000)
            val existingSnap = docRef.get().await()

            if (existingSnap.exists()) {
                val oldStars = (existingSnap.getLong("stars") ?: 1L).toInt()
                val oldBest = (existingSnap.getLong("bestMoves") ?: Int.MAX_VALUE.toLong()).toInt()
                docRef.update(
                    mapOf(
                        "stars" to maxOf(oldStars, safeStars),
                        "bestMoves" to minOf(oldBest, safeMoves),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "levelNumber" to levelNumber.coerceIn(1, 100000),
                        "stars" to safeStars,
                        "bestMoves" to safeMoves,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun upsertDailyChallenge(
        dateKey: String,
        stars: Int,
        moves: Int
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/dailyChallenges/$dateKey"
        return try {
            val uid = requireUserId()
            require(Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$").matches(dateKey)) {
                "Invalid dateKey format"
            }
            val docRef = db.collection("users").document(uid)
                .collection("dailyChallenges").document(dateKey)
            docPath = docRef.path

            val safeStars = stars.coerceIn(1, 3)
            val safeMoves = moves.coerceIn(1, 10000)
            val existingSnap = docRef.get().await()

            if (existingSnap.exists()) {
                val oldStars = (existingSnap.getLong("stars") ?: 1L).toInt()
                val oldMoves = (existingSnap.getLong("moves") ?: Int.MAX_VALUE.toLong()).toInt()
                docRef.update(
                    mapOf(
                        "stars" to maxOf(oldStars, safeStars),
                        "moves" to minOf(oldMoves, safeMoves),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "dateKey" to dateKey,
                        "stars" to safeStars,
                        "moves" to safeMoves,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }
}
