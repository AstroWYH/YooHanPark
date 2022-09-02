package com.example.yoohanpark;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CameraHelper {
    private static final String TAG = TagUtil.YOOHAN + "[CameraHelper]";
    CameraManager camera_manager_;
    Context context_;
    HandlerThread camera_thread;
    Handler camera_handler_;
    int camera_facing_ = CameraMetadata.LENS_FACING_FRONT;
    String camera_id_ = null;
    String front_camera_id_ = null;
    String back_camera_id_ = null;
    CameraCharacteristics front_camera_characteristics_ = null;
    CameraCharacteristics back_camera_characteristics_ = null;
    private SurfaceTexture surface_texture_;
    int camera_sensor_orientation_ = 0;

    public CameraHelper(Context context) {
        camera_manager_ = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        context_ = context.getApplicationContext();

        startThread();

        String[] camera_id_list = new String[0]; // [YooHan] 创建长度为0的数组
        try {
            camera_id_list = camera_manager_.getCameraIdList();  // [YooHan] 获取camera cam_id
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        if (camera_id_list.length == 0) {
            ToastUtil.heightowMsg(context, "没有可用相机"); // [YooHan]
            return;
        }

        for (int i = 0; i < camera_id_list.length; i++) {
            String cam_id = camera_id_list[i];
            try {
                CameraCharacteristics camera_characteristics = camera_manager_.getCameraCharacteristics(cam_id);
                int lens_facing = camera_characteristics.get(CameraCharacteristics.LENS_FACING);

                if (lens_facing == CameraMetadata.LENS_FACING_FRONT && front_camera_id_ == null) {
                    front_camera_id_ = cam_id;
                    front_camera_characteristics_ = camera_characteristics;
                    Log.e(TAG, "Front camera id:" + cam_id);
                } else if (lens_facing == CameraMetadata.LENS_FACING_BACK && back_camera_id_ == null) {
                    back_camera_id_ = cam_id;
                    back_camera_characteristics_ = camera_characteristics;
                    Log.e(TAG, "Back camera id:" + cam_id);
                }
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
        if (front_camera_id_ != null) {
            camera_id_ = front_camera_id_;
        } else {
            camera_id_ = back_camera_id_;
            camera_facing_ = CameraMetadata.LENS_FACING_BACK;
        }
    }

    public void setSurfaceTexture(SurfaceTexture surface_texture){
        Log.e(TAG, "set surfaceTexture and create camera!");
        surface_texture_ = surface_texture;
        releaseCamera();
        openCamera();
    }

    /**
     * 根据提供的参数值返回与指定宽高相等或最接近的尺寸
     *
     * @param target_width  目标宽度
     * @param target_height 目标高度
     * @param max_width     最大宽度(即TextureView的宽度)
     * @param max_height    最大高度(即TextureView的高度)
     * @param size_list     支持的Size列表
     * @return 返回与指定宽高相等或最接近的尺寸
     */
    private Size getBestSize(int target_width, int target_height, int max_width, int max_height, List<Size> size_list) {
        List<Size> big_enough = new ArrayList<Size>(); // [YooHan] 比指定宽高大的Size列表
        List<Size> not_big_enough = new ArrayList<Size>(); // [YooHan] 比指定宽高小的Size列表

        for (Size size : size_list) {
            int width = size.getWidth();
            int height = size.getHeight();
            // 宽<=最大宽度 && 高<=最大高度 && 宽高比 == 目标值宽高比
            if (width <= max_width && height <= max_height && width == height * target_width / target_height) {
                if (width >= target_width && height >= target_height)
                    big_enough.add(size);
                else
                    not_big_enough.add(size);
            }
            Log.e(TAG, "系统支持的尺寸: " + size.getWidth() + " * "+ size.getHeight() +
                    " 比例 ：" + size.getWidth() / (float)size.getHeight());
        }

        // 选择big_enough中最小的值，或not_big_enough中最大的值
        if (big_enough.size() > 0) {
            return Collections.min(big_enough, new CompareSizesByArea());
        } else if (not_big_enough.size() > 0) {
            return Collections.max(not_big_enough, new CompareSizesByArea());
        }
        return size_list.get(0);
    }

    private class CompareSizesByArea implements Comparator<Size> { // [YooHan] 内部类，用于比较
        @Override
        public int compare(Size o1, Size o2) {
            return Long.signum(o1.getWidth() * o1.getHeight() - o2.getWidth() * o2.getHeight());
        }
    }

    public int getRotation(){
        return camera_sensor_orientation_;
    }

    private void openCamera() {
        if(surface_texture_ == null){
            return;
        }
        CameraCharacteristics camera_characteristics_ = mFrontCameraCharacteristics;
        if(frontCameraId == null || (camera_id_ != null && camera_id_.equals(backCameraId))){
            camera_id_ = backCameraId;
            camera_characteristics_ = mBackCameraCharacteristics;
        }
        if (camera_characteristics_ == null) {
            return;
        }

        int supportLevel = camera_characteristics_.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        if (supportLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
            //Toast.makeText(mContext, "相机硬件不支持新特性", Toast.LENGTH_SHORT).show();
            Log.e(TAG,"相机硬件不支持新特性");
        }

        //获取摄像头方向
        mCameraSensorOrientation = camera_characteristics_.get(CameraCharacteristics.SENSOR_ORIENTATION);
        Log.e("Test","mCameraSensorOrientation:"+mCameraSensorOrientation);
        android.hardware.camera2.params.StreamConfigurationMap configurationMap = camera_characteristics_.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        Size[] savePicSize = configurationMap.getOutputSizes(ImageFormat.JPEG);          //保存照片尺寸
        Size[] previewSize = configurationMap.getOutputSizes(SurfaceTexture.class); //预览尺寸

        DisplayMetrics displayMetrics = mContext.getResources().getDisplayMetrics();
        int pw = PREVIEW_WIDTH;//displayMetrics.widthPixels;
        int ph = PREVIEW_HEIGHT;//displayMetrics.heightPixels;

        if (mCameraSensorOrientation == 90 || mCameraSensorOrientation == 270) {
            pw = PREVIEW_HEIGHT;//displayMetrics.heightPixels;
            ph = PREVIEW_WIDTH;//displayMetrics.widthPixels;
        }

        mPreviewSize = getBestSize(pw, ph, pw, ph, List.of(previewSize));
        Log.e("Test", "屏幕尺寸 ："+displayMetrics.widthPixels + " * " + displayMetrics.heightPixels);
        Log.e("Test", "预览最优尺寸 ："+mPreviewSize.getWidth() + " * " + mPreviewSize.getHeight());
        Log.e("Test","----------------------------------------------------");
        mSavePicSize = getBestSize(pw, ph, pw, ph, List.of(savePicSize));
        Log.e("Test", "保存图片最优尺寸 ："+mSavePicSize.getWidth() + " * " + mSavePicSize.getHeight());


        try {
            if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                mCameraManager.openCamera(camera_id_, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull CameraDevice camera) {
                        Log.e(TAG, "openCamera onOpened :");
                        mCameraDevice = camera;
                        try {
                            startPreview();
                        }catch (Exception e){
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice camera) {
                        Log.e(TAG, "openCamera onDisconnected :");
                        camera.close();
                        mCameraDevice = null;
                    }

                    @Override
                    public void onError(@NonNull CameraDevice camera, int error) {
                        Log.e(TAG, "openCamera onError :"+error);
                        camera.close();
                        mCameraDevice = null;
                        Toast.makeText(mContext, "打开相机失败！", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onClosed(@NonNull CameraDevice camera) {
                        super.onClosed(camera);
                        Log.e(TAG, "openCamera onClosed !");
                    }
                }, mCameraHandler);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void startThread() {
        if (camera_thread == null) {
            camera_thread = new HandlerThread("CameraThread");
            camera_thread.start();
            camera_handler_ = new Handler(camera_thread.getLooper());
        }
    }
}
