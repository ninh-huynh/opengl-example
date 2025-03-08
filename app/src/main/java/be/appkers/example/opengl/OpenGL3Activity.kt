package be.appkers.example.opengl

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import be.appkers.example.opengl.databinding.ActivityMainBinding
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10


class OpenGL3Activity: ComponentActivity(),
    GLSurfaceView.Renderer,
    View.OnTouchListener,
    ScaleGestureDetector.OnScaleGestureListener
{
    // region Variables
    private var view: GLSurfaceView? = null
    private var uMVPMatrix = 0
    private var detector: ScaleGestureDetector? = null
    private var scale = 1f
    private val mvpMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val rotationMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private var bitmapWidth = 0
    private var bitmapHeight = 0
    private var surfaceWidth = 0f
    private var surfaceHeight = 0f
    private val bitmapRect = RectF()
    private lateinit var binding: ActivityMainBinding

    private var rectX = 0f
    private var rectY = 0f
    private var rectScale = 1f

    // endregion Variables
    // region LifeCycle
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        detector = ScaleGestureDetector(this, this)


        binding.surface.setOnTouchListener(this)
        binding.surface.preserveEGLContextOnPause = true
        binding.surface.setEGLContextClientVersion(3)
        binding.surface.setRenderer(this)
        binding.surface.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) {v, insets ->

            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                or WindowInsetsCompat.Type.displayCutout()
            )

            v.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = bars.bottom
            )
            WindowInsetsCompat.CONSUMED
        }
    }


    override fun onResume() {
        super.onResume()
        binding.surface.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.surface.onPause()
    }

    // endregion LifeCycle

    // region GLSurfaceView.Renderer
    var vbo = 0
    var vao = 0
    var ebo = 0

    private val usePixelBasedCoordinate = true

    override fun onSurfaceCreated(gl10: GL10?, eglConfig: EGLConfig?) {
        // A little bit of initialization
        GLES30.glClearColor(0f, 1f, 0f, 1f)
        Matrix.setRotateM(rotationMatrix, 0, 0f, 0f, 0f, 1.0f)

        // First, we load the picture into a texture that OpenGL will be able to use
        val bitmap = loadBitmapFromAssets()
        bitmapRect.set(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        val texture = createTexture(bitmap.width, bitmap.height)
        GLUtils.texSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, bitmap)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        val link = IntArray(1)

        // Then, we load the shaders into a program
        val iVShader = loadShader(this, "shader.vert", GLES30.GL_VERTEX_SHADER)
        val iFShader = loadShader(this, "shader.frag", GLES30.GL_FRAGMENT_SHADER)

        val iProgId = GLES30.glCreateProgram()
        GLES30.glAttachShader(iProgId, iVShader)
        GLES30.glAttachShader(iProgId, iFShader)
        GLES30.glLinkProgram(iProgId)

        GLES30.glGetProgramiv(iProgId, GLES30.GL_LINK_STATUS, link, 0)
        if (link[0] <= 0) {
            throw RuntimeException("Program couldn't be loaded")
        }
        GLES30.glDeleteShader(iVShader)
        GLES30.glDeleteShader(iFShader)
        GLES30.glUseProgram(iProgId)

        // Now that our program is loaded and in use, we'll retrieve the handles of the parameters
        // we pass to our shaders
        uMVPMatrix = GLES30.glGetUniformLocation(iProgId, "uMVPMatrix")
        val uTextureLocation = GLES30.glGetUniformLocation(iProgId, "uTexture")

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        checkError("glBindTexture-$texture")

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + 0)
        checkError("glActiveTexture")

        GLES30.glUniform1i(uTextureLocation, 0)
        checkError("glUniform1i")

        // You can unbind the VAO afterwards so other VAO calls won't accidentally modify this VAO, but this rarely happens. Modifying other
        // VAOs requires a call to glBindVertexArray anyways so we generally don't unbind VAOs (nor VBOs) when it's not directly necessary.
//        GLES30.glBindVertexArray(0)
    }

    override fun onSurfaceChanged(gl10: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        Log.d("OpenGL3Activity", "onSurfaceChanged: $width, $height")   // 1080, 2400

        // OpenGL will stretch what we give it into a square. To avoid this, we have to send the ratio
        // information to the VERTEX_SHADER. In our case, we pass this information (with other) in the
        // MVP Matrix as can be seen in the onDrawFrame method.
        if (usePixelBasedCoordinate) {
//            Matrix.orthoM(projectionMatrix, 0, 0f, width.toFloat(), height.toFloat(), 0f, -1f, 1f)
            // the 0,0 is at bottom left
            Matrix.orthoM(projectionMatrix, 0, 0f, width.toFloat(), 0f, height.toFloat(), -1f, 1f)
        } else {
            val ratio = width.toFloat() / height
            Matrix.orthoM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, -1f, 1f)
            Matrix.setLookAtM(viewMatrix, 0,
                0f, 0f, 3f,
                0f, 0f, 0f,
                0f, 1.0f, 0.0f
            )

        }

        // Since we requested our OpenGL thread to only render when dirty, we have to tell it to.
        binding.surface.requestRender()

        surfaceWidth = width.toFloat()
        surfaceHeight = height.toFloat()

        rectX = surfaceWidth / 2f
        rectY = surfaceHeight / 2f
    }

    private var isSetupVertexBuffer = false

    override fun onDrawFrame(gl10: GL10?) {
        if (isSetupVertexBuffer.not()) {
            setupVertexBuffer()
            isSetupVertexBuffer = true
        }

        // We have setup that the background color will be black with GLES30.glClearColor in
        // onSurfaceCreated, now is the time to ask OpenGL to clear the screen with this color
        GLES30.glClearColor(1f, 1f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        Matrix.setIdentityM(mvpMatrix, 0)
        Matrix.setIdentityM(modelMatrix, 0)

        if (usePixelBasedCoordinate) {

            val time = SystemClock.uptimeMillis() % 4000L
            val angle = 0.090f * time.toInt()
            val scaleX = bitmapRect.width() * rectScale
            val scaleY = bitmapRect.height() * rectScale


            Matrix.translateM(modelMatrix, 0, rectX, rectY, 0f)
            Matrix.rotateM(modelMatrix, 0, angle, 0f, 0f, 1f)
            Matrix.scaleM(modelMatrix, 0, scaleX, scaleY, 1f)

            Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)
        } else {
            projectionMatrix.copyInto(mvpMatrix)
        }

//         We combine the scene setup we have done in onSurfaceChanged with the camera setup
//        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
        // We combile that with the applied rotation
//        Matrix.multiplyMM(mvpMatrix, 0, mvpMatrix, 0, rotationMatrix, 0)
        // We attach the float array containing our Matrix to the correct handle
        GLES30.glUniformMatrix4fv(uMVPMatrix, 1, false, mvpMatrix, 0)
        checkError("glUniformMatrix4fv")

        GLES30.glBindVertexArray(vao)
        checkError("glBindVertexArray")

        // We draw our square which will represent our logo
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        checkError("glDrawArrays")
    }

    private fun setupVertexBuffer() {
        val position = if (usePixelBasedCoordinate) {
            // create a 1x1 square, so that we can apply the correct scale later.
            floatArrayOf(
                -0.5f, -0.5f,   // bottom left
                 0.5f, -0.5f,   // bottom right
                -0.5f, 0.5f,    // top left
                 0.5f, 0.5f,    // top right
            )
        } else {
            floatArrayOf(
                -1f, -1f,       // bottom left
                1f, -1f,       // bottom right
                -1f,  1f,       // top left
                1f,  1f,       // top right
            )
        }

        val texCoords = floatArrayOf(
            0f, 0f,         // bottom left
            1f, 0f,         // bottom right
            0f, 1f,         // top left
            1f, 1f          // top right
        )

        val verticesBuffer: FloatBuffer = ByteBuffer.allocateDirect((position.size + texCoords.size) * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        verticesBuffer.put(position)
        verticesBuffer.put(texCoords)

        val indices = intArrayOf(
            0, 1, 2, 3
        )

        val indicesBuffer: IntBuffer = ByteBuffer.allocateDirect(indices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer()
            .put(indices)

        val tempArr = intArrayOf(0)

        GLES30.glGenVertexArrays(1, tempArr, 0)
        vao = tempArr[0]
        GLES30.glGenBuffers(1, tempArr, 0)
        vbo = tempArr[0]
        GLES30.glGenBuffers(1, tempArr, 0)
        ebo = tempArr[0]

        GLES30.glBindVertexArray(vao)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        verticesBuffer.position(0)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, (position.size + texCoords.size) * 4, verticesBuffer, GLES30.GL_STATIC_DRAW)

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ebo)
        indicesBuffer.position(0)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 4, indicesBuffer, GLES30.GL_STATIC_DRAW)

        // position attribute
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 2 * 4 /*or just simple 0*/, 0)
        checkError("glVertexAttribPointer-0")

        GLES30.glEnableVertexAttribArray(0)
        checkError("glEnableVertexAttribArray-0")

        // texture coord attribute
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 2 * 4 /*or just simple 0*/, 8 * 4)
        checkError("glVertexAttribPointer-1")

        GLES30.glEnableVertexAttribArray(1)
        checkError("glEnableVertexAttribArray-1")
    }


    // endregion GLSurfaceView.Renderer
    // region Listener
    private var previousX = 0f  // region Listener
    private var previousY = 0f
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        detector!!.onTouchEvent(motionEvent)
        if (motionEvent.pointerCount == 1) {
            when (motionEvent.action) {
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_DOWN -> {
                    val inverseY = view.height - motionEvent.y
                    binding.surface.queueEvent {
                        rectX = motionEvent.x
                        rectY = inverseY
                    }
                }
            }
        }

        return true
    }

    override fun onScale(scaleGestureDetector: ScaleGestureDetector): Boolean {
        if (scaleGestureDetector.scaleFactor != 0f) {
            val currentScaleFactor = scaleGestureDetector.scaleFactor
            binding.surface.queueEvent {
                rectScale *= currentScaleFactor
            }
        }
        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean = true

    override fun onScaleEnd(detector: ScaleGestureDetector) = Unit


    // endregion
    // region Utils
    private fun loadBitmapFromAssets(): Bitmap {
        var `is`: InputStream? = null
        try {
            `is` = assets.open("logo.png")
            val orgBitmap = BitmapFactory.decodeStream(`is`)
            val matrix = android.graphics.Matrix()
            matrix.postScale(1f, -1f, orgBitmap.width/2f, orgBitmap.height/2f)
//            return orgBitmap
            return Bitmap.createBitmap(orgBitmap, 0, 0, orgBitmap.width, orgBitmap.height, matrix, true)
        } catch (ex: IOException) {
            throw RuntimeException()
        } finally {
            if (`is` != null) {
                try {
                    `is`.close()
                } catch (ignored: IOException) {
                    //
                }
            }
        }
    }

    private fun createFBOTexture(width: Int, height: Int): Int {
        val temp = IntArray(1)
        GLES30.glGenFramebuffers(1, temp, 0)
        val handleID = temp[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, handleID)

        val fboTex = createTexture(width, height)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER,
            GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D,
            fboTex,
            0
        )

        check(GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE) { "GL_FRAMEBUFFER status incomplete" }

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        return handleID
    }

    private fun createTexture(width: Int, height: Int): Int {
        val mTextureHandles = IntArray(1)
        GLES30.glGenTextures(1, mTextureHandles, 0)
        val textureID = mTextureHandles[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureID)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_RGBA,
            width,
            height,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            null
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_WRAP_S,
            GLES30.GL_CLAMP_TO_EDGE
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_WRAP_T,
            GLES30.GL_CLAMP_TO_EDGE
        )
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        return textureID
    }


    private fun loadShader(strSource: String, iType: Int): Int {
        val compiled = IntArray(1)
        val iShader = GLES30.glCreateShader(iType)
        GLES30.glShaderSource(iShader, strSource)
        GLES30.glCompileShader(iShader)
        GLES30.glGetShaderiv(iShader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            throw RuntimeException("Compilation failed : " + GLES30.glGetShaderInfoLog(iShader))
        }
        return iShader
    } // endregion Utils


    private fun loadShader(context: Context, filePath: String, shaderType: Int): Int {
        // 1. Read shader source from assets
        val shaderSource = context.assets.open(filePath).use { inputStream ->
            InputStreamReader(inputStream).readText()
        }

        // 2. Create and compile the shader
        return loadShader(shaderSource, shaderType)
    }

    private fun checkError(contextToCheck: String) {
        val error = GLES30.glGetError()
        if (error != GLES30.GL_NO_ERROR) {
            throw IllegalStateException("$contextToCheck is failed with status code $error")
        }
    }
}