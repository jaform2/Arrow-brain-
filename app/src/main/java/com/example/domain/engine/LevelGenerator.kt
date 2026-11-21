package com.example.domain.engine

import com.example.domain.model.ArrowPiece
import com.example.domain.model.Direction
import com.example.domain.model.GridPos
import com.example.domain.model.PuzzleLevel
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/**
 * Deterministic, mathematically guaranteed solvable level generator for 1,000+ unique
 * artistic silhouette levels and Daily Brain Challenges.
 */
object LevelGenerator {

    private data class TierParams(
        val cols: Int,
        val rows: Int,
        val targetArrows: Int,
        val minSegmentLength: Int,
        val maxSegmentLength: Int,
        val bendProbability: Double,
        val obstacleCount: Int,
        val minBlockingRatio: Double,
        val maxInitialOpenArrows: Int
    )

    fun generateLevel(levelNumber: Int): PuzzleLevel {
        val safeLevel = levelNumber.coerceAtLeast(1)
        val tier = com.example.domain.model.DifficultyTier.forLevel(safeLevel)
        val params = paramsForLevel(safeLevel)
        val silhouette = SilhouetteLibrary.buildSilhouetteForLevel(
            levelNumber = safeLevel,
            cols = params.cols,
            rows = params.rows
        )
        val seed = mixSeed(safeLevel.toLong() * 0x5E3779B97F4A7C15L)

        return buildBestPuzzle(
            levelNumber = safeLevel,
            tier = tier,
            cols = silhouette.cols,
            rows = silhouette.rows,
            activeCells = silhouette.activeCells,
            shapeName = silhouette.shapeName,
            params = params,
            baseSeed = seed,
            isDaily = false,
            dailyDateKey = null
        )
    }

    fun generateDailyChallenge(dateKey: String): PuzzleLevel {
        var hash = 1125899906842597L
        for (ch in dateKey) {
            hash = hash * 31L + ch.code.toLong()
        }
        val seed = mixSeed(hash xor 0x517CC1B727220A95L)
        val cols = 8
        val rows = 8
        val silhouette = SilhouetteLibrary.buildDailySilhouette(hash, cols, rows)
        val params = TierParams(
            cols = cols,
            rows = rows,
            targetArrows = 26,
            minSegmentLength = 1,
            maxSegmentLength = 4,
            bendProbability = 0.60,
            obstacleCount = 2,
            minBlockingRatio = 0.55,
            maxInitialOpenArrows = 4
        )
        return buildBestPuzzle(
            levelNumber = 0,
            tier = com.example.domain.model.DifficultyTier.HARD,
            cols = silhouette.cols,
            rows = silhouette.rows,
            activeCells = silhouette.activeCells,
            shapeName = silhouette.shapeName,
            params = params,
            baseSeed = seed,
            isDaily = true,
            dailyDateKey = dateKey
        )
    }

    private fun paramsForLevel(level: Int): TierParams {
        if (level == 1000) {
            return TierParams(
                cols = 13,
                rows = 13,
                targetArrows = 52,
                minSegmentLength = 1,
                maxSegmentLength = 5,
                bendProbability = 0.78,
                obstacleCount = 4,
                minBlockingRatio = 0.78,
                maxInitialOpenArrows = 2
            )
        }

        val (cols, rows) = SilhouetteLibrary.gridDimensionsForLevel(level)
        val p = ((level - 1).coerceIn(0, 999)) / 999.0

        val targetArrows = (5 + p * 43).toInt()
        val maxSeg = when {
            level <= 40 -> 2
            level <= 150 -> 3
            level <= 400 -> 4
            level <= 800 -> 5
            else -> 5
        }
        val minSeg = 1
        val bendProb = (0.20 + p * 0.58).coerceIn(0.20, 0.78)
        val obstacles = when {
            level <= 100 -> 0
            level <= 250 -> 1
            level <= 450 -> 2
            level <= 700 -> 3
            level <= 900 -> 4
            else -> 5
        }
        val minBlocking = (0.25 + p * 0.53).coerceIn(0.25, 0.78)
        val maxOpen = when {
            level <= 100 -> 6
            level <= 300 -> 5
            level <= 600 -> 4
            level <= 850 -> 3
            else -> 2
        }

        return TierParams(
            cols = cols,
            rows = rows,
            targetArrows = targetArrows,
            minSegmentLength = minSeg,
            maxSegmentLength = maxSeg,
            bendProbability = bendProb,
            obstacleCount = obstacles,
            minBlockingRatio = minBlocking,
            maxInitialOpenArrows = maxOpen
        )
    }

    private fun buildBestPuzzle(
        levelNumber: Int,
        tier: com.example.domain.model.DifficultyTier,
        cols: Int,
        rows: Int,
        activeCells: Set<GridPos>,
        shapeName: String,
        params: TierParams,
        baseSeed: Long,
        isDaily: Boolean,
        dailyDateKey: String?
    ): PuzzleLevel {
        var bestCandidate: PuzzleLevel? = null
        var bestScore = -1.0

        val maxAttempts = 5
        for (attempt in 0 until maxAttempts) {
            val rng = Random(mixSeed(baseSeed + attempt * 7919L))
            val candidate = generateCandidate(
                levelNumber = levelNumber,
                tier = tier,
                cols = cols,
                rows = rows,
                activeCells = activeCells,
                shapeName = shapeName,
                params = params,
                rng = rng,
                isDaily = isDaily,
                dailyDateKey = dailyDateKey
            )

            if (PuzzleSolver.solveFullOrder(candidate) == null) continue

            val initialClear = PuzzleSolver.getClearArrows(
                candidate.arrows,
                candidate.obstacles,
                candidate.cols,
                candidate.rows
            ).size
            val totalArrows = candidate.arrows.size.coerceAtLeast(1)
            val blockedFraction = (totalArrows - initialClear).toDouble() / totalArrows.toDouble()
            val bottleneckBonus = if (initialClear <= params.maxInitialOpenArrows) 15.0 else 0.0
            val score = candidate.arrows.size * 2.0 + blockedFraction * 25.0 + bottleneckBonus

            if (score > bestScore) {
                bestScore = score
                bestCandidate = candidate
            }

            if (candidate.arrows.size >= params.targetArrows &&
                blockedFraction >= params.minBlockingRatio &&
                initialClear <= params.maxInitialOpenArrows
            ) {
                return candidate
            }
        }

        return bestCandidate ?: fallbackLevelOnSilhouette(
            levelNumber, tier, cols, rows, activeCells, shapeName, isDaily, dailyDateKey
        )
    }

    private fun generateCandidate(
        levelNumber: Int,
        tier: com.example.domain.model.DifficultyTier,
        cols: Int,
        rows: Int,
        activeCells: Set<GridPos>,
        shapeName: String,
        params: TierParams,
        rng: Random,
        isDaily: Boolean,
        dailyDateKey: String?
    ): PuzzleLevel {
        // 1. Place symmetric/strategic obstacles inside activeCells
        val obstacles = LinkedHashSet<GridPos>()
        if (params.obstacleCount > 0) {
            val interiorCandidates = activeCells.filter { pos ->
                // Prefer cells completely surrounded by activeCells
                pos.x in 1 until (cols - 1) && pos.y in 1 until (rows - 1) &&
                    GridPos(pos.x - 1, pos.y) in activeCells &&
                    GridPos(pos.x + 1, pos.y) in activeCells &&
                    GridPos(pos.x, pos.y - 1) in activeCells &&
                    GridPos(pos.x, pos.y + 1) in activeCells
            }.shuffled(rng)

            for (pos in interiorCandidates) {
                if (obstacles.size >= params.obstacleCount) break
                val tooClose = obstacles.any { abs(it.x - pos.x) + abs(it.y - pos.y) <= 1 }
                if (!tooClose) {
                    obstacles.add(pos)
                }
            }
        }

        val occupied = HashSet<GridPos>(obstacles)
        val placedArrows = ArrayList<ArrowPiece>()
        val rayPressure = HashMap<GridPos, Int>()

        fun addRayPressure(arrow: ArrowPiece) {
            var cursor = arrow.head + arrow.direction
            while (cursor.x in 0 until cols && cursor.y in 0 until rows) {
                if (cursor in activeCells) {
                    rayPressure[cursor] = (rayPressure[cursor] ?: 0) + 1
                }
                cursor += arrow.direction
            }
        }

        fun isRayClearOfExisting(head: GridPos, dir: Direction, selfCells: Set<GridPos>): Boolean {
            var cursor = head + dir
            while (cursor.x in 0 until cols && cursor.y in 0 until rows) {
                if (cursor in obstacles || cursor in occupied || cursor in selfCells) {
                    return false
                }
                cursor += dir
            }
            return true
        }

        val activeList = activeCells.toList()
        var failures = 0
        var nextId = 1

        while (placedArrows.size < params.targetArrows && failures < 40) {
            val validHeads = ArrayList<Triple<GridPos, Direction, Int>>()
            for (pos in activeList) {
                if (pos in occupied) continue
                val pressure = rayPressure[pos] ?: 0
                for (dir in Direction.entries) {
                    if (isRayClearOfExisting(pos, dir, emptySet())) {
                        validHeads.add(Triple(pos, dir, pressure))
                    }
                }
            }

            if (validHeads.isEmpty()) break

            validHeads.shuffle(rng)
            validHeads.sortByDescending { it.third }

            val pickPoolSize = min(validHeads.size, if (placedArrows.isEmpty()) validHeads.size else 5)
            val (head, dir, _) = validHeads[rng.nextInt(pickPoolSize)]

            val remainingEmpty = (activeCells.size - occupied.size).coerceAtLeast(1)
            val arrowsNeeded = (params.targetArrows - placedArrows.size).coerceAtLeast(1)
            val avgBudget = (remainingEmpty.toDouble() / arrowsNeeded.toDouble()).toInt().coerceAtLeast(1)
            val effectiveMaxLen = min(params.maxSegmentLength, avgBudget.coerceAtLeast(1))
            val desiredLen = if (effectiveMaxLen <= params.minSegmentLength) {
                params.minSegmentLength
            } else {
                params.minSegmentLength + rng.nextInt(effectiveMaxLen - params.minSegmentLength + 1)
            }

            val tailToHead = growRibbonBackwards(
                head = head,
                headDir = dir,
                desiredLength = desiredLen,
                bendProb = params.bendProbability,
                cols = cols,
                rows = rows,
                activeCells = activeCells,
                occupied = occupied,
                rayPressure = rayPressure,
                rng = rng
            )

            val selfSet = tailToHead.toSet()
            if (!isRayClearOfExisting(head, dir, selfSet)) {
                failures++
                continue
            }

            val piece = ArrowPiece(id = nextId++, cells = tailToHead, direction = dir)
            placedArrows.add(piece)
            occupied.addAll(tailToHead)
            addRayPressure(piece)
            failures = 0
        }

        val shuffledArrows = placedArrows.shuffled(rng).mapIndexed { index, arrow ->
            arrow.copy(id = index + 1)
        }

        return PuzzleLevel(
            levelNumber = levelNumber,
            cols = cols,
            rows = rows,
            activeCells = activeCells,
            shapeName = shapeName,
            arrows = shuffledArrows,
            obstacles = obstacles,
            difficulty = tier,
            isDailyChallenge = isDaily,
            dailyDateKey = dailyDateKey
        )
    }

    private fun growRibbonBackwards(
        head: GridPos,
        headDir: Direction,
        desiredLength: Int,
        bendProb: Double,
        cols: Int,
        rows: Int,
        activeCells: Set<GridPos>,
        occupied: Set<GridPos>,
        rayPressure: Map<GridPos, Int>,
        rng: Random
    ): List<GridPos> {
        val selfExitRay = HashSet<GridPos>()
        var rCursor = head + headDir
        while (rCursor.x in 0 until cols && rCursor.y in 0 until rows) {
            selfExitRay.add(rCursor)
            rCursor += headDir
        }

        val reverseChain = ArrayList<GridPos>(desiredLength)
        reverseChain.add(head)
        val used = HashSet<GridPos>()
        used.add(head)

        var lastMoveDir = headDir.opposite()

        for (step in 1 until desiredLength) {
            val current = reverseChain.last()
            val candidates = Direction.entries.mapNotNull { moveDir ->
                val next = current + moveDir
                if (next !in activeCells) return@mapNotNull null
                if (next in occupied || next in used || next in selfExitRay) return@mapNotNull null
                next to moveDir
            }
            if (candidates.isEmpty()) break

            val straightCandidate = candidates.firstOrNull { it.second == lastMoveDir }
            val chosen = if (straightCandidate != null && rng.nextDouble() >= bendProb) {
                straightCandidate
            } else {
                val sorted = candidates.sortedByDescending { (pos, _) -> rayPressure[pos] ?: 0 }
                val top = sorted.first()
                if ((rayPressure[top.first] ?: 0) > 0 && rng.nextDouble() < 0.78) {
                    top
                } else {
                    candidates[rng.nextInt(candidates.size)]
                }
            }

            reverseChain.add(chosen.first)
            used.add(chosen.first)
            lastMoveDir = chosen.second
        }

        return reverseChain.reversed()
    }

    private fun fallbackLevelOnSilhouette(
        levelNumber: Int,
        tier: com.example.domain.model.DifficultyTier,
        cols: Int,
        rows: Int,
        activeCells: Set<GridPos>,
        shapeName: String,
        isDaily: Boolean,
        dailyDateKey: String?
    ): PuzzleLevel {
        val chosen = activeCells.take(4).mapIndexed { index, pos ->
            ArrowPiece(index + 1, listOf(pos), Direction.UP)
        }
        return PuzzleLevel(
            levelNumber = levelNumber,
            cols = cols,
            rows = rows,
            activeCells = activeCells,
            shapeName = shapeName,
            arrows = chosen,
            obstacles = emptySet(),
            difficulty = tier,
            isDailyChallenge = isDaily,
            dailyDateKey = dailyDateKey
        )
    }

    private fun mixSeed(z0: Long): Long {
        var z = z0 + 0x5E3779B97F4A7C15L
        z = (z xor (z ushr 30)) * 0x3F58476D1CE4E5B9L
        z = (z xor (z ushr 27)) * 0x44D049BB133111EBL
        return z xor (z ushr 31)
    }
}
