package be.appkers.example.opengl

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10


class OpenGL3Activity: AppCompatActivity(),
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


    // endregion Variables
    // region LifeCycle
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        detector = ScaleGestureDetector(this, this)

        view = findViewById<View>(R.id.surface) as GLSurfaceView
        view!!.setOnTouchListener(this)
        view!!.preserveEGLContextOnPause = true
        view!!.setEGLContextClientVersion(2)
        view!!.setRenderer(this)
        view!!.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
    }


    override fun onResume() {
        super.onResume()
        view!!.onResume()
    }

    override fun onPause() {
        super.onPause()
        view!!.onPause()
    }

    // endregion LifeCycle

    // region GLSurfaceView.Renderer
    var vbo = 0
    var vao = 0
    var ebo = 0

    override fun onSurfaceCreated(gl10: GL10?, eglConfig: EGLConfig?) {
        // A little bit of initialization
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        Matrix.setRotateM(rotationMatrix, 0, 0f, 0f, 0f, 1.0f)

        // First, we load the picture into a texture that OpenGL will be able to use
        val bitmap = loadBitmapFromAssets()
        val texture = createFBOTexture(bitmap.width, bitmap.height)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLUtils.texSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, bitmap)
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

        // 1080, 2400
        val vertices = floatArrayOf(
            // position         // texture coords
                 0f,   0f, 1f,   0f, 0f,   // bottom left
               282f,   0f, 1f,   1f, 0f,   // bottom right
                 0f, 333f, 1f,   0f, 1f,   // top left
               282f, 333f, 1f,   1f, 1f,   // top right
        )

        val verticesBuffer: FloatBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)

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
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, verticesBuffer, GLES30.GL_STATIC_DRAW)

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ebo)
        indicesBuffer.position(0)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 4, indicesBuffer, GLES30.GL_STATIC_DRAW)

        // position attribute
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 5 * 4, 0)
        GLES30.glEnableVertexAttribArray(0)
        // texture coord attribute
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 5 * 4, 3 * 4)
        GLES30.glEnableVertexAttribArray(1)

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
        val ratio = width.toFloat() / height
//        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 3f, 7f)
//        Matrix.frustumM(projectionMatrix, 0, 0f, width.toFloat(), 0f, height.toFloat(), 3f, 7f)
        Matrix.orthoM(projectionMatrix, 0, 0f, width.toFloat(), 0f, height.toFloat(), 3f, 7f)
//        Matrix.perspectiveM()

        // Since we requested our OpenGL thread to only render when dirty, we have to tell it to.
        view!!.requestRender()
    }

    override fun onDrawFrame(gl10: GL10?) {
        // We have setup that the background color will be black with GLES30.glClearColor in
        // onSurfaceCreated, now is the time to ask OpenGL to clear the screen with this color
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        Matrix.setIdentityM(mvpMatrix, 0)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0,
            1080 / 2f - 282 / 2f,
            2400 / 2f - 333 / 2,
            0f
        )

        Matrix.multiplyMM(mvpMatrix, 0, modelMatrix, 0, mvpMatrix, 0)

        // Using matrices, we set the camera at the center, advanced of 7 looking to the center back
        // of -1
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 7f, 0f, 0f, -1f, 0f, 1f, 0f)
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, mvpMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0)

//         We combine the scene setup we have done in onSurfaceChanged with the camera setup
//        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
        // We combile that with the applied rotation
//        Matrix.multiplyMM(mvpMatrix, 0, mvpMatrix, 0, rotationMatrix, 0)
        // Finally, we apply the scale to our Matrix
//        Matrix.scaleM(mvpMatrix, 0, scale, scale, scale)
        // We attach the float array containing our Matrix to the correct handle
        GLES30.glUniformMatrix4fv(uMVPMatrix, 1, false, mvpMatrix, 0)

        GLES30.glBindVertexArray(vao)

        // We draw our square which will represent our logo
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }


    // endregion GLSurfaceView.Renderer
    // region Listener
    private var previousX = 0f  // region Listener
    private var previousY = 0f
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View?, motionEvent: MotionEvent): Boolean {
        detector!!.onTouchEvent(motionEvent)
        if (motionEvent.pointerCount == 1) {
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    previousX = motionEvent.x
                    previousY = motionEvent.y
                }

                MotionEvent.ACTION_MOVE -> {
                    if (previousX != motionEvent.x) {
//                        Matrix.rotateM(rotationMatrix, 0, motionEvent.x - previousX, 0f, 1f, 0f)
                    }
                    if (previousY != motionEvent.y) {
//                        Matrix.rotateM(rotationMatrix, 0, motionEvent.y - previousY, 1f, 0f, 0f)
                    }
                    this.view!!.requestRender()
                    previousX = motionEvent.x
                    previousY = motionEvent.y
                }
            }
        }

        return true
    }

    override fun onScale(scaleGestureDetector: ScaleGestureDetector): Boolean {
        if (scaleGestureDetector.scaleFactor != 0f) {
            scale *= scaleGestureDetector.scaleFactor
            view!!.requestRender()
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
}