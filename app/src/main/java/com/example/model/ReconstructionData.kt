package com.example.model

enum class ReconstructionEngineType(val displayName: String, val description: String) {
    GAUSSIAN_SPLAT_3D("3D Gaussian Splatting (3DGS)", "Continuous volumetric radiosity with fast real-time splat rendering"),
    SFM_POINT_CLOUD("SfM Sparse-to-Dense Cloud", "Structure from Motion multi-view triangulation"),
    TEXTURED_MESH("Polygon Surface Mesh (OBJ/PLY)", "Delaunay tetrahedral mesh reconstruction with vertex coloring")
}

enum class ReconstructionStage(val stageName: String, val weight: Float) {
    INITIALIZING("Initializing local pipeline", 0.05f),
    FEATURE_DETECTION("Extracting mobile keypoints (XFeat / ORB)", 0.20f),
    FEATURE_MATCHING("Multi-view epipolar matching", 0.40f),
    BUNDLE_ADJUSTMENT("Structure from Motion & camera pose refinement", 0.60f),
    DENSE_RECONSTRUCTION("Local 3D point cloud & Gaussian primitive synthesis", 0.80f),
    GEOMETRIC_FILTERING("Statistical outlier removal & surface optimization", 0.95f),
    ASSET_FINALIZATION("Compiling interactive 3D scene & export formats", 1.0f)
}

data class Point3D(
    val x: Float,
    val y: Float,
    val z: Float,
    val r: Float,
    val g: Float,
    val b: Float,
    val nx: Float = 0f,
    val ny: Float = 1f,
    val nz: Float = 0f,
    val opacity: Float = 1f,
    val scale: Float = 0.02f
)

data class Triangle3D(
    val v1: Int,
    val v2: Int,
    val v3: Int
)

data class Model3D(
    val name: String,
    val points: List<Point3D> = emptyList(),
    val triangles: List<Triangle3D> = emptyList(),
    val boundingBoxSize: Vector3 = Vector3(1f, 1f, 1f),
    val engineUsed: ReconstructionEngineType = ReconstructionEngineType.GAUSSIAN_SPLAT_3D,
    val plyFilePath: String? = null,
    val objFilePath: String? = null
)
