package jp.co.cyberagent.stf.input.agent;

import android.app.KeyguardManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

public class InputService extends Service {
    private static final String TAG = "InputService";
    private static final String ACTION_UNLOCK = "jp.co.cyberagent.stf.input.agent.InputService.ACTION_UNLOCK";
    private static final String ACTION_WAKE_LOCK_ACQUIRE = "jp.co.cyberagent.stf.input.agent.InputService.ACTION_WAKE_LOCK_ACQUIRE";
    private static final String ACTION_WAKE_LOCK_RELEASE = "jp.co.cyberagent.stf.input.agent.InputService.ACTION_WAKE_LOCK_RELEASE";
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;

    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releaseWakeLock();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        String action = intent.getAction();
        if (ACTION_UNLOCK.equals(action)) {
            unlock();
        }
        else if (ACTION_WAKE_LOCK_ACQUIRE.equals(action)) {
            acquireWakeLock();
        }
        else if (ACTION_WAKE_LOCK_RELEASE.equals(action)) {
            releaseWakeLock();
        }
        else {
            Log.e(TAG, "Unknown action " + action);
        }
        return START_STICKY;
    }

    @SuppressWarnings("deprecation")
    private void unlock() {
        Log.i(TAG, "Unlocking device");
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        keyguardManager.newKeyguardLock("InputService").disableKeyguard();
    }

    @SuppressWarnings("deprecation")
    private void acquireWakeLock() {
        releaseWakeLock();
        Log.i(TAG, "Acquiring wake lock");
        wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "InputService"
        );
        wakeLock.acquire();
    }

    private void releaseWakeLock() {
        if (wakeLock != null) {
            Log.i(TAG, "Releasing wake lock");
            wakeLock.release();
            wakeLock = null;
        }
    }
}
