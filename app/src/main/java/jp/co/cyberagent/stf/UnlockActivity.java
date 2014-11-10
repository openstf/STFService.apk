package jp.co.cyberagent.stf;

import android.app.Activity;
import android.app.KeyguardManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.Window;
import android.view.WindowManager;

public class UnlockActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();

        // Don't add FLAG_SHOW_WHEN_LOCKED, it usually causes the screen to get locked
        // again when this activity is finished.
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        // Just in case we can't finish quickly enough, let's at the very least let
        // any events through.
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);

        // Close ASAP.
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            public void run() {
                UnlockActivity.this.finish();
            }
         }, 500);
    }
}
