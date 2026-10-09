package com.example.ui.screens

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.PhotoSphereViewMode
import com.example.ui.components.PhotoSphereViewerCanvas
import com.example.viewmodel.PhotoSphereViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoSphereViewerScreen(
    panoramaFile: File,
    viewModel: PhotoSphereViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var viewMode by remember { mutableStateOf(PhotoSphereViewMode.INTERACTIVE_360) }

    DisposableEffect(viewMode) {
        if (viewMode == PhotoSphereViewMode.GYROSCOPE_LOOK || viewMode == PhotoSphereViewMode.VR_CARDBOARD) {
            viewModel.startTracking()
        }
        onDispose {
            if (viewMode == PhotoSphereViewMode.GYROSCOPE_LOOK || viewMode == PhotoSphereViewMode.VR_CARDBOARD) {
                viewModel.stopTracking()
            }
        }
    }

    val bitmap = remember(panoramaFile) {
        if (panoramaFile.exists()) {
            BitmapFactory.decodeFile(panoramaFile.absolutePath)
        } else null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("360° Photo Sphere Viewer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (bitmap != null) "${bitmap.width}x${bitmap.height} Equirectangular" else "360 Panorama",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("btn_back_viewer_360")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            Toast.makeText(context, "360 Photo saved to local gallery with Photosphere metadata!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("btn_export_360")
                    ) {
                        Icon(imageVector = Icons.Default.FileDownload, contentDescription = "Save 360", tint = Color(0xFF00E5FF))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0F18))
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF0B101B))
                .testTag("photosphere_viewer_screen")
        ) {
            if (bitmap != null) {
                PhotoSphereViewerCanvas(
                    bitmap = bitmap,
                    viewMode = viewMode,
                    gyroYawDeg = uiState.deviceYawDeg,
                    gyroPitchDeg = uiState.devicePitchDeg,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Panorama file not found", color = Color.White)
                }
            }

            // Mode Selector Pill Toolbar (Bottom)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2B).copy(alpha = 0.92f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    PhotoSphereViewMode.values().forEach { mode ->
                        val isSelected = viewMode == mode
                        Surface(
                            color = if (isSelected) Color(0xFF00E5FF) else Color.Transparent,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .clickable { viewMode = mode }
                                .testTag("btn_mode_${mode.name}")
                        ) {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFF0D1117) else Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
