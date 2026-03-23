package com.example.stablecamera.camera

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.example.stablecamera.NativeLib
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class StabilizationRenderer(private val nativeLib: NativeLib) : GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {
    private var surfaceTexture: SurfaceTexture? = null
    private var textureId = -1
    private var program = -1

    private var uSTMatrixLoc = -1
    private var uStabMatrixLoc = -1
    private var aPositionLoc = -1
    private var aTextureCoordLoc = -1

    private val stMatrix = FloatArray(16)
    @Volatile
    private var frameAvailable = false

    var onSurfaceTextureAvailable: ((SurfaceTexture) -> Unit)? = null

    private val vertexShaderCode = """
        attribute vec4 aPosition;
        attribute vec4 aTextureCoord;
        varying vec2 vTextureCoord;
        uniform mat4 uSTMatrix;
        uniform mat4 uStabMatrix;
        void main() {
            gl_Position = uStabMatrix * aPosition;
            vTextureCoord = (uSTMatrix * aTextureCoord).xy;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 vTextureCoord;
        uniform samplerExternalOES sTexture;
        void main() {
            gl_FragColor = texture2D(sTexture, vTextureCoord);
        }
    """.trimIndent()

    private val vertices = floatArrayOf(
        -1.0f, -1.0f, 0f, 0.0f, 0.0f,
         1.0f, -1.0f, 0f, 1.0f, 0.0f,
        -1.0f,  1.0f, 0f, 0.0f, 1.0f,
         1.0f,  1.0f, 0f, 1.0f, 1.0f
    )
    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(vertices).position(0) }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        textureId = createExternalTexture()
        surfaceTexture = SurfaceTexture(textureId).apply {
            setOnFrameAvailableListener(this@StabilizationRenderer)
        }
        program = createProgram(vertexShaderCode, fragmentShaderCode)

        aPositionLoc = GLES20.glGetAttribLocation(program, "aPosition")
        aTextureCoordLoc = GLES20.glGetAttribLocation(program, "aTextureCoord")
        uSTMatrixLoc = GLES20.glGetUniformLocation(program, "uSTMatrix")
        uStabMatrixLoc = GLES20.glGetUniformLocation(program, "uStabMatrix")

        surfaceTexture?.let { onSurfaceTextureAvailable?.invoke(it) }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (surfaceTexture == null) return

        synchronized(this) {
            if (frameAvailable) {
                try {
                    surfaceTexture?.updateTexImage()
                    surfaceTexture?.getTransformMatrix(stMatrix)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                frameAvailable = false
            }
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (program != -1) {
            GLES20.glUseProgram(program)

            vertexBuffer.position(0)
            GLES20.glVertexAttribPointer(aPositionLoc, 3, GLES20.GL_FLOAT, false, 20, vertexBuffer)
            GLES20.glEnableVertexAttribArray(aPositionLoc)

            vertexBuffer.position(3)
            GLES20.glVertexAttribPointer(aTextureCoordLoc, 2, GLES20.GL_FLOAT, false, 20, vertexBuffer)
            GLES20.glEnableVertexAttribArray(aTextureCoordLoc)

            GLES20.glUniformMatrix4fv(uSTMatrixLoc, 1, false, stMatrix, 0)

            // Get stabilization matrix from Rust
            val stabMatrix = nativeLib.getStabilizationMatrix(System.nanoTime())
            GLES20.glUniformMatrix4fv(uStabMatrixLoc, 1, false, stabMatrix, 0)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        synchronized(this) { frameAvailable = true }
    }

    fun release() {
        if (textureId != -1) {
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
            textureId = -1
        }
        surfaceTexture?.release()
        surfaceTexture = null
        if (program != -1) {
            GLES20.glDeleteProgram(program)
            program = -1
        }
    }

    private fun createExternalTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return textures[0]
    }

    private fun createProgram(vSource: String, fSource: String): Int {
        val vShader = loadShader(GLES20.GL_VERTEX_SHADER, vSource)
        val fShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fSource)
        if (vShader == 0 || fShader == 0) return -1

        val p = GLES20.glCreateProgram().apply {
            GLES20.glAttachShader(this, vShader)
            GLES20.glAttachShader(this, fShader)
            GLES20.glLinkProgram(this)
        }

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            GLES20.glDeleteProgram(p)
            return -1
        }
        return p
    }

    private fun loadShader(type: Int, source: String): Int {
        val s = GLES20.glCreateShader(type).apply {
            GLES20.glShaderSource(this, source)
            GLES20.glCompileShader(this)
        }
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            GLES20.glDeleteShader(s)
            return 0
        }
        return s
    }
}
