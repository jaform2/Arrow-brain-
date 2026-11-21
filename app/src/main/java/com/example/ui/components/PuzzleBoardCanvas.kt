package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.ArrowPiece
import com.example.domain.model.Direction
import com.example.domain.model.GridPos
import com.example.domain.model.PuzzleLevel
import com.example.ui.theme.BlueGlow
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.GridDotColor
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlueAccent
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.StoneObstacleBorder
import com.example.ui.theme.StoneObstacleColor
import com.example.ui.viewmodel.FlyingArrow
import kotlin.math.min
import kotlin.math.sin

@Composable
fun PuzzleBoardCanvas(
    puzzle: PuzzleLevel,
    remainingArrows: List<ArrowPiece>,
    flyingArrows: List<FlyingArrow>,
    hintedArrowId: Int?,
    blockedArrowId: Int?,
    blockedTargetCell: GridPos?,
    blockedFeedbackNonce: Int,
    onArrowTapped: (ArrowPiece) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hintPulse")
    val hintPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hintPulseAlpha"
    )
    val dashPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dashPhase"
    )

    // Shake animation for blocked tap feedback
    val shakeAnim = remember { Animatable(0f) }
    LaunchedEffect(blockedFeedbackNonce) {
        if (blockedFeedbackNonce > 0) {
            shakeAnim.snapTo(0f)
            shakeAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 360, easing = LinearEasing)
            )
        }
    }

    // Smooth exit progress per flying arrow
    val flyProgressMap = remember { mutableMapOf<Int, Animatable<Float, *>>() }
    flyingArrows.forEach { flying ->
        val anim = flyProgressMap.getOrPut(flying.piece.id) { Animatable(0f) }
        LaunchedEffect(flying.piece.id, flying.startedAtMillis) {
            anim.snapTo(0f)
            anim.animateTo(1f, animationSpec = tween(durationMillis = 320))
        }
    }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val boardPadding = 16.dp
        val availWidth = (maxWidth - boardPadding * 2).coerceAtLeast(180.dp)
        val availHeight = (maxHeight - boardPadding * 2).coerceAtLeast(180.dp)
        val aspect = puzzle.cols.toFloat() / puzzle.rows.toFloat()

        val cardWidth = if (availWidth / aspect <= availHeight) availWidth else availHeight * aspect
        val cardHeight = cardWidth / aspect

        Surface(
            modifier = Modifier
                .size(width = cardWidth, height = cardHeight)
                .border(1.5.dp, OutlineSubtle, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .testTag("puzzle_board"),
            color = PureWhite,
            shadowElevation = 6.dp,
            shape = RoundedCornerShape(24.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PureWhite)
                    .padding(12.dp)
                    .pointerInput(puzzle, remainingArrows) {
                        detectTapGestures { tapOffset ->
                            val cellW = size.width.toFloat() / puzzle.cols
                            val cellH = size.height.toFloat() / puzzle.rows
                            val col = (tapOffset.x / cellW).toInt().coerceIn(0, puzzle.cols - 1)
                            val row = (tapOffset.y / cellH).toInt().coerceIn(0, puzzle.rows - 1)
                            val tappedPos = GridPos(col, row)

                            val hit = remainingArrows.firstOrNull { tappedPos in it.cells }
                            if (hit != null) {
                                onArrowTapped(hit)
                            }
                        }
                    }
            ) {
                val cellW = size.width / puzzle.cols
                val cellH = size.height / puzzle.rows
                val cellSize = min(cellW, cellH)

                fun centerOf(pos: GridPos): Offset = Offset(
                    x = (pos.x + 0.5f) * cellW,
                    y = (pos.y + 0.5f) * cellH
                )

                // 1. Artistic silhouette tiles & subtle grid dots (active cells only)
                val tilePad = cellSize * 0.055f
                val tileCorner = CornerRadius(cellSize * 0.20f, cellSize * 0.20f)
                for (pos in puzzle.activeCells) {
                    val topLeft = Offset(
                        x = pos.x * cellW + tilePad,
                        y = pos.y * cellH + tilePad
                    )
                    val tileSize = Size(
                        width = cellW - tilePad * 2f,
                        height = cellH - tilePad * 2f
                    )
                    drawRoundRect(
                        color = SlateSurface,
                        topLeft = topLeft,
                        size = tileSize,
                        cornerRadius = tileCorner
                    )
                    drawRoundRect(
                        color = OutlineSubtle.copy(alpha = 0.72f),
                        topLeft = topLeft,
                        size = tileSize,
                        cornerRadius = tileCorner,
                        style = Stroke(width = cellSize * 0.03f)
                    )
                    drawCircle(
                        color = GridDotColor,
                        radius = cellSize * 0.062f,
                        center = centerOf(pos)
                    )
                }

                // 2. Static stone obstacles
                for (obs in puzzle.obstacles) {
                    val topLeft = Offset(
                        obs.x * cellW + cellW * 0.14f,
                        obs.y * cellH + cellH * 0.14f
                    )
                    val boxSize = Size(cellW * 0.72f, cellH * 0.72f)
                    drawRoundRect(
                        color = StoneObstacleColor,
                        topLeft = topLeft,
                        size = boxSize,
                        cornerRadius = CornerRadius(cellSize * 0.22f, cellSize * 0.22f)
                    )
                    drawRoundRect(
                        color = StoneObstacleBorder,
                        topLeft = topLeft,
                        size = boxSize,
                        cornerRadius = CornerRadius(cellSize * 0.22f, cellSize * 0.22f),
                        style = Stroke(width = cellSize * 0.05f)
                    )
                    // Subtle X inside stone obstacle
                    val c = centerOf(obs)
                    val r = cellSize * 0.14f
                    drawLine(
                        color = PureWhite.copy(alpha = 0.7f),
                        start = Offset(c.x - r, c.y - r),
                        end = Offset(c.x + r, c.y + r),
                        strokeWidth = cellSize * 0.065f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = PureWhite.copy(alpha = 0.7f),
                        start = Offset(c.x + r, c.y - r),
                        end = Offset(c.x - r, c.y + r),
                        strokeWidth = cellSize * 0.065f,
                        cap = StrokeCap.Round
                    )
                }

                // 3. Draw remaining arrows
                for (arrow in remainingArrows) {
                    val isHinted = arrow.id == hintedArrowId
                    val isBlocked = arrow.id == blockedArrowId

                    val shakeOffset = if (isBlocked && shakeAnim.value < 1f) {
                        val wave = sin(shakeAnim.value * Math.PI * 5.0).toFloat()
                        val amp = cellSize * 0.12f * (1f - shakeAnim.value)
                        Offset(wave * amp, 0f)
                    } else Offset.Zero

                    val arrowColor = when {
                        isBlocked -> CoralError
                        isHinted -> ElectricBlue
                        else -> NavyPrimary
                    }

                    // Hinted exit-ray dotted preview
                    if (isHinted) {
                        val headCenter = centerOf(arrow.head)
                        val edgeEnd = when (arrow.direction) {
                            Direction.UP -> Offset(headCenter.x, 0f)
                            Direction.DOWN -> Offset(headCenter.x, size.height)
                            Direction.LEFT -> Offset(0f, headCenter.y)
                            Direction.RIGHT -> Offset(size.width, headCenter.y)
                        }
                        drawLine(
                            color = SkyBlueAccent.copy(alpha = 0.45f + 0.35f * hintPulse),
                            start = headCenter,
                            end = edgeEnd,
                            strokeWidth = cellSize * 0.08f,
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(
                                intervals = floatArrayOf(cellSize * 0.18f, cellSize * 0.14f),
                                phase = -dashPhase
                            )
                        )
                    }

                    drawArrowPiece(
                        arrow = arrow,
                        cellW = cellW,
                        cellH = cellH,
                        cellSize = cellSize,
                        color = arrowColor,
                        glowColor = if (isHinted) BlueGlow.copy(alpha = 0.28f + 0.25f * hintPulse) else null,
                        offset = shakeOffset,
                        alpha = 1f
                    )
                }

                // 4. Draw flying/exiting arrows
                for (flying in flyingArrows) {
                    val t = flyProgressMap[flying.piece.id]?.value ?: 0f
                    val dist = cellSize * 4.5f * t
                    val flyOffset = Offset(
                        x = flying.piece.direction.dx * dist,
                        y = flying.piece.direction.dy * dist
                    )
                    drawArrowPiece(
                        arrow = flying.piece,
                        cellW = cellW,
                        cellH = cellH,
                        cellSize = cellSize,
                        color = ElectricBlue,
                        glowColor = BlueGlow.copy(alpha = 0.25f * (1f - t)),
                        offset = flyOffset,
                        alpha = (1f - t).coerceIn(0f, 1f)
                    )
                }

                // 5. Blocker target indicator ring on invalid tap
                if (blockedTargetCell != null && blockedArrowId != null) {
                    val targetCenter = centerOf(blockedTargetCell)
                    drawCircle(
                        color = CoralError.copy(alpha = 0.55f),
                        radius = cellSize * 0.36f,
                        center = targetCenter,
                        style = Stroke(width = cellSize * 0.06f)
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawArrowPiece(
    arrow: ArrowPiece,
    cellW: Float,
    cellH: Float,
    cellSize: Float,
    color: Color,
    glowColor: Color?,
    offset: Offset,
    alpha: Float
) {
    fun centerOf(pos: GridPos): Offset = Offset(
        x = (pos.x + 0.5f) * cellW + offset.x,
        y = (pos.y + 0.5f) * cellH + offset.y
    )

    val centers = arrow.cells.map(::centerOf)
    val headCenter = centers.last()
    val strokeW = cellSize * 0.25f
    val headTipOffset = cellSize * 0.26f
    val headBackOffset = cellSize * 0.14f
    val headWing = cellSize * 0.23f

    val dir = arrow.direction
    val perp = Offset(-dir.dy.toFloat(), dir.dx.toFloat())
    val dirVec = Offset(dir.dx.toFloat(), dir.dy.toFloat())

    // Build spine path
    val spinePath = Path()
    if (centers.size == 1) {
        val tailStart = headCenter - dirVec * (cellSize * 0.24f)
        val neckEnd = headCenter + dirVec * (cellSize * 0.04f)
        spinePath.moveTo(tailStart.x, tailStart.y)
        spinePath.lineTo(neckEnd.x, neckEnd.y)
    } else {
        spinePath.moveTo(centers.first().x, centers.first().y)
        for (i in 1 until centers.size) {
            spinePath.lineTo(centers[i].x, centers[i].y)
        }
        val neckEnd = headCenter + dirVec * (cellSize * 0.04f)
        spinePath.lineTo(neckEnd.x, neckEnd.y)
    }

    // Build arrowhead triangle
    val tip = headCenter + dirVec * headTipOffset
    val baseCenter = headCenter - dirVec * headBackOffset
    val leftWing = baseCenter + perp * headWing
    val rightWing = baseCenter - perp * headWing

    val headPath = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(leftWing.x, leftWing.y)
        lineTo(rightWing.x, rightWing.y)
        close()
    }

    if (glowColor != null) {
        drawPath(
            path = spinePath,
            color = glowColor.copy(alpha = glowColor.alpha * alpha),
            style = Stroke(
                width = strokeW * 1.95f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        drawCircle(
            color = glowColor.copy(alpha = glowColor.alpha * alpha),
            radius = cellSize * 0.42f,
            center = headCenter
        )
    }

    // Main navy/electric-blue ribbon body
    drawPath(
        path = spinePath,
        color = color.copy(alpha = alpha),
        style = Stroke(
            width = strokeW,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )

    // Subtle crisp inner highlight along spine for 3D depth
    if (centers.size > 1) {
        drawPath(
            path = spinePath,
            color = PureWhite.copy(alpha = 0.14f * alpha),
            style = Stroke(
                width = strokeW * 0.28f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }

    // Crisp filled arrowhead
    drawPath(
        path = headPath,
        color = color.copy(alpha = alpha)
    )
    drawPath(
        path = headPath,
        color = color.copy(alpha = alpha),
        style = Stroke(width = cellSize * 0.05f, join = StrokeJoin.Round)
    )
}
