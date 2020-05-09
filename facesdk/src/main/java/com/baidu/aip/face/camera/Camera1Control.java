/*
 * Copyright (C) 2017 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.aip.face.camera;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.baidu.aip.face.PreviewView;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

/**
 * 5.0以下相机API的封装。
 */
@SuppressWarnings("deprecation")
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class Camera1Control implements ICameraControl {

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final int MAX_PREVIEW_SIZE = 2048;

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }


    private int displayOrientation = CameraView.ORIENTATION_HORIZONTAL;//TODO--这里修改为90，原来是0
    private int cameraId = 0;
    private int flashMode;
    private AtomicBoolean takingPicture = new AtomicBoolean(false);

    private Context context;
    private Camera camera;

    private Camera.Parameters parameters;
    private PermissionCallback permissionCallback;
    private Rect previewFrame = new Rect();

    private int preferredWidth = 1280;
    private int preferredHeight = 720;

    @ICameraControl.CameraFacing
    private int cameraFacing = CAMERA_FACING_FRONT;

    private boolean usbCamera = false;

    @Override
    public void setDisplayOrientation(int displayOrientation) {
        this.displayOrientation = displayOrientation;
        Log.e("camera","setDisplayOrientation:"+displayOrientation);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void refreshPermission() {
        startPreview(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setFlashMode(@FlashMode int flashMode) {
        if (this.flashMode == flashMode) {
            return;
        }
        this.flashMode = flashMode;
        updateFlashMode(flashMode);
    }

    @Override
    public int getFlashMode() {
        return flashMode;
    }

    @Override
    public void setCameraFacing(@CameraFacing int cameraFacing) {
        this.cameraFacing = cameraFacing;
    }

    @Override
    public void setUsbCamera(boolean usbCamera) {
        this.usbCamera = usbCamera;
    }

    @Override
    public void start() {
        startCamera();
    }

    public void isUsbCamera() {

    }

    private SurfaceTexture surfaceTexture;

    private void startCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            if (permissionCallback != null) {
                permissionCallback.onRequestPermission();
            }
            return;
        }
        if (camera == null) {
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
                Camera.getCameraInfo(i, cameraInfo);
                if (cameraInfo.facing == cameraFacing) {
                    cameraId = i;
                }
            }
            camera = Camera.open(cameraId);
        }
        if (parameters == null) {
            parameters = camera.getParameters();
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        }

        surfaceTexture = new SurfaceTexture(11);
        parameters.setRotation((displayOrientation == CameraView.ORIENTATION_HORIZONTAL) ? 0 : 90); // TODO--图片的预览方向，原来是90，我设置为0
        int rotation = ORIENTATIONS.get(displayOrientation);
        Log.e("camera", "camera rotation:" + rotation+",displayOrientation:"+displayOrientation);
        camera.setDisplayOrientation(rotation);//TODO---相机的预览反向
        //TODO---设备方向，

        try {
            camera.setPreviewCallback(new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] data, Camera camera) {//TODO--相机预览回调
//                    Log.e("onPreviewFrame", "onPreviewFrame");
                    Camera.Size size = camera.getParameters().getPreviewSize();
                    Log.e("camera", "camera.getParameters().getPreviewSize() :height:" + size.height + ",width:" + size.width);
                    int rotation = getSurfaceOrientation();//TODO--这里原来是{# getSurfaceOrientation()},改成0

                    Log.e("camera", "getSurfaceOrientation :" + rotation + ",displayorientation:" + displayOrientation);

                    if (cameraFacing == ICameraControl.CAMERA_FACING_FRONT) {
                        //TODO--镜像--180旋转 android自带的摄像头和接usb摄像头，图片有180旋转，用usb摄像头注释下面的代码，
                        if (!usbCamera) {
                            if (rotation == 90 || rotation == 270) {
                                rotation = (rotation + 180) % 360;
                            }
                        }
                    }

                    Log.e("camera", "getSurfaceOrientation :" + rotation + ",rotation % 180 == 90:" + (rotation % 180 == 90));

                    if (rotation % 180 == 90) {
                        previewView.setPreviewSize(size.height, size.width);//,TODO--这里回调的是旋转的图片
                    } else {
                        previewView.setPreviewSize(size.width, size.height);
                    }

                    onFrameListener.onPreviewFrame(data, rotation, size.width, size.height);
                }
            });
            camera.setPreviewTexture(surfaceTexture);
            if (textureView != null) {
                surfaceTexture.detachFromGLContext();
                textureView.setSurfaceTexture(surfaceTexture);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        opPreviewSize(preferredWidth, preferredHeight);
    }

    private TextureView textureView;

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    public void setTextureView(TextureView textureView) {
        this.textureView = textureView;
        if (surfaceTexture != null) {
            surfaceTexture.detachFromGLContext();
            textureView.setSurfaceTexture(surfaceTexture);
        }
    }

    @Override
    public void stop() {
        if (camera != null) {
            camera.stopPreview();
            camera.setPreviewCallback(null);
            camera.release();
            camera = null;
        }
    }

    @Override
    public void pause() {
        if (camera != null) {
            camera.stopPreview();
        }
        setFlashMode(FLASH_MODE_OFF);
    }

    @Override
    public void resume() {
        takingPicture.set(false);
        if (camera == null) {
            startCamera();
        }
    }

    private OnFrameListener onFrameListener;

    @Override
    public void setOnFrameListener(OnFrameListener listener) {
        this.onFrameListener = listener;
    }

    @Override
    public void setPreferredPreviewSize(int width, int height) {
        this.preferredWidth = Math.max(width, height);
        this.preferredHeight = Math.min(width, height);
    }

    @Override
    public View getDisplayView() {
        return null;
    }


    private PreviewView previewView;

    @Override
    public void setPreviewView(PreviewView previewView) {
        this.previewView = previewView;
        setTextureView(previewView.getTextureView());
    }

    @Override
    public PreviewView getPreviewView() {
        return previewView;
    }

    @Override
    public void takePicture(final OnTakePictureCallback onTakePictureCallback) {
        if (takingPicture.get()) {
            return;
        }

        switch (displayOrientation) {
            case CameraView.ORIENTATION_PORTRAIT:
                parameters.setRotation(90);
                break;
            case CameraView.ORIENTATION_HORIZONTAL:
                parameters.setRotation(0);
                break;
            case CameraView.ORIENTATION_INVERT:
                parameters.setRotation(180);
                break;
            default:
                parameters.setRotation(90);
                break;
        }
        Camera.Size picSize =
                getOptimalSize(preferredWidth, preferredHeight, camera.getParameters().getSupportedPictureSizes());
        parameters.setPictureSize(picSize.width, picSize.height);
        camera.setParameters(parameters);
        takingPicture.set(true);
        camera.autoFocus(new Camera.AutoFocusCallback() {
            @Override
            public void onAutoFocus(boolean success, Camera camera) {
                camera.cancelAutoFocus();
                try {
                    camera.takePicture(null, null, new Camera.PictureCallback() {
                        @Override
                        public void onPictureTaken(byte[] data, Camera camera) {
                            camera.startPreview();
                            takingPicture.set(false);
                            if (onTakePictureCallback != null) {
                                onTakePictureCallback.onPictureTaken(data);
                            }
                        }
                    });
                } catch (RuntimeException e) {
                    e.printStackTrace();
                    camera.startPreview();
                    takingPicture.set(false);
                }
            }
        });
    }

    @Override
    public void setPermissionCallback(PermissionCallback callback) {
        this.permissionCallback = callback;
    }

    public Camera1Control(Context context) {
        this.context = context;
        Log.e("camera", "Camera1Control------------");
    }

    // 开启预览
    private void startPreview(boolean checkPermission) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            if (checkPermission && permissionCallback != null) {
                permissionCallback.onRequestPermission();
            }
            return;
        }
        camera.startPreview();
    }

    private void opPreviewSize(int width, @SuppressWarnings("unused") int height) {
        Camera.Size optSize;
        if (parameters != null && camera != null && width > 0) {
            optSize = getOptimalSize(width, height, camera.getParameters().getSupportedPreviewSizes());

            Log.e("camera", "getOptimalSize:width:" + optSize.width + ",height:" + optSize.height);//TODO--设置最佳预览尺寸

            parameters.setPreviewSize(optSize.width, optSize.height);

//            camera.setDisplayOrientation(getSurfaceOrientation());
            //            camera.stopPreview();
            try {
                camera.setParameters(parameters);
            } catch (RuntimeException e) {
                e.printStackTrace();

            }
            camera.startPreview();
        }
    }

    private Camera.Size getOptimalSize(int width, int height, List<Camera.Size> sizes) {

        Camera.Size pictureSize = sizes.get(0);

        List<Camera.Size> candidates = new ArrayList<>();

        for (Camera.Size size : sizes) {
            if (size.width >= width && size.height >= height && size.width * height == size.height * width) {
                // 比例相同
                candidates.add(size);
            } else if (size.height >= width && size.width >= height && size.width * width == size.height * height) {
                // 反比例
                candidates.add(size);
            }
        }
        if (!candidates.isEmpty()) {
            return Collections.min(candidates, sizeComparator);
        }

        for (Camera.Size size : sizes) {
            if (size.width > width && size.height > height) {
                return size;
            }
        }

        return pictureSize;
    }

    private Comparator<Camera.Size> sizeComparator = new Comparator<Camera.Size>() {
        @Override
        public int compare(Camera.Size lhs, Camera.Size rhs) {
            return Long.signum((long) lhs.width * lhs.height - (long) rhs.width * rhs.height);
        }
    };

    private void updateFlashMode(int flashMode) {
        switch (flashMode) {
            case FLASH_MODE_TORCH:
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                break;
            case FLASH_MODE_OFF:
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                break;
            case ICameraControl.FLASH_MODE_AUTO:
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
                break;
            default:
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
                break;
        }
        camera.setParameters(parameters);
    }

    protected int getSurfaceOrientation() {

        switch (displayOrientation) {
            case CameraView.ORIENTATION_HORIZONTAL:
                return 0;
            case CameraView.ORIENTATION_INVERT:
                return 180;
            default:
                return 90;
        }
    }

    @Override
    public Rect getPreviewFrame() {
        return previewFrame;
    }
}
