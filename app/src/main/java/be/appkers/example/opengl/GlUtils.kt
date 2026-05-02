package be.appkers.example.opengl

import android.content.Context
import android.opengl.GLES30
import java.io.InputStreamReader

fun createFBOTexture(width: Int, height: Int): Int {
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

fun createTexture(width: Int, height: Int): Int {
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


fun loadShader(strSource: String, iType: Int): Int {
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


fun loadShader(context: Context, filePath: String, shaderType: Int): Int {
    // 1. Read shader source from assets
    val shaderSource = context.assets.open(filePath).use { inputStream ->
        InputStreamReader(inputStream).readText()
    }

    // 2. Create and compile the shader
    return loadShader(shaderSource, shaderType)
}

fun checkError(contextToCheck: String) {
    val error = GLES30.glGetError()
    if (error != GLES30.GL_NO_ERROR) {
        throw IllegalStateException("$contextToCheck is failed with status code $error")
    }
}
