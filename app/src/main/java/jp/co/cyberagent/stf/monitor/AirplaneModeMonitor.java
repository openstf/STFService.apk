package jp.co.cyberagent.stf.monitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import jp.co.cyberagent.stf.io.MessageWriter;

public class AirplaneModeMonitor extends AbstractMonitor {
    private static final String TAG = "STFAirplaneModeMonitor";

    public AirplaneModeMonitor(Context context, MessageWriter.Pool writer) {
        super(context, writer);
    }

    @Override
    public void run() {
        Log.i(TAG, "Monitor starting");

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                report(intent.getBooleanExtra("state", false));
            }
        };

        context.registerReceiver(receiver, new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED));

        peek();

        try {
            synchronized (this) {
                while (!isInterrupted()) {
                    wait();
                }
            }
        }
        catch (InterruptedException e) {
            // Okay
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            Log.i(TAG, "Monitor stopping");

            context.unregisterReceiver(receiver);
        }
    }

    private void peek() {
        if (Build.VERSION.SDK_INT >= 17) {
            report(Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) == 1);
        }
        else {
            report(Settings.System.getInt(context.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0) == 1);
        }
    }

    private void report(boolean state) {
        Log.i(TAG, String.format("Airplane mode is %s", state ? "on" : "off"));
    }
}
