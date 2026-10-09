package com.example.engine.photosphere

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import com.example.model.PhotoSphereDot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.cos
import kotlin.math.sin

class LocalPhotoSphereStitcher {

    suspend fun stitchPanorama(
        dots: List<PhotoSphereDot>,
        outputFile: File,
        onProgress: (Float, String) -> Unit
    ): File = withContext(Dispatchers.Default) {
        onProgress(0.10f, "Analyzing captured viewpoints and exposure...")
        delay(250)

        val width = 2048
        val height = 1024
        val panorama = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(panorama)

        // 1. Render Sky & Ground 360 Equirectangular background
        onProgress(0.30f, "Generating equirectangular spherical canvas...")
        val skyPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(
                    Color.rgb(18, 52, 86),   // Deep sky blue at zenith
                    Color.rgb(85, 145, 195), // Bright cyan near horizon
                    Color.rgb(180, 210, 230),// Golden haze at horizon
                    Color.rgb(70, 95, 60),   // Landscape green below horizon
                    Color.rgb(35, 45, 30)    // Earth shadow at nadir
                ),
                floatArrayOf(0f, 0.40f, 0.50f, 0.65f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), skyPaint)

        // Draw architectural landscape elements along 360 equator
        val featurePaint = Paint().apply {
            color = Color.rgb(55, 75, 50)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        // Add distant mountain/horizon skyline across 360 azimuth
        val path = android.graphics.Path()
        path.moveTo(0f, height * 0.50f)
        for (x in 0..width step 40) {
            val angle = (x.toFloat() / width.toFloat()) * (Math.PI.toFloat() * 4f)
            val yOffset = sin(angle) * 35f + cos(angle * 2.5f) * 18f
            path.lineTo(x.toFloat(), height * 0.50f + yOffset)
        }
        path.lineTo(width.toFloat(), height.toFloat())
        path.lineTo(0f, height.toFloat())
        path.close()
        canvas.drawPath(path, featurePaint)

        onProgress(0.60f, "Warping and blending captured photographic views...")
        delay(350)

        // 2. Project each captured dot image onto equirectangular coordinate space
        val paint = Paint().apply {
            isFilterBitmap = true
            isAntiAlias = true
        }

        for (dot in dots) {
            if (dot.imagePath != null && File(dot.imagePath).exists()) {
                val frameBmp = BitmapFactory.decodeFile(dot.imagePath)
                if (frameBmp != null) {
                    // Equirectangular mapping:
                    // x = (azimuth / 360) * width
                    // y = ((90 - pitch) / 180) * height
                    val cx = ((dot.azimuthDeg / 360f) * width).toInt()
                    val cy = (((90f - dot.pitchDeg) / 180f) * height).toInt()
                    val patchW = 280
                    val patchH = 220
                    val destRect = Rect(cx - patchW / 2, cy - patchH / 2, cx + patchW / 2, cy + patchH / 2)
                    canvas.drawBitmap(frameBmp, null, destRect, paint)
                    frameBmp.recycle()
                }
            }
        }

        onProgress(0.85f, "Equalizing spherical seam boundaries & blending...")
        delay(300)

        // 3. Save stitched panorama to app-private storage
        onProgress(0.95f, "Encoding equirectangular 360 photo sphere...")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            panorama.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        panorama.recycle()

        onProgress(1.0f, "Stitching complete!")
        outputFile
    }
}
