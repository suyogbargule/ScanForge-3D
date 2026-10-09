package com.example

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.model.Model3D
import com.example.model.ScanSession
import com.example.ui.screens.CaptureScreen
import com.example.ui.screens.CoverageReviewScreen
import com.example.ui.screens.DeviceCheckScreen
import com.example.ui.screens.DocumentAnalysisScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ObjectSetupScreen
import com.example.ui.screens.PhotoSphereCaptureScreen
import com.example.ui.screens.PhotoSphereViewerScreen
import com.example.ui.screens.ProcessingScreen
import com.example.ui.screens.ScanHistoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.Viewer3DScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.PhotoSphereViewModel
import com.example.viewmodel.ScanViewModel
import java.io.File

sealed class AppScreen {
    object Home : AppScreen()
    object DeviceCheck : AppScreen()
    object ObjectSetup : AppScreen()
    object Capture : AppScreen()
    object CoverageReview : AppScreen()
    object Processing : AppScreen()
    data class Viewer3D(val model: Model3D) : AppScreen()
    object DocAnalysis : AppScreen()
    object ScanHistory : AppScreen()
    object Settings : AppScreen()
    object PhotoSphereCapture : AppScreen()
    data class PhotoSphereViewer(val panoramaFile: File) : AppScreen()
}

class MainActivity : ComponentActivity() {

    private val scanViewModel: ScanViewModel by viewModels()
    private val photoSphereViewModel: PhotoSphereViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainAppNavigation(
                        scanViewModel = scanViewModel,
                        photoSphereViewModel = photoSphereViewModel
                    )
                }
            }
        }
    }
}

@Composable
fun MainAppNavigation(
    scanViewModel: ScanViewModel,
    photoSphereViewModel: PhotoSphereViewModel
) {
    val navigationStack = remember { mutableStateListOf<AppScreen>(AppScreen.Home) }
    val currentScreen = navigationStack.lastOrNull() ?: AppScreen.Home

    var hasCameraPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    fun navigateTo(screen: AppScreen) {
        navigationStack.add(screen)
    }

    fun navigateBack() {
        if (navigationStack.size > 1) {
            navigationStack.removeAt(navigationStack.lastIndex)
        }
    }

    fun popToHome() {
        while (navigationStack.size > 1) {
            navigationStack.removeAt(navigationStack.lastIndex)
        }
    }

    BackHandler(enabled = navigationStack.size > 1) {
        navigateBack()
    }

    when (val screen = currentScreen) {
        is AppScreen.Home -> {
            HomeScreen(
                viewModel = scanViewModel,
                onNavigateToNewScan = { navigateTo(AppScreen.ObjectSetup) },
                onNavigateToPhotoSphere = {
                    photoSphereViewModel.resetSession()
                    navigateTo(AppScreen.PhotoSphereCapture)
                },
                onNavigateToHistory = { navigateTo(AppScreen.ScanHistory) },
                onNavigateToDeviceCheck = { navigateTo(AppScreen.DeviceCheck) },
                onNavigateToDocAnalysis = { navigateTo(AppScreen.DocAnalysis) },
                onNavigateToSettings = { navigateTo(AppScreen.Settings) },
                onOpenViewer = { session ->
                    scanViewModel.loadSessionForViewing(session) { model ->
                        navigateTo(AppScreen.Viewer3D(model))
                    }
                }
            )
        }

        is AppScreen.PhotoSphereCapture -> {
            PhotoSphereCaptureScreen(
                viewModel = photoSphereViewModel,
                onBack = { navigateBack() },
                onViewPanorama = { file ->
                    navigationStack.removeAt(navigationStack.lastIndex)
                    navigateTo(AppScreen.PhotoSphereViewer(file))
                }
            )
        }

        is AppScreen.PhotoSphereViewer -> {
            PhotoSphereViewerScreen(
                panoramaFile = screen.panoramaFile,
                viewModel = photoSphereViewModel,
                onBack = { popToHome() }
            )
        }

        is AppScreen.DeviceCheck -> {
            DeviceCheckScreen(
                viewModel = scanViewModel,
                onBack = { navigateBack() },
                onProceedToScan = {
                    navigateBack()
                    navigateTo(AppScreen.ObjectSetup)
                }
            )
        }

        is AppScreen.ObjectSetup -> {
            ObjectSetupScreen(
                viewModel = scanViewModel,
                onBack = { navigateBack() },
                onStartScan = {
                    navigateTo(AppScreen.Capture)
                }
            )
        }

        is AppScreen.Capture -> {
            CaptureScreen(
                viewModel = scanViewModel,
                onFinishScan = { navigateTo(AppScreen.CoverageReview) },
                onCancelScan = { navigateBack() }
            )
        }

        is AppScreen.CoverageReview -> {
            CoverageReviewScreen(
                viewModel = scanViewModel,
                onBackToCapture = { navigateBack() },
                onStartReconstruction = { navigateTo(AppScreen.Processing) }
            )
        }

        is AppScreen.Processing -> {
            ProcessingScreen(
                viewModel = scanViewModel,
                onView3DResult = { model ->
                    navigationStack.removeAt(navigationStack.lastIndex)
                    navigateTo(AppScreen.Viewer3D(model))
                }
            )
        }

        is AppScreen.Viewer3D -> {
            Viewer3DScreen(
                model = screen.model,
                onBack = { popToHome() }
            )
        }

        is AppScreen.DocAnalysis -> {
            DocumentAnalysisScreen(
                onBack = { navigateBack() }
            )
        }

        is AppScreen.ScanHistory -> {
            ScanHistoryScreen(
                viewModel = scanViewModel,
                onBack = { navigateBack() },
                onOpenViewer = { session ->
                    scanViewModel.loadSessionForViewing(session) { model ->
                        navigateTo(AppScreen.Viewer3D(model))
                    }
                }
            )
        }

        is AppScreen.Settings -> {
            SettingsScreen(
                onBack = { navigateBack() }
            )
        }
    }
}
