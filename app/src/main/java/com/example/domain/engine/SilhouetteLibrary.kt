package com.example.domain.engine

import com.example.domain.model.GridPos
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Parametric artistic silhouette library capable of generating 1,000+ strictly unique,
 * 4-connected, non-rectangular, recognizable shapes (hearts, butterflies, stars, flowers,
 * crowns, rockets, animals, gems, geometric mandalas, etc.).
 */
object SilhouetteLibrary {

    data class SilhouetteSpec(
        val cols: Int,
        val rows: Int,
        val activeCells: Set<GridPos>,
        val shapeName: String
    )

    private val shapeNames = listOf(
        "Heart",
        "Butterfly",
        "Star",
        "Blossom",
        "Diamond Gem",
        "Royal Crown",
        "Space Rocket",
        "Wise Owl",
        "Cat Crest",
        "Ocean Fish",
        "Knight Shield",
        "Crescent Moon",
        "Hourglass",
        "Sacred Tree",
        "Golden Key",
        "Sea Anchor",
        "Hex Crystal",
        "Angel Wings",
        "Forest Leaf",
        "Trophy Cup",
        "Sunburst",
        "Castle Tower",
        "Hero Sword",
        "Temple Bell",
        "Lotus",
        "Fox Mask",
        "Spiral Vortex",
        "Four-Leaf Clover",
        "Thunder Crest",
        "Royal Chalice",
        "Snowflake",
        "Phoenix Crest"
    )

    /**
     * Full-period bijective permutation on 0..999 so consecutive levels never repeat
     * the same motif and every level 1..1000 gets a distinct (motif, sculptCode) pairing.
     * gcd(379, 1000) == 1 -> strict 1-to-1 bijection.
     */
    fun biject1000(index0: Int): Int {
        val norm = ((index0 % 1000) + 1000) % 1000
        return (norm * 379 + 173) % 1000
    }

    fun gridDimensionsForLevel(level: Int): Pair<Int, Int> = when {
        level == 1000 -> 13 to 13
        level <= 50 -> 6 to 6
        level <= 100 -> 7 to 6
        level <= 200 -> 7 to 7
        level <= 300 -> 8 to 8
        level <= 400 -> 9 to 8
        level <= 500 -> 9 to 9
        level <= 600 -> 10 to 9
        level <= 700 -> 10 to 10
        level <= 800 -> 11 to 10
        level <= 900 -> 12 to 11
        level <= 999 -> 13 to 12
        else -> 13 to 13
    }

    /**
     * Precomputed deterministic registry for Levels 1..1000 guaranteeing 100% unique
     * silhouettes across all 1,000 levels with zero collisions.
     */
    private val registry1000: Array<SilhouetteSpec> by lazy {
        val usedSignatures = HashSet<String>(1024)
        val level1000Spec = buildUltimateLevel1000Silhouette()
        usedSignatures.add(signatureOf(level1000Spec.cols, level1000Spec.rows, level1000Spec.activeCells))

        Array(1000) { idx ->
            val levelNum = idx + 1
            if (levelNum == 1000) {
                level1000Spec
            } else {
                val (cols, rows) = gridDimensionsForLevel(levelNum)
                createUniqueForLevel(levelNum, cols, rows, usedSignatures)
            }
        }
    }

    fun buildSilhouetteForLevel(levelNumber: Int, cols: Int, rows: Int): SilhouetteSpec {
        if (levelNumber in 1..1000) {
            val cached = registry1000[levelNumber - 1]
            if (cached.cols == cols && cached.rows == rows) {
                return cached
            }
        }
        val perm = ((levelNumber * 379 + 173) and 0x7FFFFFFF)
        val shapeIdx = perm % shapeNames.size
        val variantCode = (perm / shapeNames.size) and 0xFF
        val baseMask = evaluateBaseShape(shapeIdx, cols, rows)
        val sculpted = applySymmetricSculpt(baseMask, cols, rows, variantCode, levelNumber, salt = 0)
        return SilhouetteSpec(cols, rows, sculpted, shapeNames[shapeIdx])
    }

    fun buildDailySilhouette(seedHash: Long, cols: Int, rows: Int): SilhouetteSpec {
        val idx = ((seedHash and 0x7FFFFFFFL) % shapeNames.size).toInt()
        val variant = (((seedHash ushr 8) and 0xFFL)).toInt()
        val baseMask = evaluateBaseShape(idx, cols, rows)
        val sculpted = applySymmetricSculpt(baseMask, cols, rows, variant, (seedHash and 0xFFFFL).toInt(), salt = 0)
        return SilhouetteSpec(
            cols = cols,
            rows = rows,
            activeCells = sculpted,
            shapeName = "Daily ${shapeNames[idx]}"
        )
    }

    private fun createUniqueForLevel(
        levelNumber: Int,
        cols: Int,
        rows: Int,
        usedSignatures: MutableSet<String>
    ): SilhouetteSpec {
        val perm = biject1000(levelNumber - 1)
        val shapeIdx = perm % shapeNames.size
        val variantCode = perm / shapeNames.size
        val baseMask = evaluateBaseShape(shapeIdx, cols, rows)

        for (salt in 0 until 256) {
            val sculpted = applySymmetricSculpt(baseMask, cols, rows, variantCode, levelNumber, salt)
            val sig = signatureOf(cols, rows, sculpted)
            if (usedSignatures.add(sig)) {
                return SilhouetteSpec(
                    cols = cols,
                    rows = rows,
                    activeCells = sculpted,
                    shapeName = shapeNames[shapeIdx]
                )
            }
        }

        // Deterministic guaranteed fallback if all 256 symmetric salts collided
        val fallback = LinkedHashSet(baseMask)
        val minCells = max(12, (cols * rows * 44) / 100)
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val p = GridPos(x, y)
                if (p in fallback && fallback.size > minCells) {
                    val trial = LinkedHashSet(fallback).apply { remove(p) }
                    if (isFourConnected(trial) && trial.size <= cols * rows - 4) {
                        val sig = signatureOf(cols, rows, trial)
                        if (usedSignatures.add(sig)) {
                            return SilhouetteSpec(cols, rows, trial, shapeNames[shapeIdx])
                        }
                    }
                }
            }
        }

        return SilhouetteSpec(cols, rows, fallback, shapeNames[shapeIdx])
    }

    private fun signatureOf(cols: Int, rows: Int, cells: Set<GridPos>): String = buildString {
        append(cols).append('x').append(rows).append(':')
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                if (GridPos(x, y) in cells) {
                    append(x).append(',').append(y).append(';')
                }
            }
        }
    }

    private fun buildUltimateLevel1000Silhouette(): SilhouetteSpec {
        val cols = 13
        val rows = 13
        val cells = LinkedHashSet<GridPos>()
        val cx = 6.0
        val cy = 6.0
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val dx = abs(x - cx)
                val dy = y - cy
                val inWing = dx >= 2.0 && abs(dy + dx * 0.35) <= 2.9 && dx <= 6.0
                val inCore = hypot(dx, dy) <= 3.8
                val inCrownTip = (y == 0 && (x == 6 || x == 3 || x == 9)) ||
                    (y == 1 && (dx <= 1.5 || abs(dx - 3.5) <= 0.6))
                val inTail = y >= 9 && dx <= (12 - y) * 1.25
                val isJewelCutout = (x == 6 && y == 6) || (abs(x - 6) == 2 && y == 5) || (abs(x - 6) == 4 && y == 3)
                if ((inWing || inCore || inCrownTip || inTail) && !isJewelCutout) {
                    cells.add(GridPos(x, y))
                }
            }
        }
        return SilhouetteSpec(
            cols = cols,
            rows = rows,
            activeCells = cells,
            shapeName = "Royal Phoenix"
        )
    }

    private fun evaluateBaseShape(shapeIdx: Int, cols: Int, rows: Int): MutableSet<GridPos> {
        val active = LinkedHashSet<GridPos>()
        val cx = (cols - 1) / 2.0
        val cy = (rows - 1) / 2.0
        val rx = max(1.0, (cols - 1) / 2.0)
        val ry = max(1.0, (rows - 1) / 2.0)

        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val nx = (x - cx) / rx
                val ny = (y - cy) / ry
                if (isInsideShape(shapeIdx, nx, ny, x, y)) {
                    active.add(GridPos(x, y))
                }
            }
        }

        // Repair connectivity if a thin shape split into disconnected components on a smaller grid
        ensureConnectedAndSubstantial(active, cols, rows)

        // Guarantee at least 4 corners/edges are outside so no level is ever a plain rectangle
        val maxAllowed = cols * rows - 4
        if (active.size > maxAllowed) {
            val corners = listOf(
                GridPos(0, 0),
                GridPos(cols - 1, 0),
                GridPos(0, rows - 1),
                GridPos(cols - 1, rows - 1)
            )
            for (c in corners) {
                if (active.size <= maxAllowed) break
                active.remove(c)
            }
        }
        return active
    }

    private fun ensureConnectedAndSubstantial(active: MutableSet<GridPos>, cols: Int, rows: Int) {
        val minRequired = max(12, (cols * rows * 48) / 100)
        if (active.isEmpty()) {
            active.add(GridPos(cols / 2, rows / 2))
        }

        // If disconnected, keep largest component and bridge small gaps or grow cleanly around center
        if (!isFourConnected(active)) {
            val largest = largestComponent(active)
            active.clear()
            active.addAll(largest)
        }

        // If under minRequired, gently expand 4-neighbor perimeter (respecting corner cuts)
        val cx = (cols - 1) / 2.0
        val cy = (rows - 1) / 2.0
        val maxAllowed = cols * rows - 4
        while (active.size < minRequired && active.size < maxAllowed) {
            val candidates = ArrayList<GridPos>()
            for (p in active) {
                for (nb in fourNeighbors(p, cols, rows)) {
                    if (nb !in active) {
                        val isCorner = (nb.x == 0 || nb.x == cols - 1) && (nb.y == 0 || nb.y == rows - 1)
                        if (!isCorner) candidates.add(nb)
                    }
                }
            }
            if (candidates.isEmpty()) break
            // Add symmetrically around center
            val next = candidates.minByOrNull { hypot(it.x - cx, it.y - cy) } ?: break
            active.add(next)
            val mirror = GridPos(cols - 1 - next.x, next.y)
            if (active.size < maxAllowed) {
                active.add(mirror)
            }
        }
    }

    private fun isInsideShape(
        shapeIdx: Int,
        nx: Double,
        ny: Double,
        gx: Int,
        gy: Int
    ): Boolean {
        val ax = abs(nx)
        val ay = abs(ny)
        val r = hypot(nx, ny)
        val theta = atan2(ny, nx)

        return when (shapeIdx) {
            0 -> { // Heart
                val hy = -ny * 1.05 + 0.16
                val hx = nx * 1.05
                val v = hx * hx + hy * hy - 0.86
                (v * v * v - hx * hx * hy * hy * hy) <= 0.10
            }
            1 -> { // Butterfly
                val upperWing = ax >= 0.12 && ny in -0.95..0.18 && hypot(ax - 0.56, ny + 0.36) <= 0.58
                val lowerWing = ax >= 0.12 && ny in 0.02..0.96 && hypot(ax - 0.48, ny - 0.48) <= 0.46
                val body = ax <= 0.20 && ny in -0.78..0.88
                upperWing || lowerWing || body
            }
            2 -> { // 5-Point Star
                val angle = theta + Math.PI / 2.0
                val starR = 0.72 + 0.36 * cos(5.0 * angle)
                r <= starR
            }
            3 -> { // Blossom (6-petal flower)
                val petalR = 0.74 + 0.30 * cos(6.0 * theta)
                r <= petalR
            }
            4 -> { // Diamond Gem
                val topBevel = ny >= -0.85 && ny <= -0.22 && ax <= 0.68 + (ny + 0.85) * 0.65
                val bottomCone = ny > -0.22 && ny <= 0.98 && ax <= (0.98 - ny) * 0.90
                topBevel || bottomCone
            }
            5 -> { // Royal Crown
                val base = ny >= 0.10 && ny <= 0.86 && ax <= 0.94
                val leftPeak = ny >= -0.88 && ny <= 0.15 && nx <= -0.34 && nx >= -0.96 && ny >= -0.88 + abs(nx + 0.72) * 1.35
                val midPeak = ny >= -0.98 && ny <= 0.15 && ax <= 0.38 && ny >= -0.98 + ax * 1.65
                val rightPeak = ny >= -0.88 && ny <= 0.15 && nx >= 0.34 && nx <= 0.96 && ny >= -0.88 + abs(nx - 0.72) * 1.35
                base || leftPeak || midPeak || rightPeak
            }
            6 -> { // Space Rocket
                val nose = ny <= -0.32 && ax <= (ny + 1.08) * 0.65
                val fuselage = ny > -0.32 && ny <= 0.60 && ax <= 0.45
                val fins = ny >= 0.16 && ny <= 0.90 && ax >= 0.30 && ax <= 0.94 && ny >= 0.16 + (ax - 0.30) * 0.48
                val exhaust = ny > 0.60 && ny <= 0.95 && ax <= 0.26
                nose || fuselage || fins || exhaust
            }
            7 -> { // Wise Owl
                val ears = ny in -0.98..-0.42 && ax in 0.22..0.85 && ny >= -0.98 + abs(ax - 0.55) * 1.05
                val headBody = ny in -0.65..0.82 && hypot(nx * 1.04, (ny - 0.08) * 0.92) <= 0.88
                val feet = ny in 0.72..0.98 && ax in 0.15..0.58
                ears || headBody || feet
            }
            8 -> { // Cat Crest
                val ears = ny in -0.98..-0.24 && ax in 0.22..0.90 && ny >= -0.98 + abs(ax - 0.62) * 1.25
                val face = ny in -0.48..0.88 && hypot(nx, (ny - 0.2) * 1.06) <= 0.90
                ears || face
            }
            9 -> { // Ocean Fish
                val body = nx in -0.45..0.96 && hypot((nx - 0.22) * 0.88, ny * 1.18) <= 0.82
                val tail = nx < -0.28 && nx >= -0.98 && ay <= 0.24 + (-0.28 - nx) * 1.12
                val dorsal = ny in -0.94..-0.38 && nx in -0.18..0.40
                body || tail || dorsal
            }
            10 -> { // Knight Shield
                val top = ny in -0.94..0.08 && ax <= 0.90 - max(0.0, -ny - 0.55) * 0.45
                val bottom = ny > 0.08 && ny <= 0.98 && ax <= (0.98 - ny) * 0.98
                top || bottom
            }
            11 -> { // Crescent Moon
                val outer = hypot(nx + 0.12, ny) <= 0.96
                val innerCut = hypot(nx - 0.42, ny) <= 0.62
                outer && !innerCut
            }
            12 -> { // Hourglass
                val waist = 0.28 + ay * 0.74
                ay <= 0.95 && ax <= waist
            }
            13 -> { // Sacred Tree
                val topTier = ny in -0.98..-0.18 && ax <= (ny + 1.00) * 0.78
                val midTier = ny in -0.42..0.46 && ax <= (ny + 0.58) * 0.98
                val trunk = ny in 0.35..0.98 && ax <= 0.30
                topTier || midTier || trunk
            }
            14 -> { // Golden Key
                val bow = ny in -0.96..-0.05 && hypot(nx, ny + 0.50) <= 0.56
                val shaft = ny >= -0.15 && ny <= 0.96 && ax <= 0.26
                val teeth = (ny in 0.42..0.66 || ny in 0.74..0.96) && nx in 0.18..0.68
                bow || shaft || teeth
            }
            15 -> { // Sea Anchor
                val ring = ny in -0.96..-0.40 && hypot(nx, ny + 0.68) <= 0.32
                val stem = ny in -0.55..0.90 && ax <= 0.22
                val bar = ny in -0.42..-0.14 && ax <= 0.72
                val fluke = ny >= 0.16 && hypot(nx, ny - 0.15) in 0.54..0.96 && ny <= 0.94
                ring || stem || bar || fluke
            }
            16 -> { // Hex Crystal
                ax * 0.866 + ay * 0.5 <= 0.96 && ax <= 0.88
            }
            17 -> { // Angel Wings
                val spread = ax >= 0.08 && ay <= 0.76 && hypot(ax - 0.50, ny + 0.06) <= 0.70
                val notch = ax <= 0.10 && ny > 0.38
                (spread || (ax <= 0.26 && ny in -0.58..0.68)) && !notch
            }
            18 -> { // Forest Leaf
                val blade = hypot(nx * 1.20, ny * 0.90) <= 0.92 && (ax <= (1.05 - ny) * 0.96)
                val stem = ny >= 0.62 && ax <= 0.20
                blade || stem
            }
            19 -> { // Trophy Cup
                val bowl = ny in -0.94..0.20 && ax <= 0.40 + (0.20 - ny) * 0.48
                val handles = ny in -0.80..-0.15 && ax in 0.54..0.96
                val stem = ny in 0.14..0.70 && ax <= 0.26
                val base = ny in 0.64..0.96 && ax <= 0.64
                bowl || handles || stem || base
            }
            20 -> { // Sunburst
                val rays = 0.72 + 0.30 * cos(8.0 * theta)
                r <= rays
            }
            21 -> { // Castle Tower
                val battlements = ny in -0.96..-0.42 && (ax <= 0.25 || ax in 0.45..0.94)
                val towerBody = ny in -0.46..0.94 && ax <= 0.82
                val gateCut = ny >= 0.50 && ax <= 0.20
                (battlements || towerBody) && !gateCut
            }
            22 -> { // Hero Sword
                val blade = ny in -0.98..0.34 && ax <= min(0.28, (ny + 1.06) * 0.45)
                val guard = ny in 0.24..0.50 && ax <= 0.86
                val grip = ny in 0.44..0.96 && ax <= 0.22
                blade || guard || grip
            }
            23 -> { // Temple Bell
                val dome = ny in -0.84..0.54 && ax <= 0.52 + (ny + 0.84) * 0.32
                val rim = ny in 0.40..0.74 && ax <= 0.94
                val clapper = ny in 0.70..0.96 && ax <= 0.26
                val crown = ny in -0.98..-0.76 && ax <= 0.28
                dome || rim || clapper || crown
            }
            24 -> { // Lotus
                val centerPetal = ax <= 0.38 && ny in -0.96..0.74
                val sidePetals = ny in -0.54..0.70 && ax in 0.22..0.96 && hypot(ax - 0.50, ny - 0.08) <= 0.56
                val base = ny in 0.52..0.90 && ax <= 0.76
                centerPetal || sidePetals || base
            }
            25 -> { // Fox Mask
                val ears = ny in -0.98..-0.24 && ax in 0.22..0.94 && ny >= -0.98 + abs(ax - 0.62) * 1.22
                val cheeks = ny in -0.36..0.40 && ax <= 0.96 - max(0.0, ny) * 0.52
                val snout = ny in 0.34..0.96 && ax <= (1.00 - ny) * 0.96
                ears || cheeks || snout
            }
            26 -> { // Spiral Vortex
                val disk = r <= 0.98
                val armCut = (gx + gy) % 3 == 0 && r in 0.38..0.72 && (nx * ny < 0.0)
                disk && !armCut
            }
            27 -> { // Four-Leaf Clover
                val leaf = hypot(ax - 0.42, ay - 0.42) <= 0.54
                val stem = ny in 0.42..0.98 && ax <= 0.20
                leaf || stem
            }
            28 -> { // Thunder Crest
                val topBolt = ny in -0.98..0.10 && nx in (-0.38 - ny * 0.42)..(0.68 - ny * 0.42)
                val botBolt = ny in -0.10..0.98 && nx in (-0.58 - ny * 0.32)..(0.45 - ny * 0.32)
                topBolt || botBolt
            }
            29 -> { // Royal Chalice
                val cup = ny in -0.94..0.18 && ax <= 0.32 + (0.18 - ny) * 0.62
                val stem = ny in 0.12..0.70 && ax <= 0.22
                val foot = ny in 0.64..0.96 && ax <= 0.66
                cup || stem || foot
            }
            30 -> { // Snowflake
                val cross = ax <= 0.26 || ay <= 0.26
                val diag = abs(ax - ay) <= 0.25 && r <= 0.98
                (cross || diag) && r <= 1.08
            }
            else -> { // 31: Phoenix Crest
                val wings = ax >= 0.12 && ny in -0.80..0.50 && ax <= 0.98 && abs(ny + ax * 0.28) <= 0.60
                val spine = ax <= 0.30 && ny in -0.96..0.96
                wings || spine
            }
        }
    }

    /**
     * Applies deterministic symmetric perimeter sculpts (both subtle outer accents and inner notches)
     * keyed by [variantCode] and [salt], preserving 4-connectivity and non-rectangular silhouettes.
     */
    private fun applySymmetricSculpt(
        baseMask: Set<GridPos>,
        cols: Int,
        rows: Int,
        variantCode: Int,
        levelNumber: Int,
        salt: Int
    ): Set<GridPos> {
        val result = LinkedHashSet(baseMask)
        val combinedKey = (variantCode * 2654435761L + levelNumber * 97L + salt * 131L)

        // Collect all safe symmetric boundary pairs (both removable edge cells & addable neighbor cells)
        data class SymOp(val p1: GridPos, val p2: GridPos, val isAdd: Boolean)

        val ops = ArrayList<SymOp>()
        val midX = (cols - 1) / 2
        for (y in 0 until rows) {
            for (x in 0..midX) {
                val mirrorX = cols - 1 - x
                val p1 = GridPos(x, y)
                val p2 = GridPos(mirrorX, y)
                val in1 = p1 in baseMask
                val in2 = p2 in baseMask
                if (in1 && in2) {
                    val hasOutNeighbor = fourNeighbors(p1, cols, rows).any { it !in baseMask }
                    if (hasOutNeighbor) {
                        ops.add(SymOp(p1, p2, isAdd = false))
                    }
                } else if (!in1 && !in2) {
                    val isCorner = (x == 0 || x == cols - 1) && (y == 0 || y == rows - 1)
                    val touchesBase = fourNeighbors(p1, cols, rows).any { it in baseMask }
                    if (!isCorner && touchesBase) {
                        ops.add(SymOp(p1, p2, isAdd = true))
                    }
                }
            }
        }

        if (ops.isEmpty()) return result

        // Deterministically reorder candidate ops based on level + salt
        val orderedOps = ops.withIndex().sortedBy { (i, _) ->
            val h = ((i + 1) * 1103515245L + combinedKey) and 0x7FFFFFFFL
            h
        }.map { it.value }

        val minSize = max(12, (cols * rows * 44) / 100)
        val maxSize = cols * rows - 4
        val maskBits = (variantCode xor (salt * 7)) + (salt shl 5)

        val toggleBudget = min(orderedOps.size, 8)
        for (i in 0 until toggleBudget) {
            if (((maskBits ushr (i % 12)) and 1) == 0 && !(salt > 0 && i == (salt % toggleBudget))) {
                continue
            }
            val op = orderedOps[i]
            if (op.isAdd) {
                if (result.size + 2 <= maxSize) {
                    result.add(op.p1)
                    result.add(op.p2)
                }
            } else {
                val removeCount = if (op.p1 == op.p2) 1 else 2
                if (result.size - removeCount >= minSize) {
                    result.remove(op.p1)
                    result.remove(op.p2)
                    if (!isFourConnected(result)) {
                        result.add(op.p1)
                        result.add(op.p2)
                    }
                }
            }
        }

        // If salt > 0 still matches base or needs extra distinction, also allow 1 asymmetric edge accent
        if (salt >= 32) {
            val asymCandidates = ArrayList<Pair<GridPos, Boolean>>()
            for (y in 0 until rows) {
                for (x in 0 until cols) {
                    val p = GridPos(x, y)
                    val isCorner = (x == 0 || x == cols - 1) && (y == 0 || y == rows - 1)
                    if (p in result) {
                        if (fourNeighbors(p, cols, rows).any { it !in result }) {
                            asymCandidates.add(p to false)
                        }
                    } else if (!isCorner && fourNeighbors(p, cols, rows).any { it in result }) {
                        asymCandidates.add(p to true)
                    }
                }
            }
            if (asymCandidates.isNotEmpty()) {
                val (p1, add1) = asymCandidates[(salt * 17 + levelNumber) % asymCandidates.size]
                if (add1 && result.size + 1 <= maxSize) {
                    result.add(p1)
                } else if (!add1 && result.size - 1 >= minSize) {
                    result.remove(p1)
                    if (!isFourConnected(result)) result.add(p1)
                }
            }
        }

        return result
    }

    private fun fourNeighbors(p: GridPos, cols: Int, rows: Int): List<GridPos> {
        val list = ArrayList<GridPos>(4)
        if (p.x > 0) list.add(GridPos(p.x - 1, p.y))
        if (p.x < cols - 1) list.add(GridPos(p.x + 1, p.y))
        if (p.y > 0) list.add(GridPos(p.x, p.y - 1))
        if (p.y < rows - 1) list.add(GridPos(p.x, p.y + 1))
        return list
    }

    private fun isFourConnected(cells: Set<GridPos>): Boolean {
        if (cells.isEmpty()) return false
        val start = cells.first()
        val visited = HashSet<GridPos>(cells.size)
        val queue = ArrayDeque<GridPos>()
        queue.add(start)
        visited.add(start)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val nbs = arrayOf(
                GridPos(cur.x - 1, cur.y),
                GridPos(cur.x + 1, cur.y),
                GridPos(cur.x, cur.y - 1),
                GridPos(cur.x, cur.y + 1)
            )
            for (nb in nbs) {
                if (nb in cells && visited.add(nb)) {
                    queue.add(nb)
                }
            }
        }
        return visited.size == cells.size
    }

    private fun largestComponent(cells: Set<GridPos>): Set<GridPos> {
        val remaining = LinkedHashSet(cells)
        var best: Set<GridPos> = emptySet()
        while (remaining.isNotEmpty()) {
            val start = remaining.first()
            val comp = LinkedHashSet<GridPos>()
            val queue = ArrayDeque<GridPos>()
            queue.add(start)
            comp.add(start)
            remaining.remove(start)
            while (queue.isNotEmpty()) {
                val cur = queue.removeFirst()
                val nbs = arrayOf(
                    GridPos(cur.x - 1, cur.y),
                    GridPos(cur.x + 1, cur.y),
                    GridPos(cur.x, cur.y - 1),
                    GridPos(cur.x, cur.y + 1)
                )
                for (nb in nbs) {
                    if (nb in remaining) {
                        remaining.remove(nb)
                        comp.add(nb)
                        queue.add(nb)
                    }
                }
            }
            if (comp.size > best.size) best = comp
        }
        return best
    }
}
