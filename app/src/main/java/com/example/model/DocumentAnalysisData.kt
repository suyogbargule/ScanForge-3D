package com.example.model

data class RequirementAuditItem(
    val id: String,
    val title: String,
    val priority: String, // "Must", "Should", "Optional"
    val status: String,   // "Fully Compliant", "Operational"
    val details: String
)

data class DocumentAnalysisData(
    val documentTitle: String = "Android Local 3D Object Scanner SRS / PRD (v1.0)",
    val targetPlatform: String = "Android 16 (API Level 36)",
    val architectureType: String = "Local-first / Offline Multi-View Reconstruction",
    val executiveSummary: String = "Guided spherical multi-view photographic capture around physical objects with real-time pose tracking, frame quality gating, and local interactive 3D reconstruction without mandatory cloud dependencies.",
    val requirementsAudit: List<RequirementAuditItem> = listOf(
        RequirementAuditItem("FR-01", "Create new scan session", "Must", "Fully Compliant", "Session lifecycle with unique ID, timestamp, and device profile"),
        RequirementAuditItem("FR-02", "Live camera preview", "Must", "Fully Compliant", "CameraX preview pipeline with fallback simulation for testing"),
        RequirementAuditItem("FR-03", "Spherical capture guidance", "Must", "Fully Compliant", "5-layer spherical shell (Top, Upper, Middle, Lower, Bottom) with target markers"),
        RequirementAuditItem("FR-04", "ARCore / IMU pose tracking", "Must", "Fully Compliant", "Hardware sensor fusion (gyroscope + accelerometer) with object-centered coordinates"),
        RequirementAuditItem("FR-05", "Automatic quality-gated capture", "Must", "Fully Compliant", "Real-time gating: target pose alignment, stability, sharpness, exposure, redundancy"),
        RequirementAuditItem("FR-06", "Manual capture override", "Must", "Fully Compliant", "One-tap manual capture trigger available at any time"),
        RequirementAuditItem("FR-07", "Scan progress and coverage", "Must", "Fully Compliant", "Real-time coverage percentage, cell completion status, and thumbnail feed"),
        RequirementAuditItem("FR-08", "Poor-frame detection", "Must", "Fully Compliant", "Motion blur detection, over/under exposure warning, and feedback"),
        RequirementAuditItem("FR-09", "Coverage-gap detection", "Must", "Fully Compliant", "Interactive 3D coverage globe showing missing viewing angles and directions"),
        RequirementAuditItem("FR-10", "Local image & metadata storage", "Must", "Fully Compliant", "App-private local storage with Room SQLite metadata persistence"),
        RequirementAuditItem("FR-11", "Local 3D reconstruction", "Target", "Fully Compliant", "Replaceable pipeline: feature matching, SfM bundle adjustment, 3DGS & mesh generation"),
        RequirementAuditItem("FR-12", "Interactive 3D viewer", "Must", "Fully Compliant", "Touch orbit, pan, pinch zoom, reset, 3DGS splat and mesh rendering"),
        RequirementAuditItem("FR-13", "Resume interrupted sessions", "Should", "Fully Compliant", "Session state preserved locally in Room database"),
        RequirementAuditItem("FR-14", "3D asset export", "Should", "Fully Compliant", "Direct local export to standard PLY (Gaussian splat / point cloud) and OBJ"),
        RequirementAuditItem("FR-15", "Optional Firebase backup/auth", "Optional", "Compliant", "Isolated architecture keeping core pipeline 100% offline and local")
    ),
    val openSourceReferences: List<String> = listOf(
        "OpenSplat Android: Mobile 3D Gaussian Splatting architecture",
        "ScanForge: Spherical capture guidance & IMU pose tracking",
        "Object-Centered Mobile 3DGS: Stability-gated multi-view acquisition",
        "OpenMVG: Structure from Motion & camera geometric models",
        "DenseFrame: Local Android scanning & point-cloud workflow"
    ),
    val accessibilityCompliance: List<String> = listOf(
        "Minimum 48dp x 48dp touch targets on all interactive elements",
        "Screen-reader TalkBack descriptive semantics on all visual indicators and controls",
        "Colorblind-safe status semantic cues (icons + text labels alongside Green/Yellow/Red)",
        "Haptic vibration feedback on automatic capture triggers",
        "High-contrast viewing mode and dynamic font scale support"
    )
)
