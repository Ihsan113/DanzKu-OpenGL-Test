package com.danzku.opengltest

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class OpenGLTestRenderer(private val context: Context) : GLSurfaceView.Renderer {
    private var width = 1
    private var height = 1
    private var framebuffer = 0
    private var colorTexture = 0
    private var depthTexture = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        writeReport(baseReport())
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = width
        this.height = height
        runDiagnosticsV4()
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, width, height)
        GLES30.glClearColor(0.03f, 0.03f, 0.03f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
    }

    private fun runDiagnosticsV4() {
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
        val colorTexError = GLES30.glGetError()
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, colorTexture, 0
        )
        val colorAttachError = GLES30.glGetError()

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
        val depthTexError = GLES30.glGetError()

        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT,
            GLES30.GL_TEXTURE_2D, depthTexture, 0
        )
        val depthAttachError = GLES30.glGetError()

        val framebufferStatus = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        val framebufferStatusError = GLES30.glGetError()

        GLES30.glViewport(0, 0, width, height)
        GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_ALWAYS)
        GLES30.glClearDepthf(0.625f)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        val clearError = GLES30.glGetError()

        val program = createDepthSampleProgram()
        val programCreateError = GLES30.glGetError()

        var sampledDepth = -1f
        var sampleRgba = intArrayOf(0, 0, 0, 0)
        var drawError = GLES30.GL_NO_ERROR
        var sampleReadError = GLES30.GL_NO_ERROR

        if (program != 0 && framebufferStatus == GLES30.GL_FRAMEBUFFER_COMPLETE) {
            GLES30.glUseProgram(program)

            val attribPos = GLES30.glGetAttribLocation(program, "aPosition")
            val depthLoc = GLES30.glGetUniformLocation(program, "uDepth")

            val vertices = floatArrayOf(
                -1f, -1f,
                 1f, -1f,
                -1f,  1f,
                 1f,  1f
            )
            val vertexBuffer = ByteBuffer
                .allocateDirect(vertices.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
            vertexBuffer.put(vertices).position(0)

            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, depthTexture)
            GLES30.glUniform1i(depthLoc, 0)

            GLES30.glEnableVertexAttribArray(attribPos)
            GLES30.glVertexAttribPointer(
                attribPos, 2, GLES30.GL_FLOAT, false, 0, vertexBuffer
            )
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            GLES30.glDisableVertexAttribArray(attribPos)

            drawError = GLES30.glGetError()

            val samplePixel = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
            GLES30.glReadBuffer(GLES30.GL_COLOR_ATTACHMENT0)
            GLES30.glReadPixels(
                width / 2, height / 2, 1, 1,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, samplePixel
            )
            sampleReadError = GLES30.glGetError()

            sampleRgba = intArrayOf(
                samplePixel.get(0).toInt() and 0xFF,
                samplePixel.get(1).toInt() and 0xFF,
                samplePixel.get(2).toInt() and 0xFF,
                samplePixel.get(3).toInt() and 0xFF
            )
            sampledDepth = sampleRgba[0] / 255f

            GLES30.glUseProgram(0)
        }

        if (program != 0) {
            GLES30.glDeleteProgram(program)
        }

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        val finalReport = StringBuilder(baseReport()).apply {
            appendLine()
            appendLine("=== FRAMEBUFFER V4 ===")
            appendLine("FRAMEBUFFER_STATUS: 0x" + Integer.toHexString(framebufferStatus))
            appendLine("FRAMEBUFFER_COMPLETE: " + (framebufferStatus == GLES30.GL_FRAMEBUFFER_COMPLETE))
            appendLine("COLOR_TEXTURE_SIZE: " + width + "x" + height)
            appendLine("DEPTH_TEXTURE_FORMAT: GL_DEPTH_COMPONENT24")
            appendLine("DEPTH_TEXTURE_CREATED: " + (depthTexture != 0))
            appendLine("COLOR_TEXIMAGE_GL_ERROR: 0x" + Integer.toHexString(colorTexError))
            appendLine("COLOR_ATTACH_GL_ERROR: 0x" + Integer.toHexString(colorAttachError))
            appendLine("DEPTH_TEXIMAGE_GL_ERROR: 0x" + Integer.toHexString(depthTexError))
            appendLine("DEPTH_ATTACH_GL_ERROR: 0x" + Integer.toHexString(depthAttachError))
            appendLine("FRAMEBUFFER_STATUS_GL_ERROR: 0x" + Integer.toHexString(framebufferStatusError))
            appendLine("DEPTH_CLEAR_GL_ERROR: 0x" + Integer.toHexString(clearError))
            appendLine("DEPTH_SHADER_PROGRAM: " + (program != 0))
            appendLine("DEPTH_SHADER_CREATE_GL_ERROR: 0x" + Integer.toHexString(programCreateError))
            appendLine("DEPTH_SHADER_DRAW_GL_ERROR: 0x" + Integer.toHexString(drawError))
            appendLine("DEPTH_SHADER_READ_GL_ERROR: 0x" + Integer.toHexString(sampleReadError))
            appendLine("DEPTH_SHADER_SAMPLE_RGBA: " + sampleRgba.joinToString(","))
            appendLine("DEPTH_SHADER_SAMPLE_NORMALIZED: " + String.format(Locale.US, "%.6f", sampledDepth))
            appendLine(
                "DEPTH_TEXTURE_SAMPLE_SUPPORTED: " +
                    (program != 0 &&
                        framebufferStatus == GLES30.GL_FRAMEBUFFER_COMPLETE &&
                        drawError == GLES30.GL_NO_ERROR &&
                        sampleReadError == GLES30.GL_NO_ERROR &&
                        sampleRgba[3] == 255)
            )
            appendLine("DIAGNOSTIC_NOTE: V4 samples only the app-created depth texture through a shader into the app-created color attachment; it does not expose another app's framebuffer.")
        }

        writeReport(finalReport.toString())
    }

    private fun createDepthSampleProgram(): Int {
        val vertexSource = """
            #version 300 es
            in vec2 aPosition;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """.trimIndent()

        val fragmentSource = """
            #version 300 es
            precision highp float;
            uniform sampler2D uDepth;
            out vec4 fragColor;
            void main() {
                float d = texture(uDepth, vec2(0.5, 0.5)).r;
                fragColor = vec4(d, d, d, 1.0);
            }
        """.trimIndent()

        fun compile(type: Int, source: String): Int {
            val shader = GLES30.glCreateShader(type)
            if (shader == 0) return 0

            GLES30.glShaderSource(shader, source)
            GLES30.glCompileShader(shader)

            val compiled = IntArray(1)
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                GLES30.glDeleteShader(shader)
                return 0
            }
            return shader
        }

        val vertexShader = compile(GLES30.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) return 0

        val fragmentShader = compile(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        if (fragmentShader == 0) {
            GLES30.glDeleteShader(vertexShader)
            return 0
        }

        val program = GLES30.glCreateProgram()
        if (program == 0) {
            GLES30.glDeleteShader(vertexShader)
            GLES30.glDeleteShader(fragmentShader)
            return 0
        }

        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)

        val linked = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linked, 0)

        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)

        if (linked[0] == 0) {
            GLES30.glDeleteProgram(program)
            return 0
        }

        return program
    }

    private fun baseReport(): String {
        val ext = GLES30.glGetString(GLES30.GL_EXTENSIONS) ?: ""
        return buildString {
            appendLine("DanzKu OpenGL Test")
            appendLine(
                "DATE: " +
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            )
            appendLine("GL_VERSION: " + (GLES30.glGetString(GLES30.GL_VERSION) ?: "unknown"))
            appendLine("GL_RENDERER: " + (GLES30.glGetString(GLES30.GL_RENDERER) ?: "unknown"))
            appendLine("GL_VENDOR: " + (GLES30.glGetString(GLES30.GL_VENDOR) ?: "unknown"))
            appendLine(
                "GLSL_VERSION: " +
                    (GLES30.glGetString(GLES30.GL_SHADING_LANGUAGE_VERSION) ?: "unknown")
            )
            appendLine("SURFACE_SIZE: " + width + "x" + height)
            appendLine(
                "HAS_GL_OES_depth_texture: " +
                    ext.contains("GL_OES_depth_texture")
            )
            appendLine(
                "HAS_GL_EXT_shader_framebuffer_fetch: " +
                    ext.contains("GL_EXT_shader_framebuffer_fetch")
            )
            appendLine(
                "HAS_GL_ARM_shader_framebuffer_fetch: " +
                    ext.contains("GL_ARM_shader_framebuffer_fetch")
            )
        }
    }

    private fun writeReport(text: String) {
        runCatching {
            val dir = context.getExternalFilesDir(null) ?: return
            File(dir, "danzku_opengl_report.txt").writeText(text)
        }
    }

    private fun deleteObjects() {
        if (framebuffer != 0) {
            GLES30.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
        }
        if (colorTexture != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(colorTexture), 0)
        }
        if (depthTexture != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(depthTexture), 0)
        }
        framebuffer = 0
        colorTexture = 0
        depthTexture = 0
    }
}
