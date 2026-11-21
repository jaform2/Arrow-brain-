package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.audio.AudioEngine
import com.example.auth.attemptAutoSignIn
import com.example.auth.authStateFlow
import com.example.data.repository.GameRepository
import com.example.ui.screens.GameScreen
import com.example.ui.screens.LevelSelectScreen
import com.example.ui.screens.MainMenuScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.ArrowBrainTheme
import com.example.ui.viewmodel.CloudSyncStatus
import com.example.ui.viewmodel.CloudSyncViewModel
import com.example.ui.viewmodel.GameBoardViewModel
import com.example.ui.viewmodel.MainProgressViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

private object Routes {
    const val MENU = "menu"
    const val LEVELS = "levels"
    const val SETTINGS = "settings"
    const val GAME = "game/{level}/{isDaily}"

    fun gameRoute(level: Int, isDaily: Boolean): String = "game/$level/$isDaily"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArrowBrainTheme {
                ArrowBrainAppRoot()
            }
        }
    }
}

@Composable
fun ArrowBrainAppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val repository = remember(context) { GameRepository(context.applicationContext) }
    val audioEngine = remember { AudioEngine() }

    // Lifecycle-aware background music pause/resume
    DisposableEffect(lifecycleOwner, audioEngine) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> audioEngine.onForegroundChanged(true)
                Lifecycle.Event.ON_PAUSE -> audioEngine.onForegroundChanged(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            audioEngine.onForegroundChanged(false)
        }
    }

    // Attempt silent Google Sign-In on startup (never blocks offline play)
    LaunchedEffect(Unit) {
        val cm = CredentialManager.create(context)
        attemptAutoSignIn(
            context = context,
            credentialManager = cm,
            onAuthSuccess = {},
            onUnauthenticated = {},
            scope = scope
        )
    }

    val currentUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    val progressViewModel: MainProgressViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                MainProgressViewModel(repository, audioEngine)
            }
        }
    )

    val boardViewModel: GameBoardViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                GameBoardViewModel(repository, audioEngine)
            }
        }
    )

    // Auth-gated CloudSyncViewModel: ONLY instantiated & listening when currentUser != null
    val user = currentUser
    val cloudSyncViewModel: CloudSyncViewModel? = if (user != null) {
        key(user.uid) {
            viewModel(
                key = user.uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        CloudSyncViewModel(GameRepository(app), user.uid)
                    }
                }
            )
        }
    } else {
        null
    }

    val progressState by progressViewModel.uiState.collectAsStateWithLifecycle()
    val boardState by boardViewModel.state.collectAsStateWithLifecycle()
    val cloudStatus = if (cloudSyncViewModel != null) {
        cloudSyncViewModel.syncStatus.collectAsStateWithLifecycle().value
    } else {
        CloudSyncStatus.Idle
    }

    val isSignedIn = user != null
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.MENU,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() }
    ) {
        composable(Routes.MENU) {
            LaunchedEffect(Unit) {
                progressViewModel.refreshTodayKey()
            }
            MainMenuScreen(
                uiState = progressState,
                currentUser = user,
                onContinueGame = {
                    audioEngine.playButtonClick()
                    val level = progressState.prefs.currentLevel.coerceAtLeast(1)
                    navController.navigate(Routes.gameRoute(level, isDaily = false))
                },
                onPlayLevels = {
                    audioEngine.playButtonClick()
                    navController.navigate(Routes.LEVELS)
                },
                onDailyChallenge = {
                    audioEngine.playButtonClick()
                    navController.navigate(Routes.gameRoute(level = 1, isDaily = true))
                },
                onOpenSettings = {
                    audioEngine.playButtonClick()
                    navController.navigate(Routes.SETTINGS)
                },
                onToggleSound = { progressViewModel.toggleSound(isSignedIn) },
                onToggleMusic = { progressViewModel.toggleMusic(isSignedIn) }
            )
        }

        composable(Routes.LEVELS) {
            LevelSelectScreen(
                uiState = progressState,
                onSelectLevel = { level ->
                    progressViewModel.selectLevelAsCurrent(level, isSignedIn)
                    navController.navigate(Routes.gameRoute(level, isDaily = false))
                },
                onBack = {
                    audioEngine.playButtonClick()
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                uiState = progressState,
                currentUser = user,
                cloudSyncStatus = cloudStatus,
                onToggleSound = { progressViewModel.toggleSound(isSignedIn) },
                onToggleMusic = { progressViewModel.toggleMusic(isSignedIn) },
                onToggleLivesSystem = { progressViewModel.toggleLivesSystem(isSignedIn) },
                onSyncNow = {
                    audioEngine.playButtonClick()
                    cloudSyncViewModel?.syncLocalToCloudNow()
                },
                onResetProgress = { progressViewModel.resetProgress() },
                onBack = {
                    audioEngine.playButtonClick()
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Routes.GAME,
            arguments = listOf(
                navArgument("level") { type = NavType.IntType },
                navArgument("isDaily") { type = NavType.BoolType }
            )
        ) { backStackEntry ->
            val level = backStackEntry.arguments?.getInt("level") ?: 1
            val isDaily = backStackEntry.arguments?.getBoolean("isDaily") ?: false

            GameScreen(
                levelToLoad = level,
                isDailyChallenge = isDaily,
                progressState = progressState,
                boardState = boardState,
                boardViewModel = boardViewModel,
                isSignedIn = isSignedIn,
                onBack = {
                    audioEngine.playButtonClick()
                    navController.popBackStack(Routes.MENU, inclusive = false)
                },
                onNavigateToLevel = { nextLevel ->
                    audioEngine.playButtonClick()
                    navController.navigate(Routes.gameRoute(nextLevel, isDaily = false)) {
                        popUpTo(Routes.GAME) { inclusive = true }
                    }
                },
                onToggleSound = { progressViewModel.toggleSound(isSignedIn) }
            )
        }
    }
}
