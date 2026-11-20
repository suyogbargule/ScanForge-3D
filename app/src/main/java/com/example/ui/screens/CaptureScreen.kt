package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.CaptureState
import com.example.model.SensorMode
import com.example.ui.components.CaptureHudOverlay
import com.example.ui.components.VirtualSphereGLSurfaceView
import com.example.ui.theme.CaptureGreenDark
import com.example.viewmodel.SphereCaptureViewModel

@Composable
fun CaptureScreen(
    viewModel: SphereCaptureViewModel,
    onCloseToHome: () -> Unit,
    onSessionComplete: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.captureUi.collectAsStateWithLifecycle()
    val flashAlpha = remember { Animatable(0f) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    // Lock screen to portrait & keep screen awake during capture (Section 22.4)
    DisposableEffect(context) {
        val activity = context.findActivity()
        val prevOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        viewModel.startTracking()

        onDispose {
            viewModel.stopTracking()
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = prevOrientation
        }
    }

    // Shutter flash animation on capture
    LaunchedEffect(uiState.shutterFlashTrigger) {
        if (uiState.shutterFlashTrigger > 0L) {
            flashAlpha.snapTo(0.65f)
            flashAlpha.animateTo(0f, animationSpec = tween(durationMillis = 200))
        }
    }

    // Auto-navigate to Review screen when 30/30 or single retake completes
    LaunchedEffect(uiState.sessionCompletedTrigger) {
        val completedId = uiState.sessionCompletedTrigger
        if (completedId != null) {
            viewModel.consumeSessionCompletedTrigger()
            onSessionComplete(completedId)
        }
    }

    if (!hasCameraPermission) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Camera Permission Required",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Grant camera access to preview and capture the 30 spherical viewpoints.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = CaptureGreenDark),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Allow Camera")
                }
                OutlinedButton(onClick = onCloseToHome) {
                    Text("Back", color = Color.White)
                }
            }
        }
        return
    }

    val allowManualPan = !uiState.hasLiveSensor || uiState.settings.sensorMode == SensorMode.MANUAL

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("capture_screen")
            .then(
                if (allowManualPan) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            viewModel.onManualPan(
                                yawDeltaDeg = dragAmount.x * 0.25f,
                                pitchDeltaDeg = -dragAmount.y * 0.25f
                            )
                        }
                    }
                } else Modifier
            )
    ) {
        // Layer 1: CameraX PreviewView at the bottom (Section 23.2 Step 1)
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    contentDescription = ctx.getString(R.string.accessibility_camera_preview)
                    viewModel.bindCamera(lifecycleOwner, this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Layer 2: Transparent OpenGL ES 2.0 Virtual Sphere & 3D AR Tile Overlay (Section 23.2 Step 1)
        VirtualSphereGLSurfaceView(
            targets = uiState.targets,
            activeTargetId = uiState.activeTarget?.id,
            viewMatrix4x4 = uiState.viewMatrix4x4,
            cameraFov = uiState.cameraFov,
            showTiles = uiState.captureState != CaptureState.CALIBRATING,
            modifier = Modifier.fillMaxSize()
        )

        // Layer 3: Compose HUD (START/HOLD circle, arrow, white frame, top guidance, bottom 30-dot map, X button)
        CaptureHudOverlay(
            uiState = uiState,
            onStartCalibration = { viewModel.onStartCalibrationConfirm() },
            onManualShutter = { viewModel.triggerManualShutter() },
            onToggleGuidanceMode = { viewModel.toggleGuidanceMode() },
            onCloseSession = {
                viewModel.pauseAndSaveCurrentSession()
                onCloseToHome()
            }
        )

        // Shutter flash feedback
        if (flashAlpha.value > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = flashAlpha.value))
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
