package com.example.yoohanpark;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.graphics.SurfaceTexture;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.tbruyelle.rxpermissions3.RxPermissions;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = TagUtil.YOOHAN + "[MainActivity]";
    CameraHelper camera_helper_ = null;
    GLSurfaceView surface_view_;
    FilterRenderer filter_render_;
    SurfaceTexture surface_texture_;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        surface_view_ = findViewById(R.id.glSurfaceView);
        surface_view_.setEGLContextClientVersion(2);

        int preview_width = CameraHelper.PREVIEW_WIDTH; // 1080
        int preview_height = CameraHelper.PREVIEW_HEIGHT; // 1440 预览尺寸
        DisplayMetrics display_metrics = getResources().getDisplayMetrics();
        int save_width = display_metrics.widthPixels;
        int save_height = display_metrics.heightPixels;

        int n_width = save_width;
        int n_height = n_width * preview_height / preview_width;
        if (n_height > save_height) {
            n_height = save_width;
            n_width = n_height * preview_width / preview_height;
        }
        FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) surface_view_.getLayoutParams();
        if (flp == null) {
            flp = new FrameLayout.LayoutParams(n_width, n_height);
        }
        flp.width = n_width;
        flp.height = n_height;
        surface_view_.setLayoutParams(flp);
        Log.e(TAG, "SurfaceView set width!");

        filter_render_ = new FilterRenderer(MainActivity.this);
        filter_render_.setTextureAvaliableListener(new FilterRenderer.OnSurfaceTextureAvaliableListener() {
            @Override
            public void onSurfaceCreated(SurfaceTexture surfaceTexture) {
                surface_texture_ = surfaceTexture;
                if (camera_helper_ != null) {
                    camera_helper_.setSurfaceTexture(surface_texture_);
                }
                filter_render_.setRotation(camera_helper_.getRotation());
            }

            @Override
            public void onSurfaceTextureAvaliable() {
                surface_view_.requestRender(); // 刷新
            }
        });

        surface_view_.setRenderer(filter_render_);
        surface_view_.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        RxPermissions rx_permissions = new RxPermissions(MainActivity.this);
        rx_permissions.request(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE) // 可填多个权限，逗号隔开
                .subscribe(granted -> {
                    if (granted) {
                        camera_helper_ = new CameraHelper(MainActivity.this);
                        camera_helper_.setOnCameraSwitchFinishListener(new CameraHelper.OnCameraSwitchFinishListener() {
                            @Override
                            public void onCameraSwitchFinish() {
                                filter_render_.setRotation(camera_helper_.getRotation());
                                filter_render_.setSwapCameraFlag(false);
                            }
                        });
                        if (surface_texture_ != null) {
                            camera_helper_.setSurfaceTexture(surface_texture_);
                        }
                        filter_render_.setRotation(camera_helper_.getRotation());
                    } else {
                        Toast.makeText(MainActivity.this, "没有相机权限！请先开启相机权限！", Toast.LENGTH_SHORT).show();
                    }
                });

        findViewById(R.id.switchBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ToastUtil.showMsg(MainActivity.this, "镜头切换");
                if (camera_helper_ != null) {
                    filter_render_.setSwapCameraFlag(true);
                    camera_helper_.exchangeCamera();
                    int rotation = camera_helper_.getRotation();
                    surface_view_.queueEvent(new Runnable() {
                        @Override
                        public void run() {
                            filter_render_.setRotation(rotation);
                        }
                    });
                }
            }
        });

        findViewById(R.id.takePicBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (camera_helper_ != null) {
                    camera_helper_.takePic();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (camera_helper_ != null && surface_texture_ != null) {
            camera_helper_.startThread();
            camera_helper_.setSurfaceTexture(surface_texture_);
            if (filter_render_ != null) {
                filter_render_.setRotation(camera_helper_.getRotation());
            }
        }
    }

    @Override
    protected void onPause() {
        if (camera_helper_ != null) {
            camera_helper_.releaseCamera();
            camera_helper_.releaseThread();
        }
        super.onPause();
    }
}