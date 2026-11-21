package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.auth.GoogleSignInButton
import com.example.auth.signOutFromCloud
import com.example.ui.theme.CoralError
import com.example.ui.theme.CoralSoft
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldSoft
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBlueSoft
import com.example.ui.theme.SoftSnowBg
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.AppProgressUiState
import com.example.ui.viewmodel.CloudSyncStatus
import com.google.firebase.auth.FirebaseUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: AppProgressUiState,
    currentUser: FirebaseUser?,
    cloudSyncStatus: CloudSyncStatus,
    onToggleSound: () -> Unit,
    onToggleMusic: () -> Unit,
    onToggleLivesSystem: () -> Unit,
    onSyncNow: () -> Unit,
    onResetProgress: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var authErrorMessage by remember { mutableStateOf<String?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = SoftSnowBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PureWhite),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = NavyPrimary
                        )
                    }
                },
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        color = NavyPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Audio & Gameplay Preferences
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    modifier = Modifier.border(1.dp, OutlineSubtle, RoundedCornerShape(22.dp))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Gameplay & Audio",
                            style = MaterialTheme.typography.titleMedium,
                            color = NavyPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        SettingsSwitchRow(
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            iconTint = ElectricBlue,
                            title = "Sound Effects",
                            subtitle = "Arrow taps, removals, and celebration chimes",
                            checked = uiState.prefs.soundEnabled,
                            onCheckedChange = { onToggleSound() },
                            testTag = "settings_sound_switch"
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = OutlineSubtle
                        )
                        SettingsSwitchRow(
                            icon = Icons.Filled.MusicNote,
                            iconTint = ElectricBlue,
                            title = "Background Music",
                            subtitle = "Gentle ambient focus soundtrack",
                            checked = uiState.prefs.musicEnabled,
                            onCheckedChange = { onToggleMusic() },
                            testTag = "settings_music_switch"
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = OutlineSubtle
                        )
                        SettingsSwitchRow(
                            icon = Icons.Filled.Favorite,
                            iconTint = CoralError,
                            title = "3-Lives Challenge",
                            subtitle = "Lose 1 heart on wrong taps. Turn off for Zen mode",
                            checked = uiState.prefs.livesSystemEnabled,
                            onCheckedChange = { onToggleLivesSystem() },
                            testTag = "settings_lives_switch"
                        )
                    }
                }

                // Cloud Sync Card (Google Sign-In via Credential Manager + Firestore)
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    modifier = Modifier.border(1.dp, OutlineSubtle, RoundedCornerShape(22.dp))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (currentUser != null) {
                                    Icons.Filled.CloudDone
                                } else {
                                    Icons.Filled.CloudSync
                                },
                                contentDescription = null,
                                tint = if (currentUser != null) EmeraldSuccess else ElectricBlue
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Cloud Backup & Sync",
                                style = MaterialTheme.typography.titleMedium,
                                color = NavyPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        if (currentUser == null) {
                            Text(
                                text = "Your progress is saved locally offline. Sign in with Google to back up your unlocked levels, stars, and hints to the cloud across devices.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            if (authErrorMessage != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = CoralSoft,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = authErrorMessage!!,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = CoralError,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            GoogleSignInButton(
                                onAuthSuccess = { authErrorMessage = null },
                                onAuthError = { msg -> authErrorMessage = msg },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            )
                        } else {
                            Surface(
                                color = EmeraldSoft.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = currentUser.displayName ?: "Google Account",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = NavyPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (!currentUser.email.isNullOrBlank()) {
                                        Text(
                                            text = currentUser.email!!,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextSecondary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val statusText = when (cloudSyncStatus) {
                                        CloudSyncStatus.Idle -> "Connected"
                                        CloudSyncStatus.Syncing -> "Syncing progress..."
                                        is CloudSyncStatus.Synced -> cloudSyncStatus.message
                                        is CloudSyncStatus.Error -> "Sync notice: ${cloudSyncStatus.message}"
                                    }
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (cloudSyncStatus is CloudSyncStatus.Error) {
                                            CoralError
                                        } else {
                                            EmeraldSuccess
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = onSyncNow,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("sync_now_button"),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                                ) {
                                    Text("Sync Now")
                                }

                                OutlinedButton(
                                    onClick = {
                                        signOutFromCloud(
                                            credentialManager = credentialManager,
                                            scope = scope
                                        )
                                    },
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("sign_out_button"),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("Sign Out")
                                }
                            }
                        }
                    }
                }

                // Local Data Management
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    modifier = Modifier.border(1.dp, OutlineSubtle, RoundedCornerShape(22.dp))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Progress Summary",
                            style = MaterialTheme.typography.titleMedium,
                            color = NavyPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Highest Unlocked: Level ${uiState.prefs.highestUnlockedLevel} • Completed: ${uiState.totalCompletedCount} • Stars: ${uiState.totalStars}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = { showResetConfirm = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("reset_progress_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralError)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reset Local Progress")
                        }
                    }
                }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset Local Progress?") },
            text = {
                Text("This will reset your local unlocked levels, stars, and saved boards back to Level 1.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirm = false
                        onResetProgress()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralError)
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = NavyPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = PureWhite,
                checkedTrackColor = ElectricBlue,
                uncheckedTrackColor = SkyBlueSoft
            )
        )
    }
}
