package com.danzku.opengltest

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.SystemClock
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
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

    private var sceneFramebuffer = 0
    private var sceneColorTexture = 0
    private var sceneDepthTexture = 0
    private var sceneProgram = 0
    private var sceneVao = 0
    private var sceneVbo = 0

    private var temporalHistoryTexture = 0
    private var temporalHistoryFramebuffer = 0
    private var postProgram = 0
    private var postVao = 0
    private var postVbo = 0
    private var historyValid = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        createSceneResources()
        createPostResources()
        writeReport(baseReport())
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = width
        this.height = height
        runDiagnosticsFinal()
        recreateTemporalResources()
        renderScene()
    }

    override fun onDrawFrame(gl: GL10?) {
        renderScene()
    }

    private fun renderScene() {
        if (width <= 0 || height <= 0 || sceneFramebuffer == 0 || sceneProgram == 0) return

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, sceneFramebuffer)
        GLES30.glViewport(0, 0, width, height)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LESS)
        GLES30.glClearColor(0.025f, 0.03f, 0.045f, 1f)
        GLES30.glClearDepthf(1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        GLES30.glUseProgram(sceneProgram)
        GLES30.glUniform1f(
            GLES30.glGetUniformLocation(sceneProgram, "uTime"),
            SystemClock.uptimeMillis() / 1000f
        )
        GLES30.glUniform1f(
            GLES30.glGetUniformLocation(sceneProgram, "uAspect"),
            width.toFloat() / height.coerceAtLeast(1).toFloat()
        )
        GLES30.glBindVertexArray(sceneVao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 36)
        GLES30.glBindVertexArray(0)
        GLES30.glUseProgram(0)

        if (postProgram != 0 && temporalHistoryTexture != 0) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glDisable(GLES30.GL_DEPTH_TEST)
            GLES30.glViewport(0, 0, width, height)
            GLES30.glUseProgram(postProgram)

            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sceneColorTexture)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(postProgram, "uCurrent"), 0)

            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, temporalHistoryTexture)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(postProgram, "uHistory"), 1)

            GLES30.glUniform1f(GLES30.glGetUniformLocation(postProgram, "uBlend"), 0.72f)
            GLES30.glUniform1f(
                GLES30.glGetUniformLocation(postProgram, "uHistoryReady"),
                if (historyValid) 1f else 0f
            )
            GLES30.glUniform1f(
                GLES30.glGetUniformLocation(postProgram, "uTime"),
                SystemClock.uptimeMillis() / 1000f
            )
            GLES30.glUniform2f(
                GLES30.glGetUniformLocation(postProgram, "uTexelSize"),
                1f / width.toFloat(),
                1f / height.toFloat()
            )

            GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sceneDepthTexture)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(postProgram, "uDepth"), 2)

            GLES30.glBindVertexArray(postVao)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            GLES30.glBindVertexArray(0)
            GLES30.glUseProgram(0)

            GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, 0)
            GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, temporalHistoryFramebuffer)
            GLES30.glBlitFramebuffer(
                0, 0, width, height,
                0, 0, width, height,
                GLES30.GL_COLOR_BUFFER_BIT,
                GLES30.GL_NEAREST
            )
            historyValid = true
        } else {
            GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, sceneFramebuffer)
            GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, 0)
            GLES30.glBlitFramebuffer(
                0, 0, width, height,
                0, 0, width, height,
                GLES30.GL_COLOR_BUFFER_BIT,
                GLES30.GL_LINEAR
            )
        }

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    private fun createSceneResources() {
        deleteSceneObjects()

        val vertexSource = """
            #version 300 es
            layout(location = 0) in vec3 aPosition;
            layout(location = 1) in vec3 aColor;

            uniform float uTime;
            uniform float uAspect;

            out vec3 vColor;
            out float vDepth;

            mat3 rotationY(float a) {
                float c = cos(a);
                float s = sin(a);
                return mat3(
                    c, 0.0, -s,
                    0.0, 1.0, 0.0,
                    s, 0.0, c
                );
            }

            void main() {
                vec3 p = aPosition;
                p = rotationY(0.28 * sin(uTime * 0.7) + 0.08 * cos(uTime)) * p;

                p.x += 0.22 * sin(uTime * 0.55);
                p.y += 0.08 * cos(uTime * 0.8);
                p.z -= 2.7;

                float sx = 1.0 / max(uAspect, 0.45);
                p.x *= sx;

                float nearPlane = 0.10;
                float farPlane = 8.0;
                float z = -p.z;
                float ndcDepth = ((farPlane + nearPlane) - (2.0 * nearPlane * farPlane / z)) / (farPlane - nearPlane);
                vDepth = clamp(ndcDepth * 0.5 + 0.5, 0.0, 1.0);

                gl_Position = vec4(p.xy / z, ndcDepth, 1.0);
                vColor = aColor;
            }
        """.trimIndent()

        val fragmentSource = """
            #version 300 es
            precision highp float;

            in vec3 vColor;
            in float vDepth;

            uniform int uDepthMode;

            out vec4 fragColor;

            void main() {
                if (uDepthMode == 1) {
                    float d = 1.0 - vDepth;
                    d = pow(clamp(d, 0.0, 1.0), 0.82);
                    fragColor = vec4(vec3(d), 1.0);
                } else {
                    fragColor = vec4(vColor, 1.0);
                }
            }
        """.trimIndent()

        sceneProgram = createProgram(vertexSource, fragmentSource)
        if (sceneProgram == 0) return

        val vertices = createCubeVertices()
        val buffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buffer.put(vertices).position(0)

        val vao = IntArray(1)
        val vbo = IntArray(1)
        GLES30.glGenVertexArrays(1, vao, 0)
        GLES30.glGenBuffers(1, vbo, 0)
        sceneVao = vao[0]
        sceneVbo = vbo[0]

        GLES30.glBindVertexArray(sceneVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, sceneVbo)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            vertices.size * 4,
            buffer,
            GLES30.GL_STATIC_DRAW
        )
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 6 * 4, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 6 * 4, 3 * 4)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindVertexArray(0)

        val fb = IntArray(1)
        val color = IntArray(1)
        val depth = IntArray(1)
        GLES30.glGenFramebuffers(1, fb, 0)
        sceneFramebuffer = fb[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, sceneFramebuffer)

        GLES30.glGenTextures(1, color, 0)
        sceneColorTexture = color[0]
        configureColorTexture(sceneColorTexture)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8,
            width, height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
        )
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, sceneColorTexture, 0
        )

        GLES30.glGenTextures(1, depth, 0)
        sceneDepthTexture = depth[0]
        configureDepthTexture(sceneDepthTexture)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_DEPTH_COMPONENT24,
            width, height, 0, GLES30.GL_DEPTH_COMPONENT, GLES30.GL_UNSIGNED_INT, null
        )
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT,
            GLES30.GL_TEXTURE_2D, sceneDepthTexture, 0
        )

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    private fun createPostResources() {
        postProgram = createProgram(
            """
                #version 300 es
                layout(location = 0) in vec2 aPosition;
                out vec2 vUv;
                void main() {
                    vUv = aPosition * 0.5 + 0.5;
                    gl_Position = vec4(aPosition, 0.0, 1.0);
                }
            """.trimIndent(),
            """
                #version 300 es
                precision highp float;
                in vec2 vUv;
                uniform sampler2D uCurrent;
                uniform sampler2D uDepth;
                uniform sampler2D uHistory;
                uniform float uBlend;
                uniform float uHistoryReady;
                uniform float uTime;
                uniform vec2 uTexelSize;
                out vec4 fragColor;

                float linearizeDepth(float depth) {
                    const float nearPlane = 0.10;
                    const float farPlane = 8.0;
                    float z = depth * 2.0 - 1.0;
                    return (2.0 * nearPlane * farPlane) /
                        (farPlane + nearPlane - z * (farPlane - nearPlane));
                }

                vec2 syntheticMotion(vec2 uv) {
                    float t = uTime;
                    vec2 motion = vec2(
                        0.0045 * cos(t * 0.55),
                        0.0028 * sin(t * 0.80)
                    );
                    motion += 0.0015 * vec2(
                        sin(uv.y * 10.0 + t),
                        cos(uv.x * 8.0 - t * 0.7)
                    );
                    return motion;
                }

                void main() {
                    vec2 motion = syntheticMotion(vUv);
                    vec2 reprojUv = clamp(vUv - motion, 0.001, 0.999);

                    vec3 current = texture(uCurrent, vUv).rgb;
                    vec3 history = texture(uHistory, reprojUv).rgb;
                    float currentDepth = linearizeDepth(texture(uDepth, vUv).r);
                    float historyDepth = linearizeDepth(texture(uDepth, reprojUv).r);

                    float depthDelta = abs(currentDepth - historyDepth);
                    float depthReject = smoothstep(0.015, 0.12, depthDelta);

                    float centerDepth = texture(uDepth, vUv).r;
                    float dl = texture(uDepth, vUv - vec2(uTexelSize.x, 0.0)).r;
                    float dr = texture(uDepth, vUv + vec2(uTexelSize.x, 0.0)).r;
                    float du = texture(uDepth, vUv + vec2(0.0, uTexelSize.y)).r;
                    float dd = texture(uDepth, vUv - vec2(0.0, uTexelSize.y)).r;
                    float depthEdge = clamp(
                        (abs(centerDepth - dl) + abs(centerDepth - dr) +
                         abs(centerDepth - du) + abs(centerDepth - dd)) * 18.0,
                        0.0, 1.0
                    );

                    float disocclusion = clamp(depthDelta * 9.0 + depthEdge * 0.45, 0.0, 1.0);
                    float validationMask = max(depthReject, disocclusion);
                    float blend = uBlend * uHistoryReady * (1.0 - validationMask);

                    vec3 temporal = mix(current, history, blend);
                    float linearDepthNorm = clamp((currentDepth - 0.35) / 4.8, 0.0, 1.0);
                    float confidence = 1.0 - validationMask;

                    fragColor = vec4(
                        mix(temporal, vec3(linearDepthNorm), 0.18) +
                        vec3(depthEdge * 0.10) +
                        vec3((1.0 - confidence) * 0.04),
                        1.0
                    );
                }
            """.trimIndent()
        )

        val quad = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        val buffer = ByteBuffer.allocateDirect(quad.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buffer.put(quad).position(0)

        val vao = IntArray(1)
        val vbo = IntArray(1)
        GLES30.glGenVertexArrays(1, vao, 0)
        GLES30.glGenBuffers(1, vbo, 0)
        postVao = vao[0]
        postVbo = vbo[0]

        GLES30.glBindVertexArray(postVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, postVbo)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            quad.size * 4,
            buffer,
            GLES30.GL_STATIC_DRAW
        )
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindVertexArray(0)
    }

    private fun recreateTemporalResources() {
        if (temporalHistoryFramebuffer != 0) {
            GLES30.glDeleteFramebuffers(1, intArrayOf(temporalHistoryFramebuffer), 0)
        }
        if (temporalHistoryTexture != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(temporalHistoryTexture), 0)
        }

        val tex = IntArray(1)
        val fb = IntArray(1)
        GLES30.glGenTextures(1, tex, 0)
        GLES30.glGenFramebuffers(1, fb, 0)
        temporalHistoryTexture = tex[0]
        temporalHistoryFramebuffer = fb[0]

        configureColorTexture(temporalHistoryTexture)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_RGBA8,
            width,
            height,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            null
        )

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, temporalHistoryFramebuffer)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER,
            GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D,
            temporalHistoryTexture,
            0
        )
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        historyValid = false
    }

    private fun runDiagnosticsFinal() {
        deleteObjects()

        val fb = IntArray(1)
        val color = IntArray(1)
        val depth = IntArray(1)

        GLES30.glGenFramebuffers(1, fb, 0)
        framebuffer = fb[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)

        GLES30.glGenTextures(1, color, 0)
        colorTexture = color[0]
        configureColorTexture(colorTexture)
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
        configureDepthTexture(depthTexture)
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
        val framebufferStatusName = framebufferStatusName(framebufferStatus)
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

        if (program != 0) GLES30.glDeleteProgram(program)

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        val finalReport = StringBuilder(baseReport()).apply {
            appendLine()
            appendLine("=== FINAL OPENGL DIAGNOSTICS ===")
            appendLine("FRAMEWORK_DIAGNOSTIC_VERSION: 6")
            appendLine("FRAMEBUFFER_STATUS: 0x" + Integer.toHexString(framebufferStatus))
            appendLine("FRAMEBUFFER_STATUS_NAME: " + framebufferStatusName)
            appendLine("FRAMEBUFFER_COMPLETE: " + (framebufferStatus == GLES30.GL_FRAMEBUFFER_COMPLETE))
            appendLine("COLOR_TEXTURE_SIZE: " + width + "x" + height)
            appendLine("DEPTH_TEXTURE_FORMAT: GL_DEPTH_COMPONENT24")
            appendLine("DEPTH_CLEAR_REFERENCE: 0.625000")
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
            appendLine("SCENE_NOTE: V6 renders a synthetic 3D depth scene into an app-created D24 depth attachment; it does not access another app's framebuffer.")
            appendLine("TEMPORAL_NOTE: V7 uses only app-created current/history resources.")
            appendLine("V7_MOTION: synthetic per-frame motion field + history reprojection")
            appendLine("V7_VALIDATION: motion-compensated history path active in renderer.")
            appendLine("V8_VALIDATION: depth rejection + disocclusion mask + depth-edge detection active.")
            appendLine("V8_VALIDATION: depth-aware history rejection + disocclusion detection")
            appendLine("V9_RECONSTRUCTION: integrated current-history-depth-motion temporal reconstruction prototype")
            appendLine("V9_HISTORY: first-frame history rejection fixed; history becomes valid only after first completed reprojection pass.")
            appendLine("V9_RECONSTRUCTION_PATH: current color + linearized depth + synthetic motion + depth validation + temporal history.")
        }

        writeReport(finalReport.toString())
    }

    private fun configureColorTexture(texture: Int) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
    }

    private fun configureDepthTexture(texture: Int) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
    }

    private fun createCubeVertices(): FloatArray {
        fun face(
            a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray, color: FloatArray
        ): FloatArray {
            return floatArrayOf(
                a[0], a[1], a[2], color[0], color[1], color[2],
                b[0], b[1], b[2], color[0], color[1], color[2],
                c[0], c[1], c[2], color[0], color[1], color[2],
                a[0], a[1], a[2], color[0], color[1], color[2],
                c[0], c[1], c[2], color[0], color[1], color[2],
                d[0], d[1], d[2], color[0], color[1], color[2]
            )
        }

        val p = 0.72f
        val front = floatArrayOf(0f, 0.2f, 0.9f)
        val side = floatArrayOf(0.1f, 0.75f, 0.95f)
        val top = floatArrayOf(0.75f, 0.55f, 0.1f)
        val bottom = floatArrayOf(0.2f, 0.85f, 0.45f)
        val back = floatArrayOf(0.7f, 0.25f, 0.85f)

        val faces = mutableListOf<Float>()
        faces.addAll(face(floatArrayOf(-p,-p,p), floatArrayOf(p,-p,p), floatArrayOf(p,p,p), floatArrayOf(-p,p,p), front).asList())
        faces.addAll(face(floatArrayOf(p,-p,p), floatArrayOf(p,-p,-p), floatArrayOf(p,p,-p), floatArrayOf(p,p,p), side).asList())
        faces.addAll(face(floatArrayOf(-p,p,p), floatArrayOf(p,p,p), floatArrayOf(p,p,-p), floatArrayOf(-p,p,-p), top).asList())
        faces.addAll(face(floatArrayOf(-p,-p,-p), floatArrayOf(p,-p,-p), floatArrayOf(p,-p,p), floatArrayOf(-p,-p,p), bottom).asList())
        faces.addAll(face(floatArrayOf(-p,-p,-p), floatArrayOf(-p,p,-p), floatArrayOf(p,p,-p), floatArrayOf(p,-p,-p), back).asList())
        faces.addAll(face(floatArrayOf(-p,-p,p), floatArrayOf(-p,-p,-p), floatArrayOf(-p,p,-p), floatArrayOf(-p,p,p), side).asList())
        return faces.toFloatArray()
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        fun compile(type: Int, source: String): Int {
            val shader = GLES30.glCreateShader(type)
            if (shader == 0) return 0
            GLES30.glShaderSource(shader, source)
            GLES30.glCompileShader(shader)
            val status = IntArray(1)
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) {
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

        return createProgram(vertexSource, fragmentSource)
    }

    private fun framebufferStatusName(status: Int): String = when (status) {
        GLES30.GL_FRAMEBUFFER_COMPLETE -> "GL_FRAMEBUFFER_COMPLETE"
        GLES30.GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT -> "GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT"
        GLES30.GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT -> "GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT"
        GLES30.GL_FRAMEBUFFER_INCOMPLETE_DIMENSIONS -> "GL_FRAMEBUFFER_INCOMPLETE_DIMENSIONS"
        GLES30.GL_FRAMEBUFFER_UNSUPPORTED -> "GL_FRAMEBUFFER_UNSUPPORTED"
        else -> "UNKNOWN"
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

    private fun deleteSceneObjects() {
        if (sceneVao != 0) GLES30.glDeleteVertexArrays(1, intArrayOf(sceneVao), 0)
        if (sceneVbo != 0) GLES30.glDeleteBuffers(1, intArrayOf(sceneVbo), 0)
        if (sceneProgram != 0) GLES30.glDeleteProgram(sceneProgram)
        if (sceneFramebuffer != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(sceneFramebuffer), 0)
        if (sceneColorTexture != 0) GLES30.glDeleteTextures(1, intArrayOf(sceneColorTexture), 0)
        if (sceneDepthTexture != 0) GLES30.glDeleteTextures(1, intArrayOf(sceneDepthTexture), 0)
        if (temporalHistoryFramebuffer != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(temporalHistoryFramebuffer), 0)
        if (temporalHistoryTexture != 0) GLES30.glDeleteTextures(1, intArrayOf(temporalHistoryTexture), 0)
        if (postVao != 0) GLES30.glDeleteVertexArrays(1, intArrayOf(postVao), 0)
        if (postVbo != 0) GLES30.glDeleteBuffers(1, intArrayOf(postVbo), 0)
        if (postProgram != 0) GLES30.glDeleteProgram(postProgram)
        sceneVao = 0
        sceneVbo = 0
        sceneProgram = 0
        sceneFramebuffer = 0
        sceneColorTexture = 0
        sceneDepthTexture = 0
    }
}
