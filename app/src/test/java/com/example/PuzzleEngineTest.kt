package com.example

import com.example.domain.engine.LevelGenerator
import com.example.domain.engine.PuzzleSolver
import com.example.domain.engine.SilhouetteLibrary
import com.example.domain.model.ArrowPiece
import com.example.domain.model.DifficultyTier
import com.example.domain.model.Direction
import com.example.domain.model.GridPos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleEngineTest {

    @Test
    fun all_1000_levels_are_solvable_unique_artistic_and_progressively_scaled() {
        val silhouetteSignatures = HashSet<String>(1024)
        val layoutSignatures = HashSet<String>(1024)

        val tierArrowCounts = mutableMapOf<DifficultyTier, MutableList<Int>>()

        for (levelNum in 1..1020) {
            val level = LevelGenerator.generateLevel(levelNum)
            assertEquals(levelNum, level.levelNumber)
            assertTrue("Level $levelNum must have arrows", level.arrows.isNotEmpty())
            assertTrue("Level $levelNum must have active silhouette cells", level.activeCells.isNotEmpty())

            // Never a plain full rectangle
            assertTrue(
                "Level $levelNum must not be a plain rectangle (${level.cols}x${level.rows}, active=${level.activeCells.size})",
                level.activeCells.size < level.cols * level.rows
            )

            // Every arrow and obstacle must reside strictly inside activeCells
            for (obs in level.obstacles) {
                assertTrue("Obstacle on level $levelNum outside activeCells", obs in level.activeCells)
            }
            for (arrow in level.arrows) {
                for (cell in arrow.cells) {
                    assertTrue("Arrow cell on level $levelNum outside activeCells", cell in level.activeCells)
                }
            }

            // Verify 100% solvability
            val solution = PuzzleSolver.solveFullOrder(level)
            assertNotNull("Level $levelNum must be 100% solvable", solution)
            assertEquals(
                "Solution length must match arrow count on level $levelNum",
                level.arrows.size,
                solution!!.size
            )

            if (levelNum <= 1000) {
                val silKey = buildString {
                    append(level.cols).append('x').append(level.rows).append(':')
                    level.activeCells.sortedWith(compareBy({ it.y }, { it.x })).forEach {
                        append(it.x).append(',').append(it.y).append(';')
                    }
                }
                silhouetteSignatures.add(silKey)

                val layoutKey = buildString {
                    append(silKey).append('|')
                    level.obstacles.sortedWith(compareBy({ it.y }, { it.x })).forEach {
                        append('O').append(it.x).append(',').append(it.y).append(';')
                    }
                    level.arrows.sortedBy { it.cells.first().y * 100 + it.cells.first().x }.forEach { a ->
                        append(a.direction.name).append(':')
                        a.cells.forEach { c -> append(c.x).append(',').append(c.y).append('-') }
                        append('/')
                    }
                }
                layoutSignatures.add(layoutKey)

                tierArrowCounts.getOrPut(level.difficulty) { mutableListOf() }.add(level.arrows.size)
            }
        }

        // Strict zero-duplicate guarantees across all 1..1000 levels
        assertEquals(
            "All 1,000 levels must have 1,000 unique outer silhouettes",
            1000,
            silhouetteSignatures.size
        )
        assertEquals(
            "All 1,000 levels must have 1,000 unique arrow-path layouts",
            1000,
            layoutSignatures.size
        )

        // Bijection sanity
        val permuted = (0 until 1000).map { SilhouetteLibrary.biject1000(it) }.toSet()
        assertEquals(1000, permuted.size)

        // Difficulty tiers & progressive scaling
        assertEquals(DifficultyTier.EASY, LevelGenerator.generateLevel(1).difficulty)
        assertEquals(DifficultyTier.SLIGHTLY_HARDER, LevelGenerator.generateLevel(150).difficulty)
        assertEquals(DifficultyTier.MEDIUM_HARD, LevelGenerator.generateLevel(250).difficulty)
        assertEquals(DifficultyTier.HARD, LevelGenerator.generateLevel(350).difficulty)
        assertEquals(DifficultyTier.VERY_CHALLENGING, LevelGenerator.generateLevel(450).difficulty)
        assertEquals(DifficultyTier.ADVANCED, LevelGenerator.generateLevel(550).difficulty)
        assertEquals(DifficultyTier.EXPERT, LevelGenerator.generateLevel(650).difficulty)
        assertEquals(DifficultyTier.EXTREMELY_DIFFICULT, LevelGenerator.generateLevel(750).difficulty)
        assertEquals(DifficultyTier.MASTER, LevelGenerator.generateLevel(850).difficulty)
        assertEquals(DifficultyTier.ALMOST_IMPOSSIBLE, LevelGenerator.generateLevel(950).difficulty)

        val level1000 = LevelGenerator.generateLevel(1000)
        assertEquals(DifficultyTier.ULTIMATE_FINAL, level1000.difficulty)
        assertEquals("Royal Phoenix", level1000.shapeName)
        assertTrue(level1000.obstacles.isNotEmpty())

        // Progressive arrow count increase across tiers
        var prevAvg = 0.0
        for (tier in DifficultyTier.standardTiers) {
            val avg = tierArrowCounts[tier]!!.average()
            assertTrue(
                "Average arrow count for ${tier.title} ($avg) should exceed previous tier ($prevAvg)",
                avg > prevAvg
            )
            prevAvg = avg
        }
    }

    @Test
    fun levels_are_unique_and_deterministic() {
        val level1a = LevelGenerator.generateLevel(12)
        val level1b = LevelGenerator.generateLevel(12)
        val level2 = LevelGenerator.generateLevel(13)

        assertEquals(level1a.activeCells, level1b.activeCells)
        assertEquals(level1a.arrows, level1b.arrows)
        assertNotEquals(level1a.activeCells, level2.activeCells)
        assertNotEquals(level1a.arrows, level2.arrows)
    }

    @Test
    fun daily_challenges_are_solvable_and_distinct_per_date() {
        val d1 = LevelGenerator.generateDailyChallenge("2026-11-20")
        val d2 = LevelGenerator.generateDailyChallenge("2026-11-21")

        assertTrue(d1.isDailyChallenge)
        assertTrue(d1.activeCells.size < d1.cols * d1.rows)
        assertNotNull(PuzzleSolver.solveFullOrder(d1))
        assertNotNull(PuzzleSolver.solveFullOrder(d2))
        assertNotEquals(d1.arrows, d2.arrows)
    }

    @Test
    fun blocker_and_hint_logic_works_accurately() {
        val a1 = ArrowPiece(1, listOf(GridPos(0, 1)), Direction.RIGHT)
        val a2 = ArrowPiece(2, listOf(GridPos(2, 1)), Direction.UP)
        val obstacles = setOf(GridPos(2, 0))
        val arrows = listOf(a1, a2)

        assertFalse(PuzzleSolver.isArrowClear(a1, arrows, obstacles, 4, 4))
        assertEquals(GridPos(2, 1), PuzzleSolver.findFirstBlocker(a1, arrows, obstacles, 4, 4))

        assertFalse(PuzzleSolver.isArrowClear(a2, arrows, obstacles, 4, 4))
        assertEquals(GridPos(2, 0), PuzzleSolver.findFirstBlocker(a2, arrows, obstacles, 4, 4))

        val clearHint = PuzzleSolver.findBestHintArrow(arrows, emptySet(), 4, 4)
        assertEquals(a2.id, clearHint?.id)
        assertNull(PuzzleSolver.findFirstBlocker(a2, arrows, emptySet(), 4, 4))
    }
}
