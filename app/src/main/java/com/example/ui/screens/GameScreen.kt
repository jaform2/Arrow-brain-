package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.components.GameOverOverlay
import com.example.ui.components.LevelCompleteOverlay
import com.example.ui.components.PuzzleBoardCanvas
import com.example.ui.components.RewardBonusDialog
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlueSoft
import com.example.ui.theme.SoftSnowBg
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.AppProgressUiState
import com.example.ui.viewmodel.GameBoardUiState
import com.example.ui.viewmodel.GameBoardViewModel

@Composable
fun GameScreen(
    levelToLoad: Int,
    isDailyChallenge: Boolean,
    progressState: AppProgressUiState,
    boardState: GameBoardUiState,
    boardViewModel: GameBoardViewModel,
    isSignedIn: Boolean,
    onBack: () -> Unit,
    onNavigateToLevel: (Int) -> Unit,
    onToggleSound: () -> Unit
) {
    BackHandler { onBack() }

    LaunchedEffect(levelToLoad, isDailyChallenge) {
        boardViewModel.loadLevel(
            levelNumber = levelToLoad,
            isDaily = isDailyChallenge,
            dailyDateKey = progressState.todayDateKey
        )
    }

    val prefs = progressState.prefs

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = SoftSnowBg,
            bottomBar = {
                Surface(
                    color = PureWhite,
                    tonalElevation = 2.dp,
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .widthIn(max = 560.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Hint button (Lightbulb icon)
                            FilledTonalButton(
                                onClick = {
                                    boardViewModel.requestHint(prefs.hintsRemaining, isSignedIn)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp)
                                    .testTag("hint_button"),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = SkyBlueSoft,
                                    contentColor = NavyPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lightbulb,
                                    contentDescription = "Hint",
                                    tint = ElectricBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (prefs.hintsRemaining > 0) {
                                        "Hint (${prefs.hintsRemaining})"
                                    } else {
                                        "Get Hints"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Separate button to reveal & execute the next correct move
                            Button(
                                onClick = {
                                    boardViewModel.revealNextMove(prefs.hintsRemaining, isSignedIn)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp)
                                    .testTag("reveal_next_move_button"),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricBlue,
                                    contentColor = PureWhite
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoFixHigh,
                                    contentDescription = "Reveal next move",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Next Move",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top HUD Bar
                Row(
                    modifier = Modifier
                        .widthIn(max = 620.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("game_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = NavyPrimary
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isDailyChallenge) {
                                "Daily Challenge"
                            } else {
                                "Level ${boardState.levelNumber}"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = NavyPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        val puzzle = boardState.puzzle
                        val tier = puzzle?.difficulty
                        if (tier != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(tier.accentHex), CircleShape)
                                )
                                Text(
                                    text = "${puzzle.shapeName} • ${tier.title} • ${boardState.remainingArrows.size} left",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onToggleSound,
                            modifier = Modifier.testTag("game_sound_button")
                        ) {
                            Icon(
                                imageVector = if (prefs.soundEnabled) {
                                    Icons.AutoMirrored.Filled.VolumeUp
                                } else {
                                    Icons.AutoMirrored.Filled.VolumeOff
                                },
                                contentDescription = "Toggle sound",
                                tint = NavyPrimary
                            )
                        }
                        IconButton(
                            onClick = { boardViewModel.restartLevel(isSignedIn) },
                            modifier = Modifier.testTag("game_restart_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Restart level",
                                tint = NavyPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Three Hearts Bar + Moves Counter
                Surface(
                    shape = RoundedCornerShape(50),
                    color = PureWhite,
                    border = androidx.compose.foundation.BorderStroke(1.dp, OutlineSubtle),
                    modifier = Modifier.testTag("lives_status_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (prefs.livesSystemEnabled) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (i in 1..3) {
                                    val alive = i <= prefs.livesRemaining
                                    Icon(
                                        imageVector = if (alive) {
                                            Icons.Filled.Favorite
                                        } else {
                                            Icons.Outlined.FavoriteBorder
                                        },
                                        contentDescription = if (alive) "Life $i active" else "Life $i lost",
                                        tint = if (alive) CoralError else TextSecondary.copy(alpha = 0.45f),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AllInclusive,
                                    contentDescription = "Unlimited lives mode",
                                    tint = ElectricBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Zen Mode",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ElectricBlue,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(16.dp)
                                .background(OutlineSubtle)
                        )

                        Text(
                            text = "Moves: ${boardState.movesUsed}",
                            style = MaterialTheme.typography.labelLarge,
                            color = NavyPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Centered Puzzle Board
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val puzzle = boardState.puzzle
                    if (boardState.isLoading || puzzle == null) {
                        CircularProgressIndicator(color = ElectricBlue)
                    } else {
                        PuzzleBoardCanvas(
                            puzzle = puzzle,
                            remainingArrows = boardState.remainingArrows,
                            flyingArrows = boardState.flyingArrows,
                            hintedArrowId = boardState.hintedArrowId,
                            blockedArrowId = boardState.blockedArrowId,
                            blockedTargetCell = boardState.blockedTargetCell,
                            blockedFeedbackNonce = boardState.blockedFeedbackNonce,
                            onArrowTapped = { arrow ->
                                boardViewModel.onArrowTapped(
                                    arrow = arrow,
                                    livesRemaining = prefs.livesRemaining,
                                    livesSystemEnabled = prefs.livesSystemEnabled,
                                    isSignedIn = isSignedIn
                                )
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        if (boardState.isLevelComplete) {
            LevelCompleteOverlay(
                levelNumber = boardState.levelNumber,
                isDailyChallenge = boardState.isDailyChallenge,
                earnedStars = boardState.earnedStars,
                movesUsed = boardState.movesUsed,
                onNextLevel = {
                    val next = (boardState.levelNumber + 1).coerceAtLeast(1)
                    onNavigateToLevel(next)
                },
                onMainMenu = onBack
            )
        } else if (boardState.isGameOver) {
            GameOverOverlay(
                levelNumber = boardState.levelNumber,
                isDailyChallenge = boardState.isDailyChallenge,
                onRetryLevel = { boardViewModel.restartLevel(isSignedIn) },
                onRestoreLives = { boardViewModel.openRestoreLivesReward() },
                onMainMenu = onBack
            )
        }

        val rewardReason = boardState.rewardModalReason
        if (rewardReason != null) {
            RewardBonusDialog(
                reason = rewardReason,
                onClaimReward = { boardViewModel.claimReward(rewardReason, isSignedIn) },
                onDismiss = { boardViewModel.dismissRewardModal() }
            )
        }
    }
}
