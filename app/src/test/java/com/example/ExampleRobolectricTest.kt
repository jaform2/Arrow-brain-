package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ArrowBrainDatabase
import com.example.data.local.CompletedLevelEntity
import com.example.data.local.GamePreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Arrow Brain", appName)
    }

    @Test
    fun `local room and datastore persist current level and completed stars`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ArrowBrainDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.arrowBrainDao()
        val prefs = GamePreferences(context)

        dao.upsertCompletedLevel(CompletedLevelEntity(levelNumber = 1, stars = 3, bestMoves = 5))
        dao.upsertCompletedLevel(CompletedLevelEntity(levelNumber = 2, stars = 2, bestMoves = 8))
        prefs.unlockLevel(3)
        prefs.updateCurrentLevel(3)
        prefs.setHintsRemaining(5)

        val prefState = prefs.preferencesFlow.first()
        assertEquals(3, prefState.currentLevel)
        assertEquals(3, prefState.highestUnlockedLevel)
        assertEquals(5, prefState.hintsRemaining)

        val level1 = dao.getCompletedLevel(1)
        assertNotNull(level1)
        assertEquals(3, level1?.stars)
        db.close()
    }
}
