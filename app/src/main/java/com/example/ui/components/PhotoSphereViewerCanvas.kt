package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

enum class PhotoSphereViewMode(val label: String) {
    INTERACTIVE_360("360° Orbit"),
    GYROSCOPE_LOOK("Gyro Motion"),
    VR_CARDBOARD("VR Split Screen"),
    FLAT_PANORAMA("Equirectangular")
}

@Composable
fun PhotoSphereViewerCanvas(
    bitmap: Bitmap,
    viewMode: PhotoSphereViewMode,
    gyroYawDeg: Float = 0f,
    gyroPitchDeg: Float = 0f,
    modifier: Modifier = Modifier
) {
    var touchPanX by remember { mutableFloatStateOf(0f) }
    var touchPanY by remember { mutableFloatStateOf(0f) }
    var zoomScale by remember { mutableFloatStateOf(1.0f) }

    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("photosphere_viewer_canvas")
            .pointerInput(viewMode) {
                if (viewMode != PhotoSphereViewMode.FLAT_PANORAMA) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        touchPanX += pan.x * 0.7f
                        touchPanY = (touchPanY + pan.y * 0.7f).coerceIn(-600f, 600f)
                        zoomScale = (zoomScale * zoom).coerceIn(0.6f, 3.5f)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            when (viewMode) {
                PhotoSphereViewMode.FLAT_PANORAMA -> {
                    // Display full 2:1 equirectangular image fitted
                    val destH = (width / 2f).coerceAtMost(height)
                    val destW = destH * 2f
                    val top = (height - destH) / 2f
                    val left = (width - destW) / 2f

                    drawImage(
                        image = imageBitmap,
                        dstOffset = IntOffset(left.toInt(), top.toInt()),
                        dstSize = IntSize(destW.toInt(), destH.toInt())
                    )
                }

                PhotoSphereViewMode.VR_CARDBOARD -> {
                    // Split screen: Left Eye & Right Eye with center divider
                    val eyeWidth = width / 2f
                    val effectivePanX = (touchPanX + (gyroYawDeg * 6f)) % (bitmap.width * 2)
                    val effectivePanY = (touchPanY + (gyroPitchDeg * 4f)).coerceIn(-300f, 300f)

                    // Left Eye viewport
                    clipRect(left = 0f, top = 0f, right = eyeWidth, bottom = height) {
                        renderSphereViewport(bitmap, eyeWidth, height, effectivePanX, effectivePanY, zoomScale, 0f)
                    }

                    // Right Eye viewport
                    clipRect(left = eyeWidth, top = 0f, right = width, bottom = height) {
                        renderSphereViewport(bitmap, eyeWidth, height, effectivePanX, effectivePanY, zoomScale, eyeWidth)
                    }

                    // Center divider line
                    drawLine(
                        color = Color.Black,
                        start = Offset(eyeWidth, 0f),
                        end = Offset(eyeWidth, height),
                        strokeWidth = 6f
                    )
                }

                PhotoSphereViewMode.INTERACTIVE_360,
                PhotoSphereViewMode.GYROSCOPE_LOOK -> {
                    val useGyro = viewMode == PhotoSphereViewMode.GYROSCOPE_LOOK
                    val currentYawOffset = if (useGyro) (gyroYawDeg * 8f) else 0f
                    val currentPitchOffset = if (useGyro) (gyroPitchDeg * 6f) else 0f

                    val totalPanX = touchPanX + currentYawOffset
                    val totalPanY = touchPanY + currentPitchOffset

                    renderSphereViewport(bitmap, width, height, totalPanX, totalPanY, zoomScale, 0f)
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.renderSphereViewport(
    bitmap: Bitmap,
    viewW: Float,
    viewH: Float,
    panX: Float,
    panY: Float,
    zoom: Float,
    xOffset: Float
) {
    val srcW = bitmap.width
    val srcH = bitmap.height

    // Calculate crop window on equirectangular panorama corresponding to current gaze
    val fovW = (srcW * 0.35f / zoom).toInt().coerceIn(100, srcW)
    val fovH = (srcH * 0.45f / zoom).toInt().coerceIn(100, srcH)

    var srcX = (((-panX % viewW) / viewW) * srcW).toInt() % srcW
    if (srcX < 0) srcX += srcW

    var srcY = (((-panY / viewH) * srcH) + (srcH - fovH) / 2).toInt().coerceIn(0, srcH - fovH)

    val imageBitmap = bitmap.asImageBitmap()

    // Handle seamless 360 wraparound across 0/360 meridian
    if (srcX + fovW <= srcW) {
        drawImage(
            image = imageBitmap,
            srcOffset = IntOffset(srcX, srcY),
            srcSize = IntSize(fovW, fovH),
            dstOffset = IntOffset(xOffset.toInt(), 0),
            dstSize = IntSize(viewW.toInt(), viewH.toInt())
        )
    } else {
        // Split across wrap boundary
        val part1W = srcW - srcX
        val part2W = fovW - part1W
        val destPart1W = (part1W.toFloat() / fovW) * viewW
        val destPart2W = viewW - destPart1W

        drawImage(
            image = imageBitmap,
            srcOffset = IntOffset(srcX, srcY),
            srcSize = IntSize(part1W, fovH),
            dstOffset = IntOffset(xOffset.toInt(), 0),
            dstSize = IntSize(destPart1W.toInt(), viewH.toInt())
        )
        drawImage(
            image = imageBitmap,
            srcOffset = IntOffset(0, srcY),
            srcSize = IntSize(part2W, fovH),
            dstOffset = IntOffset((xOffset + destPart1W).toInt(), 0),
            dstSize = IntSize(destPart2W.toInt(), viewH.toInt())
        )
    }
}
