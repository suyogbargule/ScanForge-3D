package com.example

import com.example.engine.guidance.CaptureGuide
import com.example.engine.quality.FrameQualityAnalyzer
import com.example.engine.quality.FrameSelector
import com.example.model.CellStatus
import com.example.model.CoverageCell
import com.example.model.DeviceCapabilityProfile
import com.example.model.Layer
import com.example.model.Pose
import com.example.model.QualityColorState
import com.example.model.QualityProfile
import com.example.model.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Scan3DUnitTest {

    @Test
    fun testVector3DistanceAndNormalization() {
        val v1 = Vector3(1f, 0f, 0f)
        val v2 = Vector3(0f, 1f, 0f)
        val dist = v1.distanceTo(v2)
        assertEquals(1.4142f, dist, 0.001f)

        val v3 = Vector3(0f, 3f, 4f)
        assertEquals(5f, v3.length(), 0.001f)
        val norm = v3.normalized()
        assertEquals(1f, norm.length(), 0.001f)
    }

    @Test
    fun testCaptureGuideGeneratesFullSphericalLattice() {
        val guide = CaptureGuide()
        val lattice = guide.generateLattice(QualityProfile.BALANCED)

        // Verifies presence of all 5 layers: Top, Upper, Middle, Lower, Bottom
        val layersPresent = lattice.map { it.layer }.toSet()
        assertTrue(layersPresent.contains(Layer.TOP))
        assertTrue(layersPresent.contains(Layer.UPPER_RING))
        assertTrue(layersPresent.contains(Layer.MIDDLE_RING))
        assertTrue(layersPresent.contains(Layer.LOWER_RING))
        assertTrue(layersPresent.contains(Layer.BOTTOM))

        // Total views should be within dynamic 30-40 range
        assertTrue(lattice.size in 30..45)

        // Coverage percentage initially 0%
        assertEquals(0f, guide.calculateCoveragePercentage(lattice), 0.001f)
    }

    @Test
    fun testCoverageCalculationUpdatesAccurately() {
        val guide = CaptureGuide()
        val lattice = guide.generateLattice(QualityProfile.FAST)
        val total = lattice.size

        // Mark 5 cells captured
        val updated = lattice.mapIndexed { index, cell ->
            if (index < 5) cell.copy(status = CellStatus.CAPTURED) else cell
        }

        val expectedPct = (5f / total.toFloat()) * 100f
        assertEquals(expectedPct, guide.calculateCoveragePercentage(updated), 0.01f)
    }

    @Test
    fun testQualityAnalyzerAcceptsOptimalFrame() {
        val analyzer = FrameQualityAnalyzer()
        val currentPose = Pose.fromSpherical(45f, 30f)

        val result = analyzer.evaluate(
            targetCloseness = 0.85f,
            stabilityScore = 0.90f,
            estimatedSharpness = 0.88f,
            estimatedExposure = 0.55f,
            lastCapturedPose = null,
            currentPose = currentPose
        )

        assertTrue(result.canAutoCapture)
        assertTrue(result.isAcceptable)
        assertEquals(QualityColorState.GREEN, result.colorState)
    }

    @Test
    fun testQualityAnalyzerRejectsRedundantPose() {
        val analyzer = FrameQualityAnalyzer()
        val lastPose = Pose.fromSpherical(45f, 30f)
        val veryClosePose = Pose.fromSpherical(46f, 31f) // only 1 deg apart

        val result = analyzer.evaluate(
            targetCloseness = 0.85f,
            stabilityScore = 0.90f,
            estimatedSharpness = 0.88f,
            estimatedExposure = 0.55f,
            lastCapturedPose = lastPose,
            currentPose = veryClosePose
        )

        assertFalse(result.canAutoCapture)
        assertTrue(result.redundancyScore >= 0.80f)
    }

    @Test
    fun testFrameSelectorDecisionMaking() {
        val analyzer = FrameQualityAnalyzer()
        val selector = FrameSelector(analyzer)
        val targetCell = CoverageCell(
            id = "cell_1",
            layer = Layer.MIDDLE_RING,
            azimuthDeg = 0f,
            elevationDeg = 0f,
            status = CellStatus.PENDING
        )

        val decision = selector.shouldCapture(
            targetCell = targetCell,
            targetCloseness = 0.95f,
            stability = 0.92f,
            currentPose = Pose.fromSpherical(0f, 0f),
            lastCapturedPose = null,
            sharpness = 0.85f,
            exposure = 0.55f
        )

        assertTrue(decision.shouldCapture)
        assertNotNull(decision.matchedCell)
    }

    @Test
    fun testDeviceCapabilityProfileValidation() {
        val profile = DeviceCapabilityProfile(
            hasCamera = true,
            hasAccelerometer = true,
            hasGyroscope = true,
            freeStorageMb = 4500L
        )

        assertTrue(profile.isReadyFor3DScan)
        assertTrue(profile.hardwareScore >= 80)
    }

    @Test
    fun testPhotoSphereGridGeneration() {
        val dots = com.example.model.PhotoSphereGenerator.generateDefaultGrid()
        // Must contain all 360 spherical directions: middle, upper, lower, zenith, nadir
        assertEquals(28, dots.size)

        val zenith = dots.find { it.id == "zenith" }
        assertNotNull(zenith)
        assertEquals(80f, zenith!!.pitchDeg, 0.001f)

        val nadir = dots.find { it.id == "nadir" }
        assertNotNull(nadir)
        assertEquals(-80f, nadir!!.pitchDeg, 0.001f)

        val middleDots = dots.filter { it.pitchDeg == 0f }
        assertEquals(10, middleDots.size)
    }
}
