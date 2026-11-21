package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.gamePrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "arrow_brain_prefs"
)

data class PlayerPreferencesState(
    val highestUnlockedLevel: Int = 1,
    val currentLevel: Int = 1,
    val hintsRemaining: Int = 3,
    val livesRemaining: Int = 3,
    val soundEnabled: Boolean = true,
    val musicEnabled: Boolean = true,
    val livesSystemEnabled: Boolean = true
)

class GamePreferences(private val context: Context) {

    private object Keys {
        val HIGHEST_UNLOCKED_LEVEL = intPreferencesKey("highest_unlocked_level")
        val CURRENT_LEVEL = intPreferencesKey("current_level")
        val HINTS_REMAINING = intPreferencesKey("hints_remaining")
        val LIVES_REMAINING = intPreferencesKey("lives_remaining")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val MUSIC_ENABLED = booleanPreferencesKey("music_enabled")
        val LIVES_SYSTEM_ENABLED = booleanPreferencesKey("lives_system_enabled")
    }

    val preferencesFlow: Flow<PlayerPreferencesState> = context.gamePrefsDataStore.data.map { prefs ->
        PlayerPreferencesState(
            highestUnlockedLevel = (prefs[Keys.HIGHEST_UNLOCKED_LEVEL] ?: 1).coerceAtLeast(1),
            currentLevel = (prefs[Keys.CURRENT_LEVEL] ?: 1).coerceAtLeast(1),
            hintsRemaining = (prefs[Keys.HINTS_REMAINING] ?: 3).coerceAtLeast(0),
            livesRemaining = (prefs[Keys.LIVES_REMAINING] ?: 3).coerceIn(0, 3),
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
            musicEnabled = prefs[Keys.MUSIC_ENABLED] ?: true,
            livesSystemEnabled = prefs[Keys.LIVES_SYSTEM_ENABLED] ?: true
        )
    }

    suspend fun updateCurrentLevel(level: Int) {
        context.gamePrefsDataStore.edit { prefs ->
            val safe = level.coerceAtLeast(1)
            prefs[Keys.CURRENT_LEVEL] = safe
            val currentHighest = prefs[Keys.HIGHEST_UNLOCKED_LEVEL] ?: 1
            if (safe > currentHighest) {
                prefs[Keys.HIGHEST_UNLOCKED_LEVEL] = safe
            }
        }
    }

    suspend fun unlockLevel(level: Int) {
        context.gamePrefsDataStore.edit { prefs ->
            val currentHighest = prefs[Keys.HIGHEST_UNLOCKED_LEVEL] ?: 1
            if (level > currentHighest) {
                prefs[Keys.HIGHEST_UNLOCKED_LEVEL] = level
            }
        }
    }

    suspend fun setHintsRemaining(hints: Int) {
        context.gamePrefsDataStore.edit { prefs ->
            prefs[Keys.HINTS_REMAINING] = hints.coerceAtLeast(0)
        }
    }

    suspend fun addHints(delta: Int) {
        context.gamePrefsDataStore.edit { prefs ->
            val current = prefs[Keys.HINTS_REMAINING] ?: 3
            prefs[Keys.HINTS_REMAINING] = (current + delta).coerceAtLeast(0)
        }
    }

    suspend fun setLivesRemaining(lives: Int) {
        context.gamePrefsDataStore.edit { prefs ->
            prefs[Keys.LIVES_REMAINING] = lives.coerceIn(0, 3)
        }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.gamePrefsDataStore.edit { prefs ->
            prefs[Keys.SOUND_ENABLED] = enabled
        }
    }

    suspend fun setMusicEnabled(enabled: Boolean) {
        context.gamePrefsDataStore.edit { prefs ->
            prefs[Keys.MUSIC_ENABLED] = enabled
        }
    }

    suspend fun setLivesSystemEnabled(enabled: Boolean) {
        context.gamePrefsDataStore.edit { prefs ->
            prefs[Keys.LIVES_SYSTEM_ENABLED] = enabled
        }
    }

    suspend fun mergeFromCloud(
        cloudHighestUnlocked: Int,
        cloudCurrentLevel: Int,
        cloudHints: Int,
        cloudLives: Int,
        cloudSoundEnabled: Boolean,
        cloudMusicEnabled: Boolean,
        cloudLivesSystemEnabled: Boolean
    ) {
        context.gamePrefsDataStore.edit { prefs ->
            val localHighest = prefs[Keys.HIGHEST_UNLOCKED_LEVEL] ?: 1
            val mergedHighest = maxOf(localHighest, cloudHighestUnlocked.coerceAtLeast(1))
            prefs[Keys.HIGHEST_UNLOCKED_LEVEL] = mergedHighest

            val localCurrent = prefs[Keys.CURRENT_LEVEL] ?: 1
            prefs[Keys.CURRENT_LEVEL] = maxOf(localCurrent, cloudCurrentLevel).coerceIn(1, mergedHighest)

            val localHints = prefs[Keys.HINTS_REMAINING] ?: 3
            prefs[Keys.HINTS_REMAINING] = maxOf(localHints, cloudHints)

            if (Keys.LIVES_REMAINING !in prefs) {
                prefs[Keys.LIVES_REMAINING] = cloudLives.coerceIn(0, 3)
            }
            if (Keys.SOUND_ENABLED !in prefs) {
                prefs[Keys.SOUND_ENABLED] = cloudSoundEnabled
            }
            if (Keys.MUSIC_ENABLED !in prefs) {
                prefs[Keys.MUSIC_ENABLED] = cloudMusicEnabled
            }
            if (Keys.LIVES_SYSTEM_ENABLED !in prefs) {
                prefs[Keys.LIVES_SYSTEM_ENABLED] = cloudLivesSystemEnabled
            }
        }
    }

    suspend fun resetAll() {
        context.gamePrefsDataStore.edit { prefs ->
            prefs[Keys.HIGHEST_UNLOCKED_LEVEL] = 1
            prefs[Keys.CURRENT_LEVEL] = 1
            prefs[Keys.HINTS_REMAINING] = 3
            prefs[Keys.LIVES_REMAINING] = 3
        }
    }
}
