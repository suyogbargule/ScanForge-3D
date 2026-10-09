package com.example.engine.guidance

import com.example.model.CellStatus
import com.example.model.CoverageCell
import com.example.model.Layer
import com.example.model.Pose
import com.example.model.QualityProfile
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class CaptureGuide {

    fun generateLattice(profile: QualityProfile = QualityProfile.BALANCED): List<CoverageCell> {
        val cells = mutableListOf<CoverageCell>()

        // 1. Top Surface
        cells.add(
            CoverageCell(
                id = "top_0",
                layer = Layer.TOP,
                azimuthDeg = 0f,
                elevationDeg = 75f,
                status = CellStatus.RECOMMENDED
            )
        )

        // 2. Upper Ring (e.g. 8 points at 45 deg intervals, elevation +40 deg)
        val upperCount = when (profile) {
            QualityProfile.FAST -> 6
            QualityProfile.BALANCED -> 8
            QualityProfile.HIGH_QUALITY -> 10
        }
        val upperStep = 360f / upperCount
        for (i in 0 until upperCount) {
            cells.add(
                CoverageCell(
                    id = "upper_$i",
                    layer = Layer.UPPER_RING,
                    azimuthDeg = i * upperStep,
                    elevationDeg = 40f,
                    status = CellStatus.PENDING
                )
            )
        }

        // 3. Middle Ring (main geometry, highest overlap, elevation 0 deg)
        val middleCount = when (profile) {
            QualityProfile.FAST -> 10
            QualityProfile.BALANCED -> 14
            QualityProfile.HIGH_QUALITY -> 18
        }
        val middleStep = 360f / middleCount
        for (i in 0 until middleCount) {
            cells.add(
                CoverageCell(
                    id = "middle_$i",
                    layer = Layer.MIDDLE_RING,
                    azimuthDeg = i * middleStep,
                    elevationDeg = 0f,
                    status = CellStatus.PENDING
                )
            )
        }

        // 4. Lower Ring (e.g. 8 points at elevation -30 deg)
        val lowerCount = when (profile) {
            QualityProfile.FAST -> 6
            QualityProfile.BALANCED -> 8
            QualityProfile.HIGH_QUALITY -> 10
        }
        val lowerStep = 360f / lowerCount
        for (i in 0 until lowerCount) {
            cells.add(
                CoverageCell(
                    id = "lower_$i",
                    layer = Layer.LOWER_RING,
                    azimuthDeg = i * lowerStep,
                    elevationDeg = -30f,
                    status = CellStatus.PENDING
                )
            )
        }

        // 5. Bottom Surface (if visible)
        if (profile != QualityProfile.FAST) {
            cells.add(
                CoverageCell(
                    id = "bottom_0",
                    layer = Layer.BOTTOM,
                    azimuthDeg = 0f,
                    elevationDeg = -70f,
                    status = CellStatus.PENDING
                )
            )
        }

        return cells
    }

    /**
     * Determines next recommended target based on proximity to current pose.
     * Prefers staying in current ring before moving to next elevation layer.
     */
    fun getNextTarget(currentPose: Pose, cells: List<CoverageCell>): CoverageCell? {
        val pending = cells.filter { !it.isCaptured }
        if (pending.isEmpty()) return null

        // Find cell with minimum angular distance
        return pending.minByOrNull { cell ->
            angularDistance(currentPose.azimuthDeg, currentPose.elevationDeg, cell.azimuthDeg, cell.elevationDeg)
        }
    }

    /**
     * Closeness score between current pose and target cell: 0.0 (far) to 1.0 (perfectly aligned)
     */
    fun evaluateTargetProximity(currentPose: Pose, target: CoverageCell): Float {
        val distDeg = angularDistance(currentPose.azimuthDeg, currentPose.elevationDeg, target.azimuthDeg, target.elevationDeg)
        // Within 15 degrees is considered highly aligned
        return (1f - (distDeg / 30f)).coerceIn(0f, 1f)
    }

    /**
     * Spherical angular distance between two spherical points in degrees
     */
    fun angularDistance(az1: Float, el1: Float, az2: Float, el2: Float): Float {
        val dAz = shortestAngleDiff(az1, az2)
        val dEl = abs(el1 - el2)
        return sqrt(dAz * dAz + dEl * dEl)
    }

    private fun shortestAngleDiff(a: Float, b: Float): Float {
        val diff = (a - b + 180f) % 360f - 180f
        return abs(if (diff < -180f) diff + 360f else diff)
    }

    fun calculateCoveragePercentage(cells: List<CoverageCell>): Float {
        if (cells.isEmpty()) return 0f
        val captured = cells.count { it.isCaptured }
        return (captured.toFloat() / cells.size.toFloat()) * 100f
    }

    fun detectCoverageGaps(cells: List<CoverageCell>): List<CoverageCell> {
        return cells.filter { !it.isCaptured }
    }
}
