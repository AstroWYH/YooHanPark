package com.example.yoohanpark;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLSurfaceView;
import android.util.Log;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class FilterRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = TagUtil.YOOHAN + "[FilterRenderer]";
    private GrayFilter gray_filter_;
    private Context context_;
    private SurfaceTexture surface_texture_;
    private OnSurfaceTextureAvaliableListener texture_avaliable_listener_;
    private volatile boolean is_swap_camera_ = false;

    public FilterRenderer(Context context) {
        context_ = context;
    }

    public interface OnSurfaceTextureAvaliableListener {
        void onSurfaceTextureAvaliable();
        void onSurfaceCreated(SurfaceTexture surface_texture);
    }

    public void setTextureAvaliableListener(OnSurfaceTextureAvaliableListener listener) {
        this.texture_avaliable_listener_ = listener;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.e(TAG, "onSurfaceCreated !!!");
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        gl.glViewport(0, 0, width, height);
        Log.e(TAG, "onSurfaceChanged !!! width:" + width + " height:" + height);
        if (gray_filter_ == null) {
            gray_filter_ = new GrayFilter(context_);
            int tex_id = gray_filter_.getOESTextureId();
            surface_texture_ = new SurfaceTexture(tex_id);
            surface_texture_.setDefaultBufferSize(CameraHelper.PREVIEW_WIDTH, CameraHelper.PREVIEW_HEIGHT);
            surface_texture_.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
                @Override
                public void onFrameAvailable(SurfaceTexture surface_texture) {
                    if (texture_avaliable_listener_ != null) {
                        texture_avaliable_listener_.onSurfaceTextureAvaliable();
                    }
                }
            });
            if (texture_avaliable_listener_ != null) {
                texture_avaliable_listener_.onSurfaceCreated(surface_texture_);
            }
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        if (surface_texture_ != null) {
            surface_texture_.updateTexImage();
        }
        if (gray_filter_ != null && !is_swap_camera_) { // 切换摄像头时翻转
            gray_filter_.onDrawFrame(gl);
        }
    }

    public synchronized void setSwapCameraFlag(boolean swap_camera) {
        is_swap_camera_ = swap_camera;
    }

    public void setRotation(int rotation) {
        if (gray_filter_ != null) {
            gray_filter_.setRotation(rotation);
        }
    }
}
