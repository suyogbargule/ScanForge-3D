package com.example.ui.components

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.CameraFovInfo
import com.example.model.CaptureTarget
import com.example.model.SphereTargetBuilder
import com.example.model.TargetStatus
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Section 23.2 OpenGL ES 2.0 Virtual Sphere & AR Tile Renderer:
 * - Transparent GLSurfaceView layered directly over CameraX PreviewView
 * - Perspective projection matched to CameraCharacteristics FOV
 * - 30 3D tile quads anchored in world space via phone rotation matrix
 * - Custom fragment shader discarding pixels inside the circular aiming hole
 */
@Composable
fun VirtualSphereGLSurfaceView(
    targets: List<CaptureTarget>,
    activeTargetId: Int?,
    viewMatrix4x4: FloatArray,
    cameraFov: CameraFovInfo,
    showTiles: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val renderer = remember { VirtualSphereRenderer() }

    val glView = remember {
        TransparentSphereGLView(context, renderer)
    }

    renderer.updateState(
        targets = targets,
        activeTargetId = activeTargetId,
        viewMatrix = viewMatrix4x4,
        fov = cameraFov,
        showTiles = showTiles
    )
    glView.requestRender()

    DisposableEffect(glView) {
        glView.onResume()
        onDispose {
            glView.onPause()
        }
    }

    AndroidView(
        factory = { glView },
        modifier = modifier
    )
}

private class TransparentSphereGLView(
    context: Context,
    renderer: VirtualSphereRenderer
) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderMediaOverlay(true)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }
}

private class VirtualSphereRenderer : GLSurfaceView.Renderer {

    @Volatile
    private var targets: List<CaptureTarget> = emptyList()

    @Volatile
    private var activeTargetId: Int? = null

    @Volatile
    private var viewMatrix: FloatArray = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }

    @Volatile
    private var cameraFov: CameraFovInfo = CameraFovInfo()

    @Volatile
    private var showTiles: Boolean = true

    private var tileProgram = 0
    private var lineProgram = 0

    private var surfaceWidth = 1080
    private var surfaceHeight = 1920

    private val projMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    // Precomputed wireframe sphere line buffers
    private var sphereLineBuffer: FloatBuffer? = null
    private var sphereLineVertexCount = 0

    // Reusable quad buffer (4 vertices * (3 pos + 2 uv) = 20 floats)
    private val quadBuffer: FloatBuffer = ByteBuffer.allocateDirect(20 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    // Cached tile geometries keyed by FOV to avoid per-frame allocations
    private var cachedFovKey = ""
    private var cachedQuads: Map<Int, FloatArray> = emptyMap()

    fun updateState(
        targets: List<CaptureTarget>,
        activeTargetId: Int?,
        viewMatrix: FloatArray,
        fov: CameraFovInfo,
        showTiles: Boolean
    ) {
        this.targets = targets
        this.activeTargetId = activeTargetId
        this.viewMatrix = viewMatrix.copyOf()
        this.cameraFov = fov
        this.showTiles = showTiles
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)

        tileProgram = createProgram(TILE_VERTEX_SHADER, TILE_FRAGMENT_SHADER)
        lineProgram = createProgram(LINE_VERTEX_SHADER, LINE_FRAGMENT_SHADER)

        buildWireframeSphereLines()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val fov = cameraFov
        val viewAspect = surfaceWidth.toFloat() / surfaceHeight.toFloat()
        Matrix.perspectiveM(projMatrix, 0, fov.vFovDeg.coerceIn(35f, 110f), viewAspect, 0.05f, 10f)
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        // 1. Draw subtle wireframe virtual sphere (meridians & latitude rings)
        drawSphereWireframe()

        if (!showTiles) return

        // 2. Ensure tile quads are computed for current FOV
        val fovKey = "${fov.hFovDeg}_${fov.vFovDeg}"
        val currentTargets = targets
        if (fovKey != cachedFovKey || cachedQuads.isEmpty()) {
            cachedFovKey = fovKey
            val hRad = Math.toRadians(fov.hFovDeg.toDouble()).toFloat()
            val vRad = Math.toRadians(fov.vFovDeg.toDouble()).toFloat()
            cachedQuads = currentTargets.associate { t ->
                t.id to buildTileQuadVertices(t, hRad, vRad, frac = 0.45f)
            }
        }

        val tileAspect = tan(Math.toRadians(fov.hFovDeg / 2.0)).toFloat() /
            tan(Math.toRadians(fov.vFovDeg / 2.0)).toFloat().coerceAtLeast(0.1f)

        // 3. Draw completed tiles gently first (so user sees sphere progress around them), then active tile
        val activeId = activeTargetId
        for (target in currentTargets) {
            if (target.id == activeId) continue
            if (target.status == TargetStatus.COMPLETED) {
                val verts = cachedQuads[target.id] ?: continue
                drawTileQuad(
                    vertices = verts,
                    r = 0.14f, g = 0.78f, b = 0.36f, a = 0.24f,
                    aspect = tileAspect,
                    holeRadius = 0.14f
                )
            } else if (target.status == TargetStatus.NEEDS_RETAKE) {
                val verts = cachedQuads[target.id] ?: continue
                drawTileQuad(
                    vertices = verts,
                    r = 0.94f, g = 0.27f, b = 0.27f, a = 0.35f,
                    aspect = tileAspect,
                    holeRadius = 0.14f
                )
            }
        }

        // 4. Draw active target tile at full vivid green brightness with circular hole (Section 23.2 Step 5 & 6)
        if (activeId != null) {
            val activeVerts = cachedQuads[activeId]
            if (activeVerts != null) {
                drawTileQuad(
                    vertices = activeVerts,
                    r = 0.18f, g = 0.95f, b = 0.40f, a = 0.80f,
                    aspect = tileAspect,
                    holeRadius = 0.14f
                )
            }
        }
    }

    private fun drawSphereWireframe() {
        val buf = sphereLineBuffer ?: return
        if (sphereLineVertexCount <= 0) return

        GLES20.glUseProgram(lineProgram)
        val mvpLoc = GLES20.glGetUniformLocation(lineProgram, "uMvpMatrix")
        val colorLoc = GLES20.glGetUniformLocation(lineProgram, "uColor")
        val posLoc = GLES20.glGetAttribLocation(lineProgram, "aPosition")

        GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniform4f(colorLoc, 1f, 1f, 1f, 0.16f)

        buf.position(0)
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, 12, buf)
        GLES20.glLineWidth(1.8f)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, sphereLineVertexCount)
        GLES20.glDisableVertexAttribArray(posLoc)
    }

    private fun drawTileQuad(
        vertices: FloatArray,
        r: Float,
        g: Float,
        b: Float,
        a: Float,
        aspect: Float,
        holeRadius: Float
    ) {
        GLES20.glUseProgram(tileProgram)

        val mvpLoc = GLES20.glGetUniformLocation(tileProgram, "uMvpMatrix")
        val colorLoc = GLES20.glGetUniformLocation(tileProgram, "uColor")
        val aspectLoc = GLES20.glGetUniformLocation(tileProgram, "uAspect")
        val holeLoc = GLES20.glGetUniformLocation(tileProgram, "uHole")
        val posLoc = GLES20.glGetAttribLocation(tileProgram, "aPosition")
        val uvLoc = GLES20.glGetAttribLocation(tileProgram, "aUv")

        GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniform4f(colorLoc, r, g, b, a)
        GLES20.glUniform1f(aspectLoc, aspect)
        GLES20.glUniform1f(holeLoc, holeRadius)

        quadBuffer.clear()
        quadBuffer.put(vertices)

        val stride = 5 * 4
        quadBuffer.position(0)
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, stride, quadBuffer)

        quadBuffer.position(3)
        GLES20.glEnableVertexAttribArray(uvLoc)
        GLES20.glVertexAttribPointer(uvLoc, 2, GLES20.GL_FLOAT, false, stride, quadBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(posLoc)
        GLES20.glDisableVertexAttribArray(uvLoc)
    }

    /**
     * Exact Section 23.2 Step 4 tileCorners quad geometry at unit distance 1.0
     * Ordered for GL_TRIANGLE_STRIP: (-1,-1), (1,-1), (-1,1), (1,1) with 0..1 UVs.
     */
    private fun buildTileQuadVertices(
        t: CaptureTarget,
        hFovRad: Float,
        vFovRad: Float,
        frac: Float
    ): FloatArray {
        val c = SphereTargetBuilder.dir(t.yawDeg, t.pitchDeg)
        val upRef = if (abs(t.pitchDeg) > 80f) floatArrayOf(0f, 1f, 0f) else floatArrayOf(0f, 0f, 1f)
        val right = SphereTargetBuilder.normalize(SphereTargetBuilder.cross(c, upRef))
        val up = SphereTargetBuilder.normalize(SphereTargetBuilder.cross(right, c))
        val hw = tan(hFovRad / 2f) * frac
        val hh = tan(vFovRad / 2f) * frac

        val corners = listOf(
            Pair(-1f to -1f, 0f to 0f),
            Pair(1f to -1f, 1f to 0f),
            Pair(-1f to 1f, 0f to 1f),
            Pair(1f to 1f, 1f to 1f)
        )

        val out = FloatArray(20)
        var idx = 0
        for ((s, uv) in corners) {
            val sx = s.first
            val sy = s.second
            out[idx++] = c[0] + right[0] * hw * sx + up[0] * hh * sy
            out[idx++] = c[1] + right[1] * hw * sx + up[1] * hh * sy
            out[idx++] = c[2] + right[2] * hw * sx + up[2] * hh * sy
            out[idx++] = uv.first
            out[idx++] = uv.second
        }
        return out
    }

    private fun buildWireframeSphereLines() {
        val pts = mutableListOf<Float>()
        val radius = 1.2f
        val latBands = listOf(-45f, 0f, 45f)
        val segments = 48

        for (lat in latBands) {
            val p = Math.toRadians(lat.toDouble())
            val z = (radius * sin(p)).toFloat()
            val r = (radius * cos(p)).toFloat()
            for (i in 0 until segments) {
                val a0 = (2.0 * Math.PI * i / segments)
                val a1 = (2.0 * Math.PI * (i + 1) / segments)
                pts += (r * sin(a0)).toFloat(); pts += (r * cos(a0)).toFloat(); pts += z
                pts += (r * sin(a1)).toFloat(); pts += (r * cos(a1)).toFloat(); pts += z
            }
        }

        // Longitude meridians every 45°
        for (deg in 0 until 360 step 45) {
            val y = Math.toRadians(deg.toDouble())
            for (i in 0 until 24) {
                val p0 = -Math.PI / 2 + (Math.PI * i / 24.0)
                val p1 = -Math.PI / 2 + (Math.PI * (i + 1) / 24.0)
                pts += (radius * cos(p0) * sin(y)).toFloat()
                pts += (radius * cos(p0) * cos(y)).toFloat()
                pts += (radius * sin(p0)).toFloat()

                pts += (radius * cos(p1) * sin(y)).toFloat()
                pts += (radius * cos(p1) * cos(y)).toFloat()
                pts += (radius * sin(p1)).toFloat()
            }
        }

        sphereLineVertexCount = pts.size / 3
        sphereLineBuffer = ByteBuffer.allocateDirect(pts.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(pts.toFloatArray())
                position(0)
            }
    }

    private fun createProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        return program
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        return shader
    }

    companion object {
        private const val TILE_VERTEX_SHADER = """
            uniform mat4 uMvpMatrix;
            attribute vec4 aPosition;
            attribute vec2 aUv;
            varying vec2 vUv;
            void main() {
                vUv = aUv;
                gl_Position = uMvpMatrix * aPosition;
            }
        """

        // Exact Section 23.2 Step 5 fragment shader for circular hole discard
        private const val TILE_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vUv;
            uniform vec4 uColor;
            uniform float uAspect;
            uniform float uHole;
            void main() {
                vec2 p = (vUv - 0.5) * vec2(uAspect, 1.0);
                if (length(p) < uHole) discard;
                gl_FragColor = uColor;
            }
        """

        private const val LINE_VERTEX_SHADER = """
            uniform mat4 uMvpMatrix;
            attribute vec4 aPosition;
            void main() {
                gl_Position = uMvpMatrix * aPosition;
            }
        """

        private const val LINE_FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uColor;
            void main() {
                gl_FragColor = uColor;
            }
        """
    }
}
