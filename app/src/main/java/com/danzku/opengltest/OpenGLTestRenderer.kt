package com.danzku.opengltest

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class OpenGLTestRenderer(private val context: Context) : GLSurfaceView.Renderer {
    private var width = 1
    private var height = 1
    private var framebuffer = 0
    private var colorTexture = 0
    private var depthTexture = 0
    
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        report = StringBuilder(baseReport())
        writeReport(baseReport())
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = width
        this.height = height
        runDiagnosticsV3()
    }

    override fun onDrawFrame(gl: GL10?) {
        if (framebuffer != 0) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)
            GLES30.glViewport(0, 0, width, height)
            GLES30.glEnable(GLES30.GL_DEPTH_TEST)
            GLES30.glClearDepthf(1f)
            GLES30.glClearColor(0.08f, 0.10f, 0.14f, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        }
        GLES30.glViewport(0, 0, width, height)
        GLES30.glClearColor(0.03f, 0.03f, 0.03f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
    }

    private fun runDiagnosticsV3() {
        deleteObjects()

        val fb = IntArray(1)
        val color = IntArray(1)
        val depth = IntArray(1)

        GLES30.glGenFramebuffers(1, fb, 0)
        framebuffer = fb[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)

        GLES30.glGenTextures(1, color, 0)
        colorTexture = color[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, colorTexture)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8,
            width, height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
        )
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, colorTexture, 0
        )

        GLES30.glGenTextures(1, depth, 0)
        depthTexture = depth[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, depthTexture)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_DEPTH_COMPONENT24,
            width, height, 0, GLES30.GL_DEPTH_COMPONENT, GLES30.GL_UNSIGNED_INT, null
        )
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT,
            GLES30.GL_TEXTURE_2D, depthTexture, 0
        )

        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        val colorBits = IntArray(4)
        GLES30.glGetIntegerv(GLES30.GL_RED_BITS, colorBits, 0)

        val readPixel = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        val depthPixel = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        GLES30.glReadBuffer(GLES30.GL_COLOR_ATTACHMENT0)
        GLES30.glClearColor(0.25f, 0.50f, 0.75f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glReadPixels(width / 2, height / 2, 1, 1, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, readPixel)
        GLES30.glReadPixels(width / 2, height / 2, 1, 1, GLES30.GL_DEPTH_COMPONENT, GLES30.GL_UNSIGNED_INT, depthPixel)
        val r = readPixel.get(0).toInt() and 0xFF
        val g = readPixel.get(1).toInt() and 0xFF
        val b = readPixel.get(2).toInt() and 0xFF
        val a = readPixel.get(3).toInt() and 0xFF
        val depthValue = depthPixel.int
        val depthNormalized = (depthValue.toDouble() / 4294967295.0).coerceIn(0.0, 1.0)
        val glErrorAfterRead = GLES30.glGetError()

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        val finalReport = StringBuilder(baseReport()).apply {
            appendLine()
            appendLine("=== FRAMEBUFFER V3 ===")
            appendLine("FRAMEBUFFER_STATUS: 0x" + Integer.toHexString(status))
            appendLine("FRAMEBUFFER_COMPLETE: " + (status == GLES30.GL_FRAMEBUFFER_COMPLETE))
            appendLine("COLOR_TEXTURE_SIZE: " + width + "x" + height)
            appendLine("DEPTH_TEXTURE_FORMAT: GL_DEPTH_COMPONENT24")
            appendLine("DEPTH_TEXTURE_ATTACHED: true")
            appendLine("COLOR_READBACK_RGBA: " + r + "," + g + "," + b + "," + a)
            appendLine("DEPTH_READBACK_UINT32: " + (depthValue and 0xFFFFFFFFL))
            appendLine("DEPTH_READBACK_NORMALIZED: " + String.format(Locale.US, "%.8f", depthNormalized))
            appendLine("GL_ERROR_AFTER_READBACK: 0x" + Integer.toHexString(glErrorAfterRead))
            appendLine("DEPTH_TEXTURE_CREATED: " + (depthTexture != 0))
            appendLine("DEPTH_READBACK_SUPPORTED: " + (glErrorAfterRead == GLES30.GL_NO_ERROR))
            appendLine("DIAGNOSTIC_NOTE: this reads only the test framebuffer created by this app.")
        }
        writeReport(finalReport.toString())
    }

    private fun baseReport(): String {
        val ext = GLES30.glGetString(GLES30.GL_EXTENSIONS) ?: ""
        return buildString {
            appendLine("DanzKu OpenGL Test")
            appendLine("DATE: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            appendLine("GL_VERSION: " + (GLES30.glGetString(GLES30.GL_VERSION) ?: "unknown"))
            appendLine("GL_RENDERER: " + (GLES30.glGetString(GLES30.GL_RENDERER) ?: "unknown"))
            appendLine("GL_VENDOR: " + (GLES30.glGetString(GLES30.GL_VENDOR) ?: "unknown"))
            appendLine("GLSL_VERSION: " + (GLES30.glGetString(GLES30.GL_SHADING_LANGUAGE_VERSION) ?: "unknown"))
            appendLine("SURFACE_SIZE: " + width + "x" + height)
            appendLine("HAS_GL_OES_depth_texture: " + ext.contains("GL_OES_depth_texture"))
            appendLine("HAS_GL_EXT_shader_framebuffer_fetch: " + ext.contains("GL_EXT_shader_framebuffer_fetch"))
            appendLine("HAS_GL_ARM_shader_framebuffer_fetch: " + ext.contains("GL_ARM_shader_framebuffer_fetch"))
        }
    }

    private fun writeReport(text: String) {
        runCatching {
            val dir = context.getExternalFilesDir(null) ?: return
            File(dir, "danzku_opengl_report.txt").writeText(text)
        }
    }

    private fun deleteObjects() {
        if (framebuffer != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
        if (colorTexture != 0) GLES30.glDeleteTextures(1, intArrayOf(colorTexture), 0)
        if (depthTexture != 0) GLES30.glDeleteTextures(1, intArrayOf(depthTexture), 0)
        framebuffer = 0
        colorTexture = 0
        depthTexture = 0
    }
}
