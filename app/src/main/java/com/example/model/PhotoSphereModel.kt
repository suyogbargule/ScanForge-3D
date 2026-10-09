package com.example.model

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.cos
import kotlin.math.sin

enum class DotStatus {
    PENDING,
    ACTIVE,
    CAPTURED
}

data class PhotoSphereDot(
    val id: String,
    val azimuthDeg: Float,  // 0..360 around the user
    val pitchDeg: Float,    // -90 (down/nadir) to +90 (up/zenith)
    val status: DotStatus = DotStatus.PENDING,
    val imagePath: String? = null
)

data class PhotoSphereSession(
    val id: String,
    val title: String,
    val createdAt: Long,
    val dots: List<PhotoSphereDot> = emptyList(),
    val stitchedPanoramaPath: String? = null,
    val isStitched: Boolean = false
) {
    val capturedCount: Int get() = dots.count { it.status == DotStatus.CAPTURED }
    val totalCount: Int get() = dots.size
    val progressPct: Float get() = if (totalCount > 0) (capturedCount.toFloat() / totalCount) * 100f else 0f
}

object PhotoSphereGenerator {
    /**
     * Generates standard 360 photo sphere target grid:
     * - Middle ring (pitch 0°): 10 dots (every 36°)
     * - Upper ring (pitch +35°): 8 dots (every 45°)
     * - Lower ring (pitch -35°): 8 dots (every 45°)
     * - Zenith (pitch +80°): 1 dot
     * - Nadir (pitch -80°): 1 dot
     * Total = 28 dots for full 360x180 spherical coverage
     */
    fun generateDefaultGrid(): List<PhotoSphereDot> {
        val list = mutableListOf<PhotoSphereDot>()

        // 1. Middle horizontal ring (0° pitch)
        val middleCount = 10
        val middleStep = 360f / middleCount
        for (i in 0 until middleCount) {
            list.add(PhotoSphereDot(id = "mid_$i", azimuthDeg = i * middleStep, pitchDeg = 0f))
        }

        // 2. Upper ring (+35° pitch)
        val upperCount = 8
        val upperStep = 360f / upperCount
        for (i in 0 until upperCount) {
            list.add(PhotoSphereDot(id = "up_$i", azimuthDeg = i * upperStep, pitchDeg = 35f))
        }

        // 3. Lower ring (-35° pitch)
        val lowerCount = 8
        val lowerStep = 360f / lowerCount
        for (i in 0 until lowerCount) {
            list.add(PhotoSphereDot(id = "low_$i", azimuthDeg = i * lowerStep, pitchDeg = -35f))
        }

        // 4. Zenith (+80°)
        list.add(PhotoSphereDot(id = "zenith", azimuthDeg = 0f, pitchDeg = 80f))

        // 5. Nadir (-80°)
        list.add(PhotoSphereDot(id = "nadir", azimuthDeg = 0f, pitchDeg = -80f))

        return list
    }
}
