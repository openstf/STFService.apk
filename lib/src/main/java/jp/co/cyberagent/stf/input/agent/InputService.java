package jp.co.cyberagent.stf.input.agent;

import android.app.IntentService;
import android.app.KeyguardManager;
import android.content.Intent;
import android.util.Log;

public class InputService extends IntentService {
    private static final String TAG = "InputService";
    private static final String UNLOCK_ACTION = "jp.co.cyberagent.stf.input.agent.Unlock";

    public InputService() {
        super("InputService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent.getAction().equals(UNLOCK_ACTION)) {
            Log.i(TAG, "Unlocking device");
            unlock();
        }
        else {
            Log.e(TAG, "Unknown action " + intent.getAction());
        }
    }

    @SuppressWarnings("deprecation")
    private void unlock() {
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        keyguardManager.newKeyguardLock("InputService/Unlock").disableKeyguard();
    }
}
