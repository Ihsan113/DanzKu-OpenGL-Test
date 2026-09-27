package com.danzku.opengltest

import android.content.Context
import android.opengl.GLES30
import java.nio.FloatBuffer
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
        val depthTexError = GLES30.glGetError()

        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT,
            GLES30.GL_TEXTURE_2D, depthTexture, 0
        )
        val attachError = GLES30.glGetError()

        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        val statusError = GLES30.glGetError()

        GLES30.glViewport(0, 0, width, height)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_ALWAYS)
        GLES30.glClearDepthf(0.625f)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        val clearError = GLES30.glGetError()

        val depthSampleProgram = createDepthSampleProgram()
        var shaderError = GLES30.glGetError()

        var sampledDepth = -1f
        var sampleRgba = intArrayOf(0, 0, 0, 0)
        if (depthSampleProgram != 0 && status == GLES30.GL_FRAMEBUFFER_COMPLETE) {
            GLES30.glUseProgram(depthSampleProgram)
            val attribPos = GLES30.glGetAttribLocation(depthSampleProgram, "aPosition")
            val depthLoc = GLES30.glGetUniformLocation(depthSampleProgram, "uDepth")

            val vertices = floatArrayOf(
                -1f, -1f, 1f, -1f, -1f, 1f,
                 1f, -1f, 1f,  1f, -1f, 1f
            )
            val vertexBuffer = FloatBuffer.allocate(vertices.size)
            vertexBuffer.put(vertices).position(0)

            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, depthTexture)
            GLES30.glUniform1i(depthLoc, 0)
            GLES30.glEnableVertexAttribArray(attribPos)
            GLES30.glVertexAttribPointer(attribPos, 2, GLES30.GL_FLOAT, false, 0, vertexBuffer)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            GLES30.glDisableVertexAttribArray(attribPos)
            shaderError = GLES30.glGetError()

            val samplePixel = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
            GLES30.glReadBuffer(GLES30.GL_COLOR_ATTACHMENT0)
            GLES30.glReadPixels(width / 2, height / 2, 1, 1, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, samplePixel)
            val readError = GLES30.glGetError()

            sampleRgba = intArrayOf(
                samplePixel.get(0).toInt() and 0xFF,
                samplePixel.get(1).toInt() and 0xFF,
                samplePixel.get(2).toInt() and 0xFF,
                samplePixel.get(3).toInt() and 0xFF
            )
            sampledDepth = sampleRgba[0] / 255f
            shaderError = readError
            GLES30.glUseProgram(0)
        }

        GLES30.glDeleteProgram(depthSampleProgram)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        val finalReport = StringBuilder(baseReport()).apply {
            appendLine()
            appendLine("=== FRAMEBUFFER V4 ===")
            appendLine("FRAMEBUFFER_STATUS: 0x" + Integer.toHexString(status))
            appendLine("FRAMEBUFFER_COMPLETE: " + (status == GLES30.GL_FRAMEBUFFER_COMPLETE))
            appendLine("COLOR_TEXTURE_SIZE: " + width + "x" + height)
            appendLine("DEPTH_TEXTURE_FORMAT: GL_DEPTH_COMPONENT24")
            appendLine("DEPTH_TEXTURE_CREATED: " + (depthTexture != 0))
            appendLine("DEPTH_TEXIMAGE_GL_ERROR: 0x" + Integer.toHexString(depthTexError))
            appendLine("DEPTH_ATTACH_GL_ERROR: 0x" + Integer.toHexString(attachError))
            appendLine("FRAMEBUFFER_STATUS_GL_ERROR: 0x" + Integer.toHexString(statusError))
            appendLine("DEPTH_CLEAR_GL_ERROR: 0x" + Integer.toHexString(clearError))
            appendLine("DEPTH_SHADER_GL_ERROR: 0x" + Integer.toHexString(shaderError))
            appendLine("DEPTH_SHADER_SAMPLE_RGBA: " + sampleRgba.joinToString(","))
            appendLine("DEPTH_SHADER_SAMPLE_NORMALIZED: " + String.format(Locale.US, "%.6f", sampledDepth))
            appendLine("DEPTH_TEXTURE_SAMPLE_SUPPORTED: " + (depthSampleProgram != 0 && shaderError == GLES30.GL_NO_ERROR && sampleRgba[3] != 0))
            appendLine("DIAGNOSTIC_NOTE: V4 samples the app-created depth texture through a shader into the app-created color attachment; it does not expose another app's framebuffer.")
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

        val vs = compile(GLES30.GL_VERTEX_SHADER, vertexSource)
        if (vs == 0) return 0
        val fs = compile(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        if (fs == 0) {
            GLES30.glDeleteShader(vs)
            return 0
        }

        val program = GLES30.glCreateProgram()
        if (program == 0) {
            GLES30.glDeleteShader(vs)
            GLES30.glDeleteShader(fs)
            return 0
        }
        GLES30.glAttachShader(program, vs)
        GLES30.glAttachShader(program, fs)
        GLES30.glLinkProgram(program)
        val linked = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linked, 0)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        if (linked[0] == 0) {
            GLES30.glDeleteProgram(program)
            return 0
        }
        return program
    }


