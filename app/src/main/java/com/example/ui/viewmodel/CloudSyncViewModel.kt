package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.GameRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

sealed interface CloudSyncStatus {
    data object Idle : CloudSyncStatus
    data object Syncing : CloudSyncStatus
    data class Synced(val message: String) : CloudSyncStatus
    data class Error(val message: String) : CloudSyncStatus
}

/**
 * Instantiated ONLY inside `key(currentUserId)` when a Firebase Google user is authenticated.
 * Observes Firestore snapshots with two-tier CancellationException unwrapping and merges progress.
 */
class CloudSyncViewModel(
    private val repository: GameRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _syncStatus = MutableStateFlow<CloudSyncStatus>(CloudSyncStatus.Syncing)
    val syncStatus: StateFlow<CloudSyncStatus> = _syncStatus.asStateFlow()

    init {
        startCloudListeners()
        syncLocalToCloudNow()
    }

    private fun startCloudListeners() {
        viewModelScope.launch {
            repository.observeCloudSummary(currentUserId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val root = firestoreError ?: error
                    Log.w(TAG, "Error observing cloud summary", root)
                    _syncStatus.value = CloudSyncStatus.Error(
                        root.message ?: "Failed to sync cloud summary"
                    )
                }
                .collectLatest { summary ->
                    if (summary != null) {
                        repository.mergeCloudSummary(summary)
                        _syncStatus.value = CloudSyncStatus.Synced("Cloud synced")
                    }
                }
        }

        viewModelScope.launch {
            repository.observeCloudCompletedLevels(currentUserId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val root = firestoreError ?: error
                    Log.w(TAG, "Error observing cloud completed levels", root)
                    _syncStatus.value = CloudSyncStatus.Error(
                        root.message ?: "Failed to sync completed levels"
                    )
                }
                .collectLatest { levels ->
                    repository.mergeCloudCompletedLevels(levels)
                    _syncStatus.value = CloudSyncStatus.Synced("Cloud synced")
                }
        }

        viewModelScope.launch {
            repository.observeCloudDailyChallenges(currentUserId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val root = firestoreError ?: error
                    Log.w(TAG, "Error observing cloud daily challenges", root)
                    _syncStatus.value = CloudSyncStatus.Error(
                        root.message ?: "Failed to sync daily challenges"
                    )
                }
                .collectLatest { daily ->
                    repository.mergeCloudDailyChallenges(daily)
                }
        }
    }

    fun syncLocalToCloudNow() {
        viewModelScope.launch {
            _syncStatus.value = CloudSyncStatus.Syncing
            val result = repository.pushAllLocalProgressToCloud()
            _syncStatus.value = result.fold(
                onSuccess = { CloudSyncStatus.Synced("All progress backed up") },
                onFailure = { CloudSyncStatus.Error(it.localizedMessage ?: "Cloud sync failed") }
            )
        }
    }

    private companion object {
        const val TAG = "CloudSyncVM"
    }
}
