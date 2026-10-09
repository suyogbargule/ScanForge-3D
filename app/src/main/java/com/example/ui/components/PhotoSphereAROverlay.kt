package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import com.example.model.DotStatus
import com.example.model.PhotoSphereDot
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PhotoSphereAROverlay(
    dots: List<PhotoSphereDot>,
    deviceYawDeg: Float,
    devicePitchDeg: Float,
    deviceRollDeg: Float,
    isAligned: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().testTag("photosphere_ar_overlay")) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val cx = width / 2f
            val cy = height / 2f

            // Field of view pixels per degree (typical mobile camera ~65 deg horizontal FOV)
            val pxPerDeg = width / 65f

            // 1. Draw central alignment reticle
            val reticleColor = if (isAligned) Color(0xFF00E676) else Color(0xFF00E5FF)
            drawCircle(
                color = reticleColor.copy(alpha = 0.8f),
                radius = 32f,
                center = Offset(cx, cy),
                style = Stroke(width = 2.5f)
            )
            drawCircle(
                color = reticleColor,
                radius = 4f,
                center = Offset(cx, cy)
            )

            // Artificial Horizon Level bar (shows roll tilt)
            val rollRad = Math.toRadians(deviceRollDeg.toDouble()).toFloat()
            val barLen = 60f
            val dx = barLen * cos(rollRad)
            val dy = barLen * sin(rollRad)
            val levelColor = if (abs(deviceRollDeg) < 10f) Color(0xFF00E676) else Color(0xFFFFB300)

            drawLine(
                color = levelColor,
                start = Offset(cx - dx - 40f, cy - dy),
                end = Offset(cx - dx, cy - dy),
                strokeWidth = 3f
            )
            drawLine(
                color = levelColor,
                start = Offset(cx + dx, cy + dy),
                end = Offset(cx + dx + 40f, cy + dy),
                strokeWidth = 3f
            )

            // 2. Draw floating 360 dots
            var nearestOffscreenDot: PhotoSphereDot? = null
            var minAngleDiff = 999f

            for (dot in dots) {
                if (dot.status == DotStatus.CAPTURED) continue

                // Relative heading
                var diffYaw = (dot.azimuthDeg - deviceYawDeg + 180f) % 360f - 180f
                if (diffYaw < -180f) diffYaw += 360f

                val diffPitch = dot.pitchDeg - devicePitchDeg

                val screenX = cx + diffYaw * pxPerDeg
                val screenY = cy - diffPitch * pxPerDeg

                // Check if dot is inside viewport
                if (screenX in 0f..width && screenY in 0f..height) {
                    val isLocked = isAligned && abs(diffYaw) < 7f && abs(diffPitch) < 7f
                    val dotColor = if (isLocked) Color(0xFF00E676) else Color(0xFF00E5FF)

                    // Floating target bubble
                    drawCircle(
                        color = dotColor.copy(alpha = 0.3f),
                        radius = if (isLocked) 28f else 18f,
                        center = Offset(screenX, screenY)
                    )
                    drawCircle(
                        color = dotColor,
                        radius = if (isLocked) 16f else 10f,
                        center = Offset(screenX, screenY),
                        style = Stroke(width = 3f)
                    )
                    drawCircle(
                        color = dotColor,
                        radius = 4f,
                        center = Offset(screenX, screenY)
                    )
                } else {
                    // Track nearest offscreen target for directional arrow
                    val totalDiff = abs(diffYaw) + abs(diffPitch)
                    if (totalDiff < minAngleDiff) {
                        minAngleDiff = totalDiff
                        nearestOffscreenDot = dot
                    }
                }
            }

            // 3. Directional guidance arrow pointing to nearest offscreen dot
            if (nearestOffscreenDot != null && !isAligned) {
                var diffYaw = (nearestOffscreenDot.azimuthDeg - deviceYawDeg + 180f) % 360f - 180f
                if (diffYaw < -180f) diffYaw += 360f
                val diffPitch = nearestOffscreenDot.pitchDeg - devicePitchDeg

                val angle = atan2(-diffPitch, diffYaw)
                val arrowDist = cx.coerceAtMost(cy) - 50f
                val ax = cx + arrowDist * cos(angle)
                val ay = cy + arrowDist * sin(angle)

                // Draw glowing directional pointer
                val path = Path().apply {
                    val tipX = ax + 18f * cos(angle)
                    val tipY = ay + 18f * sin(angle)
                    val leftX = ax + 14f * cos(angle + 2.5f)
                    val leftY = ay + 14f * sin(angle + 2.5f)
                    val rightX = ax + 14f * cos(angle - 2.5f)
                    val rightY = ay + 14f * sin(angle - 2.5f)

                    moveTo(tipX, tipY)
                    lineTo(leftX, leftY)
                    lineTo(rightX, rightY)
                    close()
                }
                drawPath(path, Color(0xFF00E5FF))
            }
        }
    }
}
