package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.CellStatus
import com.example.model.CoverageCell
import com.example.model.Pose
import com.example.model.QualityColorState
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SphericalRadarView(
    cells: List<CoverageCell>,
    currentPose: Pose,
    nextTarget: CoverageCell?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(110.dp)
            .testTag("spherical_radar_view"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.width * 0.44f

            // Background radar disc
            drawCircle(
                color = Color(0x99101622),
                radius = radius,
                center = center
            )
            // Equator and outer rings
            drawCircle(
                color = Color(0x4400E5FF),
                radius = radius,
                center = center,
                style = Stroke(width = 1.5f)
            )
            drawCircle(
                color = Color(0x22FFFFFF),
                radius = radius * 0.65f,
                center = center,
                style = Stroke(width = 1f)
            )
            drawCircle(
                color = Color(0x22FFFFFF),
                radius = radius * 0.35f,
                center = center,
                style = Stroke(width = 1f)
            )

            // Draw coverage cells
            for (cell in cells) {
                val azRad = Math.toRadians(cell.azimuthDeg.toDouble()).toFloat()
                // Radius mapping: elevation +90 -> center, 0 -> middle, -90 -> outer edge
                val normRadius = ((90f - cell.elevationDeg) / 180f) * radius
                val cx = center.x + normRadius * sin(azRad)
                val cy = center.y - normRadius * cos(azRad)

                val color = when {
                    cell.isCaptured -> Color(0xFF00E676) // Completed green
                    cell.id == nextTarget?.id -> Color(0xFF00E5FF) // Next target cyan
                    else -> Color(0x66FFFFFF) // Pending
                }
                val dotRadius = if (cell.id == nextTarget?.id) 5.5f else 3.5f
                drawCircle(color = color, radius = dotRadius, center = Offset(cx, cy))
            }

            // Draw current camera pointer
            val curAzRad = Math.toRadians(currentPose.azimuthDeg.toDouble()).toFloat()
            val curNormRadius = ((90f - currentPose.elevationDeg) / 180f) * radius
            val camX = center.x + curNormRadius * sin(curAzRad)
            val camY = center.y - curNormRadius * cos(curAzRad)

            // Camera beacon
            drawCircle(
                color = Color(0xFFFFD600),
                radius = 5.5f,
                center = Offset(camX, camY)
            )
            drawCircle(
                color = Color(0xFFFFD600).copy(alpha = 0.4f),
                radius = 10f,
                center = Offset(camX, camY),
                style = Stroke(width = 1.5f)
            )
        }
    }
}

@Composable
fun CenterTargetReticle(
    targetCloseness: Float,
    colorState: QualityColorState,
    modifier: Modifier = Modifier
) {
    val strokeColor = when (colorState) {
        QualityColorState.GREEN -> Color(0xFF00E676)
        QualityColorState.YELLOW -> Color(0xFFFFD600)
        QualityColorState.RED -> Color(0xFFFF5252)
    }

    Box(
        modifier = modifier
            .size(140.dp)
            .testTag("center_target_reticle"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = 55f

            // Inner target circle
            drawCircle(
                color = strokeColor.copy(alpha = 0.8f),
                radius = baseRadius,
                center = center,
                style = Stroke(width = 2.5f)
            )

            // Dynamic alignment ring (contracts to 0 as target closeness -> 1)
            val outerRadius = baseRadius + (1f - targetCloseness) * 45f
            drawCircle(
                color = strokeColor.copy(alpha = 0.5f),
                radius = outerRadius,
                center = center,
                style = Stroke(width = 2f)
            )

            // 4 Corner brackets for spatial tracking
            val bracketLen = 22f
            val bOffset = baseRadius + 15f

            // Top-left
            drawLine(strokeColor, Offset(center.x - bOffset, center.y - bOffset + bracketLen), Offset(center.x - bOffset, center.y - bOffset), 3f)
            drawLine(strokeColor, Offset(center.x - bOffset, center.y - bOffset), Offset(center.x - bOffset + bracketLen, center.y - bOffset), 3f)

            // Top-right
            drawLine(strokeColor, Offset(center.x + bOffset, center.y - bOffset + bracketLen), Offset(center.x + bOffset, center.y - bOffset), 3f)
            drawLine(strokeColor, Offset(center.x + bOffset, center.y - bOffset), Offset(center.x + bOffset - bracketLen, center.y - bOffset), 3f)

            // Bottom-left
            drawLine(strokeColor, Offset(center.x - bOffset, center.y + bOffset - bracketLen), Offset(center.x - bOffset, center.y + bOffset), 3f)
            drawLine(strokeColor, Offset(center.x - bOffset, center.y + bOffset), Offset(center.x - bOffset + bracketLen, center.y + bOffset), 3f)

            // Bottom-right
            drawLine(strokeColor, Offset(center.x + bOffset, center.y + bOffset - bracketLen), Offset(center.x + bOffset, center.y + bOffset), 3f)
            drawLine(strokeColor, Offset(center.x + bOffset, center.y + bOffset), Offset(center.x + bOffset - bracketLen, center.y + bOffset), 3f)

            // Center focal dot
            drawCircle(color = strokeColor, radius = 3.5f, center = center)
        }
    }
}
