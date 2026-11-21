package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.DifficultyTier
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldSoft
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GoldSoft
import com.example.ui.theme.GoldStar
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlueAccent
import com.example.ui.theme.SkyBlueSoft
import com.example.ui.theme.SoftSnowBg
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.AppProgressUiState
import com.google.firebase.auth.FirebaseUser

@Composable
fun MainMenuScreen(
    uiState: AppProgressUiState,
    currentUser: FirebaseUser?,
    onContinueGame: () -> Unit,
    onPlayLevels: () -> Unit,
    onDailyChallenge: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleSound: () -> Unit,
    onToggleMusic: () -> Unit
) {
    val currentLevel = uiState.prefs.currentLevel
    val currentTier = DifficultyTier.forLevel(currentLevel)

    Scaffold(
        containerColor = SoftSnowBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top utility row: Cloud Sync status + Quick Audio toggles + Settings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onOpenSettings,
                        shape = RoundedCornerShape(50),
                        color = if (currentUser != null) EmeraldSoft else PureWhite,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (currentUser != null) EmeraldSuccess.copy(alpha = 0.4f) else OutlineSubtle
                        ),
                        modifier = Modifier.testTag("cloud_status_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (currentUser != null) {
                                    Icons.Filled.CloudDone
                                } else {
                                    Icons.Filled.CloudOff
                                },
                                contentDescription = "Cloud sync status",
                                tint = if (currentUser != null) EmeraldSuccess else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (currentUser != null) {
                                    currentUser.displayName?.split(" ")?.firstOrNull() ?: "Cloud Synced"
                                } else {
                                    "Offline • Sync"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (currentUser != null) NavyPrimary else TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onToggleSound,
                            modifier = Modifier.testTag("menu_sound_toggle")
                        ) {
                            Icon(
                                imageVector = if (uiState.prefs.soundEnabled) {
                                    Icons.AutoMirrored.Filled.VolumeUp
                                } else {
                                    Icons.AutoMirrored.Filled.VolumeOff
                                },
                                contentDescription = "Toggle sound effects",
                                tint = if (uiState.prefs.soundEnabled) NavyPrimary else TextSecondary
                            )
                        }
                        IconButton(
                            onClick = onToggleMusic,
                            modifier = Modifier.testTag("menu_music_toggle")
                        ) {
                            Icon(
                                imageVector = if (uiState.prefs.musicEnabled) {
                                    Icons.Filled.MusicNote
                                } else {
                                    Icons.Filled.MusicOff
                                },
                                contentDescription = "Toggle background music",
                                tint = if (uiState.prefs.musicEnabled) NavyPrimary else TextSecondary
                            )
                        }
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("menu_settings_icon_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Open settings",
                                tint = NavyPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Game Logo + Branding
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier
                        .size(124.dp)
                        .border(1.5.dp, OutlineSubtle, RoundedCornerShape(28.dp))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_arrow_brain_hero),
                        contentDescription = "Arrow Brain game logo",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Arrow Brain",
                    style = MaterialTheme.typography.displayLarge,
                    color = NavyPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = SkyBlueSoft,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "TAP AWAY PUZZLE",
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricBlue,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Player Progress Summary Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, OutlineSubtle, RoundedCornerShape(22.dp)),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProgressStatItem(
                            label = "Current Level",
                            value = "Lv. $currentLevel",
                            accent = ElectricBlue
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(34.dp)
                                .background(OutlineSubtle)
                        )
                        ProgressStatItem(
                            label = "Completed",
                            value = "${uiState.totalCompletedCount}",
                            accent = EmeraldSuccess
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(34.dp)
                                .background(OutlineSubtle)
                        )
                        ProgressStatItem(
                            label = "Stars",
                            value = "★ ${uiState.totalStars}",
                            accent = GoldStar
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(34.dp)
                                .background(OutlineSubtle)
                        )
                        ProgressStatItem(
                            label = "Hints",
                            value = "${uiState.prefs.hintsRemaining}",
                            accent = NavyPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Primary CTA: Continue Game
                Button(
                    onClick = onContinueGame,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .testTag("continue_game_button"),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(listOf(NavyPrimary, ElectricBlue)),
                                RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = if (uiState.totalCompletedCount == 0 && currentLevel == 1) {
                                        "Play Game"
                                    } else {
                                        "Continue Game"
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    color = PureWhite,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Level $currentLevel • ${currentTier.title}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PureWhite.copy(alpha = 0.86f)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(PureWhite.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Continue",
                                    tint = PureWhite,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Level Selection + Settings row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onPlayLevels,
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .testTag("level_selection_button"),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = PureWhite,
                            contentColor = NavyPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GridView,
                            contentDescription = null,
                            tint = ElectricBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Levels (1,000+)",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("settings_button"),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = PureWhite,
                            contentColor = NavyPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            tint = NavyPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Daily Brain Challenge Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .clickable(onClick = onDailyChallenge)
                        .border(
                            1.5.dp,
                            if (uiState.isDailyCompletedToday) EmeraldSuccess else SkyBlueAccent.copy(alpha = 0.5f),
                            RoundedCornerShape(22.dp)
                        )
                        .testTag("daily_challenge_card"),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (uiState.isDailyCompletedToday) EmeraldSoft.copy(alpha = 0.45f) else PureWhite
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .background(
                                    if (uiState.isDailyCompletedToday) EmeraldSoft else GoldSoft,
                                    RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (uiState.isDailyCompletedToday) {
                                    Icons.Filled.CheckCircle
                                } else {
                                    Icons.Filled.CalendarToday
                                },
                                contentDescription = "Daily Challenge",
                                tint = if (uiState.isDailyCompletedToday) EmeraldSuccess else GoldStar,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Daily Brain Challenge",
                                style = MaterialTheme.typography.titleMedium,
                                color = NavyPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (uiState.isDailyCompletedToday) {
                                    "Completed for ${uiState.todayDateKey} • Replay anytime"
                                } else {
                                    "${uiState.todayDateKey} • Earn +2 Hints & Bonus Stars"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            color = if (uiState.isDailyCompletedToday) EmeraldSuccess else ElectricBlue,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (uiState.isDailyCompletedToday) Icons.Filled.Star else Icons.Filled.Lightbulb,
                                    contentDescription = null,
                                    tint = PureWhite,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (uiState.isDailyCompletedToday) "Done" else "Play",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = PureWhite,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ProgressStatItem(
    label: String,
    value: String,
    accent: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = accent,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
    }
}
