/*
 * Copyright (C) 2017 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.aip.face;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

import com.baidu.aip.FaceDetector;
import com.baidu.aip.ImageFrame;
import com.baidu.aip.face.camera.Camera1Control;
import com.baidu.aip.face.camera.Camera2Control;
import com.baidu.aip.face.camera.CameraView;
import com.baidu.aip.face.camera.ICameraControl;

import android.content.Context;
import android.content.res.Configuration;
import android.util.Log;

/**
 * 封装了系统做机做为输入源。
 */
public class CameraImageSource extends ImageSource {

    /**
     * 相机控制类
     */
    private ICameraControl cameraControl;
    private Context context;

    public ICameraControl getCameraControl() {
        return cameraControl;
    }

    private ArgbPool argbPool = new ArgbPool();

    private int cameraFaceType = ICameraControl.CAMERA_FACING_FRONT;

    public void setCameraFacing(int type) {
        this.cameraFaceType = type;
    }

    //TODO--输入源
    public CameraImageSource(Context context) {
        this.context = context;
//        Log.e("camera","orientation:="+context.getResources().getConfiguration().orientation);
        cameraControl = new Camera1Control(getContext());
        cameraControl.setCameraFacing(cameraFaceType);
        //TODO---横屏90，竖屏0
        cameraControl.setDisplayOrientation((context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) ? CameraView.ORIENTATION_HORIZONTAL : CameraView.ORIENTATION_PORTRAIT);
        cameraControl.setOnFrameListener(new ICameraControl.OnFrameListener<byte[]>() {
            @Override
            public void onPreviewFrame(byte[] data, int rotation, int width, int height) {

                Log.e("camera", "setOnFrameListener onPreviewFrame: rotation:" + rotation + ",width:" + width + ",height:" + height);

                int[] argb = argbPool.acquire(width, height);

                if (argb == null || argb.length != width * height) {
                    argb = new int[width * height];
                }

                rotation = rotation < 0 ? 360 + rotation : rotation;
                FaceDetector.yuvToARGB(data, width, height, argb, rotation, 0);

                // liujinhui modify

                // TODO--旋转了90或270度。高宽需要替换
                if (rotation % 180 == 90) {
                    int temp = width;
                    width = height;
                    height = temp;
                }

                ImageFrame frame = new ImageFrame();
                frame.setArgb(argb);
                frame.setWidth(width);
                frame.setHeight(height);
                frame.setPool(argbPool);
                ArrayList<OnFrameAvailableListener> listeners = getListeners();
                for (OnFrameAvailableListener listener : listeners) {
                    listener.onFrameAvailable(frame);
                }
            }
        });
    }

    private int[] toIntArray(byte[] buf) {
        final ByteBuffer buffer = ByteBuffer.wrap(buf)
                .order(ByteOrder.LITTLE_ENDIAN);
        final int[] ret = new int[buf.length / 4];
        buffer.asIntBuffer().put(ret);
        return ret;
    }

    @Override
    public void start() {
        super.start();
        cameraControl.start();
    }

    @Override
    public void stop() {
        super.stop();
        cameraControl.stop();
    }

    private Context getContext() {
        return context;
    }

    @Override
    public void setPreviewView(PreviewView previewView) {
        cameraControl.setPreviewView(previewView);
    }
}
