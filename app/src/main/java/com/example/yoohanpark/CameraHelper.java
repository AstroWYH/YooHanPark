package com.example.yoohanpark;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CameraHelper {
    private static final String TAG = TagUtil.YOOHAN + "[CameraHelper]";
    CameraManager camera_manager_;
    CameraDevice camera_device_;
    CameraCaptureSession camera_captureSession_;
    Context context_;
    HandlerThread camera_thread_;
    Handler camera_handler_;
    ImageReader image_reader_;
    int camera_facing_ = CameraMetadata.LENS_FACING_FRONT;
    String camera_id_ = null;
    String front_camera_id_ = null;
    String back_camera_id_ = null;
    CameraCharacteristics front_camera_characteristics_ = null;
    CameraCharacteristics back_camera_characteristics_ = null;
    private SurfaceTexture surface_texture_;
    int camera_sensor_orientation_ = 0;
    Size preview_size_;
    Size save_pic_size_;
    public static final int PREVIEW_WIDTH = 1080;
    public static final int PREVIEW_HEIGHT = 1920; // [YooHan] camera预览宽高
    boolean can_exchange_camera_ = false;
    boolean can_take_pic_ = false;
    private volatile boolean is_swap_camera_ = false;
    private OnCameraSwitchFinishListener on_camera_switch_finish_listener_;

    public CameraHelper(Context context) {
        camera_manager_ = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        context_ = context.getApplicationContext();

        startThread();

        String[] camera_id_list = new String[0];
        try {
            camera_id_list = camera_manager_.getCameraIdList();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        if (camera_id_list.length == 0) {
            ToastUtil.showMsg(context, "没有可用相机");
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
                    Log.e(TAG, "front camera id:" + cam_id);
                } else if (lens_facing == CameraMetadata.LENS_FACING_BACK && back_camera_id_ == null) {
                    back_camera_id_ = cam_id;
                    back_camera_characteristics_ = camera_characteristics;
                    Log.e(TAG, "back camera id:" + cam_id);
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

    public void setSurfaceTexture(SurfaceTexture surface_texture) {
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
            Log.e(TAG, "系统支持的尺寸: " + size.getWidth() + " * " + size.getHeight() +
                    " 比例 ：" + size.getWidth() / (float) size.getHeight());
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

    public int getRotation() {
        return camera_sensor_orientation_;
    }

    private void openCamera() {
        if (surface_texture_ == null) {
            return;
        }
        CameraCharacteristics camera_characteristics_ = front_camera_characteristics_;
        if (front_camera_id_ == null || (camera_id_ != null && camera_id_.equals(back_camera_id_))) {
            camera_id_ = back_camera_id_;
            camera_characteristics_ = back_camera_characteristics_;
        }
        if (camera_characteristics_ == null) {
            return;
        }

        int support_level = camera_characteristics_.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        if (support_level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
            Log.e(TAG, "相机硬件不支持新特性");
        }

        //获取摄像头方向
        camera_sensor_orientation_ = camera_characteristics_.get(CameraCharacteristics.SENSOR_ORIENTATION);
        Log.e(TAG, "sensor orientation:" + camera_sensor_orientation_);
        android.hardware.camera2.params.StreamConfigurationMap configuration_map =
                camera_characteristics_.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        Size[] save_pic_size = configuration_map.getOutputSizes(ImageFormat.JPEG); // 保存照片尺寸
        Size[] preview_size = configuration_map.getOutputSizes(SurfaceTexture.class); // 预览尺寸

        DisplayMetrics display_metrics = context_.getResources().getDisplayMetrics();
        int width = PREVIEW_WIDTH; // display_metrics.widthPixels;
        int height = PREVIEW_HEIGHT; // display_metrics.heightPixels;

        if (camera_sensor_orientation_ == 90 || camera_sensor_orientation_ == 270) {
            width = PREVIEW_HEIGHT; // display_metrics.heightPixels;
            height = PREVIEW_WIDTH; // display_metrics.widthPixels;
        }

        preview_size_ = getBestSize(width, height, width, height, List.of(preview_size));
        Log.e(TAG, "屏幕尺寸 ：" + display_metrics.widthPixels + " * " + display_metrics.heightPixels);
        Log.e(TAG, "预览最优尺寸 ：" + preview_size_.getWidth() + " * " + preview_size_.getHeight());
        Log.e(TAG, "----------------------------------------------------");
        save_pic_size_ = getBestSize(width, height, width, height, List.of(save_pic_size));
        Log.e(TAG, "保存图片最优尺寸 ：" + save_pic_size_.getWidth() + " * " +
                save_pic_size_.getHeight());

        try {
            if (ActivityCompat.checkSelfPermission(context_, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                camera_manager_.openCamera(camera_id_, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull CameraDevice camera) {
                        Log.e(TAG, "openCamera onOpened :");
                        camera_device_ = camera;
                        try {
                            startPreview();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice camera) {
                        Log.e(TAG, "openCamera onDisconnected :");
                        camera.close();
                        camera_device_ = null;
                    }

                    @Override
                    public void onError(@NonNull CameraDevice camera, int error) {
                        Log.e(TAG, "openCamera onError :" + error);
                        camera.close();
                        camera_device_ = null;
                        Toast.makeText(context_, "打开相机失败！", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onClosed(@NonNull CameraDevice camera) {
                        super.onClosed(camera);
                        Log.e(TAG, "openCamera onClosed !");
                    }
                }, camera_handler_);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 拍照
     */
    public void takePic() {
        if (surface_texture_ == null) {
            return;
        }
        try {
            ImageReader reader = ImageReader.newInstance(save_pic_size_.getWidth(), save_pic_size_.getHeight(), ImageFormat.JPEG, 1);
            List<Surface> output_surfaces = new ArrayList<Surface>(2);
            output_surfaces.add(reader.getSurface());
            output_surfaces.add(new Surface(surface_texture_));

            final CaptureRequest.Builder capture_builder = camera_device_.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            capture_builder.addTarget(reader.getSurface());
            capture_builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            // Orientation
            // int rotation = getWindowManager().getDefaultDisplay().getRotation();
            // capture_builder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            File path_file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Test");
            if (!path_file.exists()) {
                try {
                    path_file.mkdirs();
                    Log.e(TAG, "take picture create crate dir :" + path_file.getAbsolutePath());
                } catch (Exception e) {
                    Log.e(TAG, "take picture create dir error:" + e.toString());
                }
            }

            final File file = new File(path_file, "TestCamera.jpg");

            ImageReader.OnImageAvailableListener reader_listener = new ImageReader.OnImageAvailableListener() {

                @Override
                public void onImageAvailable(ImageReader reader) {

                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        save(bytes);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                        Log.e(TAG, "take picture error 1:" + e.toString());
                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.e(TAG, "take picture error 2:" + e.toString());
                    } finally {
                        if (image != null) {
                            image.close();
                        }
                    }
                }

                private void save(byte[] bytes) throws IOException {
                    OutputStream output = null;
                    try {
                        if (!file.exists()) {
                            file.createNewFile();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Save picture " + file.getAbsolutePath() + " error:" + e.toString());
                    }
                    try {
                        output = new FileOutputStream(file);
                        output.write(bytes);
                    } catch (Exception e) {
                        Log.e(TAG, "Save picture error:" + e.toString());
                    } finally {
                        if (null != output) {
                            output.close();
                        }
                    }
                }

            };

            HandlerThread thread = new HandlerThread("CameraPicture");
            thread.start();
            final Handler backgroud_handler = new Handler(thread.getLooper());
            reader.setOnImageAvailableListener(reader_listener, backgroud_handler);

            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    Log.e(TAG, "Saved:" + file);
                    Toast.makeText(context_, "Saved:" + file, Toast.LENGTH_SHORT).show();
                    startPreview();
                }
            };

            camera_device_.createCaptureSession(output_surfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        session.capture(capture_builder.build(), captureListener, backgroud_handler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {

                }
            }, backgroud_handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void startPreview() {
        if (null == camera_device_ || surface_texture_ == null || null == preview_size_) {
            Log.e(TAG, "startPreview fail, return");
            return;
        }
        surface_texture_.setDefaultBufferSize(preview_size_.getWidth(), preview_size_.getHeight());

        // 为相机预览，创建一个CameraCaptureSession对象
        Surface surface = new Surface(surface_texture_);
        CameraCaptureSession.StateCallback callback = new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
                Log.e(TAG, "createCaptureSession onConfigured");
                camera_captureSession_ = session;
                try {
                    CaptureRequest.Builder capture_request_builder = camera_device_.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    capture_request_builder.addTarget(surface);  // 将CaptureRequest的构建器与Surface对象绑定在一起
                    capture_request_builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);     // 闪光灯
                    capture_request_builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE); // 自动对焦

                    session.setRepeatingRequest(capture_request_builder.build(), new CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                            super.onCaptureCompleted(session, request, result);
                            can_exchange_camera_ = true;
                            can_take_pic_ = true;

                            if (is_swap_camera_) {
                                is_swap_camera_ = false;
                                if (on_camera_switch_finish_listener_ != null) {
                                    on_camera_switch_finish_listener_.onCameraSwitchFinish();
                                }
                            }
                        }

                        @Override
                        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                            super.onCaptureFailed(session, request, failure);
                            Log.e(TAG, "onCaptureFailed :" + failure.toString());
                            can_take_pic_ = false;
                            Toast.makeText(context_, "CameraCaptureSession onCaptureFailed", Toast.LENGTH_SHORT).show();
                        }
                    }, camera_handler_);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                Log.e(TAG, "createCaptureSession onConfigureFailed!");
                Toast.makeText(context_, "开启预览会话失败！", Toast.LENGTH_SHORT).show();
            }
        };

        try {
            camera_device_.createCaptureSession(List.of(surface), callback, camera_handler_);
        } catch (Exception e) {
            Log.e(TAG, "camera device createCaptureSession error:" + e.toString());
        }
    }


    public interface OnCameraSwitchFinishListener {
        void onCameraSwitchFinish();
    }

    public void setOnCameraSwitchFinishListener(OnCameraSwitchFinishListener listener) {
        this.on_camera_switch_finish_listener_ = listener;
    }

    /**
     * 切换摄像头
     */

    public void exchangeCamera() {
        is_swap_camera_ = true;
        // if (camera_device_ == null || !can_exchange_camera_) return;
        if (camera_facing_ == CameraCharacteristics.LENS_FACING_FRONT && front_camera_id_ != null) {
            camera_facing_ = CameraCharacteristics.LENS_FACING_BACK;
            camera_id_ = back_camera_id_;
        } else if (back_camera_id_ != null) {
            camera_facing_ = CameraCharacteristics.LENS_FACING_FRONT;
            camera_id_ = front_camera_id_;
        } else {
            camera_facing_ = CameraCharacteristics.LENS_FACING_FRONT;
            camera_id_ = front_camera_id_;
        }
        releaseCamera();
        openCamera();
    }

    public void releaseCamera() {
        if (camera_captureSession_ != null) {
            camera_captureSession_.close();
        }
        camera_captureSession_ = null;

        if (camera_device_ != null) {
            camera_device_.close();
        }
        camera_device_ = null;

        if (image_reader_ != null) {
            image_reader_.close();
        }
        image_reader_ = null;

        can_exchange_camera_ = false;
    }

    public void startThread() {
        if (camera_thread_ == null) {
            camera_thread_ = new HandlerThread("CameraThread");
            camera_thread_.start();
            camera_handler_ = new Handler(camera_thread_.getLooper());
        }
    }

    public void releaseThread() {
        if (camera_thread_ != null) {
            try {
                camera_thread_.quitSafely();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        camera_thread_ = null;
    }
}
