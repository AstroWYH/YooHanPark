package com.example.yoohanpark;

import android.content.Context;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.opengles.GL10;

import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_FRAGMENT_SHADER;
import static android.opengl.GLES20.GL_TRIANGLE_STRIP;
import static android.opengl.GLES20.GL_VERTEX_SHADER;
import static android.opengl.GLES20.glActiveTexture;
import static android.opengl.GLES20.glAttachShader;
import static android.opengl.GLES20.glBindTexture;
import static android.opengl.GLES20.glCompileShader;
import static android.opengl.GLES20.glCreateProgram;
import static android.opengl.GLES20.glCreateShader;
import static android.opengl.GLES20.glDrawArrays;
import static android.opengl.GLES20.glEnableVertexAttribArray;
import static android.opengl.GLES20.glGetError;
import static android.opengl.GLES20.glLinkProgram;
import static android.opengl.GLES20.glShaderSource;
import static android.opengl.GLES20.glUniform1i;
import static android.opengl.GLES20.glUniformMatrix4fv;
import static android.opengl.GLES20.glUseProgram;
import static android.opengl.GLES20.glVertexAttribPointer;

public class GrayFilter {
    private static final String TAG = "GrayFilter";
    private FloatBuffer buffer_0_;
    private FloatBuffer buffer_90_;
    private FloatBuffer buffer_180_;
    private FloatBuffer buffer_270_;
    private int oes_texture_id_ = -1;
    private int vertex_shader_ = -1;
    private int fragment_shader_ = -1;
    private int shader_program_ = -1;
    private int a_position_location_ = -1;
    private int a_texture_coord_location_ = -1;
    private int u_texture_matrix_location_ = -1;
    private int u_texture_sampler_location_ = -1;
    private int rotation_;

    private static final float[] vertex_data_0_ = {
            -1f, 1f, 0f, 0f,
            -1f, -1f, 0f, 1f,
            1f, 1f, 1f, 0f,
            1f, -1f, 1f, 1f,
    };

    private static final float[] vertex_data_90_ = {
            -1f, 1f, 0f, 1f,
            -1f, -1f, 1f, 1f,
            1f, 1f, 0f, 0f,
            1f, -1f, 1f, 0f,
    };
    private static final float[] vertex_data_180_ = {
            -1f, 1f, 1f, 1f,
            -1f, -1f, 1f, 0f,
            1f, 1f, 0f, 1f,
            1f, -1f, 0f, 0f,
    };
    private static final float[] vertex_data_270_ = {
            -1f, 1f, 1f, 1f,
            -1f, -1f, 0f, 1f,
            1f, 1f, 1f, 0f,
            1f, -1f, 0f, 0f,
    };

    private float[] transform_matrix_ = new float[16];

    public GrayFilter(Context context) {
        oes_texture_id_ = createOESTextureObject();

        transform_matrix_[0] = 1;
        transform_matrix_[5] = 1;
        transform_matrix_[11] = 1;
        transform_matrix_[15] = 1;

        buffer_0_ = ByteBuffer.allocateDirect(vertex_data_0_.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        buffer_0_.put(vertex_data_0_, 0, vertex_data_0_.length).position(0);

        buffer_90_ = ByteBuffer.allocateDirect(vertex_data_90_.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        buffer_90_.put(vertex_data_90_, 0, vertex_data_90_.length).position(0);

        buffer_180_ = ByteBuffer.allocateDirect(vertex_data_180_.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        buffer_180_.put(vertex_data_180_, 0, vertex_data_180_.length).position(0);

        buffer_270_ = ByteBuffer.allocateDirect(vertex_data_270_.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        buffer_270_.put(vertex_data_270_, 0, vertex_data_270_.length).position(0);


        vertex_shader_ = loadShader(GL_VERTEX_SHADER, readShaderFromResource(context, R.raw.base_vert));
        if (vertex_shader_ < 0) {
            return;
        }
        fragment_shader_ = loadShader(GL_FRAGMENT_SHADER, readShaderFromResource(context, R.raw.base_frag));
        if (fragment_shader_ < 0) {
            return;
        }
        shader_program_ = linkProgram(vertex_shader_, fragment_shader_);
        if (shader_program_ > 0) {
            initFilter();
        }
    }

    protected void initFilter() {
        if (shader_program_ > 0) {
            a_position_location_ = GLES20.glGetAttribLocation(shader_program_, "aPosition");
            a_texture_coord_location_ = GLES20.glGetAttribLocation(shader_program_, "aTextureCoordinate");
            u_texture_matrix_location_ = GLES20.glGetUniformLocation(shader_program_, "uTextureMatrix");
            u_texture_sampler_location_ = GLES20.glGetUniformLocation(shader_program_, "uTextureSampler");
        }
    }

    public int loadShader(int type, String shader_source) {
        int shader_id = glCreateShader(type);
        if (shader_id == 0) {
            throw new RuntimeException("Create Shader Failed!" + glGetError());
        }
        glShaderSource(shader_id, shader_source);
        glCompileShader(shader_id);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader_id, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e(TAG, "Could not compile shader_id " + ((type == GL_VERTEX_SHADER) ? "Vertex" : "Fragment") + ":");
            Log.e(TAG, GLES20.glGetShaderInfoLog(shader_id));
            GLES20.glDeleteShader(shader_id);
            shader_id = -1;
        }
        return shader_id;
    }

    public int linkProgram(int vert_id, int frag_id) {
        int program_id = glCreateProgram();
        if (program_id == 0) {
            throw new RuntimeException("Create Program Failed!" + glGetError());
        }
        glAttachShader(program_id, vert_id);
        glAttachShader(program_id, frag_id);
        glLinkProgram(program_id);

        int[] link = new int[1];
        GLES20.glGetProgramiv(program_id, GLES20.GL_LINK_STATUS, link, 0);
        if (link[0] <= 0) {
            Log.d(TAG, "Linking Program Failed");
            return 0;
        }
        int[] link_status = new int[1];
        GLES20.glGetProgramiv(program_id, GLES20.GL_LINK_STATUS, link_status, 0);
        if (link_status[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Could not link program_id in " + getClass().getCanonicalName() + ": ");
            Log.e(TAG, GLES20.glGetProgramInfoLog(program_id));
            GLES20.glDeleteShader(vert_id);
            GLES20.glDeleteShader(frag_id);
            program_id = -1;
        }
        return program_id;
    }

    public int getOESTextureId() {
        return oes_texture_id_;
    }

    public void setRotation(int rotation) {
        rotation_ = rotation;
    }

    public void onDrawFrame(GL10 gl) {
        glUseProgram(shader_program_);

        glActiveTexture(GLES20.GL_TEXTURE0);
        glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oes_texture_id_);
        glUniform1i(u_texture_sampler_location_, 0);

        glUniformMatrix4fv(u_texture_matrix_location_, 1, false, transform_matrix_, 0);

        FloatBuffer buffer = buffer_0_;
        if (rotation_ == 90) {
            buffer = buffer_90_;
        } else if (rotation_ == 180) {
            buffer = buffer_180_;
        } else if (rotation_ == 270) {
            buffer = buffer_270_;
        }
        if (buffer != null) {
            buffer.position(0);
            glEnableVertexAttribArray(a_position_location_);
            glVertexAttribPointer(a_position_location_, 2, GL_FLOAT, false, 4 * 4, buffer);

            buffer.position(2);
            glEnableVertexAttribArray(a_texture_coord_location_);
            glVertexAttribPointer(a_texture_coord_location_, 2, GL_FLOAT, false, 4 * 4, buffer);

            glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        }
    }

    private int createOESTextureObject() {
        int[] tex_id = new int[1];
        GLES20.glGenTextures(1, tex_id, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex_id[0]);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);

        return tex_id[0];
    }

    public static String readShaderFromResource(Context context, int resource_id) {
        StringBuilder builder = new StringBuilder();
        InputStream is = null;
        InputStreamReader isr = null;
        BufferedReader br = null;
        try {
            is = context.getResources().openRawResource(resource_id);
            isr = new InputStreamReader(is);
            br = new BufferedReader(isr);
            String line;
            while ((line = br.readLine()) != null) {
                builder.append(line + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (is != null) {
                    is.close();
                    is = null;
                }
                if (isr != null) {
                    isr.close();
                    isr = null;
                }
                if (br != null) {
                    br.close();
                    br = null;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return builder.toString();
    }


}
