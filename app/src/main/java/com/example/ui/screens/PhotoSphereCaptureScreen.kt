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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Panorama
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.components.PhotoSphereAROverlay
import com.example.viewmodel.PhotoSphereViewModel
import java.io.File

@Composable
fun PhotoSphereCaptureScreen(
    viewModel: PhotoSphereViewModel,
    onBack: () -> Unit,
    onViewPanorama: (File) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(Unit) {
        viewModel.startTracking()
        onDispose {
            viewModel.stopTracking()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("photosphere_capture_screen")
    ) {
        // CameraX Preview
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    viewModel.cameraProvider.bindCamera(
                        lifecycleOwner = lifecycleOwner,
                        previewView = this,
                        onError = {}
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // AR Floating Dots Overlay
        PhotoSphereAROverlay(
            dots = uiState.session.dots,
            deviceYawDeg = uiState.deviceYawDeg,
            devicePitchDeg = uiState.devicePitchDeg,
            deviceRollDeg = uiState.deviceRollDeg,
            isAligned = uiState.isAligned
        )

        // TOP HEADER: Heading, Progress & Close
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
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .testTag("btn_close_photosphere")
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CompassCalibration,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${uiState.deviceYawDeg.toInt()}° • ${uiState.session.capturedCount} / ${uiState.session.totalCount} Captured",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Button(
                    onClick = {
                        viewModel.stitchCurrentSession { file ->
                            onViewPanorama(file)
                        }
                    },
                    enabled = uiState.session.capturedCount > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(38.dp).testTag("btn_stitch_360")
                ) {
                    Icon(imageVector = Icons.Default.Panorama, contentDescription = null, tint = Color(0xFF0D1117), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Stitch 360",
                        color = Color(0xFF0D1117),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { uiState.session.progressPct / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Color(0xFF00E5FF),
                trackColor = Color.White.copy(alpha = 0.2f)
            )
        }

        // SIMULATION VIRTUAL ARROW CONTROLS (Right Side)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.clickable { viewModel.setManualHeading(0f, 15f) }.testTag("btn_pan_up")
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Tilt Up", tint = Color.White, modifier = Modifier.padding(6.dp).size(22.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { viewModel.setManualHeading(-25f, 0f) }.testTag("btn_pan_left")
                ) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Turn Left", tint = Color.White, modifier = Modifier.padding(6.dp).size(22.dp))
                }
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { viewModel.setManualHeading(25f, 0f) }.testTag("btn_pan_right")
                ) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Turn Right", tint = Color.White, modifier = Modifier.padding(6.dp).size(22.dp))
                }
            }
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.clickable { viewModel.setManualHeading(0f, -15f) }.testTag("btn_pan_down")
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Tilt Down", tint = Color.White, modifier = Modifier.padding(6.dp).size(22.dp))
            }
        }

        // INSTRUCTION PILL (Bottom Center)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp),
            color = Color.Black.copy(alpha = 0.75f),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                if (uiState.isAligned) Color(0xFF00E676) else Color(0xFF00E5FF)
            )
        ) {
            Text(
                text = if (uiState.isAligned) "Aligned! Hold steady to capture" else "Move camera to align circle with target dot",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // BOTTOM CONTROLS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 30.dp, start = 28.dp, end = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.toggleAutoCapture() },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (uiState.isAutoCaptureEnabled) Color(0xFF00E5FF).copy(alpha = 0.25f)
                        else Color.White.copy(alpha = 0.15f)
                    )
                    .testTag("toggle_photosphere_auto")
            ) {
                Icon(
                    imageVector = Icons.Default.AutoMode,
                    contentDescription = "Toggle auto-capture",
                    tint = if (uiState.isAutoCaptureEnabled) Color(0xFF00E5FF) else Color.White
                )
            }

            // Shutter Button
            val shutterColor = if (uiState.isAligned) Color(0xFF00E676) else Color(0xFF00E5FF)
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .border(3.dp, shutterColor, CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
                    .clickable { viewModel.captureManual() }
                    .testTag("btn_photosphere_shutter"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(shutterColor)
                )
            }

            Spacer(modifier = Modifier.size(48.dp))
        }

        // Stitching Dialog
        if (uiState.isStitching) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Stitching 360 Photo Sphere") },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(
                            progress = { uiState.stitchProgress },
                            color = Color(0xFF00E5FF),
                            strokeWidth = 4.dp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = uiState.stitchMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {}
            )
        }
    }
}
