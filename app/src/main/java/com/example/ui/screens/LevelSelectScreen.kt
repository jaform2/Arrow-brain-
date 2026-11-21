package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.DifficultyTier
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldSoft
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GoldStar
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlueSoft
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SoftSnowBg
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.AppProgressUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelSelectScreen(
    uiState: AppProgressUiState,
    onSelectLevel: (Int) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val highestUnlocked = uiState.prefs.highestUnlockedLevel
    val currentLevel = uiState.prefs.currentLevel

    val availableTiers = remember(highestUnlocked) {
        if (highestUnlocked > 1000) {
            DifficultyTier.standardTiers + DifficultyTier.ENDLESS
        } else {
            DifficultyTier.standardTiers
        }
    }

    var selectedTier by remember {
        mutableStateOf(DifficultyTier.forLevel(currentLevel))
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val levelsInTier = remember(selectedTier, highestUnlocked) {
        val end = if (selectedTier == DifficultyTier.ENDLESS) {
            maxOf(1050, highestUnlocked + 20)
        } else {
            selectedTier.endLevel
        }
        (selectedTier.startLevel..end).toList()
    }

    Scaffold(
        containerColor = SoftSnowBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PureWhite),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("level_select_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = NavyPrimary
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Level Selection",
                            style = MaterialTheme.typography.titleLarge,
                            color = NavyPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${uiState.totalCompletedCount} Completed • Highest: Lv. $highestUnlocked",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            selectedTier = DifficultyTier.forLevel(highestUnlocked)
                            onSelectLevel(highestUnlocked)
                        },
                        modifier = Modifier.testTag("jump_current_level_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MyLocation,
                            contentDescription = null,
                            tint = ElectricBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Lv. $highestUnlocked")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Tier filter chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(availableTiers) { tier ->
                    val isSelected = tier == selectedTier
                    val rangeText = when {
                        tier == DifficultyTier.ENDLESS -> "1001+"
                        tier.startLevel == tier.endLevel -> "${tier.startLevel}"
                        else -> "${tier.startLevel}–${tier.endLevel}"
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedTier = tier },
                        label = {
                            Text("${tier.title} ($rangeText)")
                        },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(tier.accentHex), CircleShape)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SkyBlueSoft,
                            selectedLabelColor = NavyPrimary
                        ),
                        modifier = Modifier.testTag("tier_chip_${tier.name.lowercase()}")
                    )
                }
            }

            // Numbered level grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 76.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
            ) {
                items(levelsInTier, key = { it }) { level ->
                    val completedInfo = uiState.completedLevels[level]
                    val isCompleted = completedInfo != null
                    val isUnlocked = level <= highestUnlocked
                    val isCurrent = level == highestUnlocked

                    LevelTile(
                        levelNumber = level,
                        isCompleted = isCompleted,
                        isCurrent = isCurrent,
                        isUnlocked = isUnlocked,
                        stars = completedInfo?.stars ?: 0,
                        onClick = {
                            if (isUnlocked) {
                                onSelectLevel(level)
                            } else {
                                scope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    snackbarHostState.showSnackbar(
                                        "Complete Level $highestUnlocked to unlock Level $level!"
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LevelTile(
    levelNumber: Int,
    isCompleted: Boolean,
    isCurrent: Boolean,
    isUnlocked: Boolean,
    stars: Int,
    onClick: () -> Unit
) {
    val bgColor = when {
        isCurrent -> SkyBlueSoft
        isCompleted -> PureWhite
        isUnlocked -> PureWhite
        else -> SlateSurface
    }
    val borderColor = when {
        isCurrent -> ElectricBlue
        isCompleted -> EmeraldSuccess.copy(alpha = 0.65f)
        isUnlocked -> OutlineSubtle
        else -> OutlineSubtle.copy(alpha = 0.6f)
    }

    Surface(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = if (isCurrent) 2.5.dp else 1.2.dp,
                color = borderColor,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .testTag("level_tile_$levelNumber"),
        color = bgColor,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = if (isUnlocked) 2.dp else 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
        ) {
            if (isCompleted) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.TopEnd)
                        .background(EmeraldSoft, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Completed",
                        tint = EmeraldSuccess,
                        modifier = Modifier.size(13.dp)
                    )
                }
            } else if (!isUnlocked) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "Locked",
                    tint = TextSecondary.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.TopEnd)
                )
            }

            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = levelNumber.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = when {
                        isCurrent -> ElectricBlue
                        isUnlocked -> NavyPrimary
                        else -> TextSecondary.copy(alpha = 0.6f)
                    },
                    fontWeight = FontWeight.Bold
                )

                if (isCompleted) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        for (i in 1..3) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = if (i <= stars) GoldStar else OutlineSubtle,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                } else if (isCurrent) {
                    Text(
                        text = "NOW",
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricBlue,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
