package com.example

import com.example.engine.coverage.SphereCoverageChecker
import com.example.engine.quality.CaptureQualityGate
import com.example.model.CameraFovInfo
import com.example.model.SphereLayer
import com.example.model.SphereTargetBuilder
import com.example.model.TargetStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Scan3DUnitTest {

    @Test
    fun testBuild30TargetsMatchesSection15Specification() {
        val targets = SphereTargetBuilder.buildTargets()
        assertEquals(30, targets.size)

        // ID 1 = Zenith (+90)
        val zenith = targets.find { it.id == 1 }
        assertNotNull(zenith)
        assertEquals(SphereLayer.ZENITH, zenith!!.layer)
        assertEquals(90f, zenith.pitchDeg, 0.001f)
        assertEquals("img_01.jpg", zenith.fileName)

        // IDs 2..9 = Upper (8 targets at +45)
        val upper = targets.filter { it.layer == SphereLayer.UPPER }
        assertEquals(8, upper.size)
        assertEquals((2..9).toList(), upper.map { it.id })
        assertTrue(upper.all { it.pitchDeg == 45f })

        // IDs 10..21 = Centre (12 targets at 0)
        val centre = targets.filter { it.layer == SphereLayer.CENTRE }
        assertEquals(12, centre.size)
        assertEquals((10..21).toList(), centre.map { it.id })
        assertTrue(centre.all { it.pitchDeg == 0f })

        // IDs 22..29 = Lower (8 targets at -45)
        val lower = targets.filter { it.layer == SphereLayer.LOWER }
        assertEquals(8, lower.size)
        assertEquals((22..29).toList(), lower.map { it.id })
        assertTrue(lower.all { it.pitchDeg == -45f })

        // ID 30 = Nadir (-90)
        val nadir = targets.find { it.id == 30 }
        assertNotNull(nadir)
        assertEquals(SphereLayer.NADIR, nadir!!.layer)
        assertEquals(-90f, nadir.pitchDeg, 0.001f)
        assertEquals("img_30.jpg", nadir.fileName)
    }

    @Test
    fun testRecommendedGuidedCaptureOrder() {
        val targets = SphereTargetBuilder.buildTargets()
        val first = SphereTargetBuilder.getNextGuidedTarget(targets)
        assertNotNull(first)
        // Guided order starts at Centre ring ID 10 (yaw 0, pitch 0)
        assertEquals(10, first!!.id)

        // After completing Centre ring (10..21), next should be Upper ring ID 2
        val afterCentre = targets.map {
            if (it.id in 10..21) it.copy(status = TargetStatus.COMPLETED) else it
        }
        val nextUpper = SphereTargetBuilder.getNextGuidedTarget(afterCentre)
        assertEquals(2, nextUpper!!.id)
    }

    @Test
    fun testSphereDirectionAndAngleErrorMath() {
        val fwdNorth = SphereTargetBuilder.dir(0f, 0f)
        val fwdEast = SphereTargetBuilder.dir(90f, 0f)
        val fwdZenith = SphereTargetBuilder.dir(0f, 90f)

        assertEquals(90f, SphereTargetBuilder.angleErrorDeg(fwdNorth, fwdEast), 0.01f)
        assertEquals(90f, SphereTargetBuilder.angleErrorDeg(fwdNorth, fwdZenith), 0.01f)
        assertEquals(0f, SphereTargetBuilder.angleErrorDeg(fwdNorth, fwdNorth), 0.01f)
    }

    @Test
    fun test4000PointCoverageCheckReachesHighCoverageFor30IdealShots() {
        val completed = SphereTargetBuilder.buildTargets().map { target ->
            target.copy(
                status = TargetStatus.COMPLETED,
                rotationMatrix = SphereCoverageChecker.idealRotationMatrix(target.yawDeg, target.pitchDeg)
            )
        }

        // Typical phone portrait FOV 54° x 70°
        val report = SphereCoverageChecker.evaluateSessionCoverage(
            targets = completed,
            cameraFov = CameraFovInfo(hFovDeg = 54f, vFovDeg = 70f),
            n = 4000,
            validateFilesOnDisk = false
        )

        assertTrue("Expected >98% sphere coverage, got ${report.coveragePercent}%", report.coveragePercent > 98f)
        assertEquals(30, report.validFileCount)
    }

    @Test
    fun testQualityGateDetectsBlurryVsSharpFrames() {
        val gate = CaptureQualityGate()
        val w = 120
        val h = 160

        // Blank flat frame -> zero Laplacian variance (blurry / featureless)
        val flatLum = IntArray(w * h) { 120 }
        val flatMetrics = gate.analyzeLuminanceMetrics(flatLum, w, h)
        assertTrue(flatMetrics.laplacianVariance < 5.0)
        assertTrue(flatMetrics.textureWarning)

        // High-contrast textured checkerboard frame -> high Laplacian variance & rich corner texture
        val sharpLum = IntArray(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            if (((x / 6) + (y / 6)) % 2 == 0) 225 else 45
        }
        val sharpMetrics = gate.analyzeLuminanceMetrics(sharpLum, w, h)
        assertTrue("Expected high Laplacian variance, got ${sharpMetrics.laplacianVariance}", sharpMetrics.laplacianVariance > 100.0)
        assertFalse(sharpMetrics.exposureWarning)
        assertFalse(sharpMetrics.textureWarning)
    }
}
