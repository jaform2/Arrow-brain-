package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.CoralError
import com.example.ui.theme.CoralSoft
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GoldStar
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlueSoft
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.RewardReason
import kotlinx.coroutines.delay

@Composable
fun LevelCompleteOverlay(
    levelNumber: Int,
    isDailyChallenge: Boolean,
    earnedStars: Int,
    movesUsed: Int,
    onNextLevel: () -> Unit,
    onMainMenu: () -> Unit
) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.8f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "completeScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyPrimary.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        ConfettiOverlay()

        Card(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .scale(scale)
                .testTag("level_complete_dialog"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = PureWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(SkyBlueSoft, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEvents,
                        contentDescription = "Trophy",
                        tint = ElectricBlue,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Congratulations!",
                    style = MaterialTheme.typography.headlineMedium,
                    color = NavyPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isDailyChallenge) "Daily Challenge Completed!" else "Level $levelNumber Completed!",
                    style = MaterialTheme.typography.titleMedium,
                    color = ElectricBlue
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 1..3) {
                        val filled = i <= earnedStars
                        Icon(
                            imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (filled) "Earned star $i" else "Empty star $i",
                            tint = if (filled) GoldStar else Color(0xFFCBD5E1),
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (isDailyChallenge) {
                        "Solved in $movesUsed moves • +2 Bonus Hints!"
                    } else {
                        "Solved in $movesUsed moves • Level ${levelNumber + 1} Unlocked!"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (!isDailyChallenge) {
                    Button(
                        onClick = onNextLevel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("next_level_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                    ) {
                        Text(
                            text = "Next Level (${levelNumber + 1})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                OutlinedButton(
                    onClick = onMainMenu,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("complete_main_menu_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Home,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Main Menu",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
fun GameOverOverlay(
    levelNumber: Int,
    isDailyChallenge: Boolean,
    onRetryLevel: () -> Unit,
    onRestoreLives: () -> Unit,
    onMainMenu: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyPrimary.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .testTag("game_over_dialog"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = PureWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(CoralSoft, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = "Out of lives",
                        tint = CoralError,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Out of Lives!",
                    style = MaterialTheme.typography.headlineMedium,
                    color = NavyPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isDailyChallenge) {
                        "No hearts remaining on today's Daily Challenge."
                    } else {
                        "You ran out of hearts on Level $levelNumber."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(22.dp))

                Button(
                    onClick = onRestoreLives,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("restore_lives_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess)
                ) {
                    Icon(Icons.Filled.Favorite, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Restore 3 Lives & Continue")
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onRetryLevel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("retry_level_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Retry Level")
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onMainMenu,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("gameover_main_menu_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Filled.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Main Menu")
                }
            }
        }
    }
}

@Composable
fun RewardBonusDialog(
    reason: RewardReason,
    onClaimReward: () -> Unit,
    onDismiss: () -> Unit
) {
    var progress by remember { mutableFloatStateOf(0f) }
    val ready = progress >= 1f

    LaunchedEffect(reason) {
        progress = 0f
        val steps = 20
        repeat(steps) {
            delay(100L)
            progress = (it + 1) / steps.toFloat()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = PureWhite),
            modifier = Modifier.testTag("reward_bonus_dialog")
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            if (reason == RewardReason.HINTS) SkyBlueSoft else CoralSoft,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (reason == RewardReason.HINTS) {
                            Icons.Filled.Lightbulb
                        } else {
                            Icons.Filled.Favorite
                        },
                        contentDescription = null,
                        tint = if (reason == RewardReason.HINTS) ElectricBlue else CoralError,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = if (reason == RewardReason.HINTS) "Brain Boost Bonus" else "Heart Refill Bonus",
                    style = MaterialTheme.typography.titleLarge,
                    color = NavyPrimary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (reason == RewardReason.HINTS) {
                        "Claim +3 free Hints to help unlock tricky arrow paths."
                    } else {
                        "Restore 3 Hearts and keep your current board progress!"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = ElectricBlue,
                    trackColor = SkyBlueSoft
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onClaimReward,
                    enabled = ready,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("claim_reward_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Text(
                        text = if (!ready) {
                            "Preparing Reward..."
                        } else if (reason == RewardReason.HINTS) {
                            "Claim +3 Hints"
                        } else {
                            "Restore 3 Hearts"
                        }
                    )
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("dismiss_reward_button")
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}
