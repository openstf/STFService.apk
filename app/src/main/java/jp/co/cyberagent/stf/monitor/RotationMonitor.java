package jp.co.cyberagent.stf.monitor;

import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.view.IRotationWatcher;
import android.view.IWindowManager;

import jp.co.cyberagent.stf.io.MessageWriter;

public class RotationMonitor extends AbstractMonitor {
    private static final String TAG = "STFRotationMonitor";

    private IWindowManager wm = IWindowManager.Stub.asInterface(ServiceManager.getService(Context.WINDOW_SERVICE));

    public RotationMonitor(Context context, MessageWriter.Pool writer) {
        super(context, writer);
    }

    @Override
    public void run() {
        Log.i(TAG, "Monitor starting");

        IRotationWatcher watcher = new IRotationWatcher.Stub() {
            @Override
            public void onRotationChanged(int rotation) throws RemoteException {
                report(rotation);
            }
        };

        peek();

        try {
            wm.watchRotation(watcher);

            synchronized (this) {
                while (!isInterrupted()) {
                    wait();
                }
            }
        }
        catch (RemoteException e) {
            e.printStackTrace();
        }
        catch (InterruptedException e) {
            // Okay
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            Log.i(TAG, "Monitor stopping");

            // Sadly, wm.removeRotationWatcher is only available on API >= 18. Instead, we
            // must make sure that whole process dies, causing DeathRecipient to reap the
            // watcher.
        }
    }

    @Override
    public void peek() {
        try {
            report(wm.getRotation());
        }
        catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void report(int rotation) {
        Log.i(TAG, String.format("Rotation is %d", rotation));
        // writer.write(new Wire.RotationChangeEvent(rotation))
    }
}