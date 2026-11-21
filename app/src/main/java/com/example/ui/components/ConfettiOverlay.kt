package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GoldStar
import com.example.ui.theme.PurpleExpert
import com.example.ui.theme.SkyBlueAccent
import kotlin.math.sin
import kotlin.random.Random

private data class ConfettiParticle(
    val xFraction: Float,
    val startYFraction: Float,
    val speed: Float,
    val sizeDp: Float,
    val phase: Float,
    val color: Color,
    val isCircle: Boolean
)

@Composable
fun ConfettiOverlay(
    modifier: Modifier = Modifier
) {
    val particles = remember {
        val rng = Random(42)
        val palette = listOf(ElectricBlue, SkyBlueAccent, GoldStar, EmeraldSuccess, PurpleExpert)
        List(44) {
            ConfettiParticle(
                xFraction = rng.nextFloat(),
                startYFraction = rng.nextFloat() * 0.4f - 0.35f,
                speed = 0.65f + rng.nextFloat() * 0.75f,
                sizeDp = 6f + rng.nextFloat() * 6f,
                phase = rng.nextFloat() * 6.28f,
                color = palette[rng.nextInt(palette.size)],
                isCircle = rng.nextBoolean()
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "confetti")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "confettiFall"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        for (p in particles) {
            val yFrac = (p.startYFraction + progress * p.speed * 1.35f) % 1.15f
            val xDrift = sin((progress * 6.28f + p.phase).toDouble()).toFloat() * 22f
            val px = p.xFraction * w + xDrift
            val py = yFrac * h
            val sz = p.sizeDp * density

            if (p.isCircle) {
                drawCircle(
                    color = p.color.copy(alpha = 0.88f),
                    radius = sz * 0.5f,
                    center = Offset(px, py)
                )
            } else {
                rotate(degrees = progress * 360f + p.phase * 57f, pivot = Offset(px, py)) {
                    drawRect(
                        color = p.color.copy(alpha = 0.88f),
                        topLeft = Offset(px - sz * 0.5f, py - sz * 0.3f),
                        size = Size(sz, sz * 0.6f)
                    )
                }
            }
        }
    }
}
