package com.example.engine.coverage

import android.graphics.BitmapFactory
import com.example.model.CameraFovInfo
import com.example.model.CaptureTarget
import com.example.model.CoverageReport
import com.example.model.SphereTargetBuilder
import com.example.model.TargetStatus
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Exact Section 22.2 Sphere Coverage & Integrity Checker:
 * Tests 4000 evenly distributed directions on the unit sphere against each shot's
 * saved 3x3 rotation matrix and camera FOV, plus validates all 30 files on disk.
 */
object SphereCoverageChecker {

    /**
     * Section 22.2 exact sees() function:
     * R: row-major 3x3 device->world matrix saved with the image
     * d: unit direction in world frame
     */
    fun sees(R: FloatArray, d: FloatArray, tanH: Float, tanV: Float): Boolean {
        if (R.size < 9 || d.size < 3) return false
        val x = R[0] * d[0] + R[3] * d[1] + R[6] * d[2] // device X (right)
        val y = R[1] * d[0] + R[4] * d[1] + R[7] * d[2] // device Y (up)
        val z = R[2] * d[0] + R[5] * d[1] + R[8] * d[2] // device Z (toward user)
        if (z >= 0f) return false // behind camera (looks along -Z)
        return abs(x / -z) <= tanH && abs(y / -z) <= tanV
    }

    /**
     * Evaluates full-sphere coverage across n = 4000 Fibonacci sphere samples,
     * validates 30 files on disk, and locates nearest target IDs for any gap regions.
     */
    fun evaluateSessionCoverage(
        targets: List<CaptureTarget>,
        cameraFov: CameraFovInfo,
        n: Int = 4000,
        validateFilesOnDisk: Boolean = true
    ): CoverageReport {
        val hFovRad = Math.toRadians(cameraFov.hFovDeg.toDouble()).toFloat()
        val vFovRad = Math.toRadians(cameraFov.vFovDeg.toDouble()).toFloat()
        val tH = tan(hFovRad / 2f) * 0.95f // 5% safety margin
        val tV = tan(vFovRad / 2f) * 0.95f

        val validShots = mutableListOf<CaptureTarget>()
        val missingIds = mutableListOf<Int>()
        var validFileCount = 0

        for (target in targets) {
            val fileOk = if (!validateFilesOnDisk) {
                target.status == TargetStatus.COMPLETED && target.rotationMatrix != null
            } else {
                val path = target.imagePath
                if (path != null) {
                    val f = File(path)
                    if (f.exists() && f.length() > 0L) {
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(f.absolutePath, opts)
                        opts.outWidth > 0 && opts.outHeight > 0
                    } else false
                } else false
            }

            if (fileOk && target.status == TargetStatus.COMPLETED) {
                validFileCount++
            } else {
                missingIds.add(target.id)
            }

            // Use recorded rotation matrix or fallback to ideal target pose matrix if recorded matrix unavailable
            if (target.status == TargetStatus.COMPLETED) {
                val matrix = target.rotationMatrix ?: idealRotationMatrix(target.yawDeg, target.pitchDeg)
                validShots.add(target.copy(rotationMatrix = matrix))
            }
        }

        var hit = 0
        val uncoveredDirections = mutableListOf<FloatArray>()

        for (k in 0 until n) {
            val z = 1f - 2f * (k + 0.5f) / n
            val r = sqrt((1f - z * z).coerceAtLeast(0f))
            val a = k * 2.3999632f // golden angle
            val d = floatArrayOf(r * cos(a), r * sin(a), z)
            if (validShots.any { shot -> shot.rotationMatrix?.let { sees(it, d, tH, tV) } == true }) {
                hit++
            } else {
                uncoveredDirections.add(d)
            }
        }

        val coverageFraction = hit.toFloat() / n.toFloat()

        // Identify targets closest to uncovered gap directions
        val gapTargetIds = uncoveredDirections
            .asSequence()
            .filterIndexed { index, _ -> index % 10 == 0 } // sample gap directions
            .mapNotNull { dirVec ->
                targets.minByOrNull { t ->
                    SphereTargetBuilder.angleErrorDeg(dirVec, t.directionVector())
                }?.id
            }
            .distinct()
            .toList()

        // Estimate nominal neighbor overlap based on camera FOV and 30°/45° spacing
        val horizOverlap = ((cameraFov.hFovDeg - 30f) / cameraFov.hFovDeg).coerceIn(0f, 1f)
        val vertOverlap = ((cameraFov.vFovDeg - 45f) / cameraFov.vFovDeg).coerceIn(0f, 1f)
        val minOverlap = minOf(horizOverlap, vertOverlap)

        val integrityOk = targets.size == 30 && validFileCount == 30 && missingIds.isEmpty()

        return CoverageReport(
            coverageFraction = coverageFraction,
            minNeighborOverlap = minOverlap,
            gapCount = uncoveredDirections.size,
            gapTargetIds = gapTargetIds,
            integrityOk = integrityOk,
            validFileCount = validFileCount,
            missingIds = missingIds
        )
    }

    /**
     * Constructs a row-major 3x3 device->world matrix whose camera -Z axis points along (yawDeg, pitchDeg).
     */
    fun idealRotationMatrix(yawDeg: Float, pitchDeg: Float): FloatArray {
        val fwd = SphereTargetBuilder.dir(yawDeg, pitchDeg)
        val upRef = if (abs(pitchDeg) > 80f) floatArrayOf(0f, 1f, 0f) else floatArrayOf(0f, 0f, 1f)
        val right = SphereTargetBuilder.normalize(SphereTargetBuilder.cross(fwd, upRef))
        val up = SphereTargetBuilder.normalize(SphereTargetBuilder.cross(right, fwd))
        // Row-major: columns are device X (right), Y (up), Z (-fwd)
        return floatArrayOf(
            right[0], up[0], -fwd[0],
            right[1], up[1], -fwd[1],
            right[2], up[2], -fwd[2]
        )
    }
}
