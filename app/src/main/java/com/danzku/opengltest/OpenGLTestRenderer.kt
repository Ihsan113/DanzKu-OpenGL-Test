package com.danzku.opengltest

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class OpenGLTestRenderer(private val context: Context) : GLSurfaceView.Renderer {
    private var width = 1
    private var height = 1
    private var framebuffer = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        writeReport(baseReport())
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = width
        this.height = height
        framebufferTest()
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)
        GLES30.glViewport(0, 0, width, height)
        GLES30.glClearColor(0.08f, 0.10f, 0.14f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, width, height)
        GLES30.glClearColor(0.03f, 0.03f, 0.03f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
    }

    private fun framebufferTest() {
        val fb = IntArray(1)
        val color = IntArray(1)
        val depth = IntArray(1)

        GLES30.glGenFramebuffers(1, fb, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fb[0])

        GLES30.glGenTextures(1, color, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, color[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, width, height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, color[0], 0)

        GLES30.glGenTextures(1, depth, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, depth[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_DEPTH_COMPONENT24, width, height, 0, GLES30.GL_DEPTH_COMPONENT, GLES30.GL_UNSIGNED_INT, null)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_TEXTURE_2D, depth[0], 0)

        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        framebuffer = fb[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        writeReport(baseReport() + "\nFRAMEBUFFER_STATUS: 0x" + Integer.toHexString(status) +
            "\nFRAMEBUFFER_COMPLETE: " + (status == GLES30.GL_FRAMEBUFFER_COMPLETE))
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
}
