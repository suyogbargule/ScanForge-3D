package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.Model3D
import com.example.viewmodel.ScanViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessingScreen(
    viewModel: ScanViewModel,
    onView3DResult: (Model3D) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // Start reconstruction when entering screen if not already completed
    LaunchedEffect(Unit) {
        if (!uiState.isProcessing && uiState.reconstructedModel == null) {
            viewModel.runReconstruction { model ->
                onView3DResult(model)
            }
        }
    }

    val isDone = !uiState.isProcessing && uiState.reconstructedModel != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Local 3D Reconstruction", fontWeight = FontWeight.Bold) }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .testTag("processing_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main Progress Animation Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131D2E))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.size(90.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!isDone) {
                                CircularProgressIndicator(
                                    progress = { uiState.processingProgress },
                                    modifier = Modifier.fillMaxSize(),
                                    strokeWidth = 6.dp,
                                    color = Color(0xFF00E5FF),
                                    trackColor = Color.White.copy(alpha = 0.15f)
                                )
                                Icon(
                                    imageVector = Icons.Default.HourglassTop,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(36.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00E676).copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF00E676),
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = if (isDone) "Reconstruction Complete!" else "Processing Local 3D Scene",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = uiState.processingStageText.ifBlank { "Initializing on-device pipeline…" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        LinearProgressIndicator(
                            progress = { uiState.processingProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (isDone) Color(0xFF00E676) else Color(0xFF00E5FF),
                            trackColor = Color.White.copy(alpha = 0.15f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "${(uiState.processingProgress * 100).toInt()}% Completed",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Pipeline Stages Checklist
            item {
                Text(
                    text = "Modular Pipeline Execution (SRS §9)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                PipelineStageItem(
                    title = "1. Mobile Keypoint Extraction",
                    description = "XFeat / ORB sub-pixel corner detection",
                    progressThreshold = 0.20f,
                    currentProgress = uiState.processingProgress
                )
            }

            item {
                PipelineStageItem(
                    title = "2. Epipolar Ray Matching",
                    description = "Multi-view feature correspondences & triangulation",
                    progressThreshold = 0.40f,
                    currentProgress = uiState.processingProgress
                )
            }

            item {
                PipelineStageItem(
                    title = "3. Structure from Motion (SfM)",
                    description = "Camera pose estimation & bundle adjustment",
                    progressThreshold = 0.60f,
                    currentProgress = uiState.processingProgress
                )
            }

            item {
                PipelineStageItem(
                    title = "4. Dense Point Cloud & 3DGS Synthesis",
                    description = "Radiance field gaussian primitives & geometry",
                    progressThreshold = 0.80f,
                    currentProgress = uiState.processingProgress
                )
            }

            item {
                PipelineStageItem(
                    title = "5. Optimization & Asset Export",
                    description = "Gaussian covariance pruning & PLY/OBJ generation",
                    progressThreshold = 0.95f,
                    currentProgress = uiState.processingProgress
                )
            }

            // View 3D Result Button
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        uiState.reconstructedModel?.let { onView3DResult(it) }
                    },
                    enabled = isDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("button_open_3d_viewer"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E5FF),
                        contentColor = Color(0xFF0D1117)
                    )
                ) {
                    Icon(imageVector = Icons.Default.ViewInAr, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Open Interactive 3D Viewer",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun PipelineStageItem(
    title: String,
    description: String,
    progressThreshold: Float,
    currentProgress: Float
) {
    val isFinished = currentProgress >= progressThreshold
    val isActive = currentProgress in (progressThreshold - 0.25f)..progressThreshold

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) Color(0xFF00E5FF).copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (isFinished) Color(0xFF00E676).copy(alpha = 0.2f)
                        else if (isActive) Color(0xFF00E5FF).copy(alpha = 0.2f)
                        else Color.White.copy(alpha = 0.08f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isFinished) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(18.dp)
                    )
                } else if (isActive) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF00E5FF)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.3f))
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isActive) Color(0xFF00E5FF) else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
