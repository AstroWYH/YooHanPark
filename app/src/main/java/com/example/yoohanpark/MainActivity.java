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

public class MainActivity extends AppCompatActivity{
    CameraHelper camera_helper_ = null;
    GLSurfaceView surface_view_;
//    FilterRenderer filter_render_;
    SurfaceTexture surface_texture_;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        findViewById(R.id.switchBtn).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                ToastUtil.showMsg(MainActivity.this, "镜头切换");
//            }
//        });

        findViewById(R.id.switchBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ToastUtil.showMsg(MainActivity.this, "镜头切换");
                if(camera_helper_!=null){
//                    filter_render_.setSwapCameraFlag(true);
                    camera_helper_.exchangeCamera();
                    int rotation = camera_helper_.getRotation();
                    surface_view_.queueEvent(new Runnable() {
                        @Override
                        public void run() {
//                            filter_render_.setRotation(rotation);
                        }
                    });
                }
            }
        });

        findViewById(R.id.captureBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(camera_helper_!=null){
                    camera_helper_.takePic();
                }
            }
        });

        surface_view_ = findViewById(R.id.glSurfaceView);
        surface_view_.setEGLContextClientVersion(2);

        int pw = CameraHelper.PREVIEW_WIDTH;// 1080
        int ph = CameraHelper.PREVIEW_HEIGHT; // 1440 预览尺寸
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int sw = displayMetrics.widthPixels;
        int sh = displayMetrics.heightPixels;

        int nw = sw;
        int nh = nw * ph / pw;
        if(nh > sh){
            nh = sw;
            nw = nh * pw / ph;
        }
        FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams)surface_view_.getLayoutParams();
        if(flp==null){
            flp = new FrameLayout.LayoutParams(nw, nh);
        }
        flp.width = nw;
        flp.height = nh;
        surface_view_.setLayoutParams(flp);
        Log.e("Test", "SurfaceView set width!");

//        filter_render_ = new FilterRenderer(MainActivity.this);
//        filter_render_.setTextureAvaliableListener(new FilterRenderer.OnSurfaceTextureAvaliableListener() {
//            @Override
//            public void onSurfaceCreated(SurfaceTexture surfaceTexture) {
//                surface_texture_ = surfaceTexture;
//                if(camera_helper_!=null){
//                    camera_helper_.setSurfaceTexture(surface_texture_);
//                }
//                filter_render_.setRotation(camera_helper_.getRotation());
//            }
//
//            @Override
//            public void onSurfaceTextureAvaliable() {
//                surface_view_.requestRender(); // 刷新
//            }
//        });

//        surface_view_.setRenderer(filter_render_);
//        surface_view_.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        RxPermissions rxPermissions = new RxPermissions(MainActivity.this);
        rxPermissions.request(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE) // 可填多个权限，逗号隔开
                .subscribe(granted -> {
                    if (granted) { // Always true pre-M
                        // Toast.makeText(MainActivity.this,"有相机权限x！", Toast.LENGTH_SHORT).show();
                        camera_helper_ = new CameraHelper(MainActivity.this);
                        camera_helper_.setOnCameraSwitchFinishListener(new CameraHelper.OnCameraSwitchFinishListener() {
                            @Override
                            public void onCameraSwitchFinish() {
//                                filter_render_.setRotation(camera_helper_.getRotation());
//                                filter_render_.setSwapCameraFlag(false);
                            }
                        });
                        if(surface_texture_!=null){
                            camera_helper_.setSurfaceTexture(surface_texture_);
                        }
//                        filter_render_.setRotation(camera_helper_.getRotation());
                    } else {
                        // Oups permission denied
                        Toast.makeText(MainActivity.this,"没有相机权限！请先开启相机权限！", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(camera_helper_ != null && surface_texture_ != null){
            camera_helper_.startThread();
            camera_helper_.setSurfaceTexture(surface_texture_);
//            if(filter_render_!=null) {
//                filter_render_.setRotation(camera_helper_.getRotation());
//            }
        }
    }

    @Override
    protected void onPause() {
        if(camera_helper_!=null){
            camera_helper_.releaseCamera();
            camera_helper_.releaseThread();
        }
        super.onPause();
    }
}