package com.example.domain.model

enum class Direction(val dx: Int, val dy: Int) {
    UP(0, -1),
    DOWN(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0);

    fun opposite(): Direction = when (this) {
        UP -> DOWN
        DOWN -> UP
        LEFT -> RIGHT
        RIGHT -> LEFT
    }
}

data class GridPos(val x: Int, val y: Int) {
    operator fun plus(dir: Direction): GridPos = GridPos(x + dir.dx, y + dir.dy)
}

/**
 * Represents a directional arrow piece on the puzzle board.
 * [cells] is ordered from tail (first) to head (last).
 */
data class ArrowPiece(
    val id: Int,
    val cells: List<GridPos>,
    val direction: Direction
) {
    val head: GridPos get() = cells.last()
}

enum class DifficultyTier(
    val title: String,
    val startLevel: Int,
    val endLevel: Int,
    val accentHex: Long
) {
    EASY("Easy", 1, 100, 0xFF10B981),
    SLIGHTLY_HARDER("Slightly Harder", 101, 200, 0xFF22C55E),
    MEDIUM_HARD("Medium-Hard", 201, 300, 0xFF0EA5E9),
    HARD("Hard", 301, 400, 0xFF2563EB),
    VERY_CHALLENGING("Very Challenging", 401, 500, 0xFF6366F1),
    ADVANCED("Advanced", 501, 600, 0xFF8B5CF6),
    EXPERT("Expert", 601, 700, 0xFFF59E0B),
    EXTREMELY_DIFFICULT("Extremely Difficult", 701, 800, 0xFFF97316),
    MASTER("Master", 801, 900, 0xFFEC4899),
    ALMOST_IMPOSSIBLE("Almost Impossible", 901, 999, 0xFFEF4444),
    ULTIMATE_FINAL("Ultimate Final", 1000, 1000, 0xFF0E2245),
    ENDLESS("Master Endless", 1001, Int.MAX_VALUE, 0xFF0E2245);

    companion object {
        fun forLevel(level: Int): DifficultyTier = when {
            level <= 100 -> EASY
            level <= 200 -> SLIGHTLY_HARDER
            level <= 300 -> MEDIUM_HARD
            level <= 400 -> HARD
            level <= 500 -> VERY_CHALLENGING
            level <= 600 -> ADVANCED
            level <= 700 -> EXPERT
            level <= 800 -> EXTREMELY_DIFFICULT
            level <= 900 -> MASTER
            level <= 999 -> ALMOST_IMPOSSIBLE
            level == 1000 -> ULTIMATE_FINAL
            else -> ENDLESS
        }

        val standardTiers: List<DifficultyTier> = listOf(
            EASY,
            SLIGHTLY_HARDER,
            MEDIUM_HARD,
            HARD,
            VERY_CHALLENGING,
            ADVANCED,
            EXPERT,
            EXTREMELY_DIFFICULT,
            MASTER,
            ALMOST_IMPOSSIBLE,
            ULTIMATE_FINAL
        )
    }
}

data class PuzzleLevel(
    val levelNumber: Int,
    val cols: Int,
    val rows: Int,
    val activeCells: Set<GridPos>,
    val shapeName: String,
    val arrows: List<ArrowPiece>,
    val obstacles: Set<GridPos>,
    val difficulty: DifficultyTier,
    val isDailyChallenge: Boolean = false,
    val dailyDateKey: String? = null
)
