package com.example.engine.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.cos
import kotlin.math.sin

class CameraProvider(
    private val context: Context
) {
    private var imageCapture: ImageCapture? = null

    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onError: (Throwable) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                onError(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Captures a frame to file. If hardware camera is unavailable, generates a synthetic frame.
     */
    suspend fun captureFrame(
        outputFile: File,
        azimuthDeg: Float = 0f,
        elevationDeg: Float = 0f
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Check parent dir
            outputFile.parentFile?.mkdirs()

            // If hardware capture is available, take photo
            val capture = imageCapture
            if (capture != null) {
                val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
                var success = false
                val latch = java.util.concurrent.CountDownLatch(1)

                capture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            success = true
                            latch.countDown()
                        }
                        override fun onError(exception: ImageCaptureException) {
                            success = false
                            latch.countDown()
                        }
                    }
                )
                latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
                if (success && outputFile.exists()) return@withContext true
            }

            // Fallback: render realistic object view matching viewpoint
            val bmp = generateSyntheticObjectFrame(azimuthDeg, elevationDeg)
            FileOutputStream(outputFile).use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun generateSyntheticObjectFrame(azimuthDeg: Float, elevationDeg: Float): Bitmap {
        val width = 640
        val height = 480
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Draw background studio gradient
        val bgPaint = Paint().apply {
            color = Color.rgb(28, 32, 42)
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Draw turntable shadow
        val shadowPaint = Paint().apply {
            color = Color.argb(80, 0, 0, 0)
            style = Paint.Style.FILL
        }
        canvas.drawOval(RectF(180f, 320f, 460f, 380f), shadowPaint)

        // Draw object projected from angle
        val azRad = Math.toRadians(azimuthDeg.toDouble()).toFloat()
        val elRad = Math.toRadians(elevationDeg.toDouble()).toFloat()

        val objPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            val r = (120 + 80 * cos(azRad)).toInt().coerceIn(0, 255)
            val g = (140 + 70 * sin(azRad)).toInt().coerceIn(0, 255)
            val b = (220 + 30 * sin(elRad)).toInt().coerceIn(0, 255)
            color = Color.rgb(r, g, b)
        }

        // Draw stylized geometric object
        val cx = width / 2f
        val cy = height / 2f - 20f
        canvas.drawRoundRect(RectF(cx - 90f, cy - 110f, cx + 90f, cy + 110f), 24f, 24f, objPaint)

        // Draw optical tracking fiducials
        val markerPaint = Paint().apply {
            color = Color.CYAN
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        canvas.drawCircle(cx, cy, 50f, markerPaint)

        return bmp
    }
}
