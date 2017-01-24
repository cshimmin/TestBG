package com.example.cshimmin.testbg;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;

import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class CameraService extends IntentService {
    public static final String TAG = "CameraService";

    // IntentService can perform, e.g. ACTION_FETCH_NEW_ITEMS
    private static final String ACTION_START = "com.example.cshimmin.testbg.action.START";

    private HandlerThread mBackgroundThread = null;
    private Handler mBackgroundHandler = null;

    private boolean IS_RUNNING = true;


    private Size mVideoSize = null;

    private CameraDevice mCameraDevice = null;
    private CaptureRequest.Builder mPreviewBuilder = null;
    private CameraCaptureSession mPreviewSession = null;

    private Semaphore mCameraLock = new Semaphore(1);

    private ImageReader mImageReader = null;
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image img = reader.acquireNextImage();

            Log.i("imgListener", "Got an image!");

            img.close();
        }
    };

    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            mCameraDevice = camera;
            startPreview();
            mCameraLock.release();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            mCameraLock.release();
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            mCameraLock.release();
            camera.close();
            mCameraDevice = null;
            // ????? analog of Activity.finish()?
            Log.e("cameraState", "don't know what to do in onError!");
        }
    };

    public CameraService() {
        super("CameraService");
    }

    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    // TODO: Customize helper method
    public static void startService(Context context, int repeat) {
        Intent intent = new Intent(context, CameraService.class);
        intent.setAction(ACTION_START);
        intent.putExtra("repeat", repeat);
        context.startService(intent);
    }


    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_START.equals(action)) {
                final int repeat = intent.getIntExtra("repeat", 0);
                startCamera(repeat);
            }
        }
    }

    private void startBackgroundThread() {
        if (mBackgroundThread != null) return;
        mBackgroundThread = new HandlerThread("BGCamera");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }


    private void initCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        try {
            if (!mCameraLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            String cameraID = manager.getCameraIdList()[0];

            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraID);

            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            Size videoSize = null;
            for (Size sz : map.getOutputSizes(MediaRecorder.class)) {
                Log.i(TAG, "video size: " + sz);
                if (videoSize == null || (videoSize.getHeight()*videoSize.getWidth() < sz.getHeight()*sz.getWidth())) {
                    videoSize = sz;
                }
            }
            mVideoSize = videoSize;
            Log.i(TAG, "selecting video size " + mVideoSize);

            manager.openCamera(cameraID, mStateCallback, null);


        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.");
        }
    }

    private void startPreview() {
        Log.i("startPreview", "starting preview!");
        if (mCameraDevice == null || mVideoSize == null) {
            Log.e("startPreview", "got null in startPreview!");
            return;
        }
        try {
            mImageReader = ImageReader.newInstance(mVideoSize.getWidth(), mVideoSize.getHeight(), ImageFormat.YUV_420_888, 2);
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);

            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            if (mImageReader.getSurface() == null) {
                Log.i("startPreview", "hey!! surface is null!!");
            }
            mPreviewBuilder.addTarget(mImageReader.getSurface());

            mCameraDevice.createCaptureSession(Arrays.asList(mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    mPreviewSession = session;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    Log.e("captureState", "configuer failed!");
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void updatePreview() {
        if (null == mCameraDevice) {
            return;
        }
        try {
            //setupUpCaptureRequestBuilder(mPreviewBuilder);
            //HandlerThread thread = new HandlerThread("CameraPreview");
            //thread.start();
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startCamera(int repeat) {
        startBackgroundThread();

        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                initCamera();
            }
        });

        // TODO: find some way to send a messge to the BG service to tell it to stop.
        IS_RUNNING = true;

        while (IS_RUNNING) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                continue;
            }
        }
    }
}
