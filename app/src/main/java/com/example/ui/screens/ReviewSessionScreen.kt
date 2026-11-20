package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.model.CaptureTarget
import com.example.model.SphereLayer
import com.example.model.TargetStatus
import com.example.ui.theme.CaptureGreen
import com.example.ui.theme.CaptureGreenDark
import com.example.ui.theme.RetakeRed
import com.example.ui.theme.WarningAmber
import com.example.viewmodel.SphereCaptureViewModel
import java.io.File

/**
 * Section 16 Screen 5 & Section 22.1 / 22.2:
 * Complete / Review Screen for Release 1 (Capture and store only — NO "Stitch 360" button):
 * - 30-thumbnail grid ordered by layer (1 Zenith, 8 Upper, 12 Centre, 8 Lower, 1 Nadir)
 * - Sphere coverage check report (4000-point test) & file integrity status
 * - 1-tap Retake on any thumbnail or flagged position
 * - Export session as ZIP (img_01..img_30.jpg + session.json)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewSessionScreen(
    sessionId: String,
    viewModel: SphereCaptureViewModel,
    onRetakeTarget: (sessionId: String, targetId: Int) -> Unit,
    onSaveAndDone: () -> Unit
) {
    val context = LocalContext.current
    val reviewState by viewModel.reviewUi.collectAsStateWithLifecycle()
    var inspectedTarget by remember { mutableStateOf<CaptureTarget?>(null) }

    LaunchedEffect(sessionId) {
        viewModel.loadReviewSession(sessionId)
    }

    val targets = reviewState.targets
    val report = reviewState.coverageReport
    val flaggedOrMissing = remember(targets, report) {
        targets.filter {
            it.status != TargetStatus.COMPLETED ||
                it.exposureWarning ||
                it.textureWarning ||
                it.id in report.gapTargetIds
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Review 30-Shot Capture",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${report.validFileCount} / 30 captured • ${reviewState.title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onSaveAndDone) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Home"
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (reviewState.exportMessage != null) {
                        Text(
                            text = reviewState.exportMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = CaptureGreenDark,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (flaggedOrMissing.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    val first = flaggedOrMissing.first()
                                    onRetakeTarget(sessionId, first.id)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("btn_retake_flagged")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retake (${flaggedOrMissing.size})")
                            }
                        }

                        OutlinedButton(
                            onClick = { viewModel.exportSessionZip(context, sessionId) },
                            enabled = !reviewState.isExporting && report.validFileCount > 0,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_export_zip")
                        ) {
                            if (reviewState.isExporting) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.export_zip))
                            }
                        }

                        Button(
                            onClick = onSaveAndDone,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CaptureGreenDark),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_save_session")
                        ) {
                            Text(stringResource(R.string.save_session), color = Color.White)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .testTag("review_session_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Coverage & Integrity Summary Card
            item(span = { GridItemSpan(4) }) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val coverageOk = report.coveragePercent >= 99.0f && report.integrityOk
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (coverageOk) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (coverageOk) CaptureGreenDark else WarningAmber
                            )
                            Text(
                                text = "Sphere Coverage: ${"%.1f".format(report.coveragePercent)}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Files: ${report.validFileCount}/30 valid JPEGs + session.json • Min neighbor overlap ~${(report.minNeighborOverlap * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (report.gapTargetIds.isNotEmpty() || report.missingIds.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val idsLabel = (report.missingIds + report.gapTargetIds).distinct().joinToString(", ") { "#$it" }
                            Text(
                                text = "Attention needed at positions: $idsLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = WarningAmber,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Group targets by the 5 canonical spherical layers
            SphereLayer.entries.forEach { layer ->
                val layerTargets = targets.filter { it.layer == layer }.sortedBy { it.id }
                if (layerTargets.isNotEmpty()) {
                    item(span = { GridItemSpan(4) }) {
                        val done = layerTargets.count { it.status == TargetStatus.COMPLETED }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${layer.displayName} (${layer.pitchDeg.toInt()}°)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$done / ${layerTargets.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (done == layerTargets.size) CaptureGreenDark else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(layerTargets, key = { it.id }) { target ->
                        val isGap = target.id in report.gapTargetIds
                        TargetThumbnailTile(
                            target = target,
                            isGap = isGap,
                            onClick = { inspectedTarget = target }
                        )
                    }
                }
            }
        }
    }

    if (inspectedTarget != null) {
        val t = inspectedTarget!!
        AlertDialog(
            onDismissRequest = { inspectedTarget = null },
            title = { Text("Position #${t.id} • ${t.layer.displayName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val imgFile = t.imagePath?.let { File(it) }
                    if (imgFile != null && imgFile.exists()) {
                        AsyncImage(
                            model = imgFile,
                            contentDescription = "Captured position ${t.id}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(3f / 4f)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                    Text(
                        text = "File: ${t.fileName} (${t.imageWidth}×${t.imageHeight})",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = "Target: yaw ${t.yawDeg.toInt()}°, pitch ${t.pitchDeg.toInt()}° • Angle err ${"%.1f".format(t.angleErrorDeg)}°",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Blur score: ${t.blurScore.toInt()} • Exposure warn: ${t.exposureWarning} • Texture warn: ${t.textureWarning}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (t.qualityNote != null) {
                        Text(
                            text = t.qualityNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = WarningAmber,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        inspectedTarget = null
                        onRetakeTarget(sessionId, t.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CaptureGreenDark)
                ) {
                    Text("Retake Position", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { inspectedTarget = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun TargetThumbnailTile(
    target: CaptureTarget,
    isGap: Boolean,
    onClick: () -> Unit
) {
    val borderColor = when {
        target.status == TargetStatus.NEEDS_RETAKE || target.status == TargetStatus.REMAINING -> RetakeRed
        target.exposureWarning || target.textureWarning || isGap -> WarningAmber
        else -> CaptureGreen
    }

    Box(
        modifier = Modifier
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(2.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("thumb_tile_${target.id}")
    ) {
        val file = target.thumbPath?.let { File(it) } ?: target.imagePath?.let { File(it) }
        if (file != null && file.exists()) {
            AsyncImage(
                model = file,
                contentDescription = "Target ${target.id}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Missing",
                    style = MaterialTheme.typography.labelSmall,
                    color = RetakeRed
                )
            }
        }

        // Top-left ID badge
        Surface(
            color = Color.Black.copy(alpha = 0.72f),
            shape = RoundedCornerShape(bottomEnd = 8.dp),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Text(
                text = "#${target.id}",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }

        // Warning / retake indicator dot
        if (borderColor != CaptureGreen) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(borderColor)
            )
        }
    }
}
