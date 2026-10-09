package com.example.model

enum class QualityProfile(val displayName: String, val maxResolution: String, val frameTarget: Int, val description: String) {
    FAST("Fast Preview", "720p", 24, "Quick capture with sparse point cloud generation"),
    BALANCED("Balanced", "1080p", 36, "Recommended for most phones (optimal quality & speed)"),
    HIGH_QUALITY("High Precision", "4K / Native", 48, "Dense multi-view matching with 3D Gaussian Splatting")
}

data class DeviceCapabilityProfile(
    val androidVersion: String = "Android 16",
    val hasCamera: Boolean = true,
    val maxCameraResolution: String = "1920x1080 (FHD)",
    val hasAccelerometer: Boolean = true,
    val hasGyroscope: Boolean = true,
    val isArCoreSupported: Boolean = true,
    val totalRamMb: Long = 8192L,
    val availableRamMb: Long = 4200L,
    val freeStorageMb: Long = 18400L,
    val supportsVulkan: Boolean = true,
    val supportsOpenGlEs3: Boolean = true,
    val recommendedProfile: QualityProfile = QualityProfile.BALANCED,
    val hardwareScore: Int = 92 // 0..100
) {
    val isReadyFor3DScan: Boolean
        get() = hasCamera && (hasAccelerometer || hasGyroscope) && freeStorageMb > 500
}
