package com.example.ui.components

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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import com.example.model.Model3D
import com.example.model.Point3D
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

enum class ViewerRenderMode(val label: String) {
    GAUSSIAN_SPLATS("3DGS Splats"),
    POINT_CLOUD("Point Cloud"),
    WIREFRAME("Wireframe"),
    SHADED_SURFACE("Shaded Mesh")
}

@Composable
fun Viewer3DCanvas(
    model: Model3D,
    renderMode: ViewerRenderMode,
    pointSize: Float = 6f,
    modifier: Modifier = Modifier
) {
    var yawDeg by remember { mutableFloatStateOf(35f) }
    var pitchDeg by remember { mutableFloatStateOf(15f) }
    var zoomScale by remember { mutableFloatStateOf(1.2f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .testTag("viewer_3d_canvas")
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    // Zoom
                    zoomScale = (zoomScale * zoom).coerceIn(0.4f, 4.0f)
                    // Pan
                    panOffsetX += pan.x * 0.8f
                    panOffsetY += pan.y * 0.8f
                    // Rotate
                    yawDeg += pan.x * 0.4f
                    pitchDeg = (pitchDeg - pan.y * 0.4f).coerceIn(-85f, 85f)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val cx = width / 2f + panOffsetX
            val cy = height / 2f + panOffsetY
            val focalLength = (width.coerceAtMost(height) * 1.4f) * zoomScale

            val yawRad = Math.toRadians(yawDeg.toDouble()).toFloat()
            val pitchRad = Math.toRadians(pitchDeg.toDouble()).toFloat()

            val cosYaw = cos(yawRad)
            val sinYaw = sin(yawRad)
            val cosPitch = cos(pitchRad)
            val sinPitch = sin(pitchRad)

            // Camera distance from center
            val cameraDist = 2.0f

            // Transform vertices: World -> View space
            data class TransformedPoint(
                val original: Point3D,
                val viewX: Float,
                val viewY: Float,
                val viewZ: Float,
                val projX: Float,
                val projY: Float,
                val isValid: Boolean
            )

            val transformedPoints = model.points.map { pt ->
                // 1. Yaw rotation around Y axis
                val x1 = pt.x * cosYaw - pt.z * sinYaw
                val y1 = pt.y
                val z1 = pt.x * sinYaw + pt.z * cosYaw

                // 2. Pitch rotation around X axis
                val x2 = x1
                val y2 = y1 * cosPitch - z1 * sinPitch
                val z2 = y1 * sinPitch + z1 * cosPitch

                // 3. Translate along Z (camera view)
                val viewZ = z2 + cameraDist

                if (viewZ > 0.1f) {
                    val projX = cx + (x2 / viewZ) * focalLength
                    val projY = cy - (y2 / viewZ) * focalLength
                    TransformedPoint(pt, x2, y2, viewZ, projX, projY, true)
                } else {
                    TransformedPoint(pt, 0f, 0f, viewZ, 0f, 0f, false)
                }
            }

            // Draw based on selected mode
            when (renderMode) {
                ViewerRenderMode.GAUSSIAN_SPLATS -> {
                    // Sort by depth (back-to-front)
                    val sorted = transformedPoints
                        .filter { it.isValid }
                        .sortedByDescending { it.viewZ }

                    for (tp in sorted) {
                        val pt = tp.original
                        val splatRadius = max(2f, (pt.scale * focalLength / tp.viewZ) * pointSize * 0.4f)
                        val color = Color(
                            red = pt.r,
                            green = pt.g,
                            blue = pt.b,
                            alpha = pt.opacity * 0.85f
                        )
                        // Gaussian splat ellipse
                        drawOval(
                            color = color,
                            topLeft = Offset(tp.projX - splatRadius * 1.2f, tp.projY - splatRadius * 0.8f),
                            size = Size(splatRadius * 2.4f, splatRadius * 1.6f)
                        )
                    }
                }

                ViewerRenderMode.POINT_CLOUD -> {
                    val sorted = transformedPoints
                        .filter { it.isValid }
                        .sortedByDescending { it.viewZ }

                    for (tp in sorted) {
                        val pt = tp.original
                        val radius = max(1.5f, (pointSize / tp.viewZ))
                        drawCircle(
                            color = Color(pt.r, pt.g, pt.b, 1f),
                            radius = radius,
                            center = Offset(tp.projX, tp.projY)
                        )
                    }
                }

                ViewerRenderMode.WIREFRAME -> {
                    for (tri in model.triangles) {
                        if (tri.v1 < transformedPoints.size && tri.v2 < transformedPoints.size && tri.v3 < transformedPoints.size) {
                            val p1 = transformedPoints[tri.v1]
                            val p2 = transformedPoints[tri.v2]
                            val p3 = transformedPoints[tri.v3]

                            if (p1.isValid && p2.isValid && p3.isValid) {
                                val path = Path().apply {
                                    moveTo(p1.projX, p1.projY)
                                    lineTo(p2.projX, p2.projY)
                                    lineTo(p3.projX, p3.projY)
                                    close()
                                }
                                drawPath(
                                    path = path,
                                    color = Color(0xFF00E5FF).copy(alpha = 0.5f),
                                    style = Stroke(width = 1.2f)
                                )
                            }
                        }
                    }
                }

                ViewerRenderMode.SHADED_SURFACE -> {
                    // Triangle depth sorting + simple Lambertian light
                    val lightDirX = 0.577f
                    val lightDirY = 0.577f
                    val lightDirZ = 0.577f

                    val validTriangles = model.triangles.mapNotNull { tri ->
                        val p1 = transformedPoints[tri.v1]
                        val p2 = transformedPoints[tri.v2]
                        val p3 = transformedPoints[tri.v3]
                        if (p1.isValid && p2.isValid && p3.isValid) {
                            val avgZ = (p1.viewZ + p2.viewZ + p3.viewZ) / 3f
                            Triple(tri, avgZ, listOf(p1, p2, p3))
                        } else null
                    }.sortedByDescending { it.second }

                    for (item in validTriangles) {
                        val tri = item.first
                        val pts = item.third
                        val p1 = pts[0]
                        val p2 = pts[1]
                        val p3 = pts[2]

                        // Normal
                        val n = p1.original
                        val intensity = (n.nx * lightDirX + n.ny * lightDirY + n.nz * lightDirZ).coerceIn(0.15f, 1.0f)
                        val color = Color(
                            red = (n.r * intensity).coerceIn(0f, 1f),
                            green = (n.g * intensity).coerceIn(0f, 1f),
                            blue = (n.b * intensity).coerceIn(0f, 1f),
                            alpha = 0.95f
                        )

                        val path = Path().apply {
                            moveTo(p1.projX, p1.projY)
                            lineTo(p2.projX, p2.projY)
                            lineTo(p3.projX, p3.projY)
                            close()
                        }
                        drawPath(path = path, color = color)
                    }
                }
            }
        }
    }
}
