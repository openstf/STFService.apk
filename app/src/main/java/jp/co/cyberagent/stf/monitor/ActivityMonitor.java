package jp.co.cyberagent.stf.monitor;

import android.app.ActivityManagerNative;
import android.app.IActivityController;
import android.app.IActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;
import android.util.Log;

import jp.co.cyberagent.stf.io.MessageWritable;

public class ActivityMonitor extends AbstractMonitor {
    private static final String TAG = "STFActivityMonitor";

    private Intent currentIntent;
    private String currentPackage;

    public ActivityMonitor(Context context, MessageWritable writer) {
        super(context, writer);
    }

    private class ActivityController extends IActivityController.Stub {

        /**
         * The system is trying to start an activity. Return true to allow it to be started as
         * normal, or false to cancel/reject this activity.
         */
        @Override
        public boolean activityStarting(Intent intent, String pkg) throws RemoteException {
            Log.i(TAG, String.format("Starting %s in package %s", intent, pkg));
            currentPackage = pkg;
            currentIntent = intent;
            return true;
        }

        /**
         * The system is trying to return to an activity. Return true to allow it to be resumed
         * as normal, or false to cancel/reject this activity.
         */
        @Override
        public boolean activityResuming(String pkg) throws RemoteException {
            Log.i(TAG, String.format("Resuming activity in package %s", pkg));
            return true;
        }

        /**
         * An application process has crashed (in Java). Return true for the normal error
         * recovery (app crash dialog) to occur, false to kill it immediately.
         */
        @Override
        public boolean appCrashed(String processName, int pid, String shortMsg, String longMsg, long timeMillis, String stackTrace) throws RemoteException {
            Log.i(TAG, String.format("Application %s (pid %s) crashed: %s\n\n%s", processName, pid, longMsg, stackTrace));
            return false;
        }

        /**
         * Early call as soon as an ANR is detected.
         */
        @Override
        public int appEarlyNotResponding(String processName, int pid, String annotation) throws RemoteException {
            Log.i(TAG, String.format("Early warning about application %s (pid %d) not responding: %s", processName, pid, annotation));
            return 0;
        }

        /**
         * An application process is not responding. Return 0 to show the "app not responding"
         * dialog, 1 to continue waiting, or -1 to kill it immediately.
         */
        @Override
        public int appNotResponding(String processName, int pid, String processStats) throws RemoteException {
            Log.i(TAG, String.format("Application %s (pid %s) is not responding: %s", processName, pid, processStats));
            return 1;
        }

        /**
         * The system process watchdog has detected that the system seems to be hung. Return 1 to
         * continue waiting, or -1 to let it continue with its normal kill.
         *
         * Available on API level >=18.
         */
        @Override
        public int systemNotResponding(String msg) throws RemoteException {
            Log.i(TAG, String.format("System is not responding: %s", msg));
            return 1;
        }
    }

    @Override
    public void run() {
        Log.i(TAG, "Monitor starting");

        IActivityManager am = ActivityManagerNative.getDefault();

        try {
            am.setActivityController(new ActivityController());

            synchronized (this) {
                while (!isInterrupted()) {
                    wait();
                }
            }
        }
        catch (InterruptedException e) {
            // Okay
        }
        catch (RemoteException e) {
            e.printStackTrace();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            Log.i(TAG, "Monitor stopping");

            try {
                am.setActivityController(null);
            }
            catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void peek(MessageWritable writer) {
        // Report current activity to the writer
    }
}
