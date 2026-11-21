package com.example.domain.engine

import com.example.domain.model.ArrowPiece
import com.example.domain.model.Direction
import com.example.domain.model.GridPos
import com.example.domain.model.PuzzleLevel

/**
 * Pure, deterministic puzzle physics and exact solver for Arrow Brain.
 */
object PuzzleSolver {

    /**
     * Checks if [arrow] can be removed given the current [remainingArrows] and static [obstacles].
     * Returns null if the exit path is completely clear, or the first blocking [GridPos] if blocked.
     */
    fun findFirstBlocker(
        arrow: ArrowPiece,
        remainingArrows: List<ArrowPiece>,
        obstacles: Set<GridPos>,
        cols: Int,
        rows: Int
    ): GridPos? {
        val occupiedByOthers = HashSet<GridPos>(remainingArrows.size * 3)
        for (other in remainingArrows) {
            if (other.id != arrow.id) {
                occupiedByOthers.addAll(other.cells)
            }
        }

        var cursor = arrow.head + arrow.direction
        while (cursor.x in 0 until cols && cursor.y in 0 until rows) {
            if (cursor in obstacles || cursor in occupiedByOthers) {
                return cursor
            }
            cursor += arrow.direction
        }
        return null
    }

    fun isArrowClear(
        arrow: ArrowPiece,
        remainingArrows: List<ArrowPiece>,
        obstacles: Set<GridPos>,
        cols: Int,
        rows: Int
    ): Boolean = findFirstBlocker(arrow, remainingArrows, obstacles, cols, rows) == null

    /**
     * Returns all currently removable arrows in the given board state.
     */
    fun getClearArrows(
        remainingArrows: List<ArrowPiece>,
        obstacles: Set<GridPos>,
        cols: Int,
        rows: Int
    ): List<ArrowPiece> {
        if (remainingArrows.isEmpty()) return emptyList()
        val cellOwner = HashMap<GridPos, Int>(remainingArrows.size * 3)
        for (arrow in remainingArrows) {
            for (cell in arrow.cells) {
                cellOwner[cell] = arrow.id
            }
        }

        val clearList = ArrayList<ArrowPiece>()
        for (arrow in remainingArrows) {
            var blocked = false
            var cursor = arrow.head + arrow.direction
            while (cursor.x in 0 until cols && cursor.y in 0 until rows) {
                val owner = cellOwner[cursor]
                if (cursor in obstacles || (owner != null && owner != arrow.id)) {
                    blocked = true
                    break
                }
                cursor += arrow.direction
            }
            if (!blocked) clearList.add(arrow)
        }
        return clearList
    }

    /**
     * Picks the smartest hint arrow: among all currently clear arrows, prefers the one
     * whose removal unblocks the largest number of currently blocked arrows.
     */
    fun findBestHintArrow(
        remainingArrows: List<ArrowPiece>,
        obstacles: Set<GridPos>,
        cols: Int,
        rows: Int
    ): ArrowPiece? {
        val clearArrows = getClearArrows(remainingArrows, obstacles, cols, rows)
        if (clearArrows.isEmpty()) return null
        if (clearArrows.size == 1) return clearArrows.first()

        val baseClearCount = clearArrows.size
        var bestArrow = clearArrows.first()
        var bestGain = -1

        for (candidate in clearArrows) {
            val nextBoard = remainingArrows.filter { it.id != candidate.id }
            val nextClearCount = getClearArrows(nextBoard, obstacles, cols, rows).size
            val gain = nextClearCount - (baseClearCount - 1)
            if (gain > bestGain) {
                bestGain = gain
                bestArrow = candidate
            }
        }
        return bestArrow
    }

    /**
     * Verifies whether a complete puzzle level is 100% solvable by greedy monotonic peeling.
     * Because removing any clear arrow never adds blockers, greedy peeling is both sound and complete!
     * Returns the full valid removal sequence of arrow IDs, or null if unsolvable.
     */
    fun solveFullOrder(level: PuzzleLevel): List<Int>? {
        val remaining = level.arrows.toMutableList()
        val solution = ArrayList<Int>(remaining.size)

        while (remaining.isNotEmpty()) {
            val clear = getClearArrows(remaining, level.obstacles, level.cols, level.rows)
            if (clear.isEmpty()) return null
            val chosen = clear.first()
            solution.add(chosen.id)
            remaining.removeAll { it.id == chosen.id }
        }
        return solution
    }
}
