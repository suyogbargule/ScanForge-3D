package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.ui.screens.CaptureScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OnboardingTutorialScreen
import com.example.ui.screens.ReviewSessionScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.SphereCaptureViewModel

sealed class AppScreen {
    object Home : AppScreen()
    object Tutorial : AppScreen()
    object Capture : AppScreen()
    data class Review(val sessionId: String) : AppScreen()
    object Settings : AppScreen()
}

class MainActivity : ComponentActivity() {

    private val captureViewModel: SphereCaptureViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Scan3DAppNavigation(viewModel = captureViewModel)
                }
            }
        }
    }
}

@Composable
fun Scan3DAppNavigation(viewModel: SphereCaptureViewModel) {
    val backStack = remember { mutableStateListOf<AppScreen>(AppScreen.Home) }
    val currentScreen = backStack.lastOrNull() ?: AppScreen.Home

    fun navigateTo(screen: AppScreen) {
        backStack.add(screen)
    }

    fun navigateBack() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    fun popToHome() {
        while (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    BackHandler(enabled = backStack.size > 1) {
        if (currentScreen is AppScreen.Capture) {
            viewModel.pauseAndSaveCurrentSession()
        }
        navigateBack()
    }

    when (currentScreen) {
        is AppScreen.Home -> {
            HomeScreen(
                viewModel = viewModel,
                onStartNewCapture = {
                    viewModel.startNewSession()
                    navigateTo(AppScreen.Capture)
                },
                onResumeSession = { sessionId ->
                    viewModel.resumeSession(sessionId) {
                        navigateTo(AppScreen.Capture)
                    }
                },
                onReviewSession = { sessionId ->
                    navigateTo(AppScreen.Review(sessionId))
                },
                onOpenTutorial = {
                    navigateTo(AppScreen.Tutorial)
                },
                onOpenSettings = {
                    navigateTo(AppScreen.Settings)
                }
            )
        }

        is AppScreen.Tutorial -> {
            OnboardingTutorialScreen(
                onDismiss = { navigateBack() },
                onStartCapture = {
                    navigateBack()
                    viewModel.startNewSession()
                    navigateTo(AppScreen.Capture)
                }
            )
        }

        is AppScreen.Capture -> {
            CaptureScreen(
                viewModel = viewModel,
                onCloseToHome = { popToHome() },
                onSessionComplete = { completedSessionId ->
                    if (backStack.size > 1 && backStack.last() is AppScreen.Capture) {
                        backStack.removeAt(backStack.lastIndex)
                    }
                    if (backStack.lastOrNull() !is AppScreen.Review) {
                        navigateTo(AppScreen.Review(completedSessionId))
                    }
                }
            )
        }

        is AppScreen.Review -> {
            ReviewSessionScreen(
                sessionId = currentScreen.sessionId,
                viewModel = viewModel,
                onRetakeTarget = { sid, targetId ->
                    viewModel.resumeSession(sid, singleTargetId = targetId) {
                        navigateTo(AppScreen.Capture)
                    }
                },
                onSaveAndDone = { popToHome() }
            )
        }

        is AppScreen.Settings -> {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navigateBack() }
            )
        }
    }
}
