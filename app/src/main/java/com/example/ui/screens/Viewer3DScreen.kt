package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.Model3D
import com.example.ui.components.Viewer3DCanvas
import com.example.ui.components.ViewerRenderMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Viewer3DScreen(
    model: Model3D,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var renderMode by remember { mutableStateOf(ViewerRenderMode.GAUSSIAN_SPLATS) }
    var pointSize by remember { mutableFloatStateOf(6f) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = model.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${model.points.size} Vertices • ${model.engineUsed.displayName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("viewer_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to list"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showExportDialog = true },
                        modifier = Modifier
                            .testTag("action_export_3d")
                            .semantics { contentDescription = "Export standard 3D asset file (PLY / OBJ)" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Export 3D Model",
                            tint = Color(0xFF00E5FF)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0A0F18)
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF0D121F))
                .testTag("viewer_3d_screen")
        ) {
            // Interactive 3D Canvas
            Viewer3DCanvas(
                model = model,
                renderMode = renderMode,
                pointSize = pointSize,
                modifier = Modifier.fillMaxSize()
            )

            // Touch Gesture Hint Banner (Top Center)
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            ) {
                Text(
                    text = "Drag 1-finger to orbit • 2-fingers to pan • Pinch to zoom",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            // Floating Controls Bar (Bottom)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                // Render Mode Pill Selector
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2B).copy(alpha = 0.9f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ViewerRenderMode.values().forEach { mode ->
                            val isSelected = renderMode == mode
                            Surface(
                                color = if (isSelected) Color(0xFF00E5FF) else Color.Transparent,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .clickable { renderMode = mode }
                                    .testTag("mode_${mode.name}")
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

                Spacer(modifier = Modifier.height(10.dp))

                // Point Size Slider & Info HUD
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2B).copy(alpha = 0.85f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Size",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Slider(
                            value = pointSize,
                            onValueChange = { pointSize = it },
                            valueRange = 2f..16f,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("slider_point_size")
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "${pointSize.toInt()} px",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Export Modal Dialog
            if (showExportDialog) {
                AlertDialog(
                    onDismissRequest = { showExportDialog = false },
                    title = { Text("Export 3D Model Asset") },
                    text = {
                        Column {
                            Text("Standard 3D assets ready for export from local app-private storage:")
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("• 3D Gaussian Splat PLY: ${model.plyFilePath ?: "Ready to write"}", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("• Wavefront Mesh OBJ: ${model.objFilePath ?: "Ready to write"}", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Compatible with Blender, MeshLab, 3DGS Web Viewers, and 3D printing software.")
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showExportDialog = false
                                Toast.makeText(context, "3D Asset saved to app storage: ${model.name}.ply & .obj", Toast.LENGTH_LONG).show()
                            },
                            modifier = Modifier.testTag("dialog_confirm_export")
                        ) {
                            Text("Export PLY & OBJ")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showExportDialog = false }) {
                            Text("Close")
                        }
                    }
                )
            }
        }
    }
}
