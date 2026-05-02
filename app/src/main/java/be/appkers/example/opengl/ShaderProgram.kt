package be.appkers.example.opengl

import android.opengl.GLES30

class ShaderProgram(
    vertSource: String,
    fragSource: String,
) {
    val iProgId: Int

    init {
        val link = IntArray(1)

        // Then, we load the shaders into a program
        val iVShader = loadShader(vertSource, GLES30.GL_VERTEX_SHADER)
        val iFShader = loadShader(fragSource, GLES30.GL_FRAGMENT_SHADER)

        iProgId = GLES30.glCreateProgram()
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
    }
}