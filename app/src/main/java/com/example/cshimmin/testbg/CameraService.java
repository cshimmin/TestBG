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
     * Starts this service to begin taking video with the camera.
     * If the service is already performing a task this action will be queued.
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

    /* Spin up a BG thread where the camera data will delivered to */
    private void startBackgroundThread() {
        if (mBackgroundThread != null) return;
        mBackgroundThread = new HandlerThread("BGCamera");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /* initialize the camera using the old camera API. obviously you could do
     * more configuration here. and also we should probably be careful
     * about _un_-setting up the camera.
     */
    private void initCamera() {
        if (mCamera == null) {
            Log.d(TAG, "No camera found... initializing.");
            mCamera = Camera.open();
        }
    }

    /* This is the main entrypoint that gets run in the background
     * when the main app starts the service using startService().
     */
    private void startCamera(int repeat) {
        // NB: I'm no longer using the repeat parameter...
	// but I left it as an example of how to pass params
	// to the service via intent.
	
	// start up a background thread. this is needed because
	// we need a thread to receieve the camera images, but it
	// cannot be this thread, because this thread dies as soon
	// as this function, startCamer(), is completed.
        startBackgroundThread();

	// run the camera initialization in the background thread.
	// this is important because the thread which calls
	// Camera.open() is the one which receives the callbacks
        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                initCamera();

		// define a callback function to receieve the buffers from
		// each new camera frame.
		// note I'm being lazy here by defining the callback inline;
		// it would be more proper to have the CameraService implement
		// the Camera.PreviewCallback interface.
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

		// set the camera going. we should find some way to also stop the camera from going...
                mCamera.startPreview();
            }
        });

        // TODO: find some way to send a messge to the BG service to tell it to stop.
	
	// Remember, the Service dies when this function returns.
	// So, loop forever, checking periodically if IS_RUNNING gets set to false.
	// Need to find a way for the main camera app to set IS_RUNNING to false.
	// it would also be a better idea to use some kind of blocking messaging queue,
	// so there is not a 500ms latency to disabling the camera.
        IS_RUNNING = true;
        while (IS_RUNNING) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                continue;
            }
        }

	// If we get here, we have decided to stop running.
	// TODO: shutdown the camera
    }
}
