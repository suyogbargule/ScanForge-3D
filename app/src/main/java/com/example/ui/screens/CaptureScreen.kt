package com.example.ui.screens

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.model.QualityColorState
import com.example.ui.components.CenterTargetReticle
import com.example.ui.components.SphericalRadarView
import com.example.viewmodel.ScanViewModel
import java.io.File

@Composable
fun CaptureScreen(
    viewModel: ScanViewModel,
    onFinishScan: () -> Unit,
    onCancelScan: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val qualityColor = when (uiState.quality.colorState) {
        QualityColorState.GREEN -> Color(0xFF00E676)
        QualityColorState.YELLOW -> Color(0xFFFFD600)
        QualityColorState.RED -> Color(0xFFFF5252)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("capture_screen")
    ) {
        // CameraX Preview or Simulated Feed
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    viewModel.cameraProvider.bindCamera(
                        lifecycleOwner = lifecycleOwner,
                        previewView = this,
                        onError = { /* falls back gracefully to simulation */ }
                    )
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .testTag("camera_preview_view")
                .semantics { contentDescription = "Live camera preview viewfinder for 3D capture" }
        )

        // Semi-transparent overlay gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.7f),
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        // Center Reticle
        CenterTargetReticle(
            targetCloseness = uiState.quality.targetPoseCloseness,
            colorState = uiState.quality.colorState,
            modifier = Modifier.align(Alignment.Center)
        )

        // TOP HUD BAR: Layer, Progress, Close
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 40.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCancelScan,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .testTag("button_cancel_scan")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel scan",
                        tint = Color.White
                    )
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = uiState.activeLayerName,
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Button(
                    onClick = onFinishScan,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("button_finish_scan")
                ) {
                    Text(
                        text = "Review (${uiState.capturedFrames.size})",
                        color = Color(0xFF0D1117),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = { uiState.coveragePercentage / 100f },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFF00E5FF),
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "${uiState.coveragePercentage.toInt()}%",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // FLOATING SPHERICAL RADAR (Top Right)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 110.dp, end = 16.dp)
        ) {
            SphericalRadarView(
                cells = uiState.cells,
                currentPose = uiState.currentPose,
                nextTarget = uiState.nextTarget
            )
        }

        // SIMULATION VIRTUAL ORBIT CONTROLS (For Testing / Keyboard / Emulators)
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { viewModel.updateManualPose(azimuthDelta = 25f, elevationDelta = 0f) }
                        .testTag("btn_orbit_right")
                ) {
                    Icon(
                        imageVector = Icons.Default.RotateRight,
                        contentDescription = "Orbit Right",
                        tint = Color.White,
                        modifier = Modifier.padding(8.dp).size(22.dp)
                    )
                }
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { viewModel.updateManualPose(azimuthDelta = -25f, elevationDelta = 0f) }
                        .testTag("btn_orbit_left")
                ) {
                    Icon(
                        imageVector = Icons.Default.RotateLeft,
                        contentDescription = "Orbit Left",
                        tint = Color.White,
                        modifier = Modifier.padding(8.dp).size(22.dp)
                    )
                }
            }
        }

        // QUALITY STATE HUD PILL (Above bottom controls)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 210.dp)
                .testTag("quality_hud_pill"),
            color = Color.Black.copy(alpha = 0.75f),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, qualityColor)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(qualityColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = uiState.statusMessage,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // BOTTOM CONTROLS & THUMBNAIL STRIP
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            // Thumbnail Strip of Captured Frames
            if (uiState.capturedFrames.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("thumbnail_strip"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.capturedFrames.reversed()) { frame ->
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.5.dp, Color(0xFF00E676), RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E293B)),
                            contentAlignment = Alignment.Center
                        ) {
                            val file = remember(frame.localPath) { File(frame.localPath) }
                            if (file.exists()) {
                                AsyncImage(
                                    model = file,
                                    contentDescription = "Captured view ${frame.id}",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF00E676),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Shutter Controls Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Auto-Capture Toggle
                IconButton(
                    onClick = { viewModel.toggleAutoCapture() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (uiState.isAutoCaptureEnabled) Color(0xFF00E5FF).copy(alpha = 0.25f)
                            else Color.White.copy(alpha = 0.15f)
                        )
                        .testTag("toggle_auto_capture")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoMode,
                        contentDescription = "Toggle auto-capture mode",
                        tint = if (uiState.isAutoCaptureEnabled) Color(0xFF00E5FF) else Color.White
                    )
                }

                // Primary Shutter Button (Manual Override & Visual Trigger)
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .border(3.dp, qualityColor, CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable { viewModel.captureCurrentFrame(isManual = true) }
                        .testTag("shutter_button")
                        .semantics { contentDescription = "Capture current photographic viewpoint" },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(qualityColor)
                    )
                }

                // Pause / Resume Toggle
                IconButton(
                    onClick = { viewModel.togglePause() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                        .testTag("toggle_pause_scan")
                ) {
                    Icon(
                        imageVector = if (uiState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (uiState.isPaused) "Resume scanning" else "Pause scanning",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
