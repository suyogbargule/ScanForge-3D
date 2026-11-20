package com.example.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.CaptureState
import com.example.model.CaptureTarget
import com.example.model.GuidanceMode
import com.example.model.SensorMode
import com.example.model.SphereLayer
import com.example.model.TargetStatus
import com.example.ui.theme.CaptureGreen
import com.example.ui.theme.RetakeRed
import com.example.ui.theme.WarningAmber
import com.example.viewmodel.CaptureUiState
import kotlin.math.cos
import kotlin.math.sin

/**
 * Compose HUD layered over CameraX PreviewView + OpenGL VirtualSphereGLSurfaceView (Section 16, 23.1, 23.2):
 * - Top guidance bar (hint, X/30 counter, 5-layer summary, AE/AF lock indicator, Guided/Free toggle)
 * - Fixed center White Frame (slightly larger than AR tile)
 * - Fixed center White Circle ("START" in calibration, arrow pointer in search, "HOLD" + green ring when aligned)
 * - Bottom 5-row 30-dot spherical map (white = active, green = completed, grey = remaining, red = needs retake)
 * - Bottom-left Close (X) button & Manual shutter fallback (when in manual mode)
 */
@Composable
fun CaptureHudOverlay(
    uiState: CaptureUiState,
    onStartCalibration: () -> Unit,
    onManualShutter: () -> Unit,
    onToggleGuidanceMode: () -> Unit,
    onCloseSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("capture_hud_overlay")
    ) {
        // 1. Center Reticle, White Frame, Direction Arrow, HOLD Ring & Bottom Dot Map Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f

            val frameW = (w * 0.50f).coerceIn(195f, 340f)
            val frameH = frameW * (uiState.cameraFov.vFovDeg / uiState.cameraFov.hFovDeg.coerceAtLeast(1f))
                .coerceIn(1.1f, 1.55f)

            val circleRadius = 50f

            when (uiState.captureState) {
                CaptureState.CALIBRATING -> {
                    // Fixed white circle labelled START + level cue ring
                    val levelColor = if (uiState.isLevel) CaptureGreen else WarningAmber
                    drawCircle(
                        color = levelColor,
                        radius = circleRadius + 8f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 5f)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = circleRadius,
                        center = Offset(cx, cy)
                    )
                    drawCenterLabel("START", cx, cy)
                }

                CaptureState.SEARCHING -> {
                    // Fixed white reference frame (Section 23.1)
                    drawRect(
                        color = Color.White.copy(alpha = 0.90f),
                        topLeft = Offset(cx - frameW / 2f, cy - frameH / 2f),
                        size = Size(frameW, frameH),
                        style = Stroke(width = 4.5f)
                    )

                    // Fixed white circle reticle
                    drawCircle(
                        color = Color.White,
                        radius = circleRadius,
                        center = Offset(cx, cy)
                    )

                    // White arrow beside circle pointing toward active tile (Section 23.2 Step 10)
                    if (uiState.showArrow && uiState.activeTarget != null) {
                        val angle = uiState.arrowAngleRad
                        val tipDist = circleRadius + 44f
                        val baseDist = circleRadius + 14f
                        val halfBase = 19f

                        val tipX = cx + tipDist * cos(angle)
                        val tipY = cy + tipDist * sin(angle)
                        val baseX = cx + baseDist * cos(angle)
                        val baseY = cy + baseDist * sin(angle)
                        val perp = angle + (Math.PI.toFloat() / 2f)

                        val arrowPath = Path().apply {
                            moveTo(tipX, tipY)
                            lineTo(baseX + halfBase * cos(perp), baseY + halfBase * sin(perp))
                            lineTo(baseX - halfBase * cos(perp), baseY - halfBase * sin(perp))
                            close()
                        }
                        drawPath(arrowPath, color = Color.White)
                    }
                }

                CaptureState.ALIGNED,
                CaptureState.HOLDING,
                CaptureState.CAPTURING -> {
                    // Fixed white reference frame highlighted cleanly
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(cx - frameW / 2f, cy - frameH / 2f),
                        size = Size(frameW, frameH),
                        style = Stroke(width = 5.5f)
                    )

                    // White circle disc labelled HOLD
                    drawCircle(
                        color = Color.White,
                        radius = circleRadius,
                        center = Offset(cx, cy)
                    )

                    // Subtle track ring
                    drawCircle(
                        color = Color(0x332EF266),
                        radius = circleRadius - 2f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 7f)
                    )

                    // Active green progress ring sweeping 0..360° over hold duration (Section 17.3)
                    val progress = if (uiState.captureState == CaptureState.CAPTURING) 1f else uiState.holdProgress
                    if (progress > 0.01f) {
                        drawArc(
                            color = CaptureGreen,
                            startAngle = -90f,
                            sweepAngle = progress.coerceIn(0f, 1f) * 360f,
                            useCenter = false,
                            topLeft = Offset(cx - circleRadius + 2f, cy - circleRadius + 2f),
                            size = Size((circleRadius - 2f) * 2f, (circleRadius - 2f) * 2f),
                            style = Stroke(width = 7.5f, cap = StrokeCap.Round)
                        )
                    }

                    drawCenterLabel("HOLD", cx, cy)
                }
            }

            // 2. Bottom 5-row 30-dot spherical map (Figure 5 & Step 11)
            drawFiveLayerDotMap(
                targets = uiState.targets,
                activeTarget = uiState.activeTarget,
                cx = cx,
                centerY = h - 135f
            )
        }

        // Invisible clickable center hotspot for START tap (or manual tap fallback)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(128.dp)
                .clickable {
                    if (uiState.captureState == CaptureState.CALIBRATING) {
                        onStartCalibration()
                    } else if (!uiState.hasLiveSensor || uiState.settings.sensorMode == SensorMode.MANUAL) {
                        onManualShutter()
                    }
                }
                .testTag("center_reticle_hotspot")
        )

        // 3. Top Guidance Banner (matches Figure 5 top strip + Section 15 Layer Summary)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.72f))
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = uiState.guidanceHint,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Counter: X / 30 (Percentage)
                Text(
                    text = "${uiState.completedCount} / 30  (${uiState.progressPercent}%)",
                    color = CaptureGreen,
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 13.sp
                )

                // AE/AF Lock badge + Guided/Free toggle
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.aeAfLocked) {
                        Surface(
                            color = CaptureGreen.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "AE and AWB Locked",
                                    tint = CaptureGreen,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "AE/AF",
                                    color = CaptureGreen,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Surface(
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .clickable(onClick = onToggleGuidanceMode)
                            .testTag("btn_toggle_guidance_mode")
                    ) {
                        Text(
                            text = if (uiState.settings.guidanceMode == GuidanceMode.GUIDED) "Guided" else "Free",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Section 15 Live 5-Layer Counter Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SphereLayer.entries.forEach { layer ->
                    val (done, total) = uiState.layerCount(layer)
                    val isLayerDone = done == total
                    Text(
                        text = "${layer.displayName.first()}:$done/$total",
                        color = if (isLayerDone) CaptureGreen else Color.White.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isLayerDone) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            // Quality gate alert or low storage warning
            val alertText = when {
                !uiState.storageOk -> "Low storage (${uiState.availableStorageMb} MB free) — need 150 MB"
                uiState.qualityAlertMessage != null -> uiState.qualityAlertMessage
                else -> null
            }

            if (alertText != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = WarningAmber.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, WarningAmber.copy(alpha = 0.7f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = WarningAmber,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = alertText,
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // 4. Bottom Controls Strip (Black footer matching Figure 5)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Bottom-Left Close (X) button (Section 14 & 16)
            IconButton(
                onClick = onCloseSession,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .testTag("btn_close_capture")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.accessibility_close_capture),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Bottom-Right Manual Shutter button (shown in Manual mode or if no live sensor)
            if (!uiState.hasLiveSensor || uiState.settings.sensorMode == SensorMode.MANUAL) {
                IconButton(
                    onClick = onManualShutter,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(CaptureGreen)
                        .testTag("btn_manual_shutter")
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = stringResource(R.string.accessibility_manual_shutter),
                        tint = Color.Black,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawCenterLabel(text: String, cx: Float, cy: Float) {
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = 26f
        isFakeBoldText = true
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText(text, cx, cy + 9f, paint)
}

/**
 * Exact Section 16 & 23.2 Step 11 5-row Dot Map:
 * - Row 1: Zenith (1)
 * - Row 2: Upper (8)
 * - Row 3: Centre (12)
 * - Row 4: Lower (8)
 * - Row 5: Nadir (1)
 * Colors: White = current target, Green = completed, Grey = remaining, Red = needs retake
 */
private fun DrawScope.drawFiveLayerDotMap(
    targets: List<CaptureTarget>,
    activeTarget: CaptureTarget?,
    cx: Float,
    centerY: Float
) {
    val dotRadius = 4.6f
    val spacingX = 16f
    val spacingY = 14f

    val zenith = targets.filter { it.layer == SphereLayer.ZENITH }
    val upper = targets.filter { it.layer == SphereLayer.UPPER }.sortedBy { it.yawDeg }
    val centre = targets.filter { it.layer == SphereLayer.CENTRE }.sortedBy { it.yawDeg }
    val lower = targets.filter { it.layer == SphereLayer.LOWER }.sortedBy { it.yawDeg }
    val nadir = targets.filter { it.layer == SphereLayer.NADIR }

    val rows = listOf(
        zenith to (-2f * spacingY),
        upper to (-1f * spacingY),
        centre to 0f,
        lower to (1f * spacingY),
        nadir to (2f * spacingY)
    )

    // Subtle dark pill background behind dot map
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.78f),
        topLeft = Offset(cx - 125f, centerY - (2.8f * spacingY)),
        size = Size(250f, 5.6f * spacingY),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f)
    )

    for ((rowTargets, dy) in rows) {
        val y = centerY + dy
        val count = rowTargets.size
        val startX = cx - ((count - 1) * spacingX) / 2f
        rowTargets.forEachIndexed { idx, target ->
            val x = startX + idx * spacingX
            val isActive = target.id == activeTarget?.id
            val color = when {
                isActive -> Color.White
                target.status == TargetStatus.NEEDS_RETAKE -> RetakeRed
                target.status == TargetStatus.COMPLETED -> CaptureGreen
                else -> Color(0x66FFFFFF)
            }
            val r = if (isActive) dotRadius + 2.0f else dotRadius
            drawCircle(color = color, radius = r, center = Offset(x, y))
        }
    }
}
