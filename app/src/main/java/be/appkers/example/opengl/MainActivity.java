package be.appkers.example.opengl;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import org.intellij.lang.annotations.Language;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends AppCompatActivity
        implements GLSurfaceView.Renderer,
        View.OnTouchListener, ScaleGestureDetector.OnScaleGestureListener {

  // region Constants
  public static final String MVP_MATRIX = "uMVPMatrix";
  public static final String POSITION = "vPosition";
  public static final String TEXTURE_COORDINATE = "vTextureCoordinate";
  // endregion

  // region Buffers
  private static final float[] POSITION_MATRIX = {
          -1,-1, 1,  // X1,Y1,Z1
           1,-1, 1,  // X2,Y2,Z2
          -1, 1, 1,  // X3,Y3,Z3
           1, 1, 1,  // X4,Y4,Z4
  };
  private FloatBuffer positionBuffer = ByteBuffer.allocateDirect(POSITION_MATRIX.length * 4)
          .order(ByteOrder.nativeOrder()).asFloatBuffer().put(POSITION_MATRIX);
  private static final float TEXTURE_COORDS[] = {
          0, 1, // X1,Y1
          1, 1, // X2,Y2
          0, 0, // X3,Y3
          1, 0, // X4,Y4
  };
  private FloatBuffer textureCoordsBuffer = ByteBuffer.allocateDirect(TEXTURE_COORDS.length * 4)
          .order(ByteOrder.nativeOrder()).asFloatBuffer().put(TEXTURE_COORDS);
  // endregion Buffers

  // region Shaders

  /*
   * @Language("GLSL") may require you to install a plugin to support GLSL
   * but it's only for code highlighting and is not required
   */

  @Language("GLSL")
  private static final String VERTEX_SHADER = ""+
                  "precision mediump float;" +
                  "uniform mat4 " + MVP_MATRIX + ";" +
                  "attribute vec4 " + POSITION + ";" +
                  "attribute vec4 " + TEXTURE_COORDINATE + ";" +
                  "varying vec2 position;" +
                  "void main(){" +
                  " gl_Position = " + MVP_MATRIX + " * " + POSITION + ";" +
                  " position = " + TEXTURE_COORDINATE + ".xy;" +
                  "}";
  @Language("GLSL")
  private static final String FRAGMENT_SHADER = ""+
                  "precision mediump float;" +
                  "uniform sampler2D uTexture;" +
                  "varying vec2 position;" +
                  "void main() {" +
                  "    gl_FragColor = texture2D(uTexture, position);" +
                  "}";
  // endregion Shaders

  // region Variables
  private GLSurfaceView view;
  private int vPosition;
  private int vTexturePosition;
  private int uMVPMatrix;
  private ScaleGestureDetector detector;
  private float scale = 1;
  private float[] mvpMatrix = new float[16];
  private float[] projectionMatrix = new float[16];
  private float[] viewMatrix = new float[16];
  private float[] rotationMatrix = new float[16];
  // endregion Variables

  // region LifeCycle
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    detector = new ScaleGestureDetector(this, this);

    view = (GLSurfaceView) findViewById(R.id.surface);
    view.setOnTouchListener(this);
    view.setPreserveEGLContextOnPause(true);
    view.setEGLContextClientVersion(2);
    view.setRenderer(this);
    view.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
  }


  @Override
  protected void onResume() {
    super.onResume();
    view.onResume();
  }

  @Override
  protected void onPause() {
    super.onPause();
    view.onPause();
  }
  // endregion LifeCycle

  // region GLSurfaceView.Renderer
  @Override
  public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
    // A little bit of initialization
    GLES20.glClearColor(0f, 0f, 0f, 0f);
    Matrix.setRotateM(rotationMatrix, 0, 0, 0, 0, 1.0f);

    // First, we load the picture into a texture that OpenGL will be able to use
    Bitmap bitmap = loadBitmapFromAssets();
    int texture = createFBOTexture(bitmap.getWidth(), bitmap.getHeight());
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
    GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bitmap);

    // Then, we load the shaders into a program
    int iVShader, iFShader, iProgId;
    int[] link = new int[1];
    iVShader = loadShader(VERTEX_SHADER, GLES20.GL_VERTEX_SHADER);
    iFShader = loadShader(FRAGMENT_SHADER, GLES20.GL_FRAGMENT_SHADER);

    iProgId = GLES20.glCreateProgram();
    GLES20.glAttachShader(iProgId, iVShader);
    GLES20.glAttachShader(iProgId, iFShader);
    GLES20.glLinkProgram(iProgId);

    GLES20.glGetProgramiv(iProgId, GLES20.GL_LINK_STATUS, link, 0);
    if (link[0] <= 0) {
      throw new RuntimeException("Program couldn't be loaded");
    }
    GLES20.glDeleteShader(iVShader);
    GLES20.glDeleteShader(iFShader);
    GLES20.glUseProgram(iProgId);

    // Now that our program is loaded and in use, we'll retrieve the handles of the parameters
    // we pass to our shaders
    vPosition = GLES20.glGetAttribLocation(iProgId, POSITION);
    vTexturePosition = GLES20.glGetAttribLocation(iProgId, TEXTURE_COORDINATE);
    uMVPMatrix = GLES20.glGetUniformLocation(iProgId, MVP_MATRIX);
  }

  @Override
  public void onSurfaceChanged(GL10 gl10, int width, int height) {
    GLES20.glViewport(0, 0, width, height);

    // OpenGL will stretch what we give it into a square. To avoid this, we have to send the ratio
    // information to the VERTEX_SHADER. In our case, we pass this information (with other) in the
    // MVP Matrix as can be seen in the onDrawFrame method.
    float ratio = (float) width / height;
    Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1, 1, 3, 7);

    // Since we requested our OpenGL thread to only render when dirty, we have to tell it to.
    view.requestRender();
  }

  @Override
  public void onDrawFrame(GL10 gl10) {
    // We have setup that the background color will be black with GLES20.glClearColor in
    // onSurfaceCreated, now is the time to ask OpenGL to clear the screen with this color
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

    // Using matrices, we set the camera at the center, advanced of 7 looking to the center back
    // of -1
    Matrix.setLookAtM(viewMatrix, 0, 0, 0, 7, 0, 0, -1, 0, 1, 0);
    // We combine the scene setup we have done in onSurfaceChanged with the camera setup
    Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
    // We combile that with the applied rotation
    Matrix.multiplyMM(mvpMatrix, 0, mvpMatrix, 0, rotationMatrix, 0);
    // Finally, we apply the scale to our Matrix
    Matrix.scaleM(mvpMatrix, 0, scale, scale, scale);
    // We attach the float array containing our Matrix to the correct handle
    GLES20.glUniformMatrix4fv(uMVPMatrix, 1, false, mvpMatrix, 0);

    // We pass the buffer for the position
    positionBuffer.position(0);
    GLES20.glVertexAttribPointer(vPosition, 3, GLES20.GL_FLOAT, false, 0, positionBuffer);
    GLES20.glEnableVertexAttribArray(vPosition);

    // We pass the buffer for the texture position
    textureCoordsBuffer.position(0);
    GLES20.glVertexAttribPointer(vTexturePosition, 2, GLES20.GL_FLOAT, false, 0, textureCoordsBuffer);
    GLES20.glEnableVertexAttribArray(vTexturePosition);

    // We draw our square which will represent our logo
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

    GLES20.glDisableVertexAttribArray(vPosition);
    GLES20.glDisableVertexAttribArray(vTexturePosition);
  }
  // endregion GLSurfaceView.Renderer

  // region Listener
  private float previousX, previousY;
  @Override
  public boolean onTouch(View view, MotionEvent motionEvent) {
    detector.onTouchEvent(motionEvent);
    if (motionEvent.getPointerCount() == 1) {
      switch (motionEvent.getAction()) {
        case MotionEvent.ACTION_DOWN:
          previousX = motionEvent.getX();
          previousY = motionEvent.getY();
          break;
        case MotionEvent.ACTION_MOVE:
          if (previousX != motionEvent.getX()) {
            Matrix.rotateM(rotationMatrix, 0, motionEvent.getX() - previousX, 0, 1, 0);
          }
          if (previousY != motionEvent.getY()) {
            Matrix.rotateM(rotationMatrix, 0, motionEvent.getY() - previousY, 1, 0, 0);
          }
          this.view.requestRender();
          previousX = motionEvent.getX();
          previousY = motionEvent.getY();
          break;
      }
    }

    return true;
  }

  @Override
  public boolean onScale(ScaleGestureDetector scaleGestureDetector) {
    if (scaleGestureDetector.getScaleFactor() != 0) {
      scale *= scaleGestureDetector.getScaleFactor();
      view.requestRender();
    }
    return true;
  }

  @Override
  public boolean onScaleBegin(ScaleGestureDetector scaleGestureDetector) {
    return true;
  }

  @Override
  public void onScaleEnd(ScaleGestureDetector scaleGestureDetector) {

  }
  // endregion

  // region Utils
  private Bitmap loadBitmapFromAssets() {
    InputStream is = null;
    try {
      is = getAssets().open("logo.png");
      return BitmapFactory.decodeStream(is);
    } catch (IOException ex) {
      throw new RuntimeException();
    } finally {
      if (is != null) {
        try {
          is.close();
        } catch (IOException ignored) {
          //
        }
      }
    }
  }

  private int createFBOTexture(int width, int height) {
    int[] temp = new int[1];
    GLES20.glGenFramebuffers(1, temp, 0);
    int handleID = temp[0];
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, handleID);

    int fboTex = createTexture(width, height);
    GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fboTex, 0);

    if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
      throw new IllegalStateException("GL_FRAMEBUFFER status incomplete");
    }

    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    return handleID;
  }

  private int createTexture(int width, int height) {
    int[] mTextureHandles = new int[1];
    GLES20.glGenTextures(1, mTextureHandles, 0);
    int textureID = mTextureHandles[0];
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureID);
    GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
    return textureID;
  }

  private int loadShader(final String strSource, final int iType) {
    int[] compiled = new int[1];
    int iShader = GLES20.glCreateShader(iType);
    GLES20.glShaderSource(iShader, strSource);
    GLES20.glCompileShader(iShader);
    GLES20.glGetShaderiv(iShader, GLES20.GL_COMPILE_STATUS, compiled, 0);
    if (compiled[0] == 0) {
      throw new RuntimeException("Compilation failed : " + GLES20.glGetShaderInfoLog(iShader));
    }
    return iShader;
  }
  // endregion Utils
}
