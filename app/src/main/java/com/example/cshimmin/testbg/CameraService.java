package com.example.cshimmin.testbg;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.hardware.Camera;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

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

    private Camera mCamera = null;

    private HandlerThread mBackgroundThread = null;
    private Handler mBackgroundHandler = null;

    private boolean IS_RUNNING = true;

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
        if (mCamera == null) {
            Log.d(TAG, "No camera found... initializing.");
            mCamera = Camera.open();
        }
    }

    private void startCamera(int repeat) {
        startBackgroundThread();

        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                initCamera();

                mCamera.setPreviewCallback(new Camera.PreviewCallback() {
                    private int imgs_scanned = 0;
                    @Override
                    public void onPreviewFrame(byte[] data, Camera camera) {
                        imgs_scanned += 1;
                        Log.d("onPreview", "scanned " + imgs_scanned + " imgs.");
                        // print some random pixel values to show it's working
                        for (int i = 0; i < 10; ++i) {
                            Log.d("prev", "b[" + 25*i + "] = " + (0xFF&data[25*i]));
                        }
                        Log.i("onPreview", "onPreview from thread: " + Thread.currentThread().getId());

                    }
                });

                mCamera.startPreview();
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
